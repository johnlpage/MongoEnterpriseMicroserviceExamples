package com.johnlpage.memex.generics.repository;

import com.johnlpage.memex.generics.service.HistoryTriggerService;
import com.johnlpage.memex.util.AnnotationExtractor;
import com.johnlpage.memex.util.CustomAggregationOperation;
import com.johnlpage.memex.util.MongoVersionBean;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.*;
import org.springframework.data.mongodb.core.query.Criteria;

import java.util.*;
import java.util.stream.Stream;

import static org.springframework.data.mongodb.core.aggregation.LookupOperation.newLookup;

public class MongoHistoryRepositoryImpl<T, I> implements MongoHistoryRepository<T, I> {
    private static final Logger LOG = LoggerFactory.getLogger(MongoHistoryRepositoryImpl.class);
    private static final int MAX_UNROLL_DEPTH = 10;
    private final MongoTemplate mongoTemplate;
    private final MongoVersionBean mongoVersion;

    public MongoHistoryRepositoryImpl(MongoTemplate mongoTemplate, MongoVersionBean mongoVersion) {
        this.mongoTemplate = mongoTemplate;
        this.mongoVersion = mongoVersion;
    }

    public Stream<T> GetRecordByIdAsOfDate(I recordId, Date asOf, Class<T> clazz) {
        Criteria criteria = Criteria.where("_id").is(recordId);
        return GetRecordsAsOfDate(criteria, asOf, clazz);
    }

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

        // Convert versions to arrays of key-value pairs
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

        // Flatten nested objects
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

        stages.addAll(Collections.nCopies(MAX_UNROLL_DEPTH, makeFlatter));



        // Convert flattened key/value arrays back to objects
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

        /*
         * Instead of a simple $mergeObjects, we use $reduce to merge versions one by one.
         * For each field: if the new value is an array, do element-wise merge where MinKey
         * elements preserve the accumulated value. For non-array fields, just take the new value.
         */
        AggregationOperation mergeHistoryObjects =
                new CustomAggregationOperation(
                        new Document("$project",
                                new Document("payload.combined", minKeyAwareMerge())));

        stages.add(mergeHistoryObjects);

        // Rebuild nested objects from dot-notation
        AggregationOperation rebuildPass =
                new CustomAggregationOperation(
                        new Document("$set", new Document("payload.combined", rebuildObject())));
        stages.addAll(Collections.nCopies(MAX_UNROLL_DEPTH, rebuildPass));

        AggregationOperation replaceRoot = Aggregation.replaceRoot("payload.combined");
        AggregationOperation removeLockVersion = Aggregation.project().andExclude("lock_version");
        stages.add(replaceRoot);
        stages.add(removeLockVersion);

        TypedAggregation<T> aggregation = TypedAggregation.newAggregation(clazz, stages);

