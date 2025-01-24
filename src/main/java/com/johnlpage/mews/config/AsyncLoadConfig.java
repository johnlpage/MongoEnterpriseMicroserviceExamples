package com.johnlpage.mews.config;

import java.util.concurrent.ThreadPoolExecutor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@EnableAsync
public class AsyncLoadConfig {

  @Bean(name = "loadExecutor")
  public ThreadPoolTaskExecutor loadExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(16);
    executor.setMaxPoolSize(16);
    executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
    executor.setQueueCapacity(0);
    executor.setThreadNamePrefix("AsyncLoadThread-");
    executor.initialize();
    return executor;
  }
}
