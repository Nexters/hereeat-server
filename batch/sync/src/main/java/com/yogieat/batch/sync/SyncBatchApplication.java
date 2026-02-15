package com.yogieat.batch.sync;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication(
        scanBasePackages = {
            "com.yogieat.batch.sync",
            "com.yogieat.restaurant.sync",
            "com.yogieat.datasource.db.core",
            "com.yogieat.kakao.kakao"
        }
)
@ConfigurationPropertiesScan
public class SyncBatchApplication {

    public static void main(String[] args) {
        SpringApplication.run(SyncBatchApplication.class, args);
    }
}
