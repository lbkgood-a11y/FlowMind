package com.triobase.service.catalog;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

@SpringBootApplication
@EnableCaching
public class BusinessCatalogApplication {

    public static void main(String[] args) {
        SpringApplication.run(BusinessCatalogApplication.class, args);
    }
}
