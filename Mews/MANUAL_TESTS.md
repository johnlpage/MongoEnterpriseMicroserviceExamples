# Manual test Examples

start the service with ` mvn spring-boot:run`

_the use of `| jq` is optional in these examples .. If installed the tool jq formats JSON output_

User GUI available on

`http://localhost:8080/index.html`

Load a JSON file, create or overwrite existing records

```
  curl -s -X POST "http://localhost:8080/vehicles/inspections?updateStrategy=REPLACE" -H "Content-Type: application/json" -T ~/mot.json     
```

Load a JSON file, only modify fields that are in the supplied file do not overwrite whole record, record only the
deltas in the transactions log (less network). The `futz=true` means call preWrite trigger to modify data for testing
changes

```
  curl -s -X POST "http://localhost:8080/vehicles/inspections?futz=true&updateStrategy=UPDATE" "-H "Content-Type: application/json" -T ~/mot.json     
```

Load a JSON file, apply only changes to data also record in a second collection the changes that were made to provide
a durable change history. `futz=true` used for testing.

```
  curl -s -X POST "http://localhost:8080/vehicles/inspections?futz=true&updateStrategy=UPDATWITHHISTORY" -H "Content-Type: application/json" -T ~/mot.json     
```

Fetch a single record by its ID value

  ```
 curl -s http://localhost:8080/vehicles/inspections/id/1950521387| jq
  ```

Fetch records matching a specific named and predefined attribute

```
curl -s http://localhost:8080/vehicles/inspections/model/POLO  | jq
```

Fetch records using a flexible MongoDB query in JSON format use the fields `filter`, `limit`, `skip`, `sort`
and `projection`

```
curl -s -X POST http://localhost:8080/vehicles/inspections/query -H "Content-Type: application/json" \
-d \
'
{
    "filter": {
        "model": "POLO"
    },
    "limit" : 2,
    "projection": {
      "make":1,
      "model":1
    },
    "sort" : { "make" : 1}
}' | jq
```

Extract all data using Spring primitives

```
curl -s -X GET http://localhost:8080/vehicles/inspections/json

```

Extract all data using database Native calls (Much faster and less CPU in appserver)

```
curl -s -X GET http://localhost:8080/vehicles/inspections/jsonNative

```