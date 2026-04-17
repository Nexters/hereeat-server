package com.yogieat.testsupport;

import com.yogieat.region.service.RegionService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("test")
public class DatabaseResetHookConfig {

    @Bean
    DatabaseResetHook regionDatabaseResetHook(RegionService regionService) {
        return regionService::syncRegionsFromEnumAndBackfillLegacyReferences;
    }
}
