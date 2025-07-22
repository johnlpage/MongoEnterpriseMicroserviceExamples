/* THIS IS ONLY INTENDED TO DEMONSTRATE THE BACKEND NOT TO BE A REFERENCE FRONTEND  */
const {createApp, defineComponent, h} = Vue;


const FormViewer = defineComponent({
    name: 'FormViewer',
    props: {
        data: {type: [Object, Array, String, Number, Boolean, null], required: true},
        level: {type: Number, default: 0},
        fieldName: {type: String, default: ''}
    },
    setup(props) {
        function getLevelClass() {
            return 'level-' + props.level;
        }

        const isObj = val => val && typeof val === 'object' && !Array.isArray(val);

        return () => {
            // Primitive or null
            if (props.data === null || ['string', 'number', 'boolean'].includes(typeof props.data)) {
                return h('div', {class: ['field-row', getLevelClass()]}, [
                    props.fieldName
                        ? h('label', {class: 'field-label'}, props.fieldName + ':')
                        : null,
                    h('span', {
                        class: 'field-value',
                        style: {background: '#f4f8ff'}
                    }, String(props.data))
                ]);
            }

            // Array
            if (Array.isArray(props.data)) {
                // Array of primitives
                if (!props.data.length || typeof props.data[0] !== 'object') {
                    return h('div', {class: ['array-block', 'scroll-array', getLevelClass()]}, [
                        props.fieldName
                            ? h('label', {class: 'field-label'}, props.fieldName)
                            : null,
                        ...props.data.map((item) =>
                            h('div', {class: 'field-row', style: 'margin-bottom:4px'}, [
                                h('span', {class: 'field-value', style: {background: '#f4f8ff'}}, String(item))
                            ])
                        ),
                        props.data.length === 0 && h('span', {style: 'color:#888'}, 'Empty array')
                    ]);
                } else {
                    // Array of objects or nested arrays
                    return h('div', {class: ['array-block', 'scroll-array', getLevelClass()]}, [
                        props.fieldName
                            ? h('label', {class: 'field-label'}, props.fieldName)
                            : null,
                        ...props.data.map((item) =>
                            h(FormViewer, {data: item, level: props.level + 1})
                        ),
                        props.data.length === 0 && h('span', {style: 'color:#888'}, 'Empty array')
                    ]);
                }
            }

            // Object
            if (isObj(props.data)) {
                return h('div', {class: ['object-block', getLevelClass()]}, [
                    props.fieldName
                        ? h('label', {class: 'field-label'}, props.fieldName)
                        : null,
                    ...Object.keys(props.data).map(key =>
                        h(FormViewer, {
                            data: props.data[key],
                            fieldName: key,
                            level: props.level + 1,
                            key
                        })
                    ),
                    !Object.keys(props.data).length && h('div', {style: 'color:#888'}, 'Empty object')
                ]);
            }

            // fallback display
            return h('span', String(props.data));
        };
    }
});

async function fetchCollectionInfo() {
    try {
        const collectionInfo = {};
        // Replace with your actual API endpoint
        let queryableFields = await fetch("/dummyapi/queryableFields.json");
        const qFields = await queryableFields.json();

        collectionInfo.queryableFields = {}
        collectionInfo.labels = {}
        for (f in qFields) {
            const [a, b] = f.split("=");
            if (b) {
                collectionInfo.queryableFields[b] = qFields[f];
                collectionInfo.labels[b] = a
            } else {
                collectionInfo.queryableFields[a] = qFields[f];
                collectionInfo.labels[a] = a;
            }
        }

        let gridFields = await fetch("/dummyapi/gridFields.json");
        collectionInfo.gridFields = await gridFields.json();
        return collectionInfo;
    } catch (error) {
        console.error("Error fetching data:", error);
    }
}

