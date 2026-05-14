package com.yogieat.config.warmup;

import com.yogieat.admin.domain.Admin;
import com.yogieat.admin.domain.value.AdminRole;
import com.yogieat.category.service.CategoryService;
import com.yogieat.config.jwt.JwtTokenProvider;
import com.yogieat.gathering.facade.GatheringAdminFacade;
import com.yogieat.region.facade.RegionAdminFacade;
import com.yogieat.restaurant.facade.RestaurantAdminFacade;
import java.sql.Connection;
import java.sql.Statement;
import javax.sql.DataSource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.util.StopWatch;

@Slf4j
@Component
@RequiredArgsConstructor
public class AdminWarmupRunner implements ApplicationRunner {

    private static final String DUMMY_BCRYPT_HASH = "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy";

    private final DataSource dataSource;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final CategoryService categoryService;
    private final RegionAdminFacade regionAdminFacade;
    private final GatheringAdminFacade gatheringAdminFacade;
    private final RestaurantAdminFacade restaurantAdminFacade;

    @Value("${admin.warmup.enabled:true}")
    private boolean enabled;

    @Override
    public void run(ApplicationArguments args) {
        warmUp();
    }

    void warmUp() {
        if (!enabled) {
            log.info("Admin warmup is disabled");
            return;
        }

        runStep("database connection", this::warmUpDatabaseConnection);
        runStep("password encoder", () -> passwordEncoder.matches("warmup", DUMMY_BCRYPT_HASH));
        runStep("jwt signing", () -> jwtTokenProvider.createAccessToken(
                Admin.of(1L, "warmup", "warmup", "warmup", AdminRole.ADMIN)
        ));
        runStep("categories", categoryService::findAll);
        runStep("regions", () -> regionAdminFacade.getRegions(null));
        runStep("gathering list", () -> gatheringAdminFacade.getPageGatherings(0, 1, null, null, null, false));
        runStep("gathering dashboard", gatheringAdminFacade::getGatheringDashboard);
        runStep("restaurant list", () -> restaurantAdminFacade.getPageRestaurants(0, 8, null, null, null, null));
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
            log.info("Admin warmup step completed: {} ({} ms)", stepName, stopWatch.getTotalTimeMillis());
        } catch (Exception e) {
            if (stopWatch.isRunning()) {
                stopWatch.stop();
            }
            log.warn("Admin warmup step failed: {}", stepName, e);
        }
    }

    @FunctionalInterface
    private interface WarmupStep {
        void run() throws Exception;
    }
}
