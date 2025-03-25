package com.johnlpage.mews.repository.optimized;

import static org.springframework.data.mongodb.core.aggregation.LookupOperation.*;

import com.johnlpage.mews.service.generic.HistoryTriggerService;
import com.johnlpage.mews.util.AnnotationExtractor;
import com.johnlpage.mews.util.CustomAggregationOperation;
import java.util.*;
import java.util.stream.Stream;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.*;
import org.springframework.data.mongodb.core.query.Criteria;

public class MongoHistoryRepositoryImpl<T, I> implements MongoHistoryRepository<T, I> {
  private static final Logger LOG = LoggerFactory.getLogger(MongoHistoryRepositoryImpl.class);
  private static final int MAX_UNROLL_DEPTH = 10;
  private final MongoTemplate mongoTemplate;

  public MongoHistoryRepositoryImpl(MongoTemplate mongoTemplate) {
    this.mongoTemplate = mongoTemplate;
  }

  public Stream<T> GetRecordByIdAsOfDate(I recordId, Date asOf, Class<T> clazz) {
    // Create a query object from the criteria
    Criteria criteria = Criteria.where("_id").is(recordId); // TODO Move up
    return GetRecordsAsOfDate(criteria, asOf, clazz);
  }

  /*
   This is pretty complex, We need to fetch the Records, their history and then apply
   All relevant history changes. We could actually do this for any query
   This is complicated by the need to flatten out and then recombine the objects as
   $mergeObjects will take the last whole value iven if that's an object
  */

  public Stream<T> GetRecordsAsOfDate(Criteria criteria, Date asOf, Class<T> clazz) {

    List<AggregationOperation> stages = new ArrayList<>();
    String collectionName = AnnotationExtractor.getCollectionName(clazz);
    String historyCollectionName = collectionName + HistoryTriggerService.HISTORY_POSTFIX;

    // Get the Records we want history for
    stages.add(Aggregation.match(criteria));

    // Get rid of the __previousvals appendix, it's in the history anyway
    AggregationOperation removePrevious =
        Aggregation.project().andExclude(OptimizedMongoLoadRepositoryImpl.PREVIOUS_VALS);
    stages.add(removePrevious);

    // Move everything that's currently at root one level down
    AggregationOperation shiftRootDown = Aggregation.project().and("$$ROOT").as("__root");
    stages.add(shiftRootDown);

    /*
     use $lookup to fetch the History records for it
     TODO - Deal with an insert operation if we have one we shouldn't return the document
    */

    AggregationPipeline lookupHistoryPipeline =
        Aggregation.newAggregation(
                Aggregation.match(Criteria.where("timestamp").gt(asOf)),
                Aggregation.sort(Sort.Direction.DESC, "timestamp"),
                Aggregation.replaceRoot("changes"))
            .getPipeline();

    // Fetch and Reshape relevant history entries
    LookupOperation fetchRelevantHistoryEntries =
        newLookup()
            .from(historyCollectionName)
            .localField("__root._id")
            .foreignField("recordId")
            .pipeline(lookupHistoryPipeline)
            .as("__versions");

    stages.add(fetchRelevantHistoryEntries);

    // Move what was the root document into the top of the array.

    AggregationOperation addCurrentVersionToHistory =
        Aggregation.project()
            .and(ArrayOperators.ConcatArrays.arrayOf(List.of("$__root")).concat("$__versions"))
            .as("__versions");

    stages.add(addCurrentVersionToHistory);

    /*
     Before we merge history entries  we need to flatten them - if you ask MongoDB to merge
     { a: 1, b: { c: 1, d:2 }} and  { b: { c: 3} } you get {a:1, b:{c:3} } as it
     considers the whole object to be a new field value - to deep combine we need to
     unroll this to top level fields like { "b.c" :1, "b.d":1} before we merge them
     We do not want to $unwind the array to do this and then $group the results - that
     sounds OK but isn't really  a big antipattern as the $group needs to wait for all records to
     be processed and hold everything in RAM. $group doesn't know it's  undoing an $unwind.
     WHen developing aggregations, you quite often end up developing in Compass / Javascript and
     then
     You have aggregations as Document's not as predefined operators, We can convert them like
     this.
     Convert the array of version objects to an array of arrays of key value pairs. using
     $objectToArray on each inside $map
    */

    AggregationOperation versionsAsArrays =
        new CustomAggregationOperation(
            new Document(
                "$set",
                new Document(
                    "__versions",
                    new Document(
                        "$map",
                        new Document("input", "$__versions")
                            .append("in", new Document("$objectToArray", "$$this"))))));

    stages.add(versionsAsArrays);

    /*
     Iterate over that array, where we have depth then unroll it - all the code for that is  in
     the flattenObject function. We need to call this multiple times as each pass flattens one
     level.
    */

    AggregationOperation makeFlatter =
        new CustomAggregationOperation(
            new Document(
                "$set",
                new Document(
                    "__versions",
                    new Document(
                        "$map",
                        new Document("input", "$__versions")
                            .append("as", "version")
                            .append("in", flattenObject())))));

    // Add the flatten out stage multiple times - it's  fast because no $unwind/$group
    stages.addAll(Collections.nCopies(MAX_UNROLL_DEPTH, makeFlatter));

    // Now we have flattened out the key/value arrays we want to make them be objects again.
    AggregationOperation versionsAsFlatObjects =
        new CustomAggregationOperation(
            new Document(
                "$project",
                new Document(
                    "__versions",
                    new Document(
                        "$map",
                        new Document("input", "$__versions")
                            .append("in", new Document("$arrayToObject", "$$this"))))));
    stages.add(versionsAsFlatObjects);

    // Now can combine the versions in order by using $mergeObjects
    AggregationOperation mergeHistoryObjects =
        Aggregation.project()
            .and(ObjectOperators.MergeObjects.merge("$__versions"))
            .as("payload.combined");

    stages.add(mergeHistoryObjects);

    /*
     This does the opposite of flattening, taking an object that is  { "a.b":1} and converting to
     { a: {b:1}})
    */

    AggregationOperation rebuildPass =
        new CustomAggregationOperation(
            new Document("$set", new Document("payload.combined", rebuildObject())));
    stages.addAll(Collections.nCopies(MAX_UNROLL_DEPTH, rebuildPass));

    TypedAggregation<T> aggregation = TypedAggregation.newAggregation(clazz, stages);

    try {
      return mongoTemplate.aggregateStream(aggregation, clazz);
    } catch (Exception e) {
      LOG.error(e.getMessage());
      return Stream.empty();
    }
  }

