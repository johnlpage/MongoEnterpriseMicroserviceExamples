package com.johnlpage.mews.service;

import com.johnlpage.mews.model.VehicleInspection;
import com.johnlpage.mews.model.VehicleInspectionHistory;
import com.johnlpage.mews.repository.OptimizedMongoLoadRepositoryImpl;
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

@Service
public class VehicleInspectionPostWriteTriggerServiceImpl
    extends PostWriteTriggerService<VehicleInspection> {
  private static final Logger LOG =
      LoggerFactory.getLogger(VehicleInspectionPostWriteTriggerServiceImpl.class);
  private final MongoTemplate mongoTemplate;

  public VehicleInspectionPostWriteTriggerServiceImpl(MongoTemplate mongoTemplate) {
    super();
    this.mongoTemplate = mongoTemplate;
  }

  @Override
  public void postWriteTrigger(
      ClientSession session,
      BulkWriteResult result,
      List<VehicleInspection> inspections,
      ObjectId updateId) {

    Query query = new Query();
    List<Long> testIdList = new ArrayList<>();
    for (VehicleInspection v : inspections) {
      testIdList.add(v.getTestid()); // This is easier to read than stream().map()
    }

    query.addCriteria(Criteria.where("_id").in(testIdList)); // testid is in the list
    query.addCriteria(Criteria.where(OptimizedMongoLoadRepositoryImpl.UPDATEID).is(updateId));
    query.fields().include(OptimizedMongoLoadRepositoryImpl.PREVIOUSVALS);
    List<Document> modifiedOnly =
        mongoTemplate.withSession(session).find(query, Document.class, "inspections");
    int c = 0;

    // We want to take those and write them to another collection
    List<VehicleInspectionHistory> inspectionHistories = new ArrayList<VehicleInspectionHistory>();
    for (Document v : modifiedOnly) {
      VehicleInspectionHistory vih = new VehicleInspectionHistory();
      vih.setTestid(v.getLong("_id"));
      vih.setChanges((Map<String, Object>) v.get(OptimizedMongoLoadRepositoryImpl.PREVIOUSVALS));
      vih.setTimestamp(new Date());
      inspectionHistories.add(vih);
    }
    mongoTemplate.withSession(session).insertAll(inspectionHistories);
  }
}
