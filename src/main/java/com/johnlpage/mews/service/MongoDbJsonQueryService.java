package com.johnlpage.mews.service;

import com.johnlpage.mews.models.MewsModel;
import com.johnlpage.mews.repository.OptimizedMongoLoadRepository;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Example;
import org.springframework.data.domain.ExampleMatcher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Service;

@Service
public class MongoDbJsonQueryService<
    R extends OptimizedMongoLoadRepository<M> & MongoRepository<M, I>,
    M extends MewsModel,
    I extends Object> {
  @Autowired private R repository;

  // Find One by ID
  public Optional<M> getModelById(I id) {
    return repository.findById(id);
  }

  // Find By Example with Paging

  public Page<M> getModelByExample(M probe, int page, int size) {

    ExampleMatcher matcher = ExampleMatcher.matching().withIgnoreNullValues();
    Example<M> example = Example.of(probe, matcher);
    Page<M> allItems = repository.findAll(example, PageRequest.of(page, size));
    return allItems;
  }
}
