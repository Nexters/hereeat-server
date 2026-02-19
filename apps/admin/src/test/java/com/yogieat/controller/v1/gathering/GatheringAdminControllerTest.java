package com.yogieat.controller.v1.gathering;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.yogieat.admin.service.AdminService;
import com.yogieat.common.error.CustomException;
import com.yogieat.common.error.ErrorCode;
import com.yogieat.config.jwt.JwtTokenProvider;
import com.yogieat.controller.advice.ErrorHttpStatusMapper;
import com.yogieat.controller.advice.GlobalApiResponseAdvice;
import com.yogieat.controller.advice.GlobalExceptionHandler;
import com.yogieat.controller.v1.gathering.fixture.GatheringAdminFixture;
import com.yogieat.gathering.facade.GatheringAdminFacade;
import com.yogieat.gathering.result.GatheringAdminResult;
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

@WebMvcTest(GatheringAdminController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({
        GlobalExceptionHandler.class,
        GlobalApiResponseAdvice.class,
        ErrorHttpStatusMapper.class,
        GatheringAdminControllerTest.MockTestBeanConfig.class
})
class GatheringAdminControllerTest {

    private static final String BASE_URL = "/api/v1/admin/gatherings";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private GatheringAdminFacade gatheringAdminFacade;

    @TestConfiguration
    static class MockTestBeanConfig {
        @Bean
        @Primary
        GatheringAdminFacade gatheringAdminFacade() {
            return Mockito.mock(GatheringAdminFacade.class);
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
    @DisplayName("모임 목록 조회 응답이 200으로 반환된다")
    void listGatherings_ShouldReturn200() throws Exception {
        GatheringAdminResult.Page result = GatheringAdminFixture.samplePage();
        when(gatheringAdminFacade.getPageGatherings(0, 10, "점심", null, null, false))
                .thenReturn(result);

        mockMvc.perform(get(BASE_URL)
                        .param("page", "0")
                        .param("size", "10")
                        .param("keyword", "점심"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.page").value(0))
                .andExpect(jsonPath("$.data.content[0].id").value(1L));
    }

    @Test
    @DisplayName("단건 모임 조회 응답이 200으로 반환된다")
    void getGatheringById_ShouldReturn200_WhenGatheringExists() throws Exception {
        GatheringAdminResult.Detail result = GatheringAdminFixture.sampleDetail();
        when(gatheringAdminFacade.getGatheringBy(1L)).thenReturn(result);

        mockMvc.perform(get(BASE_URL + "/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.gathering.id").value(1L))
                .andExpect(jsonPath("$.data.participantCount").value(4));
    }

    @Test
    @DisplayName("단건 모임 조회 실패 시 404을 반환한다")
    void getGatheringById_ShouldReturn404_WhenNotFound() throws Exception {
        when(gatheringAdminFacade.getGatheringBy(999L))
                .thenThrow(new CustomException(ErrorCode.GATHERING_NOT_FOUND));

        mockMvc.perform(get(BASE_URL + "/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.data.errorCode").value(ErrorCode.GATHERING_NOT_FOUND.getCode()));
    }

    @Test
    @DisplayName("모임 대시보드 조회 응답이 200으로 반환된다")
    void getGatheringDashboard_ShouldReturn200() throws Exception {
        GatheringAdminResult.Dashboard result = GatheringAdminFixture.sampleDashboard();
        when(gatheringAdminFacade.getGatheringDashboard()).thenReturn(result);

        mockMvc.perform(get(BASE_URL + "/dashboard"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.gatherings").isArray())
                .andExpect(jsonPath("$.data.participants").isArray())
                .andExpect(jsonPath("$.data.issues").isArray());
    }
}
