package com.yogieat;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication(
        scanBasePackages = {
                "com.yogieat.config",
                "com.yogieat.controller",
                "com.yogieat.service",
                "com.yogieat.admin",
                "com.yogieat.datasource.db.core"
        }
)
@ConfigurationPropertiesScan
public class AdminApplication {

    public static void main(String[] args) {
        SpringApplication.run(AdminApplication.class, args);
    }
}
