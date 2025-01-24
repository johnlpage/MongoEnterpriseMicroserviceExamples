package com.johnlpage.mews.service;

import com.mongodb.bulk.BulkWriteResult;
import com.mongodb.client.ClientSession;
import org.bson.types.ObjectId;

import java.util.List;

public abstract class PostWriteTriggerService<T> {

     public void postWriteTrigger(ClientSession session, BulkWriteResult result, List<T> inspections, ObjectId updateId) {}
}
