package com.johnlpage.memex.generics.repository;

import java.time.Instant;
import java.util.stream.Stream;

import org.springframework.data.mongodb.core.query.Criteria;

public interface MongoHistoryRepository<T, I> {

    Stream<T> GetRecordByIdAsOfDate(I recordId, Instant asOf, Class<T> clazz);

    Stream<T> GetRecordsAsOfDate(Criteria criteria, Instant asOf, Class<T> clazz);
}
