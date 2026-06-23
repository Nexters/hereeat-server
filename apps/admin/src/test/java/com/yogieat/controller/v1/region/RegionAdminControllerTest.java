package com.yogieat.controller.v1.region;

import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.yogieat.admin.service.AdminService;
import com.yogieat.common.GeoJson;
import com.yogieat.config.jwt.JwtTokenProvider;
import com.yogieat.controller.advice.ErrorHttpStatusMapper;
import com.yogieat.controller.advice.GlobalApiResponseAdvice;
import com.yogieat.controller.advice.GlobalExceptionHandler;
import com.yogieat.region.domain.RegionMaster;
import com.yogieat.region.domain.RegionStatus;
import com.yogieat.region.domain.RegionSummary;
import com.yogieat.region.facade.RegionAdminFacade;
import com.yogieat.region.service.RegionCommand;
import java.util.List;
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

@WebMvcTest(RegionAdminController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({
        GlobalExceptionHandler.class,
        GlobalApiResponseAdvice.class,
        ErrorHttpStatusMapper.class,
        RegionAdminControllerTest.MockTestBeanConfig.class
})
class RegionAdminControllerTest {

    private static final String BASE_URL = "/api/v1/admin/regions";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RegionAdminFacade regionAdminFacade;

    @TestConfiguration
    static class MockTestBeanConfig {
        @Bean
        @Primary
        RegionAdminFacade regionAdminFacade() {
            return Mockito.mock(RegionAdminFacade.class);
        }

        @Bean
        @Primary
        AdminService adminService() {
            return Mockito.mock(AdminService.class);
        }

        @Bean
        @Primary
        JwtTokenProvider jwtTokenProvider() {
            return Mockito.mock(JwtTokenProvider.class);
        }
    }

    @Test
    @DisplayName("활성 지역 목록 조회 응답이 200으로 반환된다")
    void getRegions_ShouldReturn200_WhenRegionsExist() throws Exception {
        when(regionAdminFacade.getRegions(null)).thenReturn(List.of(
                new RegionSummary(
                        new RegionMaster(
                                1L,
                                "GANGNAM",
                                "서울",
                                "강남역",
                                new GeoJson.Point(List.of(127.0276, 37.4979)),
                                RegionStatus.ACTIVE,
                                1,
                                null,
                                null
                        ),
                        12L
                ),
                new RegionSummary(
                        new RegionMaster(
                                2L,
                                "HONGDAE",
                                "서울",
                                "홍대입구역",
                                new GeoJson.Point(List.of(126.92378, 37.55684)),
                                RegionStatus.ACTIVE,
                                2,
                                null,
                                null
                        ),
                        7L
                )
        ));

        mockMvc.perform(get(BASE_URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.regions[0].id").value(1))
                .andExpect(jsonPath("$.data.regions[0].name").value("GANGNAM"))
                .andExpect(jsonPath("$.data.regions[0].province").value("서울"))
                .andExpect(jsonPath("$.data.regions[0].displayName").value("강남역"))
                .andExpect(jsonPath("$.data.regions[0].status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.regions[0].sortOrder").value(1))
                .andExpect(jsonPath("$.data.regions[0].restaurantCount").value(12))
                .andExpect(jsonPath("$.data.regions[0].coordinatesStandard.coordinates[0]").value(127.0276))
                .andExpect(jsonPath("$.data.regions[1].name").value("HONGDAE"));
    }

    @Test
    @DisplayName("province query param이 있으면 해당 province 지역만 반환한다")
    void getRegions_ShouldFilterByProvince_WhenProvinceQueryParamExists() throws Exception {
        when(regionAdminFacade.getRegions("경기")).thenReturn(List.of(
                new RegionSummary(
                        new RegionMaster(
                                3L,
                                "SUWON",
                                "경기",
                                "수원역",
                                new GeoJson.Point(List.of(127.0000, 37.2667)),
                                RegionStatus.ACTIVE,
                                3,
                                null,
                                null
                        ),
                        4L
                )
        ));

        mockMvc.perform(get(BASE_URL)
                        .queryParam("province", " 경기 "))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.regions.length()").value(1))
                .andExpect(jsonPath("$.data.regions[0].name").value("SUWON"))
                .andExpect(jsonPath("$.data.regions[0].province").value("경기"));
    }

    @Test
    @DisplayName("region 단건 조회 응답이 200으로 반환된다")
    void getRegion_ShouldReturn200() throws Exception {
        when(regionAdminFacade.getRegionById(1L)).thenReturn(
                new RegionSummary(
                        new RegionMaster(
                                1L,
                                "GANGNAM",
                                "서울",
                                "강남역",
                                new GeoJson.Point(List.of(127.0276, 37.4979)),
                                RegionStatus.ACTIVE,
                                1,
                                null,
                                null
                        ),
                        12L
                )
        );

        mockMvc.perform(get(BASE_URL + "/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.region.id").value(1))
                .andExpect(jsonPath("$.data.region.name").value("GANGNAM"))
                .andExpect(jsonPath("$.data.region.restaurantCount").value(12));
    }

    @Test
    @DisplayName("region 생성 응답이 201로 반환된다")
    void createRegion_ShouldReturn201() throws Exception {
        when(regionAdminFacade.createRegion(Mockito.any(RegionCommand.Create.class))).thenReturn(
                new RegionMaster(
                        3L,
                        "YEOKSAM",
                        "서울",
                        "역삼역",
                        new GeoJson.Point(List.of(127.033, 37.5006)),
                        RegionStatus.ACTIVE,
                        3,
                        null,
                        null
                )
        );

        mockMvc.perform(post(BASE_URL)
                        .contentType("application/json")
                        .content("""
                                {
                                  "code": "yeoksam",
                                  "province": "서울",
                                  "displayName": "역삼역",
                                  "coordinatesStandard": {
                                    "coordinates": [127.033, 37.5006]
                                  },
                                  "status": "ACTIVE"
                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.region.id").value(3))
                .andExpect(jsonPath("$.data.region.name").value("YEOKSAM"))
                .andExpect(jsonPath("$.data.region.province").value("서울"))
                .andExpect(jsonPath("$.data.region.displayName").value("역삼역"))
                .andExpect(jsonPath("$.data.region.status").value("ACTIVE"));
    }

    @Test
    @DisplayName("region 생성 요청에서 status가 없으면 ACTIVE로 처리한다")
    void createRegion_ShouldUseActiveStatus_WhenStatusIsOmitted() throws Exception {
        when(regionAdminFacade.createRegion(Mockito.argThat(command ->
                command != null && command.status() == RegionStatus.ACTIVE
        ))).thenReturn(
                new RegionMaster(
                        4L,
                        "SEONGSU",
                        "서울",
                        "성수역",
                        new GeoJson.Point(List.of(127.0556, 37.5447)),
                        RegionStatus.ACTIVE,
                        4,
                        null,
                        null
                )
        );

        mockMvc.perform(post(BASE_URL)
                        .contentType("application/json")
                        .content("""
                                {
                                  "code": "seongsu",
                                  "province": "서울",
                                  "displayName": "성수역",
                                  "coordinatesStandard": {
                                    "coordinates": [127.0556, 37.5447]
                                  }
                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.region.id").value(4))
                .andExpect(jsonPath("$.data.region.name").value("SEONGSU"))
                .andExpect(jsonPath("$.data.region.status").value("ACTIVE"));
    }

    @Test
    @DisplayName("region 부분 수정 응답이 200으로 반환된다")
    void updateRegion_ShouldReturn200() throws Exception {
        when(regionAdminFacade.updateRegion(Mockito.eq(3L), Mockito.any(RegionCommand.Patch.class))).thenReturn(
                new RegionSummary(
                        new RegionMaster(
                                3L,
                                "YEOKSAM",
                                "서울",
                                "역삼",
                                new GeoJson.Point(List.of(127.033, 37.5006)),
                                RegionStatus.INACTIVE,
                                5,
                                null,
                                null
                        ),
                        2L
                )
        );

        mockMvc.perform(patch(BASE_URL + "/3")
                        .contentType("application/json")
                        .content("""
                                {
                                  "displayName": "역삼",
                                  "status": "INACTIVE",
                                  "sortOrder": 5
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.region.id").value(3))
                .andExpect(jsonPath("$.data.region.name").value("YEOKSAM"))
                .andExpect(jsonPath("$.data.region.displayName").value("역삼"))
                .andExpect(jsonPath("$.data.region.status").value("INACTIVE"))
                .andExpect(jsonPath("$.data.region.sortOrder").value(5))
                .andExpect(jsonPath("$.data.region.restaurantCount").value(2));
    }

    @Test
    @DisplayName("region 삭제 응답이 204로 반환된다")
    void deleteRegion_ShouldReturn204() throws Exception {
        doNothing().when(regionAdminFacade).deleteRegionById(3L);

        mockMvc.perform(delete(BASE_URL + "/3"))
                .andExpect(status().isNoContent());
    }
}
