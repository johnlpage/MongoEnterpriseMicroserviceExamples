# Service 1

Service 1 Loads data into a defined structure / Model
It streams in JSON and loads it as efficiently as possible

start with ` mvn spring-boot:run`

Send data with cURL - You caould also have it just read a file

```
  curl -X POST http://localhost:8080/vehicles/inspections -H "Content-Type: application/json" -T ~/mot2022.json     
  ```



Send a Single Doc recording history and update batch id

```
  curl -X POST "http://localhost:8080/vehicles/inspections?futz=true&updateStrategy=UPDATEWITHHISTORY" -H "Content-Type: application/json" -T ~/mot1.json     
  ```


the motupdates endpoint modifies the data as it's read to simulate getting a new version.

```
  curl -X POST "http://localhost:8080/vehicles/inspections?futz=true&updateStrategy=UPDATE" -H "Content-Type: application/json" -T ~/mot1M.json  
  ```

  ```
 curl http://localhost:8080/vehicles/inspections/1950521387| jq
  ```


```
curl -s http://localhost:8080/vehicles/inspections/model/POLO| jq
```

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
    "sort" : { "model" : 1}
}' | jq
```



```
curl -s -X POST http://localhost:8080/vehicles/inspections/query -H "Content-Type: application/json" \
-d \
'
{
    "filter": {
        "model": "POLO"
    },
    "limit" : 2
}' | jq
```


curl -s -X POST http://localhost:8080/vehicles/inspections/query -H "Content-Type: application/json" \
-d \
'
{
"filter": { },
"limit" : 2,
"projection": {
"make":1,
"model":1
},
"sort" : { "model" : 1}
}' | jq