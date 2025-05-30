# Handy Tips working with MongoDB

These are notes the author uses now and then .

## Enable slow query logging to logfile

`db.setProfilingLevel(0,20); `

## Enable slow query logging to collection

`db.setProfilingLevel(1,1);`

## View latest database log entries from shell

```
var log = db.adminCommand({getLog:"global"})
for( entry of log.log ) {
   let logEntry = JSON.parse(entry);
   console.log(logEntry);
}
```

## Rotate Database logfile

```
db.adminCommand({logRotate: "server"})
```

## Using jq to modify a json file

```angular2html
jq -c '.vehicle = { vehicleid,make,model,colour } | del(.vehicleid,.make,.model,.colour)' mot1M.json 
```

## Using Search Indexes in Shell

```angular2html


mkSearchIndex = { "createSearchIndexes" : "inspections",
"indexes":[{  "name": "default",
"type": "search",
"definition": { "mappings" : { "dynamic" : true}}
}]}

// Or dropSearchIndex
// or UpdateSearchIndex

updateSearchIndex: "
<collection name>",
    id: "
    <index Id>",
        name: "
        <index name>",
            definition: {
            /* search index definition fields */
            }

            db.aggregate({$listSearchIndexes:true})

```