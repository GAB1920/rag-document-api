package com.ragapi;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class RagDocumentApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(RagDocumentApiApplication.class, args);
    }
}
