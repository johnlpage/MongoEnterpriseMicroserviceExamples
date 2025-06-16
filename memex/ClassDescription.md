# Introduction

This document outlines the purpose of the various classes in the Memex examples and highlights their specific features.
Rather than using JavaDoc, which can be sterile and often incomplete, this separate document explains not just the
interfaces but the concepts behind them. It serves as a good starting point for understanding the nature and purpose of
Memex.

Memex comes as a working example application with most reusable functionality abstracted into reusable templated
classes. If you cannot simply reuse these as they are, you can derive from them or modify them as needed.

# Model Classes

Model classes represent the data objects used by your application. They typically map to the objects you persist in the
database and read/write as JSON from services.

## VehicleInspection.java

This represents the results of a manual inspection of a specific vehicle at a point in time by one or more mechanics to
ensure it is safe for road use. It is the core entity in the demonstration code and stores data from the UK Vehicle and
Operator Service Agency (VOSA). This inspection data is published by VOSA
at [data.gov.uk](https://www.data.gov.uk/dataset/e3939ef8-30c7-4ca8-9c7c-ad9475cc9b2f/anonymised_mot_test).

_VehicleInspection_ is annotated as a _@Document_, which is MongoDB's equivalent of a JPA _@Entity_ annotation. It uses
Lombok to avoid boilerplate code for getters, setters, and constructors. It is a @Data rather than a @Value type,
although it could be either. Various places in the Memex source show how to deal with both mutable and immutable models.

_VehicleInspection_ demonstrates mapping a class member to a database field with a different name using the _@Field_
annotation.

_VehicleInspection_ demonstrates using Java Bean Validation (JSR-380, JSR-303) to constrain a field. In this case, it
uses @Min on the vehicle engine capacity, set to one (1) because there are some vehicles with 9cc engine capacities
according to the data!

It demonstrates the use of a _@Transient_ (do not store in DB) field flagged as _@DeleteFlag_. This is used to indicate
that a record should be deleted from the database. If this metadata field is set to true, rather than load or update the
data, Memex will remove it in its bulk loader.

**Important**: This class shows the use of _@JsonAnySetter_ and _@JsonAnyGetter_ annotations. These annotations inform
_Jackson_ what to do with any fields in incoming JSON that do not map to members in the class. Handling these in a
traditional RDBMS is difficult and normally considered an error, but often data can change, or some parts of the
document may be undefined or subject to change. With MongoDB, we can have _Jackson_ map these to an embedded Document (
represented as a HashMap in Java) so they are saved, returned, and also queryable using native MongoDB queries (although
not with auto-generated repository queries). This mechanism is one way to get more flexibility from MongoDB than you get
from a traditional RDBMS.

## Vehicle.java

Vehicle is a basic model class used to show how a field in one model (VehicleInspection) can be another class and how
MongoDB seamlessly stores this as a nested object in the database. Unlike an RDBMS, this would not be stored in another
table and linked normally, although you can configure it to do so if needed.

## DocumentHistory.java

DocumentHistory is used to store a history of changes to any given record over time. It is a generic class that can
store field-by-field modifications for any entity. There is no specific VehicleInspectionHistory—although the type of
record it applies to is stored as a string value within it. Although queryable, it's used more internally in Memex and
accessed using methods such as the asOf query mechanism.

# Repository Classes

## VehicleInspectionRepository.java

This is a basic and typical repository provided as an example. It has several commented-out examples of how to define
custom repository access methods to find data by various fields. What makes it more interesting is the additional set of
base repositories it is derived from. As well as the normal MongoRepository from Spring Data MongoDB, it also includes
the following base classes:

### OptimizedMongoLoadRepository.java

This interface and its implementation provide two mechanisms for loading data into MongoDB. Unlike Save() and SaveAll()
in the standard JPA/Spring Data interface, these optimize writes using minimal calls to the server, transactions, and
sophisticated expression-based updates to provide up to 200x faster performance. They also include trigger mechanisms
and access to "what changed" within a transactional context.

```java  
BulkWriteResult writeMany(
        List<T> items,
        Class<T> clazz,
        InvalidDataHandlerService<T> invalidDataHandlerService,
        UpdateStrategy updateStrategy,
        PostWriteTriggerService<T> postTrigger)
        throws IllegalAccessException;

CompletableFuture<BulkWriteResult> asyncWriteMany(
        List<T> items,
        Class<T> clazz,
        InvalidDataHandlerService<T> invalidDataHandlerService,
        UpdateStrategy updateStrategy,
        PostWriteTriggerService<T> postTrigger);

```

This is provided as both synchronous and asynchronous versions (both using the Java sync library, not reactive Java).
Since a database write is a network call and can take milliseconds, there is usually no need to wait for it to complete
when more data is waiting to be sent.

Each method takes:

- A list of updated or new items of the related Model class (this can be a list of one to replace Save(), although
  replacing a single Save() is not much faster, it does open up all the additional capabilities)
- The Class of the Model, as Java does not let you infer that through a template
- An InvalidDataHandlerService - which lets you define what to do for any documents that could not be processed
- An UpdateStrategy, whether to Insert, OverWrite (Replace), Update, or "Update and Return a change document" for each
  of the items in the list. All of these will default to inserting if the document does not exist and will delete if the
  _@DeleteFlag_ field is true
- PostWriteTriggerService postTrigger - if non-null, this is an interface with a function that is called after updating
  the data in the database. This function is passed a means to retrieve the nature of all the changes, down to a field
  level, and is used to write change histories as well as any other post-update processing required. It is similar to an
  RDBMS trigger but is written in client-side Java.

Each returns a standard MongoDB BulkWriteResult which provides information on the number and type of operations
completed.

### OptimizedMongoQueryRepository.java

MongoRepository provides all the typical Spring/JPA query facilities - auto-generated queries of the form
FindByThisAndThat() or FindAllByFieldLessThan(), etc. You can also use _@Query_ annotations and query by both Example
and Criteria as you would normally do. You can also use the native MongoDB driver to construct queries as code using
fluent query-building classes.

What _OptimizedMongoQueryRepository_ brings is the ability to run queries defined at runtime and passed from an
application. Similar to GraphQL, we may not always want to create an explicit endpoint for every possible query our API
supports. If a User Interface has some form of query builder, perhaps a form with many optional fields, then we may want
the UI designer to have control over the queries run. We might also want to offer both Database Queries and Lucene
full-text index queries - Lucene indexing is available in MongoDB Atlas and provides a powerful way to perform fuzzy,
best-match, full-text queries among others.

This interface and its underlying implementation provide the following three functions:

```java  
List<T> mongoDbNativeQuery(String jsonString, Class<T> clazz);

List<T> atlasSearchQuery(String jsonString, Class<T> clazz);

int costMongoDbNativeQuery(String jsonString, Class<T> clazz);  
```  

The first two take a description in JSON of the required Query, Projection, Order, and Limits - it's very much a
general-purpose query function to allow arbitrary queries. Query mongoDBNative (querying using the MongoDB database and
indexes) or atlasSearch - querying using any defined Atlas Search Lucene indexes.

The final method, which you can and should call from your service before passing a query to mongoDbNativeQuery, returns
a 'cost' between 1 and 500 for that query, where 1 represents a fully indexed and index-covered query and 500 represents
a collection scan. This score indicates how much resource the query will take, and you can use that to determine how to
proceed. You may reject queries that will be harmful, you may allow and log them, you may pass them to a secondary or
analytic replica, or you may modify them, for example adding a limited and indexed date range so they only impact a
recent set of data. All of this you have the tools to do in your service layer.

### OptimizedMongoDownstreamRepository.java

When you want to perform a large extraction of data from a database, through Spring and out as JSON, there is a simple
and obvious solution - perform a query or aggregation to retrieve a Stream of the Object model and then convert those to
JSON to stream them out. This is the simplest way to perform a large-scale extraction. However, when we do this, we have
to create large numbers of temporary row (Document) objects and Model objects in order to render them as JSON. In the
case of MongoDB, this is a conversion from BSON (the native internal and network format) to Document classes (HashMap
based) to Model Classes to JSON.

If you are extracting a few MB now and then, this is OK, but what if you want to regularly and quickly extract Gigabytes
of data? You can shortcut a lot of the process above by telling MongoDB you want JSON, not Objects from the database,
and this is where the single method in OptimizedMongoDownstreamRepository comes in.

```java  
Stream<JsonObject> nativeJsonExtract(String formatRequired, Class<T> modelClazz);  
```  

This is an example with a single method to show that instead of `Stream<VehicleInspection>` we can use the much more
efficient `Stream<JsonObject>` and in our service and controller simply stream them out to our end user, avoiding 99% of
the object creation and garbage collection. JsonObject converts from the BSON native format directly to JSON without
creating any intermediate objects.

To use this, we do need to explicitly tell MongoDB exactly what our JSON should look like, and what we pass in is a
String representation in JSON of the required format. The function performs a find() retrieving the whole collection and
applying this projection. The purpose of this is not to simply use as is but to demonstrate how much more efficient
large-scale data downstream as JSON (or BSON) can be when you avoid all the Spring object mapping.

### MongoHistoryRepository.java

MongoHistoryRepository is used to read one or multiple documents but to apply the changes made to them over time in
reverse order to revert to older versions. It has two methods, although the code can be extended and reused for more
sophisticated cases including querying historical data.

```java  
Stream<T> GetRecordByIdAsOfDate(I recordId, Date asOf, Class<T> clazz);

Stream<T> GetRecordsAsOfDate(Criteria criteria, Date asOf, Class<T> clazz);  
```  

This assumes the documents and any changed versions of them were ingested using the OptimizedLoadRepository with the
record history option enabled. These methods take the base document (latest version), perform a $lookup (JOIN) to fetch
the historical changes, and then merge those to wind the document back to its prior form.

# Services

Services sit between Controllers (handling interfaces to the service) and Repositories (interfacing with an underlying
database). Services contain business logic and processing that is specific to your business and also how you intend to
use the data. A very simple controller could access the repository directly, but this is not good practice. In the Memex
examples, we do not access the repository directly from the controller - only via services, even if in some cases those
service classes perform no additional processing or logic.

## MongoDbJsonStreamingLoaderService.java

The most significant service in Memex is the StreamingJsonLoaderService. This service parses an incoming JSON stream
into the associated Model class and every 200 documents it uses OptimizedMongoLoadRepository to load that batch of
documents as an asynchronous process while it parses and processes the next 200 documents. This stream processing and
parallelism is at the heart of high-performance data loading in Memex.

It can accept data as a JSON array or simply multiple JSON objects, optionally separated by a newline. It works by
finding the first object start token `{`, then finding the corresponding close token `}`, and treating what is between
them as an object to load - it then finds the next open token.

After parsing each document to a model object, the _StreamingJsonLoader_ will pass that object to a _preWriteTrigger_
class if one is defined to allow for any modifications needed before writing to MongoDB.

## PreWriteTriggerService.java

This class defines a single overrideable function which is called by StreamingJsonLoaderService (may be moved to
OptimizedLoadRepository soon). This is called for each Model object just before writing it to MongoDB and can be used to
augment the data. The correct method to override depends on whether you are using mutable or immutable models. The
default class does nothing to the object.

```java  
public void modifyMutableDataPreWrite(T document) {
}

public T newImmutableDataPreWrite(T document) {
    return document;
}  
```  

## PostWriteTriggerService.java

This defines a function to be called after a batch of documents have been written to MongoDB inside the transaction used
to write them and before it commits. The default function does nothing but can be overridden in a derived class.

```java  
public void postWriteTrigger(
        ClientSession session,
        BulkWriteResult result,
        List<T> records,
        Class<T> clazz,
        ObjectId updateId)
        throws IllegalAccessException {
}  
```  

This function receives:

- The transactional session used for the write. By using that session inside this function, you are able to see
  operations that have not yet been committed.
- The BulkWriteResult object describing how many of each operation took place and the ID values for anything that was
  inserted.
- The batch of records that were presented for updating, deleting, or inserting.
- An updateId value, which can be used to query MongoDB to retrieve the documents as they are post-update if needed.

This is derived from the greater HistoryTriggerService that records changes to the data transactionally.

## HistoryTriggerService.java

HistoryTriggerService is a templated reusable class, extending PostWriteTriggerService to generate a change history of
any data updated.

Where a model is stored in collection X, for example `vehicleinspection`, this class writes DocumentHistory objects to
`vehicleinspection_history`. These are a reverse delta history showing field values in previous versions of the document
at each stage. To use them, you need to take the latest version of the data and apply in reverse order - this is done by
MongoHistoryRepository for you for functions such as asOf (a point in time).

When a record is first inserted, an entry is recorded in the history table just saying this is when the record was first
inserted; it does not contain any of the content.

When a record is modified, the modified fields' previous versions are stored along with an updateId and timestamp.

When a record is deleted, currently the whole record is stored in the trigger service and removed from the principal
collection. This could be changed to delete the record from the principal collection AND to remove all historic versions
very easily, but many users prefer to archive the data or retain a history of its existence and deletion. It would also
be simple to have a history that retained the creation and deletion records but no record of the content modifications.

## InvalidDataHandlerService.java

This class defines a single function to be called before a document is loaded into the database if that document fails
Java Bean Validation. It is passed the document and a list of ways it fails validation - the default behavior is to log
as a warning. This function returns a boolean which determines if the data should be loaded anyway or skipped in the
data loading/updating process. It should be used to populate a data file of things to be reloaded - it is designed to
allow the rest of the load to complete and errors or exceptions to be caught.

```java  
public boolean handleInvalidData(
        T document, Set<ConstraintViolation<T>> violations, Class<T> clazz) {
    LOG.warn(
            "Invalid data detected ({} violations) in document, but no explicit handler provided, discarding.",
            violations.size());
    return false;
}  
```  

## MongoDbPreflightCheckService.java

This Java Bean based on ApplicationRunner demonstrates one good practice with services built on MongoDB - having the
server validate any database prerequisites, be they permissions, version, or the existence of database objects including
collections, indexes, search indexes, and schema validators.

The way things like indexes are populated through environment can differ based on the system, architecture, and business
requirements. In a development environment, it is normal to generate indexes that are not present in the preflight
service. However, in production, given that creation of an index may have an impact on a running system, it is better to
build indexes via management tooling and failover orchestration. In that case, preflight should fail until the index
already exists.

By default, this is configured to create indexes and search indexes for ease of development but warn in the logs that
this is not for production.

## VehicleInspectionPreWriteTriggerService.java

This is provided as an example of a pre-write trigger which makes small changes to the incoming data to make it easy to
test updates. You can load the same file, but this will modify some details to cause an update and relevant history
entry if required. It also serves to demonstrate what a preWrite trigger looks like both for mutable and immutable data.

## VehicleInspectionQueryService.java

The class OptimizedMongoQueryRepository provides an endpoint supporting any MongoDB query defined by an EJSON string.
This is intended to allow front-end clients to define their own queries and avoid the requirement to create an endpoint
for each new frontend feature - especially where some form of GUI query creation is available.

The OptimizedMongoQueryRepository also provides a cost estimator function for queries to determine if they will take few
or many resources from the database server. This Service takes each incoming query, performs a cost estimation (this is
efficient and cached in the estimator), logs the cost, and runs it anyway. This is where you would add business logic to
deny, log, modify, or redirect expensive queries to a secondary server.

This class shows how to expose a simple hard-coded query and a Spring query-by-example to the controller class.

## VehicleInspectionXXXXService.java

Where XXXXX is one of: DownStream, History, HistoryTrigger, InvalidDataHandler, JsonLoader, then the service is a thin
wrapper over the generic service or to provide a simple call to the repository with space for additional logic.

# Controllers

Memex includes an example _@RestController_ to show how the features are accessed. A RestController was chosen as it is
the simplest to test and understand, but other controller types are equally usable in your own code.

## VehicleInspectionController.java

This simply provides REST endpoints for the various service functions discussed above. The only additional functionality
that isn't a default Spring RestController is a function which takes a Stream of objects and writes them back to the
client output stream. There are two versions here: one that streams back VehicleInspection classes and one that has the
underlying MongoDB database construct the JSON (as BSON) and stream it without creating any of the intermediary client
objects. This could save 50-90% of client CPU cycles with a consequent increase in performance for large data sets.  

