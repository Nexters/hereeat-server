package com.yogieat.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.yogieat.restaurant.service.RestaurantCollectionProcessor;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

class RestaurantCollectionSchedulerTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfig.class)
            .withBean(RestaurantCollectionProcessor.class, () -> mock(RestaurantCollectionProcessor.class));

    @Test
    void restaurantCollectionScheduler_isNotRegistered_whenDevProfileActive() {
        contextRunner
                .withInitializer(context -> context.getEnvironment().setActiveProfiles("dev"))
                .run(context -> assertThat(context).doesNotHaveBean(RestaurantCollectionScheduler.class));
    }

    @Test
    void restaurantCollectionScheduler_isRegistered_whenProdProfileActive() {
        contextRunner
                .withInitializer(context -> context.getEnvironment().setActiveProfiles("prod"))
                .run(context -> assertThat(context).hasSingleBean(RestaurantCollectionScheduler.class));
    }

    @Configuration
    @Import(RestaurantCollectionScheduler.class)
    static class TestConfig {
    }
}
