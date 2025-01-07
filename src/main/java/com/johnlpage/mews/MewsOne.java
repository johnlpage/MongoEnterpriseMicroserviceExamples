package com.johnlpage.mews;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class MewsOne {

    public static void main(String[] args) {
        SpringApplication.run(MewsOne.class, args);
    }

}
