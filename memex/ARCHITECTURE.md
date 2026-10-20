# memex Architecture Reference

## Package map

```
com.johnlpage.memex
├── MemexApplication.java              -- Spring Boot entry point
├── config/                            -- Spring configuration
│   ├── AsyncLoadConfig.java           -- Thread pool for parallel bulk writes
│   ├── MongoConfig.java               -- MongoTemplate, transactions, custom converters
│   ├── MongoTypeMapperConfig.java     -- MongoDB type mapping overrides
│   ├── ObjectMapperConfig.java        -- Jackson ObjectMapper setup
│   ├── ValidationConfig.java          -- Jakarta Bean Validation provider
│   └── WebConfig.java                 -- Spring Data web support
├── util/                              -- Cross-cutting utilities
│   ├── AnnotationExtractor.java       -- Reflects custom annotations from model classes
│   ├── CustomAggregationOperation.java -- Wraps a raw JSON aggregation stage
│   ├── DeleteFlag.java                -- @DeleteFlag annotation (marks transient delete field)
│   ├── GlobalExceptionHandler.java    -- @ControllerAdvice error mapper
│   ├── MongoFieldPathMapper.java      -- Maps between Java field names and MongoDB storage paths
│   ├── MongoSchemaGenerator.java      -- Generates JSON Schema from a model class
│   ├── MongoVersionBean.java          -- Holds MongoDB server version string
│   ├── ObjectConverter.java           -- Recursive type coercion for payload values
│   ├── ObjectIdSerializer.java        -- Jackson serializer for ObjectId → hex string
│   └── UpdateStrategy.java            -- Enum: REPLACE, UPDATE, UPDATEWITHHISTORY, INSERT
├── generics/                          -- Reusable generic layer (the core of memex)
│   ├── dto/
│   │   └── PageDto.java               -- Wraps Spring Data Slice for JSON response
│   ├── repository/
│   │   ├── OptimizedMongoLoadRepository.java        -- Interface: bulk write methods
│   │   ├── OptimizedMongoLoadRepositoryImpl.java    -- Impl: smart updates, triggers, shard key
│   │   ├── OptimizedMongoQueryRepository.java       -- Interface: native query, search, costing
│   │   ├── OptimizedMongoQueryRepositoryImpl.java   -- Impl: dynamic queries, explain-based cost
│   │   ├── OptimizedMongoDownstreamRepository.java  -- Interface: native BSON streaming
│   │   ├── OptimizedMongoDownstreamRepositoryImpl.java -- Impl: JsonObject-based streaming
│   │   ├── MongoHistoryRepository.java              -- Interface: point-in-time queries
│   │   └── MongoHistoryRepositoryImpl.java          -- Impl: $lookup + reverse-merge pipeline
│   └── service/
│       ├── MongoDbJsonStreamingLoaderService.java   -- Stream parser + async batch dispatch
│       ├── MongoDbPreflightCheckService.java        -- Startup validation (indexes, collections, etc.)
│       ├── CollectionPreflightConfig.java           -- Interface: per-collection preflight config
│       ├── PreWriteTriggerService.java              -- Interface: pre-write hook (mutable/immutable)
│       ├── PostWriteTriggerService.java             -- Interface: post-write hook (inside transaction)
│       ├── HistoryTriggerService.java               -- Extends PostWriteTriggerService: field-level history
│       ├── InvalidDataHandlerService.java           -- Interface: handle bean validation failures
│       ├── DocumentHistory.java                     -- Model for the _history collection
│       └── DataLoadException.java                   -- Exception carrying partial load results
└── VehicleInspection/                 -- Example entity (replace with generated code)
    ├── model/
    │   ├── VehicleInspection.java      -- Root document model for vehicleinspection collection
    │   └── Vehicle.java                -- Embedded sub-document
    ├── repository/
    │   ├── VehicleInspectionRepository.java -- Extends all generic repos + custom queries
    │   └── VehicleRepository.java            -- @Aggregation-based vehicle lookup with inspections
    ├── service/
    │   ├── VehicleInspectionQueryService.java         -- Delegates queries with cost logging
    │   ├── VehicleInspectionJsonLoaderService.java     -- Wraps MongoDbJsonStreamingLoaderService
    │   ├── VehicleInspectionPreWriteTriggerService.java -- Example: random field mutation for testing
    │   ├── VehicleInspectionHistoryTriggerService.java  -- Extends HistoryTriggerService
    │   ├── VehicleInspectionHistoryService.java        -- Delegates history queries
    │   ├── VehicleInspectionDownstreamService.java     -- Delegates streaming
    │   ├── VehicleInspectionInvalidDataHandlerService.java -- Logs validation failures
    │   └── VehicleInspectionPreflightConfig.java       -- Collection/index/search index definitions
    ├── controller/
    │   └── VehicleInspectionController.java   -- REST endpoints at /api/inspections and /api/vehicles
    └── kafka/
        └── VehicleInspectionKafkaConsumer.java   -- Kafka → batch loader
```

