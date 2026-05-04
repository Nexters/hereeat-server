package com.yogieat.controller.v1.restaurant;

import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.yogieat.admin.service.AdminService;
import com.yogieat.config.jwt.JwtTokenProvider;
import com.yogieat.controller.advice.ErrorHttpStatusMapper;
import com.yogieat.controller.advice.GlobalApiResponseAdvice;
import com.yogieat.controller.advice.GlobalExceptionHandler;
import com.yogieat.restaurant.facade.RestaurantCollectionAdminFacade;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(RestaurantCollectionAdminController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({
        GlobalExceptionHandler.class,
        GlobalApiResponseAdvice.class,
        ErrorHttpStatusMapper.class,
        RestaurantCollectionAdminControllerTest.MockTestBeanConfig.class
})
class RestaurantCollectionAdminControllerTest {

    private static final String BASE_URL = "/api/v1/admin/restaurants/collections";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RestaurantCollectionAdminFacade restaurantCollectionAdminFacade;

    @TestConfiguration
    static class MockTestBeanConfig {
        @Bean
        @Primary
        RestaurantCollectionAdminFacade restaurantCollectionAdminFacade() {
            return Mockito.mock(RestaurantCollectionAdminFacade.class);
        }

        @Bean
        @Primary
        JwtTokenProvider jwtTokenProvider() {
            return Mockito.mock(JwtTokenProvider.class);
        }

        @Bean
        @Primary
        AdminService adminService() {
            return Mockito.mock(AdminService.class);
        }
    }

    @Test
    @DisplayName("맛집 수집 수동 실행 요청 시 204를 반환한다")
    void collectRestaurants_ShouldReturnNoContent_WhenRequestValid() throws Exception {
        mockMvc.perform(post(BASE_URL))
                .andExpect(status().isNoContent());

        verify(restaurantCollectionAdminFacade).collectRestaurants();
    }
}
