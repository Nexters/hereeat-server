package com.yogieat.controller.v1.category;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.yogieat.admin.service.AdminService;
import com.yogieat.category.domain.Category;
import com.yogieat.category.domain.value.LargeCategory;
import com.yogieat.category.facade.CategoryAdminFacade;
import com.yogieat.config.jwt.JwtTokenProvider;
import com.yogieat.controller.advice.ErrorHttpStatusMapper;
import com.yogieat.controller.advice.GlobalApiResponseAdvice;
import com.yogieat.controller.advice.GlobalExceptionHandler;
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

@WebMvcTest(CategoryAdminController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({
        GlobalExceptionHandler.class,
        GlobalApiResponseAdvice.class,
        ErrorHttpStatusMapper.class,
        CategoryAdminControllerTest.MockTestBeanConfig.class
})
class CategoryAdminControllerTest {

    private static final String BASE_URL = "/api/v1/admin/categories";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CategoryAdminFacade categoryAdminFacade;

    @TestConfiguration
    static class MockTestBeanConfig {
        @Bean
        @Primary
        CategoryAdminFacade categoryAdminFacade() {
            return Mockito.mock(CategoryAdminFacade.class);
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
    @DisplayName("카테고리 목록은 mediumCategory 기준으로 정렬된다")
    void getCategories_ShouldSortByMediumCategory() throws Exception {
        when(categoryAdminFacade.getCategories()).thenReturn(List.of(
                category(1L, LargeCategory.WESTERN, "Pasta"),
                category(2L, LargeCategory.KOREAN, "찌개"),
                category(3L, LargeCategory.JAPANESE, "라멘"),
                category(4L, LargeCategory.WESTERN, "burger"),
                category(5L, LargeCategory.KOREAN, "국밥")
        ));

        mockMvc.perform(get(BASE_URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.categories[0].mediumCategory").value("burger"))
                .andExpect(jsonPath("$.data.categories[1].mediumCategory").value("Pasta"))
                .andExpect(jsonPath("$.data.categories[2].mediumCategory").value("국밥"))
                .andExpect(jsonPath("$.data.categories[3].mediumCategory").value("라멘"))
                .andExpect(jsonPath("$.data.categories[4].mediumCategory").value("찌개"));
    }

    private Category category(Long id, LargeCategory largeCategory, String mediumCategory) {
        return new Category(id, largeCategory, mediumCategory, null);
    }
}
