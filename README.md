# MongoDB Enterprise Java Examples

This monorepo contains three sibling projects that together demonstrate
enterprise-grade MongoDB patterns in Java:

## [memex](memex/) - MongoDB Enterprise Microservice Examples

A **Spring Boot 3.4 / Java 17** microservice that showcases production MongoDB
patterns: streaming bulk-load with upsert strategies (REPLACE, UPDATE,
UPDATEWITHHISTORY, INSERT), ACID-transaction change-history, Atlas Search,
Kafka ingestion, dynamic config-driven query endpoints with query-costing,
and downstream streaming with native BSON-to-JSON conversion.

Build: `mvn clean package` in `memex/` -> `memex-1.0.jar`

## [DataGen](DataGen/) - Probabilistic Data Generator

A **standalone Java 17 CLI** that generates large volumes of realistic JSONL
test data from a directory of weighted CSV (or `.csv.gz`) definition files.
Supports auto-incrementing IDs (`@ONEUP`), random integers/doubles/dates
(`@INTEGER`, `@DOUBLE`, `@DATE`, `@DATETIME`), nested arrays
(`@ARRAY(subdir,n)`), embedded literal JSON, and parallel sharded generation.
The output is streamable JSONL ready for `mongoimport` or memex's bulk-load
endpoints.

    java -jar DataGen-1.0.jar <inputDir> <docsToGenerate> <outputFile>

## [mxtest](mxtest/) - Minimal HTTP API Test Client

A **single-file Java 17 CLI** (267 lines) for manually exercising REST
endpoints. It sends GET/POST/DELETE requests, pretty-prints JSON responses,
supports field-filtering via dotted paths, and loads POST bodies from files.
Its only dependency is Gson. Think of it as a lightweight, JSON-aware `curl`
for quick ad-hoc testing of memex endpoints.

    java -jar mxtest-1.0.jar GET|POST|DELETE <URL> [field1,field2...]

## How they fit together

```
DataGen --generates--> JSONL data --POST /api/inspections--> memex --MongoDB-->
                                                                ^
                                                             tested via
                                                                |
                                                            mxtest (HTTP CLI)
```

- **DataGen** creates statistically realistic JSON datasets
- **memex** runs as a Spring Boot service, loading that data into MongoDB and
  demonstrating enterprise patterns (streaming, history, search, Kafka, etc.)
- **mxtest** is a lightweight HTTP client for manually hitting memex's REST
  endpoints and inspecting responses

## Workshop materials

The original `SAMPLE_DATA/` (UK VOSA MOT dataset scripts), `Notes/`, and
sample JSON files have been moved to the
[MEMEX_Workshop](https://github.com/johnlpage/MEMEX_Workshop) private repo.
`companies250k.json` (403MB raw) is stored there as `companies250k.json.gz`
to stay under GitHub's file size limit.