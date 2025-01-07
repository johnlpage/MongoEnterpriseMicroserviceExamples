package com.johnlpage.mews;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class MEWS_One {

    public static void main(String[] args) {
        SpringApplication.run(MEWS_One.class, args);
    }

}
