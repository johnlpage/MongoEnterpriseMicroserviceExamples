# Example Endpoints

## Data Loading

The Data Loading endpoints read a stream of JSON of any size and load it into
MongoDB into the vehicleinspections
collection. In all cases it is assumed that the data may already exist and the
field testid is used to identify each
document. If an existing document exists it is overwritten. This is provided
through _OptimizedMongoLoadRepository_

The data load endpoint is

```
POST /api/inspections
```

This takes an application/json payload containing the data to load, this can be
JSON objects with no seperators, or an
array of JSON objects. The stream parser will basically find the fist open
bracket, then find the paired closing bracket
and treat what is between them as an object.

Eample usage

```shell
curl -s \
-X POST "http://localhost:8080/api/inspections?updateStrategy=REPLACE" \
-H "Content-Type: application/json" -T mot.json

```

This loader endpoint sends documents to the database in batches and is
multi-threaded so can be preparing the next batch
whilst waiting for previous batches to complete.

If the model class has a field annotated with `@DELETE` and this has a non-empty
value then this will delete rather than
update the document.

It supports 3 strategies for loading the data specified as updateStrategy in the
URL these are
`REPLACE|UPDATE|UPDATEWITHHISTORY`

* `REPLACE` overwrites the complete existing document, if there is no change
  compared to the existing document then this
  is a no-op, the replacement is sent to MongoDB but the database server will
  determine nothing needs to be modified and
  silently ignores is thus not requiring any disk IO or transaction logging. If
  there is any difference in the document
  then the whole document is replaced, any additional fields are overwritten and
  the whoel document is added to the
  transaction log and appears in the change stream.
* `UPDATE` the document is converted to an update statement setting each
  individual field separately, currently arrays
  do not set each element seperately but are set as the whole array. This allows
  the database server to determine
  individually which fields have changed and which have not and place only those
  changes into the transaction log. Where
  you have small changes happening in a large number of documents this can
  greatly reduce the transaction log size which
  can have an impact on backup strategies and other things that use the
  transaction log. If nothing changes this is also
  a no-op.
* `UPDATEWITHHISTORY` this applies updates to individual fields as above but,
  for any fields that change it records
  those fields previous values in an additional document field called
  `__previousValues` along with a unique updateId .
  This can be used in a  _postWriteTrigger_   to retrieve what changed after
  each batch has loaded , for example to
  maintain a change history. In this example a  _postWriteTrigger_ is configured
  to save that history of changes in the
  collection `vehicleinspection_history`
* `INSERT` the document is added without explicitly checking if it already
  exists based on the annotated @ID field. In
  the event of a duplicate key then this will throw and log an error and your
  code should be adapted to deal with it.
  This is faster than upsert but only really useful in initial bulk loading.

There is an additional option in this example where adding the option to the URL
of `futz=true` uses a _preWriteTrigger_
to modify the incoming data.This is purely for testing and modifies documents
before writing to change field values, set
the delete flag or change the testid randomly to simulate how the data might
vary the next time you receive it.

### Triggers

When calling  `inspectionLoaderService.loadFromJsonStream() ` from yout
controller you can pass two trigger services (or
either can be null) `pre` and `post` write triggers, these derive from the
Relevant Pre and PostWriteTriggerService
Interfaces and inject functions that are called during loading.

* _preWriteTrigger_ is called once for each object before loading.

* _postWriteTrigger_ is a little more complex, if it is defined (non null) then
  each batch is loaded inside an ACID
  transaction, once the data has been loaded but not committed the trigger is
  called and is passed the list of
  documents, a unique identifier for the update and the response from the
  bulkWrite call showing how many were inserted,
  updated and deleted. It is principally intended, as in the example code to
  give a way to efficient record a change
  history.

## Query

These examples include examples of simple Repository Queries both generated from
the function name and explicitly
defined
as JSON in the VehicleInspectionRepository, these are not hooked up to a service
or directly to the controller.

