package com.johnlpage.mews.service;

import com.johnlpage.mews.models.MewsModel;
import com.mongodb.client.MongoCollection;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.bson.Document;
import org.bson.json.JsonWriterSettings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Example;
import org.springframework.data.domain.ExampleMatcher;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.BasicQuery;
import org.springframework.data.mongodb.repository.MongoRepository;

@RequiredArgsConstructor
public abstract class MongoDbJsonQueryService<T extends MewsModel<ID>, ID> {

  private static final Logger LOG = LoggerFactory.getLogger(MongoDbJsonQueryService.class);
  private final MongoRepository<T, ID> repository;
  private final MongoTemplate mongoTemplate;

  /** Find One by ID */
  public Optional<T> getModelById(ID id) {
    return repository.findById(id);
  }

  /** Find By Example with Paging */
  public Slice<T> getModelByExample(T probe, int page, int size) {
    ExampleMatcher matcher = ExampleMatcher.matching().withIgnoreNullValues();
    Example<T> example = Example.of(probe, matcher);
    return repository.findAll(example, PageRequest.of(page, size));
  }

  /**
   * This is wrapping the ability to do a native MongoDB call Passing in Query, Sort,Skip,Limit and
   * Projection
   */
  public List<T> getModelByMongoQuery(String jsonString, Class<T> type) {
    try {
      Document queryRequest = Document.parse(jsonString);
      Document filter = queryRequest.get("filter", new Document());
      Document projection = queryRequest.get("projection", new Document());
      int skip = queryRequest.getInteger("skip") != null ? queryRequest.getInteger("skip") : 0;
      int limit =
          queryRequest.getInteger("limit") != null ? queryRequest.getInteger("limit") : 1000;
      Document sort = queryRequest.get("sort", new Document());

      // Check the cost to see if we allow it
      Integer cost = costManager(type, filter, projection, sort);

      // Decide what to do based on cost

      BasicQuery query = new BasicQuery(filter, projection);
      query.skip(skip);
      query.limit(limit);
      query.setSortObject(sort);

      return mongoTemplate.find(query, type);

    } catch (Exception e) {
      LOG.warn(e.getMessage());
      return null;
    }
  }

  /**
   * Because the getModelByMongoQuery allows any query - we want to be able to assess each query and
   * then decide what we are doing based on the cost - for example disallow unindexed queries or
   * send them to secondaries, flag and log them etc. To do this we need to know the cost of a query
   * by calling explain - but also possibly caching that so we don't do it for every call
   */
  private Integer costManager(Class<T> type, Document filter, Document projection, Document sort) {
    int queriesBeforeRecheck = 1000;
    Map<String, QueryScore> queryScores = new HashMap<>();
    QueryScore qs;

    String collectionName = mongoTemplate.getCollectionName(type);
    MongoCollection<Document> collection = mongoTemplate.getCollection(collectionName);

    String queryHash = computeQueryShapeHash(filter, projection, sort);
    // LOG.info(queryHash);
    qs = queryScores.get(queryHash);
    if (qs != null) {
      if (qs.count < queriesBeforeRecheck) {
        qs.count++;
        return qs.score;
      }
    }

    // TODO: If we are doing some type mapping or renaming how do we apply that automatically
    // Most obviously @ID - Spring can do this for us with getQueryObject() if we are passing in
    // something
    // Spring like

    // Retrieve the explain plan for the query
    LOG.info("Query Shape not cached  - estimating efficiency.");
    Document explain = collection.find(filter).sort(sort).projection(projection).explain();
    JsonWriterSettings jsonWriterSettings = JsonWriterSettings.builder().indent(true).build();
    // LOG.info(explain.toJson(jsonWriterSettings));

    int score = 0;

    String queryPlan =
        explain
            .get("queryPlanner", new Document())
            .get("winningPlan", new Document())
            .get("stage", "Unknown");

    if (queryPlan.equals("COLLSCAN")) {
      LOG.warn("COLLECTION SCAN QUERY - THIS IS NOT A GOOD IDEA");
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
      // Imperfect index so more index scanning bu no extra fetch
      else if (nReturned == totalDocsExamined && totalKeysExamined > nReturned) {
        score = 5;
      }
      // Index wasnt enough had to filter again after fetch
      else if (totalKeysExamined == totalDocsExamined && totalDocsExamined > nReturned) {
        score = 10;
      }

      // Cost of a sort depends how much we are sorting but quite a lot!
      if (queryPlan.equals("SORT")) {
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
      byte[] shortHashBytes = Arrays.copyOf(hashBytes, Math.min(24, hashBytes.length));

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

  private static final class QueryScore {
    int count;
    int score;
  }
}
