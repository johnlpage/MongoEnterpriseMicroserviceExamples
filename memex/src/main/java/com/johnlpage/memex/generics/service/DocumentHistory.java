package com.johnlpage.memex.generics.service;

import com.fasterxml.jackson.annotation.*;

import java.time.Instant;
import java.util.Map;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import org.bson.types.ObjectId;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/* Replace @Getter/@Setter with this to make an Immutable model
 * which is a little more efficient but no setters just a builder
 * This also impacts the controller and PreWriteTrigger and
 *  JsonLoaderService changes there are commented
 *
 *  @Builder(toBuilder = true)
 *  @Jacksonized
 *  @Value
 */

@Getter
@Setter
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@Document()
public class DocumentHistory {
    @Id
    @EqualsAndHashCode.Include
    ObjectId historyId;
    Object recordId;
    Instant timestamp;
    String type; // TOOO enum?
    Map<String, Object> changes;
}
