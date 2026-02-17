package com.johnlpage.memex.VehicleInspection.model;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.johnlpage.memex.util.DeleteFlag;
import com.johnlpage.memex.util.ObjectConverter;
import jakarta.validation.constraints.Min;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/* Replace @Data with this to make an Immutable model
 * which is a little more efficient but no setters just a builder
 * This also impact the controller and fuzzer and JsonLoaderService -
 * changes there are commented
 *
 *  @Builder(toBuilder = true)
 *  @Jacksonized
 *  @Value
 */

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@Document(collection = "vehicleinspection")
public class VehicleInspection {
    @Id
    Long testid;

    @Field("version_field")
    @Version
    Long version;

    Date testdate;
    String testclass;
    String testtype;
    String testresult;
    @Min(1)
    Long testmileage;
    String postcode;
    Vehicle vehicle;
    String files;


    Long capacity;

    Date firstusedate;
    /* Use this to flag from the JSON we want to remove the record */
    @Transient
    @DeleteFlag
    Boolean deleted;

    /**
     * Captures any fields not explicitly mapped to class fields.
     * Supports schema flexibility and evolution.
     * Only persisted/serialized when non-empty.
     */
    @Field(write = Field.Write.NON_NULL)
    private Map<String, Object> payload;

    @JsonAnySetter
    public void set(String key, Object value) {
        if (payload == null) {
            payload = new HashMap<String, Object>();
        }
        payload.put(key, ObjectConverter.convertObject(value));
    }

    @JsonAnyGetter
    public Map<String, Object> getPayload() {
        return payload;
    }

    /**
     * Helper method to safely add to payload from your own code
     */
    public void addToPayload(String key, Object value) {
        set(key, value);
    }
}
