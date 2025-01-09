# Service 1

Service 1 Loads data into a defined structure / Model
It streams in JSON and loads it as efficiently as possible

start with ` mvn spring-boot:run`

Send data with cURL - You caould also have it just read a file

```
  curl -X POST http://localhost:8080/vehicles/inspections -H "Content-Type: application/json" -T ~/mot2022.json     
  ```


the motupdates endpoint modifies the data as it's read to simulate getting a new version.

```
  curl -X POST "http://localhost:8080/vehicles/inspections?futz=true&useUpdate=true" -H "Content-Type: application/json" -T ~/mot1M.json  
  ```

  ```
 curl http://localhost:8080/vehicles/inspections/89373| jq
  ```


```
curl -s http://localhost:8080/vehicles/inspections/model/POLO| jq
```