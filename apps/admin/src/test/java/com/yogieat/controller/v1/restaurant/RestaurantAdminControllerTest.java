package com.yogieat.controller.v1.restaurant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yogieat.admin.service.AdminService;
import com.yogieat.common.error.CustomException;
import com.yogieat.common.error.ErrorCode;
import com.yogieat.config.jwt.JwtTokenProvider;
import com.yogieat.controller.advice.ErrorHttpStatusMapper;
import com.yogieat.controller.advice.GlobalApiResponseAdvice;
import com.yogieat.controller.advice.GlobalExceptionHandler;
import com.yogieat.controller.v1.restaurant.fixture.RestaurantAdminFixture;
import com.yogieat.controller.v1.restaurant.request.RestaurantRequest;
import com.yogieat.restaurant.facade.RestaurantAdminFacade;
import com.yogieat.restaurant.result.RestaurantAdminResult;
import com.yogieat.restaurant.service.RestaurantCommand;
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
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(RestaurantAdminController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({
        GlobalExceptionHandler.class,
        GlobalApiResponseAdvice.class,
        ErrorHttpStatusMapper.class,
        RestaurantAdminControllerTest.MockTestBeanConfig.class
})
class RestaurantAdminControllerTest {

    private static final String BASE_URL = "/api/v1/admin/restaurants";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private RestaurantAdminFacade restaurantAdminFacade;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private AdminService adminService;

    @TestConfiguration
    static class MockTestBeanConfig {
        @Bean
        @Primary
        RestaurantAdminFacade restaurantAdminFacade() {
            return Mockito.mock(RestaurantAdminFacade.class);
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
    @DisplayName("단건 맛집 조회 응답이 200으로 정상 반환된다")
    void getRestaurantById_ShouldReturnOk_WhenRestaurantExists() throws Exception {
        RestaurantAdminResult.Detail result = RestaurantAdminFixture.sampleRestaurantAdminDetail();
        when(restaurantAdminFacade.getRestaurantBy(1L)).thenReturn(result);

        mockMvc.perform(get(BASE_URL + "/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(1L))
                .andExpect(jsonPath("$.data.name").value("restaurant"));
    }

    @Test
    @DisplayName("단건 맛집 조회 실패 시 404 응답한다")
    void getRestaurantById_ShouldReturn404_WhenRestaurantNotFound() throws Exception {
        when(restaurantAdminFacade.getRestaurantBy(999L))
                .thenThrow(new CustomException(ErrorCode.RESTAURANT_NOT_FOUND));

        mockMvc.perform(get(BASE_URL + "/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.data.errorCode").value(ErrorCode.RESTAURANT_NOT_FOUND.getCode()));
    }

    @Test
    @DisplayName("단건 맛집 수정 요청이 유효하면 200으로 응답한다")
    void updateRestaurant_ShouldReturn200_WhenRequestValid() throws Exception {
        RestaurantAdminResult.Detail result = RestaurantAdminFixture.sampleUpdatedRestaurantAdminDetail();

        when(restaurantAdminFacade.updateRestaurant(eq(1L), any(RestaurantCommand.Patch.class)))
                .thenReturn(result);

        RestaurantRequest.Patch request = RestaurantAdminFixture.patchForUpdateName();

        mockMvc.perform(
                        patch(BASE_URL + "/1")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("updated"));
    }

    @Test
    @DisplayName("위치 좌표가 잘못되면 400을 반환한다")
    void updateRestaurant_ShouldReturn400_WhenLocationInvalid() throws Exception {
        RestaurantRequest.Patch request = RestaurantAdminFixture.patchForInvalidLocation();

        mockMvc.perform(
                        patch(BASE_URL + "/1")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request))
                )
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("지역 값이 유효하지 않으면 400을 반환한다")
    void updateRestaurant_ShouldReturn400_WhenRegionInvalid() throws Exception {
        RestaurantRequest.Patch request = RestaurantAdminFixture.patchForInvalidRegion();

        mockMvc.perform(
                        patch(BASE_URL + "/1")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request))
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.errorCode").value(ErrorCode.INVALID_LOCATION_NAME.getCode()));
    }

    @Test
    @DisplayName("타임슬롯 값이 유효하지 않으면 400을 반환한다")
    void updateRestaurant_ShouldReturn400_WhenTimeSlotInvalid() throws Exception {
        RestaurantRequest.Patch request = RestaurantAdminFixture.patchForInvalidTimeSlot();

        mockMvc.perform(
                        patch(BASE_URL + "/1")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request))
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.errorCode").value(ErrorCode.METHOD_ARGUMENT_TYPE_MISMATCH.getCode()));
    }
}
