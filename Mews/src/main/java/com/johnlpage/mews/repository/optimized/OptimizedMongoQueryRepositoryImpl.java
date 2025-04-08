package com.johnlpage.mews.repository.optimized;

import static com.johnlpage.mews.util.AnnotationExtractor.renameKeysRecursively;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Aggregates;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.mapping.PersistentEntity;
import org.springframework.data.mapping.PersistentProperty;
import org.springframework.data.mapping.context.MappingContext;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.convert.MappingMongoConverter;
import org.springframework.data.mongodb.core.convert.QueryMapper;
import org.springframework.data.mongodb.core.mapping.MongoPersistentEntity;
import org.springframework.data.mongodb.core.query.BasicQuery;

@RequiredArgsConstructor
public class OptimizedMongoQueryRepositoryImpl<T> implements OptimizedMongoQueryRepository<T> {

  private static final Logger LOG =
      LoggerFactory.getLogger(OptimizedMongoQueryRepositoryImpl.class);
  private final MongoTemplate mongoTemplate;
  private final Map<String, QueryScore> queryScores = new HashMap<>();

  public static String computeHash(List<String> strings) {
    try {
      // Concatenate all strings in the list
      StringBuilder combinedString = new StringBuilder();
      for (String s : strings) {
        combinedString.append(s);
      }

      // Choose hashing algorithm
      MessageDigest digest = MessageDigest.getInstance("SHA-256");

      // Compute the hash
      byte[] hashBytes = digest.digest(combinedString.toString().getBytes());

      // Convert hash bytes to a hex string
      StringBuilder hashString = new StringBuilder();
      for (byte b : hashBytes) {
        hashString.append(String.format("%02x", b)); // %02x formats the byte as a two-digit hex
      }
      return hashString.toString();
    } catch (NoSuchAlgorithmException e) {
      throw new RuntimeException("Hashing algorithm not found", e);
    }
  }

  /**
   * This is wrapping the ability to do a native MongoDB call Passing in Query, Sort,Skip,Limit and
   * Projection
   */
  public List<T> mongoDbNativeQuery(String jsonString, Class<T> clazz) {
    try {
      Document queryRequest = Document.parse(jsonString);
      Document filter = queryRequest.get("filter", new Document());
      Document projection = queryRequest.get("projection", new Document());
      Document sort = queryRequest.get("sort", new Document());

      // This maps from the JSON fields we see to the underlying field names if they aren't the same
      filter = new Document(renameKeysRecursively(clazz, filter));
      projection = new Document(renameKeysRecursively(clazz, projection));
      sort = new Document(renameKeysRecursively(clazz, sort));

      int skip = queryRequest.getInteger("skip") != null ? queryRequest.getInteger("skip") : 0;
      // Default ot a limit of 1000 unless otherwise advised
      int limit =
          queryRequest.getInteger("limit") != null ? queryRequest.getInteger("limit") : 1000;

      BasicQuery query = new BasicQuery(filter, projection);
      query.skip(skip);
      query.limit(limit);
      query.setSortObject(sort);

      return mongoTemplate.find(query, clazz);

    } catch (Exception e) {
      LOG.warn(e.getMessage(), e);
      return List.of();
    }
  }

  public int costMongoDbNativeQuery(String jsonString, Class<T> clazz) {
    Document queryRequest = Document.parse(jsonString);
    Document filter = queryRequest.get("filter", new Document());
    Document projection = queryRequest.get("projection", new Document());
    Document sort = queryRequest.get("sort", new Document());

    // This maps from the JSON fields we see to the underlying field names if they aren't the same
    filter = new Document(renameKeysRecursively(clazz, filter));
    projection = new Document(renameKeysRecursively(clazz, projection));
    sort = new Document(renameKeysRecursively(clazz, sort));

    // Check the cost to see if we allow it

    return costManager(clazz, filter, projection, sort);
  }

