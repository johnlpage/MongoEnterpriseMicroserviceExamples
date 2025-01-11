package com.johnlpage.mews.models;

import java.util.Map;

/**
 * Interface to reflect that:
 *
 * <ul>
 *   <li>(a) We want to be able to tell form a document if it should be deleted
 *   <li>(b) We want to be able to get the @id and it's value without reflection being needed
 *   <li>(c) Our Loader class want's the idea
 * </ul>
 */
public interface MewsModel<ID> {
  boolean toDelete();

  ID getDocumentId();

  /** Somewhat optional function to take a HashMap and modify it for testing */
  void modifyDataForTest(Map<String, Object> document);
}
