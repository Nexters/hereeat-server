package com.yogieat.config.warmup;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yogieat.category.service.CategoryService;
import com.yogieat.region.service.RegionService;
import com.yogieat.restaurant.service.RestaurantService;
import java.sql.Connection;
import java.sql.Statement;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ApiWarmupRunnerTest {

    @Mock
    private DataSource dataSource;

    @Mock
    private Connection connection;

    @Mock
    private Statement statement;

    @Mock
    private CategoryService categoryService;

    @Mock
    private RegionService regionService;

    @Mock
    private RestaurantService restaurantService;

    private ApiWarmupRunner runner;

    @BeforeEach
    void setUp() {
        runner = new ApiWarmupRunner(
                dataSource,
                categoryService,
                regionService,
                restaurantService
        );
        ReflectionTestUtils.setField(runner, "enabled", true);
    }

    @Test
    void warmUp_ShouldRunApiWarmupSteps() throws Exception {
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.createStatement()).thenReturn(statement);

        runner.warmUp();

        verify(statement).execute("select 1");
        verify(categoryService).findAll();
        verify(regionService).findAllRegions();
        verify(restaurantService).countActiveRestaurants();
    }

    @Test
    void warmUp_ShouldContinue_WhenStepFails() throws Exception {
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.createStatement()).thenReturn(statement);
        doThrow(new IllegalStateException("category failure")).when(categoryService).findAll();

        runner.warmUp();

        verify(statement).execute("select 1");
        verify(categoryService).findAll();
        verify(regionService).findAllRegions();
        verify(restaurantService).countActiveRestaurants();
    }
}
