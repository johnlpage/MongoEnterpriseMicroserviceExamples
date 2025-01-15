package com.johnlpage.mews.model;

import java.util.Map;

/**
 * Interface to reflect that:
 *
 * <ul>
 *   <li>(a) We add a method to say if this should be an update or a delete
 *   <li>(b) Returns the @ID field name to help us do so.
 * </ul>
 */
public interface Deleteable<ID> {
  boolean toDelete();

  ID getDocumentId();

  /** Optional function to take a HashMap and modify it for testing */
  default Map<String, Object> modifyDataForTest(Map<String, Object> document) {
    return document;
  }
}
