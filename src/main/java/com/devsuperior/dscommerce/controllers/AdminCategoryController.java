package com.devsuperior.dscommerce.controllers;

import com.devsuperior.dscommerce.dto.CategoryOptionDTO;
import com.devsuperior.dscommerce.security.AuthenticatedAccountPrincipal;
import com.devsuperior.dscommerce.services.AdminCategoryQueryService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/admin/categories")
public class AdminCategoryController {
    private final AdminCategoryQueryService queries;

    public AdminCategoryController(AdminCategoryQueryService queries) {
        this.queries = queries;
    }

    @GetMapping
    public ResponseEntity<List<CategoryOptionDTO>> listCategories(
            @RequestParam MultiValueMap<String, String> query,
            @AuthenticationPrincipal AuthenticatedAccountPrincipal principal) {
        AdminQueryParameters.requireNoQuery(query);
        return ResponseEntity.ok(queries.listCategories(principal));
    }
}
