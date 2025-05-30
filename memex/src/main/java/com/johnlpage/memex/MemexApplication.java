package com.johnlpage.memex;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class MemexApplication {

  public static void main(String[] args) {
    SpringApplication.run(MemexApplication.class, args);
  }
}
