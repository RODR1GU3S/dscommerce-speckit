package com.devsuperior.dscommerce.services;

import com.devsuperior.dscommerce.dto.CategoryOptionDTO;
import com.devsuperior.dscommerce.repositories.CategoryRepository;
import com.devsuperior.dscommerce.security.AuthenticatedAccountPrincipal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class AdminCategoryQueryService {
    private final AdminAccessService access;
    private final CategoryRepository categories;

    public AdminCategoryQueryService(AdminAccessService access, CategoryRepository categories) {
        this.access = access;
        this.categories = categories;
    }

    public List<CategoryOptionDTO> listCategories(AuthenticatedAccountPrincipal principal) {
        access.requireAdmin(principal);
        return categories.findAllByOrderByNameAscIdAsc().stream()
                .map(category -> new CategoryOptionDTO(category.getId(), category.getName()))
                .toList();
    }
}
