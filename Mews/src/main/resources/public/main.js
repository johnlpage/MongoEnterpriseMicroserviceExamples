/* THIS IS ONLY INTENDED TO DEMONSTRATE THE BACKEND NOT TO BE A REFERENCE FRONTEND  */

async function fetchCollectionInfo() {
    try {
        const collectionInfo = {};
        // Replace with your actual API endpoint
        let queryableFields = await fetch("/dummyapi/queryableFields.json");
        collectionInfo.queryableFields = await queryableFields.json();
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
    let index = key.indexOf(".");
    parts = key.split(".");
    for (let part of parts) {
        doc = doc[part];
    }
    val = doc;

    // If it's a date ending in midnight strip the time
    if (typeof val == "string") {
        val = val.replace("T00:00:00.000+00:00", "");
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
            },
            // Convert the data to a JSON string
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
            request.projection[context.gridFields[column]] = true;
        }

        const options = {
            method: "POST", // Specify the method as POST
            headers: {
                "Content-Type": "application/json", // Set the content type to JSON
            },
            // Convert the data to a JSON string
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
            },
            // Convert the data to a JSON string
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

function onLoad() {
    const app = Vue.createApp({
        data() {
            return {
                choices: {},
                queryableFields: {}, // Stores the list of items
                gridFields: {},
                queryResults: [],
                selectedDoc: {},
                isQuerying: false,
                fulltext: "test"
            };
        },
        computed: {
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
                                }
                                rval[choice] = {[op]: val};
                            } else {
                                if (typeof this.queryableFields[choice] == "number") {
                                    val = Number(val);
                                }
                                rval[choice] = val;
                            }
                        }
                    }
                    return rval;
                },
            },
            searchQuery: {
                /* ATLAS SEARCH QUERY */
                get() {
                    return {index: "default", text: {query: this.fulltext, path: {"wildcard": "*"}}}
                }
            }
        },
        mounted() {
            // Fetch data when the component is mounted.
            fetchCollectionInfo().then((data) => {
                this.queryableFields = data.queryableFields || {};
                this.gridFields = data.gridFields || {};
            });
        },
        methods: {
            runGridQuery() {
                this.isQuerying = true;
                runGridQuery(this);
            },
            runGridSearch() {
                this.isQuerying = true;
                runGridSearch(this);
            },
            fetchDocument(document) {
                fetchDocument(this, document);
            },
            formatForGrid,
        },
    });

    // Mount the Vue application
    app.mount("#app");
}
