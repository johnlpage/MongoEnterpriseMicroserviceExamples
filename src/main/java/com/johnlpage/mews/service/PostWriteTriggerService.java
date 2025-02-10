package com.johnlpage.mews.service;

import com.mongodb.bulk.BulkWriteResult;
import com.mongodb.client.ClientSession;
import java.util.List;
import org.bson.types.ObjectId;

// TODO - Make this an interface?

public abstract class PostWriteTriggerService<T> {
  public void postWriteTrigger(
      ClientSession session, BulkWriteResult result, List<T> inspections, ObjectId updateId) {}
}