---

## Layer overview

```
HTTP / Kafka
    │
    ▼
┌──────────────────────────────────────────────────┐
│  Controller  (e.g. VehicleInspectionController)  │
│  JSON in/out, HTTP status codes, streaming       │
└──────────────────┬───────────────────────────────┘
                   │
                   ▼
┌──────────────────────────────────────────────────┐
│  Vehicle-specific Services                       │
│  (thin wrappers: add logging, costing,           │
│   business rules, trigger wiring)                │
└──────────────────┬───────────────────────────────┘
                   │
                   ▼
┌──────────────────────────────────────────────────┐
│  Generic Services                                │
│  (reusable across entities)                      │
│  MongoDbJsonStreamingLoaderService               │
│  MongoDbPreflightCheckService                    │
└──────────────────┬───────────────────────────────┘
                   │
                   ▼
┌──────────────────────────────────────────────────┐
│  Generic Repository Interfaces + Implementations │
│  (the reusable layer — see below)                │
└──────────────────┬───────────────────────────────┘
                   │
                   ▼
┌──────────────────────────────────────────────────┐
│  Spring Data MongoRepository<T, ID>              │
│  (built-in: CRUD, derived queries, @Query, etc.) │
└──────────────────────────────────────────────────┘
```

---

## Generic repository interfaces

These four interfaces are the core reusable layer. Your entity repository
extends them all, gaining their capabilities automatically.

### OptimizedMongoLoadRepository\<T\>

**Package:** `generics.repository`

**Purpose:** High-performance bulk writes with configurable
update strategies, triggers, and shard-key awareness.

**Methods:**

```java
BulkWriteResult writeMany(
    List<T> items,
    Class<T> clazz,
    InvalidDataHandlerService<T> invalidDataHandler,
    UpdateStrategy updateStrategy,
    PostWriteTriggerService<T> postTrigger);

CompletableFuture<BulkWriteResult> asyncWriteMany(
    List<T> items,
    Class<T> clazz,
    InvalidDataHandlerService<T> invalidDataHandler,
    UpdateStrategy updateStrategy,
    PostWriteTriggerService<T> postTrigger);
```

**What it does (the impl):**

- Uses `MongoTemplate.bulkOps()` in unordered mode for throughput
- Supports four strategies (see README: REPLACE/UPDATE/UPDATEWITHHISTORY/INSERT)
- Smart `$set` pipeline for UPDATE/UPDATEWITHHISTORY — only changed fields are
  sent, computed by comparing the incoming object to the current DB document
- If `@DeleteFlag` field is true, issues a `deleteOne` instead
- Validates each document via Jakarta Bean Validation before writing
- If `postTrigger` is non-null, wraps writes in an ACID transaction and calls
  the trigger before commit
- If shard key fields are configured (via `CollectionPreflightConfig`), adds
  them to the query criteria for targeted shard routing
- Async version runs on the `loadExecutor` thread pool (configured in
  `AsyncLoadConfig`)

### OptimizedMongoQueryRepository\<T\>

**Package:** `generics.repository`

**Purpose:** Dynamic, runtime-defined MongoDB queries and Atlas Search,
with query cost estimation.

**Methods:**

```java
List<T> mongoDbNativeQuery(String jsonString, Class<T> clazz);
List<T> atlasSearchQuery(String jsonString, Class<T> clazz);
int costMongoDbNativeQuery(String jsonString, Class<T> clazz);
```

