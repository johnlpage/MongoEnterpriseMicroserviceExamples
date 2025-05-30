package com.johnlpage.memex.service.generic;

public class PreWriteTriggerService<T> {

  /**
   * Optional function to take a Model and modify it after parsing from JSON You can use spring
   * lifecycle events for this too which can trigger just before sending to MongoDB, this is
   * explicitly called in the optimized loader to allow us to set a deleted flag for testing
   */
  public void modifyMutableDataPreWrite(T document) {}

  public T newImmutableDataPreWrite(T document) {
    return document;
  }
}
