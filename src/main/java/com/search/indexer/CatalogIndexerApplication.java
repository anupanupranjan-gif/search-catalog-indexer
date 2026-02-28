package com.search.indexer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class CatalogIndexerApplication {

    private static final Logger log = LoggerFactory.getLogger(CatalogIndexerApplication.class);

    public static void main(String[] args) {
        log.info("Starting Catalog Indexer...");
        var context = SpringApplication.run(CatalogIndexerApplication.class, args);
        int exitCode = SpringApplication.exit(context);
        System.exit(exitCode);
    }
}

