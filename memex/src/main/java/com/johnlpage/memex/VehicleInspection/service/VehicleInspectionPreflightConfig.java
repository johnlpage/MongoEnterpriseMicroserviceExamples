package com.johnlpage.memex.VehicleInspection.service;

import com.johnlpage.memex.VehicleInspection.model.VehicleInspection;
import com.johnlpage.memex.generics.service.CollectionPreflightConfig;
import com.mongodb.client.model.IndexModel;
import com.mongodb.client.model.Indexes;
import org.bson.Document;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class VehicleInspectionPreflightConfig implements CollectionPreflightConfig {

    @Override
    public String getCollectionName() {
        return "vehicleinspection";
    }

    @Override
    public Class<?> getSchemaClass() {
        return VehicleInspection.class;
    }


    @Override
    public List<IndexModel> getIndexes() {
        return List.of(
                // Simple ascending index
                new IndexModel(Indexes.ascending("vehicle.model"))
        );
    }


    @Override
    public List<Document> getSearchIndexes() {

        String SEARCH_INDEXES = """  
                { "searchIndexes": [  
                    {  
                        "name": "default",  
                        "definition": {  
                            "mappings": {  
                                "dynamic": true,  
                                "fields": {}  
                            }  
                        }  
                    }  
                ]}  
                """;

        return Document.parse(SEARCH_INDEXES).getList("searchIndexes", Document.class);
    }
}