  /**
   * Because the getModelByMongoQuery allows any query - we want to be able to assess each query and
   * then decide what we are doing based on the cost - for example disallow unindexed queries or
   * send them to secondaries, flag and log them etc. To do this we need to know the cost of a query
   * by calling explain - but also possibly caching that so we don't do it for every call
   */
  private Integer costManager(Class<T> type, Document filter, Document projection, Document sort) {
    int queriesBeforeRecheck = 1000;

    String collectionName = mongoTemplate.getCollectionName(type);
    MongoCollection<Document> collection = mongoTemplate.getCollection(collectionName);

    // To get any field mappings we need to take the incoming query and perform any
    // field mapping hidden away in the model - like the @ID annotations even our RAW interface
    // will want to use the Spring field names.

    MappingMongoConverter mongoConverter = (MappingMongoConverter) mongoTemplate.getConverter();

    MappingContext<? extends PersistentEntity<?, ?>, ? extends PersistentProperty<?>>
        mappingContext = mongoConverter.getMappingContext();

    QueryMapper queryMapper = new QueryMapper(mongoConverter);

    filter =
        queryMapper.getMappedObject(
            filter, (MongoPersistentEntity<?>) mappingContext.getPersistentEntity(type));
    projection =
        queryMapper.getMappedObject(
            projection, (MongoPersistentEntity<?>) mappingContext.getPersistentEntity(type));
    sort =
        queryMapper.getMappedObject(
            sort, (MongoPersistentEntity<?>) mappingContext.getPersistentEntity(type));

    QueryScore qs;

    String queryHash = computeQueryShapeHash(filter, projection, sort);

    qs = queryScores.get(queryHash);
    if (qs != null && qs.count < queriesBeforeRecheck) {
      qs.count++;
      return qs.score;
    }

    // Retrieve the explain plan for the query
    LOG.info("Query Shape {} not cached  - estimating efficiency.", filter.toJson());
    // Added a limit in here as otherwise a COLLSCAN can take forever.
    Document explain =
        collection.find(filter).sort(sort).projection(projection).limit(1000).explain();
    // JsonWriterSettings jsonWriterSettings = JsonWriterSettings.builder().indent(true).build();
    // Uncomment this to see the query explain in the logs.
    // LOG.info(explain.toJson(jsonWriterSettings));

    int score = 0;

    String queryPlan =
        explain.get("queryPlanner", new Document()).get("winningPlan", new Document()).toJson();

    if (queryPlan.contains("COLLSCAN")) {
      // Limits and other things may come first - is COLLSCAN is in there it's bad
      LOG.warn("COLLECTION SCAN QUERY !- This is not healthy in production.");
      score = 500; // Unindexed query is very expensive
    } else {
      Document exStats = explain.get("executionStats", new Document());
      int nReturned = exStats.get("nReturned", 0);
      int totalKeysExamined = exStats.get("totalKeysExamined", 0);
      int totalDocsExamined = exStats.get("totalDocsExamined", 0);

      if (nReturned == totalKeysExamined && totalDocsExamined == 0) {
        score = 1; // This is a covered query which is great
      } else if (nReturned == totalKeysExamined && nReturned == totalDocsExamined) {
        // Well indexed non covered
        score = 3;
      }
      // Imperfect index so more index scanning but no extra fetch
      else if (nReturned == totalDocsExamined && totalKeysExamined > nReturned) {
        score = 5;
      }
      // Index wasn't enough had to filter again after FETCH ing document
      else if (totalKeysExamined == totalDocsExamined && totalDocsExamined > nReturned) {
        score = 10;
      }

      // Cost of a sort depends on how much we are sorting but quite a lot!
      if (queryPlan.contains("SORT")) {
        // Small set isn't a HUGE issue
        if (nReturned < 100) {
          score += 10;
        } else if (nReturned < 1000) {
          score += 25;
        } else {
          score += 100;
        }
      }
    }

    // Hash the latest score
    qs = new QueryScore();
    qs.count = 0;
    qs.score = score;
    queryScores.put(queryHash, qs);
    LOG.info("EFFICIENCY SCORE OF QUERY = {}", score);
    return score;
  }

  private String computeQueryShapeHash(Document filter, Document projection, Document sort) {
    ArrayList<String> fields = new ArrayList<>();
    // idea here is to flatten out into list of field names

    for (Map.Entry<String, Object> entry : filter.entrySet()) {
      fields.add(entry.getKey());
    }

    for (Map.Entry<String, Object> entry : projection.entrySet()) {
      fields.add("projection." + entry.getKey());
    }

    Collections.sort(fields);
    for (Map.Entry<String, Object> entry : sort.entrySet()) {
      fields.add("sort." + entry.getKey());
    }
    // Sort is special because the order matters
    return computeHash(fields);
  }

  public List<T> atlasSearchQuery(String jsonString, Class<T> clazz) {
    try {
      Document queryRequest = Document.parse(jsonString);
      Document searchSpec = queryRequest.get("search", new Document());

      Document projection = queryRequest.get("projection", new Document());

      // Both sorting and filtering are options with Atlas Search but better not as pipeline stages.
      // Document sort = queryRequest.get("sort", new Document());
      // Document filter = queryRequest.get("filter", new Document());

      int skip = queryRequest.getInteger("skip") != null ? queryRequest.getInteger("skip") : 0;
      // Default ot a limit of 1000 unless otherwise advised
      int limit =
          queryRequest.getInteger("limit") != null ? queryRequest.getInteger("limit") : 1000;

      Bson searchStage = new Document("$search", searchSpec);
      Aggregation aggregation;
      if (!projection.isEmpty()) {
        Bson projectStage = Aggregates.project(projection);

        aggregation =
            Aggregation.newAggregation(
                Aggregation.stage(searchStage),
                Aggregation.skip(skip),
                Aggregation.limit(limit),
                Aggregation.stage(projectStage));

      } else {
        aggregation =
            Aggregation.newAggregation(
                Aggregation.stage(searchStage), Aggregation.skip(skip), Aggregation.limit(limit));
      }
      LOG.info(aggregation.toString());
      AggregationResults<T> results = mongoTemplate.aggregate(aggregation, clazz, clazz);
      return results.getMappedResults();

    } catch (Exception e) {
      LOG.error("Error parsing query request", e);
    }
    return List.of();
  }

  private static final class QueryScore {
    int count;
    int score;
  }
}