//Apply formatting
function formatForGrid(doc, key) {
    // walk through key getting each part
    let val = "";
    try {
        let index = key.indexOf(".");
        parts = key.split(".");
        parts = parts.filter(part => part != "payload"); //Payload needed in projection
        //But not returned
        for (let part of parts) {
            if (part == "$") {
                doc = doc[0]
            } else if (Array.isArray(doc)) {
                // We have selected an Array field, probably an array of objects
                // Easy enough to do and we don't want the matching one we want all of them
                // Let's assume it's a simple case
                const field = parts[parts.length - 1];
                doc = doc.map(item => item[field]).filter(x => x).join(",");
                break;
            } else {
                doc = doc[part];
            }
        }

        val = doc;

        // If it's a date ending in midnight strip the time
        if (typeof val == "string") {
            val = val.replace("T00:00:00.000+00:00", "");
        }
    } catch (error) {
        console.error("Error mapping data:", error.message);
    }
    return val;
}


//Given the grid fields, fetch the whole document - first grid field is the key.

async function fetchDocument(context, document) {
    try {
        // This is a POST to let us post a query for porocessing
        const queryEndpoint = "/api/inspections/query";
        const request = {};
        const idField = Object.values(context.gridFields)[0];
        const idValue = document[idField];
        request.filter = {[idField]: idValue};
        request.limit = 1;

        const options = {
            method: "POST", // Specify the method as POST
            headers: {
                "Content-Type": "application/json", // Set the content type to JSON
            }, // Convert the data to a JSON string
            body: JSON.stringify(request),
        };

        //Could use fetch by ID here but using generic MDB endpoint instead

        const find = await fetch(queryEndpoint, options);
        if (find.status > 300) {
            context.selectedDoc = {Error: await find.json()};
        }

        const result = await find.json();
        context.selectedDoc = result[0];
    } catch (error) {
        context.selectedDoc = {Error: error};
    }
    context.isQuerying = false;
}

async function runGridQuery(context) {
    try {
        // Clear the grid context.
        context.queryResults = [];
        context.selectedDoc = {Message: "Query is Running"};
        // This is a POST to let us post a query for processing
        const queryEndpoint = "/api/inspections/query";
        const request = {};
        request.filter = context.mongoQuery;

        // Request only the fields we need, don't rename them though as that will stop
        // Springs mapping to object from working.

        request.projection = {};
        for (let column in context.gridFields) {
            let projectField = context.gridFields[column]
            //No support for a.$.b in MongoDB after 4.4. for some reason :shrug:
            projectField = projectField.replace(/\$.*/, '$');
            request.projection[projectField] = true;
        }

        const options = {
            method: "POST", // Specify the method as POST
            headers: {
                "Content-Type": "application/json", // Set the content type to JSON
            }, // Convert the data to a JSON string
            body: JSON.stringify(request),
        };
        const find = await fetch(queryEndpoint, options);
        if (find.status > 300) {
            context.selectedDoc = {Error: await find.json()};
        }
        context.queryResults = await find.json();

        context.selectedDoc = {};
    } catch (error) {
        context.selectedDoc = {Error: error};
    }
    context.isQuerying = false;
}

async function runGridSearch(context) {
    try {
        // Clear the grid context.
        context.queryResults = [];
        context.selectedDoc = {Message: "Atlas Search is Running"};
        // This is a POST to let us post a query for processing
        const queryEndpoint = "/api/inspections/search";
        const request = {};
        // request.filter = context.mongoQuery;

        // Request only the fields we need, don't rename them though as that will stop
        // Springs mapping to object from working.
        request.search = context.searchQuery;

        request.projection = {};
        for (let column in context.gridFields) {
            request.projection[context.gridFields[column]] = true;
        }

        const options = {
            method: "POST", // Specify the method as POST
            headers: {
                "Content-Type": "application/json", // Set the content type to JSON
            }, // Convert the data to a JSON string
            body: JSON.stringify(request),
        };

        console.log(request);

        const find = await fetch(queryEndpoint, options);
        if (find.status > 300) {
            context.selectedDoc = {Error: await find.json()};
        }
        context.queryResults = await find.json();

        context.selectedDoc = {};
    } catch (error) {
        context.selectedDoc = {Error: error};
    }
    context.isQuerying = false;
}

