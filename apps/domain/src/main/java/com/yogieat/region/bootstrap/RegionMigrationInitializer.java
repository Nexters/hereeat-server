package com.yogieat.region.bootstrap;

import com.yogieat.region.service.RegionService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RegionMigrationInitializer implements ApplicationRunner {

    private final RegionService regionService;

    @Override
    public void run(ApplicationArguments args) {
        regionService.syncRegionsFromEnumAndBackfillLegacyReferences();
    }
}
