package com.devsuperior.dscommerce.services;

import com.devsuperior.dscommerce.dto.AdminFieldErrorDTO;
import com.devsuperior.dscommerce.dto.AdminProductDetailDTO;
import com.devsuperior.dscommerce.dto.CategoryOptionDTO;
import com.devsuperior.dscommerce.dto.ProductWriteRequestDTO;
import com.devsuperior.dscommerce.entities.Category;
import com.devsuperior.dscommerce.entities.Product;
import com.devsuperior.dscommerce.entities.UserRole;
import com.devsuperior.dscommerce.repositories.CategoryRepository;
import com.devsuperior.dscommerce.repositories.ProductRepository;
import com.devsuperior.dscommerce.repositories.UserAccountRepository;
import com.devsuperior.dscommerce.security.AuthenticatedAccountPrincipal;
import com.devsuperior.dscommerce.services.exceptions.InvalidProductInputException;
import com.devsuperior.dscommerce.services.exceptions.ProductCreationPersistenceException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;

/** Inclusion only; the proxy commits before return when this call owns the REQUIRED transaction. */
@Service
@Transactional(propagation = Propagation.REQUIRED)
public class ProductMaintenanceService {
    private final AdminAccessService access;
    private final ProductInputValidationService local;
    private final ProductCategoryDiagnosticService diagnostic;
    private final CategoryRepository categories;
    private final ProductRepository products;
    private final UserAccountRepository accounts;

    public ProductMaintenanceService(AdminAccessService access, ProductInputValidationService local,
                                     ProductCategoryDiagnosticService diagnostic, CategoryRepository categories,
                                     ProductRepository products, UserAccountRepository accounts) {
        this.access = access;
        this.local = local;
        this.diagnostic = diagnostic;
        this.categories = categories;
        this.products = products;
        this.accounts = accounts;
    }

    public AdminProductDetailDTO createProduct(ProductWriteRequestDTO input, AuthenticatedAccountPrincipal principal) {
        access.requireAdmin(principal);
        // OSIV can retain the previously authorized entity/roles; revalidate database values, not that collection.
        var effectiveRoles = accounts.findEffectiveAccountRolesById(principal.accountId());
        if (effectiveRoles.isEmpty()) throw new BadCredentialsException("Authentication required or invalid");
        if (effectiveRoles.stream().noneMatch(row -> row.getRole() == UserRole.ADMIN)) {
            throw new AccessDeniedException("Product maintenance not permitted");
        }
        if (input == null) {
            throw new InvalidProductInputException(List.of(new AdminFieldErrorDTO(
                    "body", "INVALID_BODY", "Invalid request body")));
        }
        var checked = local.validate(input);
        var complete = diagnostic.diagnose(input, checked.diagnostic(), principal);
        if (!complete.errors().isEmpty()) throw new InvalidProductInputException(complete.errors());
        var values = checked.normalizedValues();
        if (values == null) throw new IllegalStateException("Product normalization could not be completed");
        // Capture the actual FK identity while the transaction is usable, never after a failed flush.
        var categoryForeignKeys = diagnostic.categoryForeignKeysForCreation();

        // Diagnosis loaded and compared every distinct ID in this same REQUIRED persistence context.
        // These references reuse the verified managed categories; getReferenceById alone is not an existence check.
        var selected = new LinkedHashSet<>(values.categoryIds()).stream().map(categories::getReferenceById).toList();
        var product = new Product(null, values.name(), values.description(), values.image(), values.price());
        product.getCategories().addAll(selected);
        Product saved;
        try {
            saved = products.saveAndFlush(product);
        } catch (DataIntegrityViolationException failure) {
            // Propagate out through the transactional proxy: no retry, lookup or continuation after failure.
            throw new ProductCreationPersistenceException(categoryForeignKeys, failure);
        }
        var options = saved.getCategories().stream()
                .sorted(Comparator.comparing(Category::getName).thenComparing(Category::getId))
                .map(category -> new CategoryOptionDTO(category.getId(), category.getName()))
                .toList();
        // Flush and DTO mapping happen inside the transaction; failures propagate and no failed transaction continues.
        return new AdminProductDetailDTO(saved.getId(), saved.getName(), saved.getDescription(), saved.getImage(),
                saved.getPrice().toPlainString(), options);
    }
}
