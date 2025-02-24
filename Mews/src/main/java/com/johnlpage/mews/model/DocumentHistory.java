package com.johnlpage.mews.model;

import com.fasterxml.jackson.annotation.*;
import java.util.Date;
import java.util.Map;
import lombok.Data;
import org.bson.types.ObjectId;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/* Replace @Data with this to make an Immutable model
 * which is a little more efficient but no setters just a builder
 * This also impacts the controller and PreWriteTrigger and
 *  JsonLoaderService changes there are commented
 *
 *  @Builder(toBuilder = true)
 *  @Jacksonized
 *  @Value
 */

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@Document()
public class DocumentHistory {
  @Id ObjectId historyId;
  Object recordId;
  Date timestamp;
  Map<String, Object> changes;
}
