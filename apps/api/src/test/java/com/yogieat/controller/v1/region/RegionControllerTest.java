package com.yogieat.controller.v1.region;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.yogieat.common.GeoJson;
import com.yogieat.controller.advice.ErrorHttpStatusMapper;
import com.yogieat.controller.advice.GlobalApiResponseAdvice;
import com.yogieat.controller.advice.GlobalExceptionHandler;
import com.yogieat.region.domain.RegionMaster;
import com.yogieat.region.service.RegionService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class RegionControllerTest {

    private MockMvc mockMvc;
    private RegionService regionService;

    @BeforeEach
    void setUp() {
        regionService = Mockito.mock(RegionService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new RegionController(regionService))
                .setControllerAdvice(
                        new GlobalExceptionHandler(new ErrorHttpStatusMapper()),
                        new GlobalApiResponseAdvice()
                )
                .build();
    }

    @Test
    @DisplayName("지역 목록 조회 응답이 200으로 반환된다")
    void getRegions_ShouldReturnOk() throws Exception {
        when(regionService.findActiveRegions()).thenReturn(List.of(
                new RegionMaster(
                        1L,
                        "GANGNAM",
                        "서울",
                        "강남역",
                        new GeoJson.Point(List.of(127.0276, 37.4979)),
                        true,
                        0,
                        null,
                        null
                ),
                new RegionMaster(
                        2L,
                        "HONGDAE",
                        "서울",
                        "홍대입구역",
                        new GeoJson.Point(List.of(126.92378, 37.55684)),
                        true,
                        1,
                        null,
                        null
                )
        ));

        mockMvc.perform(get("/api/v1/regions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.regions[0].code").value("GANGNAM"))
                .andExpect(jsonPath("$.data.regions[0].province").value("서울"))
                .andExpect(jsonPath("$.data.regions[0].displayName").value("강남역"))
                .andExpect(jsonPath("$.data.regions[0].coordinatesStandard.coordinates[0]").value(127.0276))
                .andExpect(jsonPath("$.data.regions[1].code").value("HONGDAE"));
    }
}
