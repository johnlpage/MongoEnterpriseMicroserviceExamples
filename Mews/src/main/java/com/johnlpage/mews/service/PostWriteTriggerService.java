package com.johnlpage.mews.service;

import com.mongodb.bulk.BulkWriteResult;
import com.mongodb.client.ClientSession;
import java.util.List;
import org.bson.types.ObjectId;

public interface PostWriteTriggerService<T> {
  void postWriteTrigger(
      ClientSession session, BulkWriteResult result, List<T> records, ObjectId updateId);
}
