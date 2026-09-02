package com.johnlpage.memex.config;

import java.util.concurrent.ThreadPoolExecutor;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
@Configuration
@EnableAsync
public class AsyncLoadConfig {

    @Bean(name = "loadExecutor")
    public ThreadPoolTaskExecutor loadExecutor(
        @Value("${mongo.jsonloader.parallelism:16}") int parallelLoadFactor) {
      ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
      executor.setCorePoolSize(parallelLoadFactor);
      executor.setMaxPoolSize(parallelLoadFactor);
      executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
      executor.setQueueCapacity(0);
      executor.setThreadNamePrefix("AsyncLoadThread-");
      executor.initialize();
      return executor;
    }
}
