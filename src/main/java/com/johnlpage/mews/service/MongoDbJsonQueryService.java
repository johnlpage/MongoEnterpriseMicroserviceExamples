package com.johnlpage.mews.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.johnlpage.mews.models.MewsModel;
import com.johnlpage.mews.repository.GenericOptimizedMongoLoadRepository;
import java.util.List;
import java.util.Optional;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Example;
import org.springframework.data.domain.ExampleMatcher;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.BasicQuery;
import org.springframework.stereotype.Service;

@Service
public class MongoDbJsonQueryService<T extends MewsModel<ID>, ID> {

  private static final Logger LOG = LoggerFactory.getLogger(MongoDbJsonQueryService.class);
  private final GenericOptimizedMongoLoadRepository<T, ID> repository;
  private final MongoTemplate mongoTemplate;
  private final ObjectMapper objectMapper;

  @Autowired
  public MongoDbJsonQueryService(
      GenericOptimizedMongoLoadRepository<T, ID> repository,
      MongoTemplate mongoTemplate,
      ObjectMapper objectMapper) {
    this.repository = repository;
    this.mongoTemplate = mongoTemplate;
    this.objectMapper = objectMapper;
  }

  /** Find One by ID */
  public Optional<T> getModelById(ID id) {
    return repository.findById(id);
  }

  /** Find By Example with Paging */
  public Slice<T> getModelByExample(T probe, int page, int size) {
    ExampleMatcher matcher = ExampleMatcher.matching().withIgnoreNullValues();
    Example<T> example = Example.of(probe, matcher);
    return repository.findAll(example, PageRequest.of(page, size));
  }

  /**
   * This is wrapping the ability to do a native MongoDB call Passing in Query, Sort,Skip,Limit and
   * Projection
   */
  public List<T> getModelByMongoQuery(String jsonString, Class<T> type) {
    try {
      Document queryRequest = Document.parse(jsonString);
      Document filter = queryRequest.get("filter", new Document());
      Document projection = queryRequest.get("projection", new Document());
      int skip = queryRequest.getInteger("skip") != null ? queryRequest.getInteger("skip") : 0;
      int limit =
          queryRequest.getInteger("limit") != null ? queryRequest.getInteger("limit") : 1000;
      Document sort = queryRequest.get("sort", new Document());

      LOG.info("{}{}", filter.toJson(), projection.toJson());
      BasicQuery query = new BasicQuery(filter, projection);
      query.skip(skip);
      query.limit(limit);

      return mongoTemplate.find(query, type);

    } catch (Exception e) {
      LOG.warn(e.getMessage());
      return null;
    }
  }
}
