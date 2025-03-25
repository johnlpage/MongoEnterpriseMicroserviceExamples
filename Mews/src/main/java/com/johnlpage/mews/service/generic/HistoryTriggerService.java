package com.johnlpage.mews.service.generic;

import static com.johnlpage.mews.util.AnnotationExtractor.getIdFromModel;
import static com.johnlpage.mews.util.AnnotationExtractor.hasDeleteFlag;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.johnlpage.mews.model.DocumentHistory;
import com.johnlpage.mews.repository.optimized.OptimizedMongoLoadRepositoryImpl;
import com.johnlpage.mews.util.AnnotationExtractor;
import com.mongodb.bulk.BulkWriteResult;
import com.mongodb.bulk.BulkWriteUpsert;
import com.mongodb.client.ClientSession;
import java.util.*;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

/* This trigger writes out a change history for documents modified in OptimizedMonogoLoadRepository*/

@Service
public abstract class HistoryTriggerService<T> extends PostWriteTriggerService<T> {
  public static final String HISTORY_POSTFIX = "_history";
  private static final Logger LOG = LoggerFactory.getLogger(HistoryTriggerService.class);
  private final MongoTemplate mongoTemplate;
  private final ObjectMapper objectMapper;

  public HistoryTriggerService(MongoTemplate mongoTemplate, ObjectMapper objectMapper) {
    super();
    this.mongoTemplate = mongoTemplate;
    this.objectMapper = objectMapper;
  }

  @Override
  public void postWriteTrigger(
      ClientSession session,
      BulkWriteResult result,
      List<T> documents,
      Class<T> clazz,
      ObjectId updateId)
      throws IllegalAccessException {

    List<DocumentHistory> history = new ArrayList<>();

    String collectionName = AnnotationExtractor.getCollectionName(clazz);

    // Add inserts to history
    if (!result.getUpserts().isEmpty()) {
      for (BulkWriteUpsert v : result.getUpserts()) {

        DocumentHistory vih = new DocumentHistory();
        vih.setRecordId(v.getId());
        vih.setType("insert");
        vih.setTimestamp(new Date());
        history.add(vih); // Add this history records to the history list
      }
    }

    // Add updates
    if (result.getModifiedCount() > 0) {
      Query query = new Query();
      List<Object> testIdList = new ArrayList<>();
      for (T v : documents) {
        try {
          testIdList.add(
              AnnotationExtractor.getIdFromModel(v)); // This is easier to read than stream().map()
        } catch (IllegalAccessException e) {
          LOG.error("Model has no defined @ID !!{}", e.getMessage());
        }
      }

      query.addCriteria(Criteria.where("_id").in(testIdList)); // testid is in the list
      query.addCriteria(
          Criteria.where(
                  OptimizedMongoLoadRepositoryImpl.PREVIOUS_VALS
                      + "."
                      + OptimizedMongoLoadRepositoryImpl.UPDATE_ID)
              .is(updateId));
      query.fields().include(OptimizedMongoLoadRepositoryImpl.PREVIOUS_VALS);
      List<Document> modifiedOnly =
          mongoTemplate.withSession(session).find(query, Document.class, collectionName);

      // We want to take those and write them to another collection

      for (Document v : modifiedOnly) {
        DocumentHistory vih = new DocumentHistory();
        vih.setRecordId(v.get("_id"));
        vih.setType("update");
        Document previousValues =
            v.get(OptimizedMongoLoadRepositoryImpl.PREVIOUS_VALS, Document.class);
        cleanMap(previousValues);
        // Also remove __updatedId and __lastUpdateDate from this
        previousValues.remove(OptimizedMongoLoadRepositoryImpl.UPDATE_ID);
        previousValues.remove(OptimizedMongoLoadRepositoryImpl.LAST_UPDATE_DATE);

        vih.setChanges(previousValues);
        vih.setTimestamp(new Date());
        history.add(vih); // Add this history records to the history list
      }
    }

    // We also need to capture any that have been deleted
    // we are assuming here that the upstream only send us a delete once ( as they then deleted it )
    // If not we woudl need ot make this an upsert or ass a unique constraint and ignore the dup key
    // error

    if (result.getDeletedCount() > 0) {
      for (T v : documents) {
        if (hasDeleteFlag(v)) {
          DocumentHistory vih = new DocumentHistory();
          vih.setRecordId(getIdFromModel(v));
          Map<String, Object> finalState;
          finalState = objectMapper.convertValue(v, new TypeReference<>() {});
          vih.setChanges(finalState);
          vih.setType("delete");
          vih.setTimestamp(new Date());
          history.add(vih); // Add this history records to the history list
        }
      }
    }

    // Write them all in one operation, we can use insert which is fast
    mongoTemplate.withSession(session).insert(history, collectionName + HISTORY_POSTFIX);
  }

  // Remove all the empty children so our history isn't full of empty objects

  boolean cleanMap(Map<String, Object> map) {
    Iterator<Map.Entry<String, Object>> iterator = map.entrySet().iterator();
    boolean hasNonEmptyChildren = false;
    while (iterator.hasNext()) {
      Map.Entry<String, Object> entry = iterator.next();
      Object value = entry.getValue();
      if (value instanceof Map) { // Check if the value is a map
        Map<String, Object> nestedMap = (Map<String, Object>) value;
        // Recursively clean the nested map
        if (!cleanMap(nestedMap)) {
          iterator.remove(); // Remove if the nested map is empty after cleaning
        } else {
          hasNonEmptyChildren = true;
        }
      } else {
        hasNonEmptyChildren = true; // Non-map entries are considered non-empty
      }
    }
    return hasNonEmptyChildren; // Return true if any non-empty entries remain
  }
}