**What it does (the impl):**

- `mongoDbNativeQuery`: parses a JSON body with `filter`, `projection`,
  `sort`, `skip`, `limit` and runs it against the collection. Field names
  in the query are mapped from Java → MongoDB storage names via
  `MongoFieldPathMapper`. Default limit is 1000.
- `atlasSearchQuery`: passes the `search` field directly to
  `$search` aggregation stage. Also maps field paths in `projection`.
  Requires an Atlas Search index on the collection.
- `costMongoDbNativeQuery`: runs the query through `explain()` and
  assigns a score from 1 (index-covered) to 500 (collection scan).
  Results are cached per query shape (SHA-256 hash) and re-evaluated
  every 1000 calls. Cost tiers:

| Score | Meaning |
|---|---|
| 1        | Index-covered query (ideal) |
| 3        | Index used, not covered |
| 5        | Index used but suboptimal |
| 10       | Post-fetch filter applied |
| 10–100   | Sort without index support |
| 500      | Collection scan (worst) |

### OptimizedMongoDownstreamRepository\<T\>

**Package:** `generics.repository`

**Purpose:** High-performance JSON streaming for large data extracts,
bypassing object mapping.

**Method:**

```java
Stream<JsonObject> nativeJsonExtract(String formatRequired, Class<T> modelClazz);
```

**What it does (the impl):**

- Runs a `find()` against the collection with the given projection
- Uses the MongoDB driver's `JsonObjectCodec` to convert BSON directly to
  JSON strings, creating no intermediate Document or model objects
- Returns a `Stream<JsonObject>` — write each `jsonObject.getJson()` to
  your output stream
- This avoids 50-90% of the CPU overhead of the normal Spring Data path
  (BSON → Document → Model → ObjectMapper → JSON)
- The `formatRequired` string is a MongoDB projection document specifying
  which fields to include and optionally using `$`-prefixed field
  expressions (e.g. `"testid": "$_id"` to rename)

### MongoHistoryRepository\<T, I\>

**Package:** `generics.repository`

**Purpose:** Point-in-time document reconstruction from change history.

**Methods:**

```java
Stream<T> GetRecordByIdAsOfDate(I recordId, Instant asOf, Class<T> clazz);
Stream<T> GetRecordsAsOfDate(Criteria criteria, Instant asOf, Class<T> clazz);
```

**What it does (the impl):**

- Requires data loaded with `UPDATEWITHHISTORY` strategy (which populates
  a `{collection}_history` collection with `DocumentHistory` entries)
- Uses an aggregation pipeline:
  1. Fetches the current document
  2. `$lookup` to join history entries after the asOf timestamp
  3. Sorts history newest-first and rebuilds the document by reversing
     each change: for each `__previousValues` entry, the old field value
     is restored
  4. Handles nested objects via dot-notation flattening and `$setField`
  5. Handles array elements via MinKey-aware merge
- Returns a stream of reconstructed documents as they existed at the
  given point in time

---

## Generic service classes

### MongoDbJsonStreamingLoaderService\<T\>

**Package:** `generics.service`

**Purpose:** Parses an incoming JSON stream and dispatches batches to
`OptimizedMongoLoadRepository` asynchronously.

**Key method:**

```java
JsonStreamingLoadResponse loadFromJsonStream(
    InputStream inputStream,
    Class<T> type,
    InvalidDataHandlerService<T> invalidDataHandler,
    UpdateStrategy updateStrategy,
    PreWriteTriggerService<T> preTrigger,
    PostWriteTriggerService<T> postTrigger);
```

**What it does:**

- Uses Jackson's `JsonParser` to tokenize the stream object-by-object
  without loading it into memory
- Accepts JSON arrays and bare objects (newline-separated or not) —
  the parser finds matching `{...}` pairs
- Accumulates a batch (default 200 documents, configurable via
  `mongo.jsonloader.batch-size`)
- After each batch, calls `asyncWriteMany()` on the repository and
  immediately starts parsing the next batch — the write and parse
  overlap in time
