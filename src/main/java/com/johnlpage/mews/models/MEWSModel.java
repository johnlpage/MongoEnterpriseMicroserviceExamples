package com.johnlpage.mews.models;

import java.util.Map;

import org.bson.Document;

/* Interface to reflect that:
(a) We want to be able to tell form a document if ti shoudl eb deleted
(b) We want to be able to get the @id and it's value withouth reflection being needed
*/

public interface MEWSModel {
    public boolean isDeleted();
    public Object getDocumentId();
    public Document getPayload() ;
    public void setPayload( Document payload);
    public void modifyDataForTest(Map<String, Object> document); //Somewhat option functipn to take a HashMap and modify it for testing
}
 