function isNumberOrDate(value) {
    if (typeof value == "number") {
        return true;
    }
    let parsedDate = new Date(value);
    if (!isNaN(parsedDate)) return true;

    return false;
}

function onLoad() {
    const app = Vue.createApp({
        components: {FormViewer},
        data() {
            return {
                choices: {}, queryableFields: {}, // Stores the list of items
                gridFields: {}, labels: {}, queryResults: [], selectedDoc: {}, isQuerying: false, fulltext: "test",
                showAlt: false
            };
        }, computed: {
            mongoQuery: {
                get() {
                    // Change < and > and Not
                    //And remove empty things
                    const rval = {};
                    for (choice in this.choices) {
                        let val = this.choices[choice];
                        if (val != "") {
                            const opmap = {"<": "$lt", ">": "$gt"};
                            if (opmap[val[0]]) {
                                let op = opmap[val[0]];
                                val = val.slice(1);
                                if (typeof this.queryableFields[choice] == "number") {
                                    val = Number(val);
                                } else if (val.length > 9) {

                                    let parsedDate = new Date(val);
                                    if (!isNaN(parsedDate)) {
                                        val = {"$date": parsedDate.toISOString()};
                                    }
                                }
                                rval[choice] = {[op]: val};
                            } else {
                                if (typeof this.queryableFields[choice] == "number") {
                                    val = Number(val);
                                } else if (val.length > 9) {
                                    let parsedDate = new Date(val);
                                    if (!isNaN(parsedDate)) {
                                        val = {"$date": parsedDate.toISOString()};
                                    }
                                }
                                rval[choice] = val;
                            }
                        }
                    }
                    return rval;
                },
            }, searchQuery: {
                /* ATLAS SEARCH QUERY */
                get() {
                    let rval = {index: "default"}
                    let textClause = {}
                    if (this.fulltext) {
                        textClause = {text: {query: this.fulltext, path: {"wildcard": "*"}}}
                    }
                    let must = []
                    if (Object.keys(this.mongoQuery).length > 0) {
                        let mongoQuery = JSON.parse(JSON.stringify(this.mongoQuery)) // Deep copy

                        for (let f in mongoQuery) {
                            // Support for > , < , Dates etc. in Search would go here.

                            if (isNumberOrDate(mongoQuery[f])) {
                                must.push({
                                    equals: {
                                        path: f, value: mongoQuery[f]
                                    }
                                })
                            } else {
                                // FOr now it ignores objects liek $gt, $lt or dates
                                // Also you can use equals with strings only with a keyword analyzer
                                asString = mongoQuery[f].toString()
                                if (asString.startsWith("[object") == false) {
                                    must.push({
                                            text: {
                                                path: f, query: asString
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }

                    if (must.length > 0) {
                        if (Object.keys(textClause).length > 0) {
                            must.push(textClause);
                        }
                        rval = {...rval, compound: {must}}
                    } else if (Object.keys(textClause).length > 0) {
                        rval = {...rval, ...textClause}
                    }


                    return rval;
                }
            }
        }, mounted() {
            // Fetch data when the component is mounted.
            fetchCollectionInfo().then((data) => {
                this.queryableFields = data.queryableFields || {};
                this.gridFields = data.gridFields || {};
                this.labels = data.labels || {};
            });
        }, methods: {
            runGridQuery() {
                this.isQuerying = true;
                runGridQuery(this);
            }, runGridSearch() {
                this.isQuerying = true;
                runGridSearch(this);
            }, fetchDocument(document) {
                fetchDocument(this, document);
            }, formatForGrid,
        },
    });

    // Mount the Vue application
    app.mount("#app");
}