- For each document, calls `preTrigger.modifyMutableDataPreWrite()` (or
  `newImmutableDataPreWrite()`) before adding to the batch
- Returns counts of inserts, updates, and deletes, plus a success flag
  and optional error message

### PreWriteTriggerService\<T\>

**Package:** `generics.service`

**Purpose:** Hook called per-document before writing. Use to augment,
validate, or modify data.

```java
public void modifyMutableDataPreWrite(T document) { }
public T newImmutableDataPreWrite(T document) { return document; }
```

Override the mutable or immutable variant depending on your model. The
VehicleInspection example overrides `modifyMutableDataPreWrite` to
randomly tweak mileage, flip pass/fail, assign a new testid, or flag
deletion when `futz=true`.

### PostWriteTriggerService\<T\>

**Package:** `generics.service`

**Purpose:** Hook called after each batch is written, inside the same
ACID transaction, before commit.

```java
public void postWriteTrigger(
    ClientSession session,
    BulkWriteResult result,
    List<T> records,
    Class<T> clazz,
    ObjectId updateId) { }
```

The `session` lets you see uncommitted writes. The `updateId` lets you
correlate changes across collections.

### HistoryTriggerService\<T\>

**Package:** `generics.service`
**Extends:** `PostWriteTriggerService<T>`

**Purpose:** Records field-level changes to a `{collection}_history`
collection. Used when `updateStrategy=UPDATEWITHHISTORY`.

- On insert: writes a creation record (no field values)
- On update: writes only the previous values of changed fields, keyed by
  `updateId` and timestamp
- On delete: stores the entire deleted document in the history
- Writes are done using the same transactional session as the main
  batch write

### InvalidDataHandlerService\<T\>

**Package:** `generics.service`

**Purpose:** Callback for documents that fail Jakarta Bean Validation.

```java
public boolean handleInvalidData(
    T document,
    Set<ConstraintViolation<T>> violations,
    Class<T> clazz);
```

Return `true` to load despite violations, `false` to skip. Default
implementation logs a warning and skips.

### MongoDbPreflightCheckService

**Package:** `generics.service`
**Implements:** `ApplicationRunner`

**Purpose:** Validates the MongoDB environment at startup and creates
required database objects. Configured via `application.properties`:

| Property | Default | Effect |
|---|---|---|
| `memex.preflight.createCollections` | `true` | Create collections if missing |
| `memex.preflight.createRequiredIndexes` | `true` | Create indexes if missing |
| `memex.preflight.createSearchIndexes` | `true` | Create Atlas Search indexes |
| `memex.preflight.schemaOnCreate` | `false` | Apply schema validator on create |
| `memex.preflight.dropAllCollections` | `false` | Drop everything on startup |

The checks are driven by `CollectionPreflightConfig` implementations
registered as Spring Beans. Each config declares:

- `getCollectionName()` — MongoDB collection name
- `getModelClass()` — Java model class
- `getIndexDefinitions()` — list of index specs
- `getSearchIndexDefinitions()` — list of Atlas Search index specs
- `getShardKeyFields()` — shard key fields for targeted writes (see SHARDING.md)

### CollectionPreflightConfig

**Package:** `generics.service`

**Purpose:** Interface each entity implements to tell the preflight
checker what database objects it needs. See `VehicleInspectionPreflightConfig`
for an example.

### DataLoadException

**Package:** `generics.service`

**Purpose:** Thrown when a batch partially fails. Carries the counts of
successful updates, inserts, and deletes so the caller can report a
meaningful partial-success response (HTTP 207 Multi-Status).

### DocumentHistory

**Package:** `generics.service`

**Purpose:** Model class for the `{collection}_history` collection.
Stores the `updateId`, timestamp, and a map of field → previous value.
Used internally by `HistoryTriggerService` and `MongoHistoryRepository`.

---

## DTOs

### PageDto\<T\>

**Package:** `generics.dto`

**Purpose:** Wraps a Spring Data `Slice<T>` for JSON serialization.
Exposes `content`, `page`, `size`, and `hasNext`. Avoids the overhead
of `Page` (which does a count query).

---

## Util classes

### UpdateStrategy (enum)

**Package:** `util`