The controller includes two predefined query endpoints

```
GET /api/inspections/id/{vehicleId}
```

Example:

```shell
 curl -s http://localhost:8080/api/inspections/id/1950521387
```

to fetch a single inpection by its testId and also

```
GET /api/inspections/model/{model}
```

Example:

```shell
 curl -s http://localhost:8080/api/inspections/model/POLO 
```

To fetch a set of inspection by the vehicle model (for example 'POLO' ). The
model exaple demonstrates using Spring
Data's "Query by Example" query syntax where you populate some fields in the
object and matching object are found. This
also includes the url options `page` (default 0)  and `size`  (default 0) using
Spring data's native _Slice_ class and
paging. Using Spring data's internal paging mechanisms causes at least the first
run of the query to internally find
and fetch all matching documents in the database server without using a covering
index in order to get a count. In this
example we disable that as it can be a serious performance bottleneck.

The controller also exposes a generic MongoDB Query endpoint similar to the now
deprecated MongoDB Cloud Data API.
This allows queries to be defined by the front-end / middleware dynamically
without them having to be explicitly built
into the service. This is useful for internal applications where the application
includes a query building interface
for end users. THe endpoint for this is.

```
POST /api/inspections/query
```

It is a POST rather than GET as we want to pass a JSON based request to it even
though it notionally doesn't create a
new server resource, in future version it might create a cursor we can then
iterate over. To use this endpoint you
pass an object like this

```shell

curl -s -X POST http://localhost:8080/api/inspections/query -H "Content-Type: application/json" \
-d \
'
{
  "filter": {
    "model": "POLO"
  },
  "limit": 2,
  "projection": {
    "make": 1,
    "model": 1
  },
  "sort": {"make": 1}
}
'
```

`filter` is a basic MongoDB Query, as this may perform some mappings where field
names differ between the JSON,
Model and database there may be some cases where this does not work as expected
currently as it only renames top
level fields in the query.

`limit` (default 1000)  `skip` (default 0) can be used to perform basic paging
of results.
`sort` defines the sort order of results
`projection` determines what fields to return (1) or what fields to exclude(0)
you cannot mix inclusion and exclusion

### Query Costing and Management

A database query that is not supported by an appropriate database index can be
very expensive in terms of CPU, Disk
I/O and cache eviction. In a production system it is important to know what
queries are being performed and treat
them appropriately, whether simply logging them to create indexes later,
blocking them or limiting them (by setting a
small limit value), rewriting them to force index usage. The
OptimizedMongoQueryRepository includes a costing
function which returns an estimated relative query cost - from 1 (A fully
index-covered query) to 500 (A collection
scan).

Query costing uses a query shape hashing technique to make it cheap to call for
each query, thsi is demonstrated in
the VehicleInspectionQueryService that costs and logs the cost of each query
before running it anyway, this coudl be
used to direct 'bad' queries to secondary or analytic nodes, to block them to
warn the user they are slow,

## Atlas Search

MongoDB Atlas Search adds the ability to build Lucene indexes in addition to
traditional BTree database indexes and
query using them.

Lucene indexes create a sorted list of documents containing a given 'Term' - a
Term may be a combination of field and
value or the value in the field many be split into many terms (Tokens) for
example a text string may be split by
some definition of a Word.

This makes it efficient to query for words inside strings (Full text search)
including a range of options like fuzzy
and ngram searched.

The indexes, each of which is basically a map from term->"sorted list of ids"
are easy to intersect (although very
expensive to modify) so you can use Atlas Search where you want to query for
multiple words and find document with
both or where you want to select a set of field values and find documents where
all the clauses are true. This makes
Atlas Search very values for ad-hoc query cases but less so for well known
queries or transactional queries.

The _OptimizedMonogQueryRepository_ includes an endpoint to allow you to query
using Atlas Search indexes rather
than databases indexes, like the interface about this allows the client to send
a well-formed Atlas search request
and have it run dynamically rather than hard coding it.

