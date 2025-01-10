/* THIS IS ONLY INTENDED TO DEMONSTRATE THE BACKEND NOT TO BE A REFERENCE FRONTEND  */ 

async function fetchCollectionInfo() {
    try {
        const collectionInfo = {}
        // Replace with your actual API endpoint
        let queryableFields = await fetch('/dummyapi/queryableFields.json');
        collectionInfo.queryableFields = await queryableFields.json();
        let gridFields = await fetch('/dummyapi/gridFields.json');
        collectionInfo.gridFields = await gridFields.json();
        return collectionInfo;

    } catch (error) {
        console.error('Error fetching data:', error);
    }
}

//Given the grid fields, fetch the whole document - first grid fied is the key.

async function fetchDocument(context,document) {
    try {
        // This is a POST to let us post a query for porocessing
            const queryEndpoint="/vehicles/inspections/query";
            const request = {}
            const idField = Object.values(context.gridFields)[0]
            const idValue = document[idField]
            request.filter = { [idField] : idValue }
            request.limit = 1;
    
            const options = {
                method: 'POST', // Specify the method as POST
                headers: {
                'Content-Type': 'application/json' // Set the content type to JSON
                },
                // Convert the data to a JSON string
                body: JSON.stringify(request)
            };
    
            //Could use fetch by ID here but using generic MDB endpoint instead

            const find = await fetch(queryEndpoint,options)
            if(find.status >300) {
                alert("Error Fetching Results" + JSON.stringify(await find.json()))
            }

            const result = await find.json();
            context.selectedDoc= result[0]

        }
        catch (error) {
            console.error('Error fetching data:', error);
        }
}

async function runGridQuery(context) {

    try {
    // This is a POST to let us post a query for porocessing
        const queryEndpoint="/vehicles/inspections/query";
        const request = {}
        request.filter = context.mongoQuery;


        const options = {
            method: 'POST', // Specify the method as POST
            headers: {
            'Content-Type': 'application/json' // Set the content type to JSON
            },
            // Convert the data to a JSON string
            body: JSON.stringify(request)
        };

        const find = await fetch(queryEndpoint,options)
        if(find.status >300) {
            alert("Error Fetching Results" + JSON.stringify(await find.json()))
        }
        context.queryResults= await find.json();
    }
    catch (error) {
        console.error('Error fetching data:', error);
    }
}

function onLoad() {
    const app = Vue.createApp({
        data() {
            return {
                choices: {},
                queryableFields: {}, // Stores the list of items
                gridFields: {},
                queryResults: [ ],
                selectedDoc: {}
            };
        },
        computed: {
            mongoQuery : {
                get() {
                    // Change < and > and Not
                    //And remove empty things
                    const rval = {}
                    for( choice in this.choices) {
                        let val = this.choices[choice];
                        if(val != "") {
                            //TODO Operators like  with >, < and !
                            rval[choice] = val
                        }
                    }
                    return rval;
                }
            }
        },
        mounted() {
            // Fetch data when the component is mounted.
            fetchCollectionInfo().then(data => {
                this.queryableFields = data.queryableFields || {};
                this.gridFields = data.gridFields || {};
            });
        },
        methods: {
            runGridQuery() { runGridQuery(this); },
            fetchDocument(document) { fetchDocument(this,document); }
        }
    });

    // Mount the Vue application
    app.mount('#app');

}