Values: `REPLACE`, `UPDATE`, `UPDATEWITHHISTORY`, `INSERT`

Controls how `OptimizedMongoLoadRepository` writes documents. Passed as
a request parameter to the bulk-load endpoint.

### DeleteFlag (annotation)

**Package:** `util`

**Target:** Fields

Marks a transient boolean field as the delete signal. When true during
load, the document is deleted instead of upserted.

### MongoFieldPathMapper

**Package:** `util`

Maps between Java field names and MongoDB storage field names.
Handles `@Field` annotations (where Java and DB names differ) and
`payload.*` prefixed paths for `@JsonAnySetter`-captured fields.
Used by `OptimizedMongoQueryRepositoryImpl` to rewrite field names
in dynamic queries.

### ObjectConverter

**Package:** `util`

Recursively converts values placed into the `payload` map. Handles
`Document` → `Map`, `List<Document>` → `List<Map>`, etc., so that
Jackson serializes them correctly.

### AnnotationExtractor

**Package:** `util`

Reflectively finds fields annotated with a given annotation on a model
class, including inherited fields. Used by the load repository to
locate `@Id`, `@Version`, and `@DeleteFlag` fields.

### CustomAggregationOperation

**Package:** `util`

Wraps a raw JSON pipeline stage string for use with Spring Data's
`Aggregation` API. Needed because Spring Data doesn't natively support
all MongoDB aggregation operators.

### ObjectIdSerializer

**Package:** `util`

Jackson `JsonSerializer<ObjectId>` that serializes ObjectId as its
hex string. Registered globally in `ObjectMapperConfig`.

### MongoVersionBean

**Package:** `util`

Simple Spring Bean holding the MongoDB server version string, fetched
at startup. Useful for feature-gating.

### GlobalExceptionHandler

**Package:** `util`

`@ControllerAdvice` that maps uncaught exceptions to appropriate HTTP
status codes and JSON error responses.

### MongoSchemaGenerator

**Package:** `util`

Generates a JSON Schema document from a model class by reflecting over
its fields and annotations.

---

## Config classes

### MongoConfig

**Package:** `config`

**Beans:**
- `MongoTransactionManager` — enables ACID transactions
- `MongoCustomConversions` — uses native driver Java time codecs
  (avoids legacy date conversions)
- `MongoVersionBean` — captures MongoDB server version

### AsyncLoadConfig

**Package:** `config`

Configures the `loadExecutor` thread pool (8 core, 16 max) used by
`asyncWriteMany`.

### ObjectMapperConfig

**Package:** `config`

Registers `ObjectIdSerializer` and disables `FAIL_ON_UNKNOWN_PROPERTIES`
so unknown JSON fields flow into `@JsonAnySetter`.

### WebConfig

**Package:** `config`

Enables Spring Data web support (`EnableSpringDataWebSupport`) for
automatic Pageable resolution in controller parameters.

### ValidationConfig

**Package:** `config`

Provides the Jakarta Bean Validation `Validator` instance.

### MongoTypeMapperConfig

**Package:** `config`

Configures Spring Data's `MongoTypeMapper` — disables `_class` type
discriminator injection. The service does not mix types in a single
collection.

---

## How to add a new entity (for AI agents)

To generate a complete entity for a new document type:

1. **Scaffold with `generate-entity`** — this creates:
   - `model/{Entity}.java` — generic model with `payload` map
   - `repository/{Entity}Repository.java` — extends all 5 repos
   - `service/{Entity}*Service.java` — 7 thin wrapper services
   - `controller/{Entity}Controller.java` — full endpoint set
   - `service/{Entity}PreflightConfig.java` — collection/index config

2. **Optionally regenerate the model with `generate-models-from-json`**
   — replaces only the model class with typed fields from a JSON sample

3. **Configure the web UI** by editing `configapi/` JSON files:
   - `apiEndpoint.json` — set to `/api/{plural}`
   - `gridFields.json` — column headers → field paths (first = `@Id`)
   - `queryableFields.json` — query inputs, types, dropdown values

4. **Build and run** — the preflight check creates the collection and
   indexes automatically

See `README.md` for the exact Maven commands and `configapi/README.md`
for UI configuration details.