  // This takes an Object
  private Document flattenObject() {

    // Evaluates to true if the value part of the current KV pair is an object
    Document isDocument =
        new Document("$eq", Arrays.asList(new Document("$type", "$$this.v"), "object"));

    Document subObject =
        new Document(
            "$map",
            new Document("input", new Document("$objectToArray", "$$this.v"))
                .append("as", "that")
                .append(
                    "in",
                    new Document(
                            "k",
                            new Document("$concat", Arrays.asList("$$this.k", ".", "$$that.k")))
                        .append("v", "$$that.v")));

    Document asIs = new Document("$cond", Arrays.asList(isDocument, subObject, List.of("$$this")));

    Document unwindElement = new Document("$concatArrays", Arrays.asList("$$value", asIs));

    return new Document(
        "$reduce",
        new Document("input", "$$version")
            .append("initialValue", Collections.emptyList())
            .append("in", unwindElement));
  }

  // Take a single object with dotpath fields and convert it to a nested one

  private Document rebuildObject() {

    // Create the $regexFind expression to find up to the last dot
    Document regexFindExpr =
        new Document("$regexFind", new Document("input", "$$this.k").append("regex", "^(.*)\\."));
    /*
     Define the $let expression to pull a value out of object returned by $regex ( we want all
     this in one stage otherwise you can use $set in one stage and grab parts in the next stage.
    */
    Document letExpr =
        new Document(
            "$let",
            new Document("vars", new Document("match", regexFindExpr))
                .append("in", new Document("$arrayElemAt", List.of("$$match.captures", 0))));

    // Wrap an $ifNull round it so if we didnt have a path it becomes an empty string.
    Document path = new Document("$ifNull", List.of(letExpr, ""));

    // Create the key using $substr and the path length
    Document key =
        new Document(
            "$substr",
            List.of(
                "$$this.k", new Document("$add", List.of(new Document("$strLenCP", path), 1)), -1));

    // Create dynamicObj from a key and value , { "k": "a", "v": 1} -> { "a" : 1}

    Document dynamicObjExpr =
        new Document(
            "$arrayToObject", List.of(List.of(new Document("k", key).append("v", "$$this.v"))));

    // Get any existing Field content from the accumulating value

    Document existingValue =
        new Document("$getField", new Document("input", "$$value").append("field", path));

    // Merge this value with any parent object if we have one

    Document mergeWithParent =
        new Document(
            "$arrayToObject",
            List.of(
                List.of(
                    new Document("k", path)
                        .append(
                            "v",
                            new Document(
                                "$mergeObjects", List.of(existingValue, dynamicObjExpr))))));

    /*
     More efficient than dynamicObjectExpression as here we can just use "$$this.k" we don't
     need to check if it has depth.
    */

    var newObject =
        new Document(
            "$arrayToObject",
            List.of(List.of(new Document("k", "$$this.k").append("v", "$$this.v"))));

    /*
     If no path then it's all in key, arguably path is expensive to compute
     We should just check $$this.k for a dot (TODO)
    */

    Document isRootElement = new Document("$eq", List.of(path, ""));

    Document flattenElements =
        new Document(
            "$mergeObjects",
            List.of(
                "$$value",
                new Document(
                    "$cond",
                    new Document("if", isRootElement)
                        .append("then", newObject) // Copy into new object as is
                        .append("else", mergeWithParent))));

    /*
     Iterate over the K/V array converting it back to an object but also merging things
     with same parent
    */

    return new Document(
        "$reduce",
        new Document("input", new Document("$objectToArray", "$payload.combined"))
            .append("initialValue", new Document())
            .append("in", flattenElements));
  }
}