The endpoint is

```shell
POST /api/inspections/search
```

This is posted a JSON document with 4 parameters `search`,`skip`,`limit` and
`projection`

skip, limit and projection as are they are for the query interface above.
search is passed directly to Atlas search and can be learned though the
[playground](https://search-playground.mongodb.com/tools/code-sandbox/snapshots/new?tck=intro_as_webinar_as_promo_banner)
.

Example

```shell
curl -X POST  http://localhost:8080/api/inspections/search -H "Content-Type: application/json" \
-d \
'
{
   "search":{
      "index":"default",
        "text":{
           "query":"test",
           "path":{
              "wildcard":"*"
           }
        }
      }
}
'
```

## Updates

TODO

## Downstream

Previous endpoints that return data are typically returning a relativle small
set of data to feed a UI and are doing
so without using any streaming. Where the requirement is to supply a downstream
system with a large quantity of data
then a sifferent approach is required that does not assume all the data can be
held in the RAM of the appserver. The
examples show two endpoints showing how to stream data back using standard
Spring data methods and also using a
native methpod which avoids conversions from the MongoDB Wire format (BSON) to
Documents and Model Objects before
converting to JSON, instead converting the database response diretly to JSON -
this is faster and uses less
appserver CPU.

### Native data stream

```shell
GET /api/inspections/json
```

Example

```
curl -s -X GET http://localhost:8080/api/inspections/json

```

MongoDB native JSON stream using Driver and JSONObject data type.

```shell
GET /api/inspections/jsonNative

```

Example

```shell
curl -s -X GET http://localhost:8080/api/inspections/jsonNative

```

### Aggreagtions

TODO

### History

If you load in data using UPDATEWITHHISTORY then alongside the data collection
you get a colleciton_history that
includes previous versions of fields. You can then use the MongoHistoryService
to fetch prior versions

To test you in multiple versions of the same data ( SAMPLE_DATA/history has
example )

```shell

cd SAMPLE_DATA/history

curl  \
-X POST "http://localhost:8080/api/inspections?updateStrategy=UPDATEWITHHISTORY" \
-H "Content-Type: application/json" -T v1.json

sleep 1
date +"%Y%m%d%H%M%S"

curl  \
-X POST "http://localhost:8080/api/inspections?updateStrategy=UPDATEWITHHISTORY" \
-H "Content-Type: application/json" -T v2.json
sleep 1
date +"%Y%m%d%H%M%S"

curl  \
-X POST "http://localhost:8080/api/inspections?updateStrategy=UPDATEWITHHISTORY" \
-H "Content-Type: application/json" -T v3.json


```

Now use the date time emitted to fetch the various versions

```shell
curl "http://localhost:8080/api/inspections/asOf?id=1&asOfDate=20250325123007"

```

## Kafka

Memex now includes an example of loading data from a Kafka topic. This is in the
class
_VehicleInspectionKafkaConsumer_ ,
you can configure access to your kafka broker(s) in
`resources/application.properties`.
This loads in batches but sends the current batch if nothing has been received
in 100 milliseconds.

# Auto Code Generation

Using Maven (not yet gradle) You can Automatically generate the base set of
classes for an Entity including all the new
derived classes by running the maven goal. This adds a Generic flexible model.

```shell
 mvn generate-sources -Pgenerate-entity -Dentity=Company -Dplural=companies -DidFieldName=companyNumber -DidFieldType=String
```

You can delete it with

```shell
 mvn generate-sources -Pgenerate-entity -Dentity=MyEntity -DidType=Long -Ddelete
```

# Model Generation

You can also Generate a data specific model from a JSON file using the maven
goal below. customId shodu lmap to the id
field in the JSON

```shell

 mvn generate-sources -Pgenerate-models-from-json   -DjsonFile=../companies250k.json  -DbasePackage=com.johnlpage.memex  -Dentity=Company  -DidFieldName=companyNumber

```

# Testing

