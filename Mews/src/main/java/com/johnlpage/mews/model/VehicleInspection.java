package com.johnlpage.mews.model;

import com.fasterxml.jackson.annotation.*;
import com.johnlpage.mews.util.ObjectConverter;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.mapping.Document;

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

  @Id Long testid;
  @Version Long version;

  Date testdate;
  String testclass;
  String testtype;
  String testresult;
  Long testmileage;
  String postcode;
  Vehicle vehicle;
  String files;
  Long capacity;
  Date firstusedate;
  /* Use this to flag from the JSON we want to remove the record */
  @Transient @DeleteFlag Boolean deleted;

  /**
   * Use this to capture any fields not captured explicitly As MongoDB's flexibility makes this easy
   */
  private Map<String, Object> payload = new HashMap<>();

  @JsonAnySetter
  public void set(String key, Object value) {
    payload.put(key, ObjectConverter.convertObject(value));
  }

  @JsonAnyGetter
  public Map<String, Object> getPayload() {
    return payload;
  }
}
