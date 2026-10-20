# memex — MongoDB Enterprise Microservice Examples

A Spring Boot 3.4 / Java 17 microservice demonstrating enterprise MongoDB
patterns: streaming bulk load, ACID-transaction change history, dynamic
query endpoints with cost estimation, Atlas Search, Kafka ingestion, and
high-performance downstream data streaming.

The example domain is UK MOT vehicle inspections — but the architecture is
generic. Code generation scaffolds a complete REST API (controller, services,
repository) from a JSON sample file, so you can point memex at your own data
and get a working microservice in minutes.

---

## Quick start

**Prerequisites:** Java 17+ and Maven 3.8+.

### Run the example default VehicleInspection service

Generate test data, or use your own JSON (see [Generate a new entity from a JSON sample](#generate-a-new-entity-from-a-json-sample) below):

```shell
cd DataGen
mvn clean package
java -jar target/DataGen-1.0.jar Inspections 1000000 ../mot.json
```

See `DataGen/README.md` for full DataGen documentation.

Build and run the service:

```shell
cd ../memex
mvn clean package
java -jar target/memex-1.0.jar
```

At startup the preflight check creates the `vehicleinspection` collection,
indexes, and an Atlas Search index (configurable in `application.properties`).

Load data:

```shell
curl -s -X POST "http://localhost:8080/api/inspections?updateStrategy=REPLACE" \
  -H "Content-Type: application/json" -T mot.json
```

Open http://localhost:8080/ for the web UI.

### Generate a new entity from a JSON sample

Point memex at your own data. First scaffold the full REST API (model,
repository, services, controller), then replace the generic model with
typed fields inferred from your JSON:

```shell
# Step 1: Generate the full scaffold (model + repo + services + controller)
mvn generate-sources \
  -Pgenerate-entity \
  -Dentity=MotorClaim \
  -Dplural=motorclaims \
  -DidFieldName=claimId \
  -DidType=Long

# Step 2: Replace the generic model with typed fields from your data
mvn generate-sources \
  -Pgenerate-models-from-json \
  -DjsonFile=../DataGen/mot.json \
  -DbasePackage=com.johnlpage.memex \
  -Dentity=MotorClaim \
  -DidFieldName=claimId

mvn clean package
java -jar target/memex-1.0.jar
```

This produces a full REST API at `/api/motorclaims`. The `-DidFieldName`
must exactly match the id key in your JSON (case and underscores). If the
key is `_id`, pass any Java identifier you like.

### Generate a generic entity (no sample data)

Start with a scaffold, then write the model by hand to match your data:

```shell
# Scaffold the full REST API
mvn generate-sources \
  -Pgenerate-entity \
  -Dentity=Customer \
  -Dplural=customers \
  -DidFieldName=customerId \
  -DidType=Long

# Now edit src/main/java/.../Customer/model/Customer.java
# to add your typed fields instead of relying on the payload map
```

The generated model includes a `payload` map (`@JsonAnySetter`/`@JsonAnyGetter`)
that captures any fields not explicitly declared. Add your own typed
properties alongside it — `private String name;`, `private LocalDate dateOfBirth;`
— and `payload` catches everything else. The repository, services, and
controller are ready to go.

```shell
mvn clean package
java -jar target/memex-1.0.jar
```

Your Customer API is at `/api/customers` with the same endpoint set.

### Delete a generated entity

```shell
mvn generate-sources \
  -Pgenerate-entity \
  -Dentity=Customer \
  -DidType=Long \
  -Ddelete
```

---

## REST API reference

Every memex entity exposes the same set of endpoints. The default entity
(`VehicleInspection`) lives at `/api/inspections`. Generated entities take
the plural name you supply (`-Dplural=customers` → `/api/customers`).

### POST /api/inspections — Bulk load from JSON stream

Reads a stream of JSON objects (JSON array *or* bare objects, optionally
newline-separated) and loads them into MongoDB. Multi-threaded: parses the
next batch while the previous batch writes.

| Parameter | Values | Default | Description |
|---|---|---|---|
| `updateStrategy` | `REPLACE`, `UPDATE`, `UPDATEWITHHISTORY`, `INSERT` | `REPLACE` | How to handle existing documents (see strategies below) |
| `futz` | `true`, `false` | `false` | If true, injects `VehicleInspectionPreWriteTriggerService` which randomly modifies ~1% of documents: adjusts mileage by 1%, flips pass/fail, reassigns to a new testid, or marks for deletion — letting you test updates and history with real-looking changes |

**Update strategies:**

- **`REPLACE`** — Replaces the whole document. If nothing changed, the server
  detects the no-op and skips disk I/O and oplog entries. Most efficient for
  bulk initial load.
- **`UPDATE`** — Converts each document to a `$set` of individual fields.
  Only changed fields appear in the oplog, reducing oplog volume for
  frequent small updates across many documents.
- **`UPDATEWITHHISTORY`** — Like `UPDATE` but also captures previous values
  in a `__previousValues` field and records changes in a `_history`
  collection (see asOf endpoint).
- **`INSERT`** — Inserts without checking for existing documents. Fastest for
  initial bulk loads. Duplicate keys throw errors.

If the model has a `@DeleteFlag`-annotated field with a non-null value, the
document is **deleted** instead of upserted.

```shell
curl -s -X POST "http://localhost:8080/api/inspections?updateStrategy=REPLACE" \
  -H "Content-Type: application/json" -T data.json
```

**Response:**
```json
{
  "updates": 954,
  "deletes": 0,
  "inserts": 46,
  "success": true,
  "message": ""
}
```

### GET /api/inspections/id/{id} — Get by ID

Fetches a single document by its `@Id` field.

```shell
curl -s http://localhost:8080/api/inspections/id/1950521387
```

Returns `200` with the document, or `404`.

### GET /api/inspections/model/{model} — Query by example (paginated)

Spring Data "Query by Example" — pass a field value, get matching documents.
Supports paging via `page` (0-based) and `size`.

```shell
curl -s "http://localhost:8080/api/inspections/model/POLO?page=0&size=5"
```

**Response:**
```json
{
  "content": [ ... ],
  "page": 0,
  "size": 5,
  "hasNext": true
}
```

### POST /api/inspections/byExample — Query by example with body

Same as above, but the example fields are sent as a JSON body instead of a
path variable. Useful when you want to match on multiple fields.

```shell
curl -s -X POST "http://localhost:8080/api/inspections/byExample?page=0&size=5" \
  -H "Content-Type: application/json" \
  -d '{"vehicle": {"make": "FORD"}}'
```

### POST /api/inspections/query — Raw MongoDB query

Accepts a MongoDB query document (similar to the deprecated MongoDB Cloud
Data API). Allows the front-end or middleware to define queries dynamically
without a new endpoint per use case.

**Request body:**

| Field | Type | Default | Description |
|---|---|---|---|
| `filter` | object | `{}` | MongoDB query filter |
| `projection` | object | — | Fields to include (`1`) or exclude (`0`) — don't mix |
| `sort` | object | — | Sort specification |
| `skip` | number | `0` | Documents to skip |
| `limit` | number | `1000` | Max documents to return |

```shell
curl -s -X POST http://localhost:8080/api/inspections/query \
  -H "Content-Type: application/json" \
  -d '{
    "filter": {"model": "POLO"},
    "limit": 2,
    "projection": {"make": 1, "model": 1},
    "sort": {"make": 1}
  }'
```

Field names in the filter are mapped from MongoDB storage names to Java
field names where they differ. The costing engine estimates query
efficiency before executing (see Query Costing below).

### POST /api/inspections/search — Atlas Search

Passes the request body directly to MongoDB Atlas Search (Lucene). Requires
an Atlas Search index on the collection (created automatically at startup
when `memex.preflight.createSearchIndexes=true`).

```shell
curl -X POST http://localhost:8080/api/inspections/search \
  -H "Content-Type: application/json" \
  -d '{
    "search": {
      "index": "default",
      "text": {
        "query": "test",
        "path": {"wildcard": "*"}
      }
    }
  }'
```

Also supports `projection`, `skip`, `limit` in the same way as the query
endpoint.

### GET /api/inspections/json — Stream all records (object-mapped)

Streams every document as newline-delimited JSON using Spring Data object
mapping (`Document → Model Object → JSON`). Suitable for moderate volumes.

```shell
curl -s http://localhost:8080/api/inspections/json
```

### GET /api/inspections/jsonnative — Stream all records (native BSON)

Streams every document as newline-delimited JSON using raw BSON to JSON
conversion, bypassing object creation. Uses approximately half the CPU of
the object-mapped stream, at the cost of having to specify a hard-coded
projection. The default VehicleInspection projection is:

```json
{
  "testid": "$_id",
  "testdate": 1, "testclass": 1, "testtype": 1, "testresult": 1,
  "testmileage": 1, "postcode": 1, "fuel": 1, "capacity": 1,
  "firstusedate": 1, "faileditems": 1, "vehicle": 1
}
```

```shell
curl -s http://localhost:8080/api/inspections/jsonnative
```

### GET /api/inspections/asOf — Point-in-time query

Returns a document as it existed at a specific date/time. Requires data to
have been loaded with `updateStrategy=UPDATEWITHHISTORY`.

| Parameter | Format | Example |
|---|---|---|
| `id` | Long | `1` |
| `asOfDate` | `yyyyMMddHHmmss` | `20250325123007` |

```shell
curl "http://localhost:8080/api/inspections/asOf?id=1&asOfDate=20250325123007"
```

Load three versions first to see the history in action:

```shell
curl -X POST "http://localhost:8080/api/inspections?updateStrategy=UPDATEWITHHISTORY" \
  -H "Content-Type: application/json" -T v1.json
date +"%Y%m%d%H%M%S"   # capture this
sleep 1
curl -X POST "http://localhost:8080/api/inspections?updateStrategy=UPDATEWITHHISTORY" \
  -H "Content-Type: application/json" -T v2.json
date +"%Y%m%d%H%M%S"
curl "http://localhost:8080/api/inspections/asOf?id=1&asOfDate=<earlier-timestamp>"
```

### GET /api/vehicles/id/{id} — Vehicle with inspections

Demonstrates an aggregation-based lookup — returns a vehicle document with
its inspection history embedded as an array.

```shell
curl -s http://localhost:8080/api/vehicles/id/1950521387
```

---

## Kafka ingestion

When `memex.kafkaexmple.enabled=true` (default `false`), the service
consumes JSON messages from a Kafka topic and loads them in batches (with a
100ms flush timeout). Configure the broker in `application.properties`:

```properties
memex.kafkaexmple.enabled=true
spring.kafka.bootstrap-servers=localhost:9092
```

The consumer class is `VehicleInspectionKafkaConsumer`.

---

## Triggers — intercepting the load pipeline

When calling `loaderService.loadFromJsonStream()` you can pass two optional
trigger services:

- **Pre-write trigger** — called once per object before writing. In the
  VehicleInspection example, `futz=true` injects
  `VehicleInspectionPreWriteTriggerService`, which randomly tweaks ~1% of
  documents: nudges mileage by 1%, flips pass/fail, generates a new testid,
  or marks the record for deletion — letting you re-load the same file
  repeatedly and see updates, inserts, and deletes flow through the system.
- **Post-write trigger** — called inside an ACID transaction after each
  batch is written but before commit. Receives the batch of documents, the
  bulk write result, and a unique update ID. The
  `HistoryTriggerService` uses this to record field-level changes in a
  `_history` collection.

Implement `PreWriteTriggerService<T>` or `PostWriteTriggerService<T>` from
`com.johnlpage.memex.generics.service` to add your own logic.

---

## Query costing

`OptimizedMongoQueryRepository` includes a query cost estimator that scores
queries from **1** (fully index-covered) to **500** (collection scan) using
MongoDB's `explain()`. The score is cached per query shape (SHA-256 hash)
and re-evaluated every 1000 calls.

`VehicleInspectionQueryService` logs the cost of every query before running
it. You can use this to:

- **Reject** destructive queries
- **Log** expensive queries for later index tuning
- **Redirect** bad queries to a secondary or analytics node
- **Modify** queries — e.g. add a limited, indexed date range

---

## Adding your own entity

Memex includes two Maven code generators that scaffold a complete entity
from scratch.

### Generate from a JSON sample file

Run both generators: first `generate-entity` to scaffold the full REST API,
then `generate-models-from-json` to replace the generic model with typed
fields matching your data:

```shell
# Step 1: Full scaffold (model + repo + services + controller)
mvn generate-sources \
  -Pgenerate-entity \
  -Dentity=Customer \
  -Dplural=customers \
  -DidFieldName=customerId \
  -DidType=Long

# Step 2: Model with typed fields from your data (replaces Step 1's generic model)
mvn generate-sources \
  -Pgenerate-models-from-json \
  -DjsonFile=../DataGen/mot.json \
  -DbasePackage=com.johnlpage.memex \
  -Dentity=Customer \
  -DidFieldName=customerId
```

Step 1 generates the full scaffold:

- `model/Customer.java` — generic model with `payload` map and `@Id`,
  `@Version`, `@DeleteFlag`
- `repository/CustomerRepository.java` — extends all the optimized repos
- `service/Customer*Service.java` — seven service classes (Query, JsonLoader,
  Downstream, History, HistoryTrigger, PreWriteTrigger, InvalidDataHandler)
- `controller/CustomerController.java` — all REST endpoints at `/api/customers`

Step 2 replaces only `model/Customer.java` with typed fields inferred from
your JSON, nested classes for embedded objects, `@JsonFormat` annotations
on temporal fields, and `@JsonProperty` annotations where field names don't
follow standard JavaBean conventions.

**Important:** `-DidFieldName` must exactly match the id key as it appears in
your JSON (including case and underscores) — unless that key is literally
`_id`. If the key is `_id`, pass any Java identifier you like. If it's
`claim_number`, pass `-DidFieldName=claim_number`. A mismatch silently
generates two separate fields — always inspect the generated model.

### Generate with a generic entity

If you don't have a sample file, generate a minimal, flexible entity with a
`payload` map for all fields:

```shell
mvn generate-sources \
  -Pgenerate-entity \
  -Dentity=Customer \
  -Dplural=customers \
  -DidFieldName=customerId \
  -DidType=Long
```

To remove it:

```shell
mvn generate-sources \
  -Pgenerate-entity \
  -Dentity=Customer \
  -DidType=Long \
  -Ddelete
```

### What you get

Both generators produce a complete REST API following the same endpoint
pattern as `/api/inspections`:

```
POST   /api/{plural}              — Bulk load from JSON stream
GET    /api/{plural}/id/{id}       — Get by ID
POST   /api/{plural}/byExample     — Query by example (paginated)
POST   /api/{plural}/query         — Raw MongoDB query
POST   /api/{plural}/search        — Atlas Search
GET    /api/{plural}/json          — Stream all (object-mapped)
GET    /api/{plural}/json/native   — Stream all (native BSON)
GET    /api/{plural}/asOf          — Point-in-time query
```

---

## Adding custom query endpoints

Beyond the dynamic query endpoint (`POST /api/{plural}/query`), the
repository pattern lets you add named query methods using Spring Data's
standard conventions.

### Derived query methods

In your repository interface, add methods following Spring Data's naming
conventions — Spring Data MongoDB generates the implementation:

```java
List<VehicleInspection> findByCapacityGreaterThan(Long engineCapacity);
List<VehicleInspection> findByVehicleColourAndVehicleModel(String colour, String model);
Slice<VehicleInspection> findByVehicleModel(String model, PageRequest page);
```

### @Query and @Update annotations

For explicit control, use `@Query` and `@Update`:

```java
@Query("{ 'vehicle.make': ?0, 'vehicle.model': ?1 }")
List<VehicleInspection> findByVehicleMakeAndModel(String make, String model);

@Query("{ 'testid' : ?0 }")
@Update("{ 'inc' : { 'testmileage' : ?1 } }")
void adjustTestMileage(Long testid, int increment);
```

### @Aggregation

For complex pipelines, use `@Aggregation`:

```java
@Aggregation(pipeline = {
    "{ '$match': { 'vehicle.model': ?0 } }",
    "{ '$group': { '_id': null, 'averageMileage': { '$avg': '$vehicle.mileage' } } }"
})
List<Document> findAverageMileageByModel(String model);
```

### You can also use…

- **Query by Example** — `POST /api/{plural}/byExample` in the controller
- **The raw query endpoint** — `POST /api/{plural}/query` for dynamically
  composed queries from the frontend
- **MongoTemplate / MongoCollection** directly in a custom service for
  anything that doesn't fit the above patterns

---

## Configuring the web UI

The Vue.js frontend (served at `/`) is driven by three static JSON files in
`src/main/resources/public/configapi/`:

### apiEndpoint.json

The base path of your entity's controller — the UI appends `/query` and
`/search`:

```json
"/api/inspections"
```

### gridFields.json

Column definitions for the results table. Keys are display labels, values
are MongoDB storage paths. The first entry must be the `@Id` field.

```json
{
  "Test Number": "testid",
  "Make": "vehicle.make",
  "Model": "vehicle.model",
  "Result": "testresult"
}
```

Fields stored in the `payload` map (from `@JsonAnySetter`) must be prefixed
with `payload.`:

```json
{
  "Company Number": "companyNumber",
  "Name": "payload.companyName"
}
```

### queryableFields.json

Query input definitions. Keys are field paths (optionally
`Label=storagePath`). Values determine the input type:

- `1` — numeric: free-text input, coerces to number, enables `<` `>`
- `["val1", "val2"]` — categorical: dropdown
- `""` or any other value — free-text string; the UI attempts date parsing
  for values longer than 9 characters

```json
{
  "vehicle.vehicleid": 1,
  "vehicle.make": ["FORD", "VOLKSWAGEN"],
  "vehicle.model": "",
  "testmileage": 1
}
```

**Date fields must use `""`, not `1`** — numeric coercion turns date strings
into `NaN` which becomes `null` in the query.

See `configapi/README.md` for full documentation and a "companies" worked
example.

---

## Preflight startup checks

On startup, `MongoDbPreflightCheckService` validates:

- MongoDB server version
- Required collections exist (created if `memex.preflight.createCollections=true`)
- Required indexes exist (created if `memex.preflight.createRequiredIndexes=true`)
- Atlas Search indexes exist (created if `memex.preflight.createSearchIndexes=true`)
- Schema validators can be applied (`memex.preflight.schemaOnCreate=false`)

Configure in `application.properties`:

```properties
memex.preflight.createRequiredIndexes=true
memex.preflight.createCollections=true
memex.preflight.createSearchIndexes=true
memex.preflight.schemaOnCreate=false
memex.preflight.dropAllCollections=false
```

---

## Architecture overview

```
Client / UI
    |
    v
Controller (@RestController)
    |
    v
Service (business logic, costing, triggers)
    |
    v
Repository (generic interfaces)
    |--- OptimizedMongoLoadRepository   — bulk write with strategies + triggers
    |--- OptimizedMongoQueryRepository  — native query + Atlas Search + costing
    |--- OptimizedMongoDownstreamRepository — raw BSON-to-JSON streaming
    |--- MongoHistoryRepository          — point-in-time history reconstruction
    |
    v
MongoDB
```

---

## See also

- [ClassDescription.md](ClassDescription.md) — detailed explanation of every
  class and interface in the codebase
- [SHARDING.md](SHARDING.md) — configuring shard key awareness in the write
  path
- [TIPS.md](TIPS.md) — handy MongoDB operations (profiling, log rotation,
  search indexes from the shell)

---

## Testing

Cucumber integration tests using Testcontainers for MongoDB are in
`src/test/java/.../cucumber/`. Run them with:

```shell
mvn test
```

---

## License

Apache 2.0 — take the code and do whatever you want with it.