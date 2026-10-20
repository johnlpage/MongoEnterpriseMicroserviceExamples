# mxtest — Minimal HTTP API Test Client

A single-file Java 17 CLI for manually exercising REST endpoints. Sends
GET/POST/DELETE requests, pretty-prints JSON and NDJSON responses, and
supports dot-notation field filtering. Its only dependency is Gson.

Think of it as a lightweight, JSON-aware `curl` for quick ad-hoc testing.

## Usage

```
java -jar mxtest-1.0.jar GET|POST|DELETE <URL> [@file|string|fields]
```

### GET

```shell
# Pretty-print the full response
java -jar mxtest-1.0.jar GET http://localhost:8080/api/inspections

# Filter to specific fields (dot-notation, comma-separated)
java -jar mxtest-1.0.jar GET http://localhost:8080/api/inspections/id/1950521387 testid,vehicle.make,vehicle.model

# Works with NDJSON streams too
java -jar mxtest-1.0.jar GET http://localhost:8080/api/inspections/json testid,vehicle.make
```

### POST

```shell
# Inline JSON body (auto-detects Content-Type: application/json)
java -jar mxtest-1.0.jar POST http://localhost:8080/api/inspections '{"testid":"001","vehicle":{"make":"FORD"}}'

# Body from file (prefix with @)
java -jar mxtest-1.0.jar POST http://localhost:8080/api/inspections @payload.json
```

### DELETE

```shell
java -jar mxtest-1.0.jar DELETE http://localhost:8080/api/inspections/id/001
```

## Field filtering

A comma-separated list of dotted field paths. Only matching fields are kept
in the output. Nested paths traverse into objects and arrays.

```shell
java -jar mxtest-1.0.jar GET http://localhost:8080/api/inspections/id/1950521387 testid,vehicle.make,vehicle.model,testResult
```

## Response formatting

- **JSON** — pretty-printed with indentation.
- **NDJSON** — each line is parsed and pretty-printed individually with a
  blank-line separator.
- **Non-JSON** — echoed verbatim.

Non-2xx responses print the status code and body to stderr and exit with
code 1.

## Build

```shell
mvn clean package
```

Produces `target/mxtest-1.0.jar` — a minimized fat JAR with Gson shaded in.

A Gradle alternative is available at `build.gradle.example`.

## Dependency

Java 17+ only. Gson 2.10.1 is the single external dependency (shaded into
the JAR).