package com.yogieat.controller.v1.restaurant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yogieat.admin.service.AdminService;
import com.yogieat.category.domain.value.LargeCategory;
import com.yogieat.common.Region;
import com.yogieat.common.error.CustomException;
import com.yogieat.common.error.ErrorCode;
import com.yogieat.config.jwt.JwtTokenProvider;
import com.yogieat.controller.advice.ErrorHttpStatusMapper;
import com.yogieat.controller.advice.GlobalApiResponseAdvice;
import com.yogieat.controller.advice.GlobalExceptionHandler;
import com.yogieat.controller.v1.restaurant.fixture.RestaurantAdminFixture;
import com.yogieat.controller.v1.restaurant.request.RestaurantRequest;
import com.yogieat.restaurant.facade.RestaurantAdminFacade;
import com.yogieat.restaurant.result.RestaurantAdminListItemResult;
import com.yogieat.restaurant.result.RestaurantAdminListResult;
import com.yogieat.restaurant.result.RestaurantAdminResult;
import com.yogieat.restaurant.service.RestaurantCommand;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
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
                .andExpect(jsonPath("$.data.name").value("restaurant"))
                .andExpect(jsonPath("$.data.largeCategory").value("KOREAN"))
                .andExpect(jsonPath("$.data.mediumCategory").value("국밥"))
                .andExpect(jsonPath("$.data.teamRecommendationTitle").value("요기잇 개발자 픽"))
                .andExpect(jsonPath("$.data.teamRecommendationReason").value("여기 정말 가봤는데, 국밥이 맛있어요"))
                .andExpect(jsonPath("$.data.isDisplay").value(true));
    }

    @Test
    @DisplayName("페이지 조회 응답에 카테고리 대/중분류가 포함된다")
    void getPageRestaurants_ShouldContainCategoryFields() throws Exception {
        RestaurantAdminListItemResult item = new RestaurantAdminListItemResult(
                1L,
                "restaurant",
                10L,
                LargeCategory.KOREAN,
                "국밥",
                4.5,
                "image",
                Region.fromString("GANGNAM"),
                false,
                LocalDateTime.now()
        );
        RestaurantAdminListResult result = RestaurantAdminListResult.of(List.of(item), 0, 10, 1);

        when(restaurantAdminFacade.getPageRestaurants(0, 10, null, null, null, null)).thenReturn(result);

        mockMvc.perform(get(BASE_URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].id").value(1L))
                .andExpect(jsonPath("$.data.content[0].largeCategory").value("KOREAN"))
                .andExpect(jsonPath("$.data.content[0].mediumCategory").value("국밥"))
                .andExpect(jsonPath("$.data.content[0].isDisplay").value(false));
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
        Mockito.clearInvocations(restaurantAdminFacade);

        mockMvc.perform(
                        patch(BASE_URL + "/1")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("updated"))
                .andExpect(jsonPath("$.data.teamRecommendationTitle").value("요기잇 개발자 픽"))
                .andExpect(jsonPath("$.data.teamRecommendationReason").value("여기 정말 가봤는데, 국밥이 맛있어요"))
                .andExpect(jsonPath("$.data.isDisplay").value(false));

        ArgumentCaptor<RestaurantCommand.Patch> commandCaptor =
                ArgumentCaptor.forClass(RestaurantCommand.Patch.class);
        verify(restaurantAdminFacade).updateRestaurant(eq(1L), commandCaptor.capture());
        RestaurantCommand.Patch command = commandCaptor.getValue();
        assertThat(command.teamRecommendationTitle()).isEqualTo("요기잇 개발자 픽");
        assertThat(command.teamRecommendationReason()).isEqualTo("여기 정말 가봤는데, 국밥이 맛있어요");
    }

    @Test
    @DisplayName("팀 추천 문구를 빈 문자열로 수정하면 초기화 값으로 전달한다")
    void updateRestaurant_ShouldPassEmptyTeamRecommendationFields() throws Exception {
        RestaurantAdminResult.Detail result = RestaurantAdminFixture.sampleUpdatedRestaurantAdminDetail();
        when(restaurantAdminFacade.updateRestaurant(eq(1L), any(RestaurantCommand.Patch.class)))
                .thenReturn(result);

        Map<String, Object> request = new HashMap<>();
        request.put("teamRecommendationTitle", "  ");
        request.put("teamRecommendationReason", "");
        Mockito.clearInvocations(restaurantAdminFacade);

        mockMvc.perform(
                        patch(BASE_URL + "/1")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request))
                )
                .andExpect(status().isOk());

        ArgumentCaptor<RestaurantCommand.Patch> commandCaptor =
                ArgumentCaptor.forClass(RestaurantCommand.Patch.class);
        verify(restaurantAdminFacade).updateRestaurant(eq(1L), commandCaptor.capture());
        RestaurantCommand.Patch command = commandCaptor.getValue();
        assertThat(command.teamRecommendationTitle()).isEmpty();
        assertThat(command.teamRecommendationReason()).isEmpty();
    }

    @Test
    @DisplayName("팀 추천 제목이 50자를 초과하면 400을 반환한다")
    void updateRestaurant_ShouldReturn400_WhenTeamRecommendationTitleTooLong() throws Exception {
        Map<String, Object> request = new HashMap<>();
        request.put("teamRecommendationTitle", "가".repeat(51));

        mockMvc.perform(
                        patch(BASE_URL + "/1")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request))
                )
                .andExpect(status().isBadRequest());
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
        when(restaurantAdminFacade.updateRestaurant(eq(1L), any(RestaurantCommand.Patch.class)))
                .thenThrow(new CustomException(ErrorCode.INVALID_LOCATION_NAME));

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

    @Test
    @DisplayName("키워드로 카카오 맛집 검색 시 200과 결과를 반환한다")
    void searchRestaurants_ShouldReturnOk_WhenRequestValid() throws Exception {
        RestaurantAdminResult.Search result = new RestaurantAdminResult.Search(
                "파스타",
                List.of(
                        new RestaurantAdminResult.SearchItem(
                                "ext-1",
                                "restaurant",
                                "address",
                                "road-address",
                                "카페",
                                "126.0",
                                "37.0"
                        )
                )
        );
        when(restaurantAdminFacade.searchRestaurants("파스타")).thenReturn(result);

        mockMvc.perform(
                        get(BASE_URL + "/search")
                                .param("keyword", "파스타")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.keyword").value("파스타"))
                .andExpect(jsonPath("$.data.items").isArray())
                .andExpect(jsonPath("$.data.items[0].externalId").value("ext-1"));
    }

    @Test
    @DisplayName("검색 키워드가 공백이면 400을 반환한다")
    void searchRestaurants_ShouldReturnBadRequest_WhenKeywordBlank() throws Exception {
        mockMvc.perform(
                        get(BASE_URL + "/search")
                                .param("keyword", "  ")
                )
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("맛집 생성 요청 시 201과 생성 응답을 반환한다")
    void createRestaurant_ShouldReturn201_WhenRequestValid() throws Exception {
        RestaurantAdminResult.Create result = RestaurantAdminResult.Create.created(101L);
        when(restaurantAdminFacade.createRestaurant(any(RestaurantCommand.Create.class))).thenReturn(result);

        Map<String, Object> request = new HashMap<>();
        request.put("externalId", "ext-101");
        request.put("categoryId", 1L);
        request.put("region", "GANGNAM");

        mockMvc.perform(
                        post(BASE_URL)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request))
                )
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.restaurantId").value(101L))
                .andExpect(jsonPath("$.data.duplicated").value(false));
    }

    @Test
    @DisplayName("동일한 외부 식당 ID로 중복 생성 시 duplicated=true와 200을 반환한다")
    void createRestaurant_ShouldReturn200_WhenDuplicated() throws Exception {
        RestaurantAdminResult.Create result = RestaurantAdminResult.Create.duplicated(101L);
        when(restaurantAdminFacade.createRestaurant(any(RestaurantCommand.Create.class))).thenReturn(result);

        Map<String, Object> request = new HashMap<>();
        request.put("externalId", "ext-101");
        request.put("categoryId", 1L);
        request.put("region", "GANGNAM");

        mockMvc.perform(
                        post(BASE_URL)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.restaurantId").value(101L))
                .andExpect(jsonPath("$.data.duplicated").value(true));
    }

    @Test
    @DisplayName("맛집 생성 요청에서 region가 비면 400을 반환한다")
    void createRestaurant_ShouldReturn400_WhenRegionBlank() throws Exception {
        Map<String, Object> request = new HashMap<>();
        request.put("externalId", "ext-101");
        request.put("categoryId", 1L);
        request.put("region", "");

        mockMvc.perform(
                        post(BASE_URL)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request))
                )
                .andExpect(status().isBadRequest());
    }
}
