# Intro

This document outlines the purpose of the various classes in the Memex examples and any specific features of note in
them. I decided not to use JavaDoc as that ends up quite sterile and often incomplete - therefore this is a separate
document outlining not just the interfaces but the concepts behind them. It is a good starting point to understand
the nature and purpose ot Memex.

Memex comes as a working example application with most reusable functionality abstracted into reusable templated
classes. If you cannot simply reuse these as they are, then you can derive from them or modify them as needed.

# Model Classes

Model classes represent the data objects used by your application; they also generally map to the objects you persist
in the database and read and write as JSON from services.

## VehicleInspection.java

This represents the results of a manual inspection of a specific vehicle at a point in time by one or more mechanics to
ensure it is safe for road use. It is the core entity in the demonstration code and is used to store data from the
UK Vehicle and Operator Service Agency (VOSA). This inspection data is published by VOSA
at [data.gov.uk](https://www.data.gov.uk/dataset/e3939ef8-30c7-4ca8-9c7c-ad9475cc9b2f/anonymised_mot_test).

_VehicleInspection_ is annotated as a _@Document_, the MongoDB equivalend of a JPA _@Entity_ annotation. It uses
Lombok to avoid boilerplate for getters, setters and constructors. It is a @Data rather than a @Value type, although
it could be either, and various places in the rest of the Memex source show how to deal with both mutable and
immutable models.

_VehicleInspection_  demonstrates mapping a class member to database field with a different name using the _@Field_
annotation.

_VehicleInspection_ demonstrates using Java Bean Validation (JSR-380, JSR-303) to constrain a field, in this case using
@Min on the
vehicle engine capacity, it's set to one(1) as there are some vehicles wutgh 9cc engine capacities, according to the
data!

It demonstrates the use of a _@Transient_ (Do not store in DB) field flagged as _@DeleteFlag_, this is used to annotateœ
that a
record should be deleted from the databases, if this metadate field is set to true then rahter than load or update the
data Memex will remove it in its bulk loader.

__Important__ this clas shows the use of _@JsonAnySetter_ and _@JsonAnyGetter_ annotations, these annotations inform
_Jackson_ what to do with any fields in incoming JSON which do not map to members in the class. Handling these in a
traditional RDBMS is hard, and it's normally considered an error but often data can change, or some parts of the
document may be undefined or subject to change. With MongoDB, we can have _Jackson_ map these to an embedded Document (
Represented as a HashMap in Java) so they are saved, returned and also queryable using the native MngoDB queries
although not with the auto-generated repository queries. This mechanism is one way to get more from flexibility MongoDB
than you get from a traditional RDBMS.

## Vehicle.java

Vehicle is a basic model class used to show how a field in one model (VehicleInspection) can be another class and
how MongoDB seamlessly stores this as a nested object in the database. Unlike an RDBMS, this would not be stored in
another table and linked normally, although you can configure it to do so if you need to.

## DocumentHistory.java

DocumentHistory is used to store a history of changes to any given record over time; it is a generic class that can
store
field by field modifications for any entity; there is no specific VehicleInspectionHistory — although the type of record
it applies to is helps as a string value within it. Although queryable, it's used more internally in Memex and acesesed
using methods such as the asOf query mechanism.

# Repository Classes

## VehicleInspectionRepository.java

This is a basic and typical repository provided as an example, it has a number of commented out examples of how to
define customer
repository access methods to find data by various fields what makes it more interesting is the additional set of base
repositoriesit is derived from. As well as the normal MongoRepository from Spring Data MongoDB, it also includes the
following base
classes

### OptimizedMongoLoadRepository.java

This Interface and it's implmenetation provides two mecahnisms for loading data into MongoDB, unlike Save() and
SaveAll() in the standard JPA/Spring Data interface these optimise writes using minimal calls to the server,
transactions and sophiticated, expression-based updates to prvide up to 200X faster performance as well as trigger
mechanisms and access to "What changed" inside a transactional context.

```shell
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

This is provided as both a synchronous and asynchronous version (Both using the Java Sync library, not reactive Java),
as a database write is a network call and can therefore take potential milliseconds there is usually no need to wait for
it to complete when more data is waiting to send.

Each takes a list of updated or new items of the related Model class (this can be a list of one to replace Save(),
although replacing a single Save() is not much faster it does open up all the additional capabilities. ) , It also needs
to to send the Class of the Model as Java does not let you infer that through a template.

It take and inbvalidDataHandlerService - which lets you define what to do for any documents that could not be processed.

It takes an Update strategy, whether to Insert, OverWrite (Replace) , Update or "Update and Return a change document"
for each of the items in the list. All of these will default to inserting if the document does not exist and will delete
if the _@DeleteFlag_ field is true

PostWiteTriggerService postTrigger - if non null is an interface with a finction that is called after updateing the data
in the database , this function is passed a means to retrieve the nature of all the changes, ddown to a field level and
is used to write change histories as well as any other post-update processing required. It is akin to an RDBMS trigger
but is written in client side Java.

Each returns a standard MongODB BulkWrite Result which provides information on the number and type of operations
completed.

### OptimizedMongoQueryRepsitory.java

_mongoRepository_ provides all the typical Spring/JPA query faciliites - auto generated queries for the form
FindByThisandThat() or FindAllByFieldLessThan() etc. You can also use _@Query_ annotataions and Query by both Exampel
and Criteria as you woudl normally do. You can also use the Native MongoDB driver to construct queries as code using
fluent query building classes.

What _OptimizedMongoQueryRepsitory_ brings is the ability to run queries defined at runtime and passed form an
application. In a similar maanner to GraphQL we may not always want to create an explicit endpoint for every possible
queriy our API supports, if a User Interface is some form of query builder, perhaps a form with many optional fields
then we may want the UI designer to have control over the queries run. We might also want to offer both Datbase Querys
and Lucene full text index queries - Lucene indexing is available in MongODB atlas and gives a powerful way to perform
fuzzy, best-match, full texts queries among others.

This Interface, and it's underlying implemention provide the following three functions

```java

List<T> mongoDbNativeQuery(String jsonString, Class<T> clazz);

List<T> atlasSearchQuery(String jsonString, Class<T> clazz);

int costMongoDbNativeQuery(String jsonString, Class<T> clazz);

```

The first two take a description, in JSON of the required Query, PRojection, Order and Limits - it's very much a general
purpose query function to allow arbitrary queries . Qirty mongoDBNative (QUerying using the MognoDB database and
indexes) or atlasSearch - Querying using any degined Atlas Search Lucenes indexes.

The final method, which you can and shoudl call from your service before passing a query to mongoDbNativeQuery returns
a 'cost' between 1 and 500 for that query, where 1 represents a fullyindexed and index covered query and 500 represents
a collection scan. This score indicates how much resouce the query will take and you can use that to determine how to
proceeed. You may reject queries that will be harmful, you may allow and log them, you may pass them to a secondary or
analytic replica or you may modify them, for example adding a limited and indexed date range so they only impact a
recent set of data. Allo fo this you have the tools to do in your selvice layer.

### OprimisedMongoDownstreamRepository.java

When you want to perform a large extraction of data from a database, through Spring and out as JSON then there is a
simple and objecous solution - perform a query or aggregation to retirve a Stream or the Object model and then convert
those to JSON to stream them out. This is the simplest way to performa a large scale extraction, when we do thsi however
we have to create large numbers of temporary row (Document) objects and Model objects in order to the render them as
JSON. In the case of MongoDB this is a conversion from BSON (The native internal and network format) to Document
classes ( HashMap based) to Model Classes to JSON.

If you are extracing a few MB now and then this is OK but what if yo want to regularly and quickly extract Gigabytes of
data - you can shortcut a lot fo the process above by telling MongoDB you want JSON, not Objects form the databaee and
this is where the single method in OptimisedMongoDownstreamRepository comes in.

```
  Stream<JsonObject> nativeJsonExtract(String formatRequired, Class<T> modelClazz);
```

This is an example it has a single methos to show that instead of `Stream<VehicleInspection>` we can use the much more
efficient `Stream<JsonObject>` and in our ser ice and controller simply stream the out to our end used avoiding 99% of
the object createion and garbage colleciton. JsonObject converts from the BSON native format directly to JSON without
creating any intermediate objets.

To use this we do need to explicitly tell mongodb exactly what our JSON shodul look like, and what we pass in is a
String representation in JSON of the required format, the function performs a find() retriueving the whole collection
and applying this projections. The purpose of this is not to simply use as is but to demonstrate how much more efficient
large scale data downstrewming as JSON (or BSON) can be when you avoid all the SPring obhjectmapping.

### MongoHistoryRepository.java

MongoHistoryRepository is used to read one or multipel documents but to apply the changes made to them over time in
reverse order to revert to older versions. It has two methods although the code can be extended and reused for more
sophiticaed cases including querying historical data.

```java
Stream<T> GetRecordByIdAsOfDate(I recordId, Date asOf, Class<T> clazz);

Stream<T> GetRecordsAsOfDate(Criteria criteria, Date asOf, Class<T> clazz);
```

This assumes the documents, and any changed version sof them were ingested using the OptimizedLoadRepository with the
record history option enabled. These methods take the base document (latest version) perform a $lookup (JOIN) to fetch
the historical changes and then ,merge tthose to wind the document back to it's prior form.

# Services

Services sit between Controlers ( Handing interfaces to the service) and Repositories (Interfacing with an underlying ]
database). Services contain business logic and processing that is specific to your business and also how you intend to
use the data - a very simple controller could acess the repository directly but this is not good practise. In the Memex
examples we do not access the repository directly from the controller - only via services, even if in some cases those
service classes perform no additonal processing or logic.

## MongoDbJsonStreamingLoaderService.java

The most significant service in Memex is the StreamingJsonLoaderService - this service parses an incoming JSON stream
into the associated Model class and every 200 documents it uses OptimizedMongoLoadRespository to load that batch of
documents as an asynchronous process whilst it parses and processes the next 200 documents. This Stream processing and
parallelism is at the haeart of high performance data loading in Memex.

It can accept data as a JSON array or simply multiple JSON obejects, optionaly seperated by a newline, it work by
finding the first object start token `{` then finding the corresponding close token `}` and treating what is between
them as and object to load - it then finds the next open token.

After parsing each document to a model object, the _StreamingJsonLoader_ will pass that object to a _preWriteTrigger_
class if one is defined to allow for any modifications needed before writing to MongoDB.

## PreWriteTriggerService.java

This class defines a single overrideable functiion which is called by StreamingJsonLoaderService
( May be moved to OptimizedLoadRepositor soon). This is called for each Model object just before writing it to
MongoDB and can be used to augment the data - the correct method to override depends on whather you ar e using mutable
or immutable models. The default class does nothing to the object

```java
public void modifyMutableDataPreWrite(T document) {
}

public T newImmutableDataPreWrite(T document) {
    return document;
}
```

## PostWriteTriggerService.java

This defines a function to be called after a batch of documents have been written to MongoDB inside the transaction
used to write them and before it commits. The dfault function does nothing but can be overridder in a derived class.

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

This function recieves the transactional session used for the write. By using that session inside this function you are
able to see operations that have not yet been comitted.
It recieves the BulkWriteResult object descring how many of each operation took place and the id values for
anything that was inserted.
It revieces the batch of rercords that were presented for updating, deleteing or inserting
It also inclues an updateId value, this can be used to Query MongoDB to retireve the documents as they are post update
if needed.

This is derived from the greate the HistoryTriggerService that records changes to the data transactionally.

## HistoryTriggerService.java

HistoryTriggerService is a tempalted reuasable class, extending PostWriteTriggerService to generate a change hostiry
of any data updated.

Where a model is stored in collection X, for example `vehicleinspaeciton` this class writes DocumentHistory objects to
`vehicleinspection_history`. These are a reverse delta history showing field values in previous versions of the document
at each stage. To use them you need to take the latest version of the data and apply in reverse order - this is done by
MongoHistoryReportity for you for functions such as asOf (a point in time).

When a record is first inserted, then an entry is recorded in the history table just saying this is when the record was
first inserted, it does not contain any of the content.

When a record is modified, then the modified fields previous version are stored alone with an updateid and timestamp

When a record is deleted then , currently the whole record is stored in the trigger service and removed from the
principle colleciton.
This could be changed to delete the record from the principle collection AND to remove all hisotic versions very easily
but
many users prefer to archive the data or retain a hisotoyry of its existence and deleteion. It woudl also be simple to
have a history that
retained the creation and deleteion records but no record of the content to modifications.

## InvalidDataHandlerService.java

This class frines a singel function to be called before a document is loaded into the database if that documnt fails
java bean validation Java Bean Validation.
It is passed the document and a list of ways it fails validation - the default behaviour is to log as a warning.
This function returns a boolean which determines is the data shoudl be loaded anyway or skipped in the data
loading/updaing process.
It shoudl be used to populate a data file of things to be reloaded - it is designed to allow the rest of the load to
complete and errors or exceprions to be caught.

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

This Java Bean based on ApplicationRunner demonstrates one good practise with services built on MongoDB - what the
server validate any database prerequisites,
be they permissions, version or the exisstence of database objects includeing collecitons, indexes, search indexes and
schema validators.

The way things like indexes are ppopulated through environment can differ based on the system, srchitecture and business
requirements. In a
development environment it is normal to generate indexes that are not present in the preflight service, however
in production, given creation of an index may have an impact on a running system it is better to build index via
management toolling and failover orchestration. In that case pre-flight shoudl fail until the index already exsts.

By default this is configured to create indexes and search indexes for ease fo developemtn but warn in the logs this is
not for priduciton.

## VehicleInspectionPreWriteTriggerService.java

This is provided as an example of a pre-write trigger which is passed makes small changes to the incoming data to
makle it easy to test updates, you can load the same file but this will modify some details to cause an update and
relevant history entry if required. It also serves to demonstrate what a preWrite trigger looks like both for
mutable and immutable data

## VehicleInspectionQueryService.java

The class OptimizedMongoQueryRepository provides an endpoint supporting any MongoDB query defined by an EJSON string,
this is intended to allow front end clients to define their own queries and avoid the requiremetn to create an
endpoint for each new frontend feature - especially where some for of GUI query creation is available.

The OptimizedMongoQueryRepository also provides a cost estimator function for queries to determine if they will take
a few or a lot fo resources from the datbase server. This Service takes each incoming query, performs a cost
estimation (This is efficent and cached in the estimator) logs the cost and runs it anyway - this si where you woudl
add business logic to deny, log , modify or redirect expensive queries to a secondayr server.

This class shows how to expose a simple hard-coded query and a Spring query-by-example to the controller class.

## VehicleInspectionXXXXService.java

Where XXXXX is one of: DownStream,History,HistoryTrigger,InvalidDataHandler,JsonLoader then the service is thin
wrapper over the generic service or to provide a simple call to the repository with space for additional logic.

# Controllers

Memex includes an example _@RestController_ to show how the features are acessed, a RestController was chosen as it
is the simplest to test and understand but other controller types are equally usable in your own code.

## VehicleInspectionController.java

