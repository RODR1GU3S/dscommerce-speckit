package com.devsuperior.dscommerce.services;

import com.devsuperior.dscommerce.dto.AdminProductDetailDTO;
import com.devsuperior.dscommerce.dto.AdminProductItemDTO;
import com.devsuperior.dscommerce.dto.CategoryOptionDTO;
import com.devsuperior.dscommerce.dto.PageResponseDTO;
import com.devsuperior.dscommerce.entities.Category;
import com.devsuperior.dscommerce.entities.Product;
import com.devsuperior.dscommerce.repositories.ProductRepository;
import com.devsuperior.dscommerce.security.AuthenticatedAccountPrincipal;
import com.devsuperior.dscommerce.services.exceptions.ProductNotFoundException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.RoundingMode;
import java.util.Comparator;

@Service
@Transactional(readOnly = true)
public class AdminProductQueryService {
    private final AdminAccessService access;
    private final ProductRepository products;

    public AdminProductQueryService(AdminAccessService access, ProductRepository products) {
        this.access = access;
        this.products = products;
    }

    public PageResponseDTO<AdminProductItemDTO> listProducts(String name, int page, int size,
                                                           AuthenticatedAccountPrincipal principal) {
        access.requireAdmin(principal);
        var filter = ProductNameFilter.normalize(name);
        var request = PageRequest.of(page, size);
        var result = filter.isEmpty() ? products.findAdminPage(request)
                : products.findPageByName(filter, request);
        var content = result.getContent().stream()
                .map(product -> new AdminProductItemDTO(product.getId(), product.getName(),
                        product.getImage(), price(product)))
                .toList();
        return new PageResponseDTO<>(content, result.getNumber(), result.getSize(),
                result.getTotalElements(), result.getTotalPages());
    }

    public AdminProductDetailDTO findProductDetails(Long id, AuthenticatedAccountPrincipal principal) {
        access.requireAdmin(principal);
        var product = products.findAdminByIdWithCategories(id)
                .orElseThrow(() -> new ProductNotFoundException("Product not found"));
        var categories = product.getCategories().stream()
                .sorted(Comparator.comparing(Category::getName).thenComparing(Category::getId))
                .map(category -> new CategoryOptionDTO(category.getId(), category.getName()))
                .toList();
        return new AdminProductDetailDTO(product.getId(), product.getName(), product.getDescription(),
                product.getImage(), price(product), categories);
    }

    private static String price(Product product) {
        return product.getPrice().setScale(2, RoundingMode.UNNECESSARY).toPlainString();
    }
}
