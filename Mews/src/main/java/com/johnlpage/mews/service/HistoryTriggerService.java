package com.johnlpage.mews.service;

import static com.johnlpage.mews.util.AnnotationExtractor.getIdFromModel;
import static com.johnlpage.mews.util.AnnotationExtractor.hasDeleteFlag;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.johnlpage.mews.model.DocumentHistory;
import com.johnlpage.mews.repository.optimized.OptimizedMongoLoadRepositoryImpl;
import com.johnlpage.mews.util.AnnotationExtractor;
import com.mongodb.bulk.BulkWriteResult;
import com.mongodb.client.ClientSession;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

/*
 TODO: Refactor this into something more generic

*/
@Service
public class HistoryTriggerService<T> extends PostWriteTriggerService<T> {
  private static final Logger LOG = LoggerFactory.getLogger(HistoryTriggerService.class);
  private final String HISTORY_POSTFIX = "_history";
  private final MongoTemplate mongoTemplate;
  private final ObjectMapper objectMapper;
  private String collectionName = null;

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
    if (collectionName == null) {
      collectionName = AnnotationExtractor.getCollectionName(clazz);
    }

    query.addCriteria(Criteria.where("_id").in(testIdList)); // testid is in the list
    query.addCriteria(Criteria.where(OptimizedMongoLoadRepositoryImpl.UPDATE_ID).is(updateId));
    query.fields().include(OptimizedMongoLoadRepositoryImpl.PREVIOUS_VALS);
    List<Document> modifiedOnly =
        mongoTemplate.withSession(session).find(query, Document.class, collectionName);

    // We want to take those and write them to another collection
    List<DocumentHistory> history = new ArrayList<>();
    for (Document v : modifiedOnly) {
      DocumentHistory vih = new DocumentHistory();
      vih.setRecordId(v.get("_id"));
      vih.setChanges(v.get(OptimizedMongoLoadRepositoryImpl.PREVIOUS_VALS, Document.class));
      vih.setTimestamp(new Date());
      history.add(vih); // Add this history records to the history list
    }

    // Write them all in one operation, we can use insert which is fast
    mongoTemplate.withSession(session).insert(history, collectionName + HISTORY_POSTFIX);

    // We also need to capture any that have been deleted but we can assume the upstream keeps
    // giving us them with the deleted flag set

    history.clear();
    for (T v : documents) {
      if (hasDeleteFlag(v)) {
        DocumentHistory vih = new DocumentHistory();
        vih.setRecordId(getIdFromModel(v));
        Map<String, Object> finalState;
        finalState = objectMapper.convertValue(v, new TypeReference<Map<String, Object>>() {});
        vih.setChanges(finalState);
        vih.setTimestamp(new Date());
        history.add(vih); // Add this history records to the history list
      }
    }
    // Write them all in one operation, we can use insert which is fast
    mongoTemplate.withSession(session).insert(history, collectionName + HISTORY_POSTFIX);
  }
}
