package com.johnlpage.mews.repository.optimized;

import java.util.Date;
import java.util.stream.Stream;
import org.springframework.data.mongodb.core.query.Criteria;

public interface MongoHistoryRepository<T, I> {

  Stream<T> GetRecordByIdAsOfDate(I recordId, Date asOf, Class<T> clazz);

  Stream<T> GetRecordsAsOfDate(Criteria criteria, Date asOf, Class<T> clazz);
}
