package com.yogieat.controller.v1.restaurant;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.yogieat.category.domain.value.LargeCategory;
import com.yogieat.common.Region;
import com.yogieat.controller.advice.ErrorHttpStatusMapper;
import com.yogieat.controller.advice.GlobalApiResponseAdvice;
import com.yogieat.controller.advice.GlobalExceptionHandler;
import com.yogieat.restaurant.facade.RestaurantFacade;
import com.yogieat.restaurant.result.RestaurantDetailResult;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class RestaurantControllerTest {

    private MockMvc mockMvc;
    private RestaurantFacade restaurantFacade;

    @BeforeEach
    void setUp() {
        restaurantFacade = Mockito.mock(RestaurantFacade.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new RestaurantController(restaurantFacade))
                .setControllerAdvice(
                        new GlobalExceptionHandler(new ErrorHttpStatusMapper()),
                        new GlobalApiResponseAdvice()
                )
                .build();
    }

    @Test
    @DisplayName("맛집 상세 조회 응답이 200으로 반환된다")
    void getRestaurantDetail_ShouldReturnOk() throws Exception {
        when(restaurantFacade.getRestaurantDetailBy(1L)).thenReturn(RestaurantDetailResult.of(
                1L,
                "맛집 이름",
                "고속터미널역",
                "서울 서초구 ...",
                Region.GANGNAM,
                LargeCategory.KOREAN,
                4.6,
                "https://img.example.com/restaurant.jpg",
                "https://place.map.kakao.com/123",
                "맛집 설명",
                "MEDIUM",
                "된장찌개",
                10000,
                "대표 리뷰",
                120,
                "AI 요약 제목",
                List.of("요약1", "요약2")
        ));

        mockMvc.perform(get("/api/v1/restaurants/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.restaurantId").value(1L))
                .andExpect(jsonPath("$.data.restaurantName").value("맛집 이름"))
                .andExpect(jsonPath("$.data.station").value("고속터미널역"))
                .andExpect(jsonPath("$.data.largeCategory").value("KOREAN"))
                .andExpect(jsonPath("$.data.representMenuPrice").value(10000));
    }
}
