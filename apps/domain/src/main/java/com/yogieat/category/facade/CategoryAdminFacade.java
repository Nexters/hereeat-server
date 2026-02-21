package com.yogieat.category.facade;

import com.yogieat.category.domain.Category;
import com.yogieat.category.service.CategoryService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class CategoryAdminFacade {

    private final CategoryService categoryService;

    public List<Category> getCategories() {
        return categoryService.findAll();
    }
}
