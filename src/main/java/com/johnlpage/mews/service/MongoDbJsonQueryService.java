package com.johnlpage.mews.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.johnlpage.mews.models.MewsModel;
import com.johnlpage.mews.repository.OptimizedMongoLoadRepository;
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

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Service;

@Service
public class MongoDbJsonQueryService<
    R extends OptimizedMongoLoadRepository<M> & MongoRepository<M, I>,
    M extends MewsModel,
    I extends Object> {

  private static final Logger logger = LoggerFactory.getLogger(MongoDbJsonQueryService.class);

  @Autowired private R repository;
  @Autowired private MongoTemplate mongoTemplate;

  @Autowired private ObjectMapper objectMapper;

  // Find One by ID
  public Optional<M> getModelById(I id) {
    return repository.findById(id);
  }

  // Find By Example with Paging

  public Slice<M> getModelByExample(M probe, int page, int size) {

    ExampleMatcher matcher = ExampleMatcher.matching().withIgnoreNullValues();
    Example<M> example = Example.of(probe, matcher);
    Slice<M> allItems = repository.findAll(example, PageRequest.of(page, size));
    return allItems;
  }

  // This is wrapping the ability to do a native MognoDB call
  // Passing in Query, Skort,Skip,Limit and Projection
  public List<M> getModelByMongoQuery(String jsonString,  Class<M> type ) {

    try {
    
      Document queryRequest = Document.parse(jsonString);
      Document filter = queryRequest.get("filter", new Document());
      Document projection = queryRequest.get("projection",new Document());
      Integer skip = queryRequest.getInteger("skip")  != null ? queryRequest.getInteger("skip") : 0;
      Integer limit = queryRequest.getInteger("limit")  != null ? queryRequest.getInteger("limit") : 1000;
      Document sort = queryRequest.get("sort",new Document());

      logger.info(filter.toJson() + projection.toJson());
      BasicQuery query  = new BasicQuery(filter,projection);
      query.skip(skip);
      query.limit(limit);

      return mongoTemplate.find(query, type);

    } catch (Exception e) {
      logger.warn(e.getMessage());
      return null;
    }
  }
}
