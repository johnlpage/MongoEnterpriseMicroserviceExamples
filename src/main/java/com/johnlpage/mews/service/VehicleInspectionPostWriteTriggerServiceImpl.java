package com.johnlpage.mews.service;

import com.johnlpage.mews.model.VehicleInspection;
import com.mongodb.bulk.BulkWriteResult;
import com.mongodb.client.ClientSession;
import org.bson.types.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class VehicleInspectionPostWriteTriggerServiceImpl extends PostWriteTriggerService<VehicleInspection> {
    private static final Logger LOG = LoggerFactory.getLogger(VehicleInspectionPostWriteTriggerServiceImpl.class);
    private final MongoTemplate mongoTemplate;

    public VehicleInspectionPostWriteTriggerServiceImpl(MongoTemplate mongoTemplate) {
        super();
        this.mongoTemplate = mongoTemplate;
    }


    @Override
    public void postWriteTrigger(ClientSession session, BulkWriteResult result, List<VehicleInspection> inspections, ObjectId updateId) {
        for (VehicleInspection inspection : inspections) {
            //LOG.info(result.toString());
            //LOG.info(inspection.toString());
            //LOG.info(updateId.toString());
   if(true) return;
            Query query = new Query();
            List<Long> testIdList = new ArrayList<>();
            for( VehicleInspection v: inspections) {
                testIdList.add(v.getTestid()); // This is easier to read than stream().map()
            }


            query.addCriteria(Criteria.where("_id").in(testIdList)); // testid is in the list
            query.addCriteria(Criteria.where("__previousvalues._updateBatchId").is(updateId));
            query.fields().include("__previousvalues");
            List<VehicleInspection> modifiedOnly = mongoTemplate.withSession(session).find(query, VehicleInspection.class);
            int c=0;
            for(VehicleInspection v: modifiedOnly) {
               // LOG.info(v.toString());
                c++;
            }

        }
    }

}
