package com.yogieat.config.warmup;

import com.yogieat.category.service.CategoryService;
import com.yogieat.region.service.RegionService;
import com.yogieat.restaurant.service.RestaurantService;
import java.sql.Connection;
import java.sql.Statement;
import javax.sql.DataSource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.util.StopWatch;

@Slf4j
@Component
@RequiredArgsConstructor
public class ApiWarmupRunner implements ApplicationRunner {

    private final DataSource dataSource;
    private final CategoryService categoryService;
    private final RegionService regionService;
    private final RestaurantService restaurantService;

    @Value("${api.warmup.enabled:true}")
    private boolean enabled;

    @Override
    public void run(ApplicationArguments args) {
        warmUp();
    }

    void warmUp() {
        if (!enabled) {
            log.info("API warmup is disabled");
            return;
        }

        runStep("database connection", this::warmUpDatabaseConnection);
        runStep("categories", categoryService::findAll);
        runStep("regions", regionService::findAllRegions);
        runStep("active restaurant count", restaurantService::countActiveRestaurants);
    }

    private void warmUpDatabaseConnection() throws Exception {
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            statement.execute("select 1");
        }
    }

    private void runStep(String stepName, WarmupStep step) {
        StopWatch stopWatch = new StopWatch(stepName);
        try {
            stopWatch.start();
            step.run();
            stopWatch.stop();
            log.info("API warmup step completed: {} ({} ms)", stepName, stopWatch.getTotalTimeMillis());
        } catch (Exception e) {
            if (stopWatch.isRunning()) {
                stopWatch.stop();
            }
            log.warn("API warmup step failed: {}", stepName, e);
        }
    }

    @FunctionalInterface
    private interface WarmupStep {
        void run() throws Exception;
    }
}