        try {
            return mongoTemplate.aggregateStream(aggregation, clazz);
        } catch (Exception e) {
            LOG.error(e.getMessage());
            return Stream.empty();
        }
    }

    /**
     * Produces an expression that reduces the __versions array by merging each version
     * into an accumulator. For each field in the new version:
     * - If the value is an array, do element-wise merge: replace MinKey elements with
     *   the corresponding element from the accumulator's version of that array.
     * - Otherwise, just take the new value (standard $mergeObjects behavior).
     *
     * The result is equivalent to $mergeObjects but with MinKey-aware array handling.
     */
    private Document minKeyAwareMerge() {
        /*
         * For a single array field, merge element-wise:
         *   For each index i in newArray:
         *     if newArray[i] is MinKey -> keep accArray[i]
         *     else -> take newArray[i]
         *
         * We also need to handle the case where the accumulated array is longer than the
         * new array (keep trailing elements) or shorter (take new trailing elements).
         */

        // $$newVal is the new field value (an array)
        // $$accVal is the accumulated field value (may be an array or may not exist)

        // Build the element-wise array merge expression
        // We iterate over the indices of the longer of the two arrays
        Document mergedArray = elementWiseArrayMerge("$$accVal", "$$newVal");

        // For each field in the incoming version object, decide how to merge it
        // We convert the new version to k/v pairs, process each, then convert back

        // The new version's fields as k/v array
        Document newVersionKV = new Document("$objectToArray", "$$newVersion");

        // For each k/v pair in the new version, produce a merged k/v pair
        Document processedKV = new Document("$map",
                new Document("input", newVersionKV)
                        .append("as", "field")
                        .append("in",
                                new Document("k", "$$field.k")
                                        .append("v",
                                                new Document("$cond",
                                                        new Document("if",
                                                                new Document("$isArray", "$$field.v"))
                                                                .append("then",
                                                                        // Array field: do element-wise MinKey-aware merge
                                                                        new Document("$let",
                                                                                new Document("vars",
                                                                                        new Document("accVal",
                                                                                                getFieldFromObject("$$acc", "$$field.k"))
                                                                                                .append("newVal", "$$field.v"))
                                                                                        .append("in", mergedArray)))
                                                                .append("else",
                                                                        // Non-array field: just take the new value
                                                                        "$$field.v")))));

        // Convert the processed k/v pairs back to an object and merge with accumulator
        Document mergedVersion = new Document("$mergeObjects",
                List.of("$$acc", new Document("$arrayToObject", processedKV)));

        // $reduce over all versions
        return new Document("$reduce",
                new Document("input", "$__versions")
                        .append("initialValue", new Document())
                        .append("in",
                                new Document("$let",
                                        new Document("vars",
                                                new Document("acc", "$$value")
                                                        .append("newVersion", "$$this"))
                                                .append("in", mergedVersion))));
    }

    /**
     * Produces an expression that does element-wise merge of two arrays,
     * where MinKey in the new array means "keep the old value".
     *
     * @param accArrayExpr expression resolving to the accumulated array
     * @param newArrayExpr expression resolving to the new array
     * @return a Document expression that produces the merged array
     */
    private Document elementWiseArrayMerge(String accArrayExpr, String newArrayExpr) {
        // Handle case where accumulated value isn't an array (first time seeing this field)
        // In that case, just strip MinKey values and replace with null (or just return new as-is)
        // Actually if there's no accumulated value, we should just take the new array as-is
        // because MinKey in the first version would mean "no change" but there's nothing to
        // fall back to. This shouldn't normally happen (first version is the current doc).

        // Ensure accArray is usable - if null/missing, use an empty array
        Document safeAccArray = new Document("$ifNull", List.of(accArrayExpr, List.of()));

        // Use $range to iterate over indices of the longer array
        Document accSize = new Document("$size", safeAccArray);
        Document newSize = new Document("$size", newArrayExpr);
        Document maxSize = new Document("$max", List.of(accSize, newSize));

        Document indices = new Document("$range", List.of(0, maxSize));

        // For each index, pick the right element
        Document elementAtIndex = new Document("$map",
                new Document("input", indices)
                        .append("as", "idx")
                        .append("in",
                                new Document("$let",
                                        new Document("vars",
                                                new Document("newElem",
                                                        new Document("$cond",
                                                                new Document("if",
                                                                        new Document("$lt", List.of("$$idx", newSize)))
                                                                        .append("then",
                                                                                new Document("$arrayElemAt", List.of(newArrayExpr, "$$idx")))
                                                                        .append("else", null)))
                                                        .append("accElem",
                                                                new Document("$cond",
                                                                        new Document("if",
                                                                                new Document("$lt", List.of("$$idx", accSize)))
                                                                                .append("then",
                                                                                        new Document("$arrayElemAt", List.of(safeAccArray, "$$idx")))
                                                                                .append("else", null))))
                                                .append("in",
                                                        new Document("$cond",
                                                                new Document("if",
                                                                        // Check if newElem is MinKey
                                                                        new Document("$eq",
                                                                                List.of(new Document("$type", "$$newElem"), "minKey")))
                                                                        .append("then", "$$accElem")
                                                                        .append("else", "$$newElem"))))));

        // If the accumulated value is not an array (or missing), just return the new array
        // but filter out any MinKey values (replace with null) since there's nothing to fall back to
        return new Document("$cond",
                new Document("if", new Document("$isArray", safeAccArray))
                        .append("then", elementAtIndex)
                        .append("else", newArrayExpr));
    }

    /**
     * Helper to get a field from an object by dynamic field name.
     * Uses $getField on MongoDB 8+ or falls back to $filter/$objectToArray approach.
     */
    private Document getFieldFromObject(String objectExpr, String fieldExpr) {
        if (mongoVersion.getMajorVersion() >= 8) {
            return new Document("$getField",
                    new Document("input", objectExpr).append("field", fieldExpr));
        } else {
            // Fallback: convert to k/v array, filter by key, extract value
            Document fieldArray = new Document("$objectToArray", objectExpr);
            Document filterExpr = new Document("$filter",
                    new Document("input", fieldArray)
                            .append("as", "field")
                            .append("limit", 1)
                            .append("cond", new Document("$eq", List.of("$$field.k", fieldExpr))));
            Document fieldObj = new Document("$first", filterExpr);
            return new Document("$getField",
                    new Document("input", fieldObj).append("field", "v"));
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

        Document regexFindExpr =
                new Document("$regexFind", new Document("input", "$$this.k").append("regex", "^(.*)\\."));

        Document letExpr =
                new Document(
                        "$let",
                        new Document("vars", new Document("match", regexFindExpr))
                                .append("in", new Document("$arrayElemAt", List.of("$$match.captures", 0))));

        Document path = new Document("$ifNull", List.of(letExpr, ""));

        Document key =
                new Document(
                        "$substr",
                        List.of(
                                "$$this.k", new Document("$add", List.of(new Document("$strLenCP", path), 1)), -1));

        Document dynamicObjExpr =
                new Document(
                        "$arrayToObject", List.of(List.of(new Document("k", key).append("v", "$$this.v"))));

        Document existingValue;
        if (mongoVersion.getMajorVersion() >= 8) {
            existingValue =
                    new Document("$getField", new Document("input", "$$value").append("field", path));
        } else {
            Bson fieldArray = new Document("$objectToArray", "$$value");
            Bson cond = new Document("$eq", java.util.Arrays.asList("$$field.k", path));
            Bson filterExpr =
                    new Document(
                            "$filter",
                            new Document("input", fieldArray)
                                    .append("as", "field")
                                    .append("limit", 1)
                                    .append("cond", cond));
            Bson fieldObj = new Document("$first", filterExpr);
            existingValue =
                    new Document("$getField", new Document().append("input", fieldObj).append("field", "v"));
        }

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

        var newObject =
                new Document(
                        "$arrayToObject",
                        List.of(List.of(new Document("k", "$$this.k").append("v", "$$this.v"))));

        Document isRootElement = new Document("$eq", List.of(path, ""));

        Document flattenElements =
                new Document(
                        "$mergeObjects",
                        List.of(
                                "$$value",
                                new Document(
                                        "$cond",
                                        new Document("if", isRootElement)
                                                .append("then", newObject)
                                                .append("else", mergeWithParent))));

        return new Document(
                "$reduce",
                new Document("input", new Document("$objectToArray", "$payload.combined"))
                        .append("initialValue", new Document())
                        .append("in", flattenElements));
    }
}
