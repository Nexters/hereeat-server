package com.yogieat.controller.v1.category;

import com.yogieat.category.facade.CategoryAdminFacade;
import com.yogieat.controller.v1.category.response.CategoryAdminResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/categories")
@Validated
@RequiredArgsConstructor
public class CategoryAdminController {

    private final CategoryAdminFacade categoryAdminFacade;

    @GetMapping
    public CategoryAdminResponse.ListResponse getCategories() {
        return CategoryAdminResponse.ListResponse.from(categoryAdminFacade.getCategories());
    }
}
