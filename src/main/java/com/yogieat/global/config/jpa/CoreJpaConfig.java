package com.yogieat.global.config.jpa;

import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@Configuration
@EnableTransactionManagement
@EntityScan(
        basePackages = {
                "com.yogieat"
        }
)
@EnableJpaRepositories(
        basePackages = {
                "com.yogieat"
        }
)
public class CoreJpaConfig { // JPA + Transaction + Repository
}
