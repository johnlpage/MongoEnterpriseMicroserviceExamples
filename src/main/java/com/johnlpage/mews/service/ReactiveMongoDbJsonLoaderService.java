package com.johnlpage.mews.service;

import com.johnlpage.mews.models.MewsModel;
import com.johnlpage.mews.models.UpdateStrategy;
import com.johnlpage.mews.repository.GenericReactiveOptimizedMongoLoadRepository;
import java.io.BufferedInputStream;
import java.io.EOFException;
import java.io.InputStream;
import java.util.concurrent.atomic.AtomicLong;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ResolvableType;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.codec.json.Jackson2JsonDecoder;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@RequiredArgsConstructor
@Service
public class ReactiveMongoDbJsonLoaderService<T extends MewsModel<ID>, ID> {

  private static final Logger LOG = LoggerFactory.getLogger(ReactiveMongoDbJsonLoaderService.class);
  private final GenericReactiveOptimizedMongoLoadRepository<T, ID> reactiveRepository;
  private final Jackson2JsonDecoder jackson2JsonDecoder;
  private final DataBufferFactory dataBufferFactory;

  /** Parses a JSON stream object by object, assumes it's not an Array. */
  public Mono<LoadResult> loadFromJsonStreamReactive(
      InputStream inputStream, Class<T> type, UpdateStrategy updateStrategy) {
    LoadResult loadResult = LoadResult.create();
    long startTime = System.currentTimeMillis();
    // maximum number of concurrent bulkWrites
    int maxConcurrency = 25;

    // todo: test this
    ResolvableType resolvableType = ResolvableType.forType(type);
    Flux<DataBuffer> dataBufferFlux =
        DataBufferUtils.readInputStream(
            () -> new BufferedInputStream(inputStream), dataBufferFactory, 4096);
    return jackson2JsonDecoder
        .decode(dataBufferFlux, resolvableType, null, null)
        .cast(type)
        .buffer(100)
        .doOnNext(batch -> loadResult.processed().addAndGet(batch.size()))
        .flatMap(
            batch ->
                reactiveRepository
                    .bulkWrite(batch, type, updateStrategy)
                    .subscribeOn(Schedulers.boundedElastic()),
            maxConcurrency)
        .doOnNext(
            bulkWriteResult -> {
              loadResult.updates().addAndGet(bulkWriteResult.getModifiedCount());
              loadResult.deletes().addAndGet(bulkWriteResult.getDeletedCount());
              loadResult.inserts().addAndGet(bulkWriteResult.getUpserts().size());
            })
        // log progress
        .doOnNext(ignored -> LOG.debug("Progress: {}", loadResult))
        // Error handling
        .doOnError(
            EOFException.class,
            e ->
                LOG.error(
                    "Load Terminated as sender stopped sending JSON: {}: {}",
                    e.getMessage(),
                    loadResult,
                    e))
        .doOnError(
            e -> !(e instanceof EOFException),
            e -> LOG.error("{}: {}", e.getMessage(), loadResult, e))
        .doOnComplete(
            () -> {
              loadResult.durationMillis().set(System.currentTimeMillis() - startTime);
              LOG.info("Processing completed: {}", loadResult);
            })
        .then(Mono.just(loadResult));
  }

  public record LoadResult(
      AtomicLong processed,
      AtomicLong updates,
      AtomicLong deletes,
      AtomicLong inserts,
      AtomicLong durationMillis) {
    public static LoadResult create() {
      return new LoadResult(
          new AtomicLong(), new AtomicLong(), new AtomicLong(), new AtomicLong(), new AtomicLong());
    }
  }
}
