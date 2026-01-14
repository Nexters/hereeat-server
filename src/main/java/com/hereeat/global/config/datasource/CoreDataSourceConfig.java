package com.hereeat.global.config.datasource;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CoreDataSourceConfig { // Datasource / HikariCP

    @Bean
    @ConfigurationProperties(prefix = "datasource.db.core")
    public HikariConfig coreHikariConfig() {
        return new HikariConfig();
    }

    @Bean
    public DataSource coreDataSource(
            @Qualifier("coreHikariConfig") HikariConfig config
    ) {
        return new HikariDataSource(config);
    }
}
