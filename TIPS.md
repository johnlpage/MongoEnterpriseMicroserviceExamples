#Enable slow query logging to logfile

`db.setProfilingLevel(0,20); `

# Enable slow query logging to collection
#View latest database log entries from shell

`db.setProfilingLevel(1,1);`

```
var log = db.adminCommand({getLog:"global"})
for( entry of log.log ) {
   let logEntry = JSON.parse(entry);
   console.log(logEntry);
}
```

```
db.adminCommand({logRotate: "server"})
```


Using jq to modify the json file
```angular2html
jq -c '.vehicle = { vehicleid,make,model,colour } | del(.vehicleid,.make,.model,.colour)' mot1M.json 
```


```angular2html


mkSearchIndex = { "createSearchIndexes" : "inspections",
"indexes":[{  "name": "default",
"type": "search", 
"definition": { "mappings" : { "dynamic" : true}}
}]}

// Or dropSearchIndex
// or UpdateSearchIndex

updateSearchIndex: "<collection name>",
    id: "<index Id>",
        name: "<index name>",
            definition: {
            /* search index definition fields */
            }
            
db.aggregate({$listSearchIndexes:true
```