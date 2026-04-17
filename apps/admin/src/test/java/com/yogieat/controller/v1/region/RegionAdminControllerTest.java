package com.yogieat.controller.v1.region;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.yogieat.admin.service.AdminService;
import com.yogieat.common.GeoJson;
import com.yogieat.config.jwt.JwtTokenProvider;
import com.yogieat.controller.advice.ErrorHttpStatusMapper;
import com.yogieat.controller.advice.GlobalApiResponseAdvice;
import com.yogieat.controller.advice.GlobalExceptionHandler;
import com.yogieat.region.domain.RegionMaster;
import com.yogieat.region.facade.RegionAdminFacade;
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
        when(regionAdminFacade.getRegions()).thenReturn(List.of(
                new RegionMaster(
                        1L,
                        "GANGNAM",
                        "강남역",
                        new GeoJson.Point(List.of(127.0276, 37.4979)),
                        true,
                        1,
                        null,
                        null
                ),
                new RegionMaster(
                        2L,
                        "HONGDAE",
                        "홍대입구역",
                        new GeoJson.Point(List.of(126.92378, 37.55684)),
                        true,
                        2,
                        null,
                        null
                )
        ));

        mockMvc.perform(get(BASE_URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.regions[0].name").value("GANGNAM"))
                .andExpect(jsonPath("$.data.regions[0].displayName").value("강남역"))
                .andExpect(jsonPath("$.data.regions[0].coordinatesStandard.coordinates[0]").value(127.0276))
                .andExpect(jsonPath("$.data.regions[1].name").value("HONGDAE"));
    }
}
