package com.johnlpage.mews.models;

import java.util.Map;

/* Interface to reflect that:
(a) We want to be able to tell form a document if it should be deleted
(b) We want to be able to get the @id and it's value without reflection being needed
(c) Our Loader calss want's the idea
*/

public interface MewsModel {
  boolean isDeleted();

  Object getDocumentId();

  void modifyDataForTest(
      Map<String, Object>
          document); // Somewhat optional function to take a HashMap and modify it for testing
}
