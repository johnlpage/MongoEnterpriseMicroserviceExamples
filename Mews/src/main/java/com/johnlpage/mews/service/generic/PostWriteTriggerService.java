package com.johnlpage.mews.service.generic;

import com.mongodb.bulk.BulkWriteResult;
import com.mongodb.client.ClientSession;
import java.util.List;
import org.bson.types.ObjectId;

public class PostWriteTriggerService<T> {
  public void postWriteTrigger(
      ClientSession session,
      BulkWriteResult result,
      List<T> records,
      Class<T> clazz,
      ObjectId updateId)
      throws IllegalAccessException {}
}
