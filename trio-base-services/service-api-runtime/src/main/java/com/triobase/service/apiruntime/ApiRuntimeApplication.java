package com.triobase.service.apiruntime;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class ApiRuntimeApplication {

    public static void main(String[] args) {
        SpringApplication.run(ApiRuntimeApplication.class, args);
    }
}
