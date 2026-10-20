package com.devsuperior.dscommerce.services;

import com.devsuperior.dscommerce.dto.AdminProductItemDTO;
import com.devsuperior.dscommerce.dto.CategoryOptionDTO;
import com.devsuperior.dscommerce.entities.Category;
import com.devsuperior.dscommerce.entities.Product;
import com.devsuperior.dscommerce.repositories.CategoryRepository;
import com.devsuperior.dscommerce.repositories.ProductRepository;
import com.devsuperior.dscommerce.security.AuthenticatedAccountPrincipal;
import com.devsuperior.dscommerce.services.exceptions.OrderAccountResolutionException;
import com.devsuperior.dscommerce.services.exceptions.ProductNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** T018 service behavior: real services/guard, simulated account resolution and persistence. */
class AdminQueryServicesTest {
    private static final AuthenticatedAccountPrincipal ADMIN = new AuthenticatedAccountPrincipal(42L);
    private AuthenticatedAccountService accounts;
    private ProductRepository products;
    private CategoryRepository categories;
    private AdminProductQueryService productService;
    private AdminCategoryQueryService categoryService;

    @BeforeEach
    void prepareServicesWithRealGuard() {
        accounts = mock(AuthenticatedAccountService.class);
        products = mock(ProductRepository.class);
        categories = mock(CategoryRepository.class);
        var guard = new AdminAccessService(accounts);
        productService = new AdminProductQueryService(guard, products);
        categoryService = new AdminCategoryQueryService(guard, categories);
        when(accounts.resolve(ADMIN.accountId())).thenReturn(resolved("ROLE_ADMIN"));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" \t\n", "\u00a0\u2007\u202f", "\u2003\u3000"})
    void shouldChooseUnrestrictedQueryForAbsentEmptyOrUnicodeWhitespace(String filter) {
        var request = PageRequest.of(1, 2);
        var legacy = new Product(7L, "X", "curta", "imagem-legada", new BigDecimal("0.00"));
        when(products.findAdminPage(request)).thenReturn(new PageImpl<>(List.of(legacy), request, 3));

        var result = productService.listProducts(filter, 1, 2, ADMIN);

        assertThat(result).isNotNull();
        assertThat(result.content()).containsExactly(new AdminProductItemDTO(7L, "X", "imagem-legada", "0.00"));
        assertThat(result.page()).isEqualTo(1);
        assertThat(result.size()).isEqualTo(2);
        assertThat(result.totalElements()).isEqualTo(3);
        assertThat(result.totalPages()).isEqualTo(2);
        var ordered = inOrder(accounts, products, categories);
        ordered.verify(accounts).resolve(ADMIN.accountId());
        ordered.verify(products).findAdminPage(request);
        ordered.verifyNoMoreInteractions();
    }

    @ParameterizedTest
    @ValueSource(strings = {"FONE  Café%_/d'agua", "Cafe\u0301", "\ud83d\ude00 Fone", "\u200bFone\u200b"})
    void shouldTrimOnlyRecognizedExternalCodePointsAndKeepLiteralInterior(String expected) {
        var request = PageRequest.of(3, 2);
        when(products.findPageByName(expected, request)).thenReturn(new PageImpl<>(List.of(), request, 5));

        var result = productService.listProducts("\u00a0\u2003\t" + expected + "\n\u2007\u3000", 3, 2, ADMIN);

        assertThat(result).isNotNull();
        assertThat(result.content()).isEmpty();
        assertThat(result.page()).isEqualTo(3);
        assertThat(result.totalElements()).isEqualTo(5);
        assertThat(result.totalPages()).isEqualTo(3);
        var ordered = inOrder(accounts, products, categories);
        ordered.verify(accounts).resolve(ADMIN.accountId());
        ordered.verify(products).findPageByName(expected, request);
        ordered.verifyNoMoreInteractions();
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "1", "1.000", "99999999999999999.99"})
    void shouldMapPersistedMoneyExactlyToTwoDecimalPlacesWithoutExponents(String value) {
        var request = PageRequest.of(0, 12);
        var product = new Product(8L, "Nome original", "descricao", "image", new BigDecimal(value));
        when(products.findAdminPage(request)).thenReturn(new PageImpl<>(List.of(product), request, 1));

        var result = productService.listProducts(null, 0, 12, ADMIN);

        assertThat(result).isNotNull();
        assertThat(result.content()).singleElement().satisfies(item -> {
            assertThat(item.price()).matches("[0-9]+[.][0-9]{2}");
            assertThat(new BigDecimal(item.price())).isEqualByComparingTo(value);
        });
        assertThat(product.getPrice().toPlainString()).isEqualTo(value);
    }

    @Test
    void shouldMapLegacyDetailAndOrderCategoriesByOriginalNameThenIdWithoutMergingIds() {
        var legacy = new Product(9L, " X ", "curta", "imagem-legada", new BigDecimal("0.00"));
        legacy.getCategories().addAll(List.of(new Category(3L, "Z"), new Category(2L, "A"), new Category(1L, "A")));
        when(products.findAdminByIdWithCategories(9L)).thenReturn(Optional.of(legacy));

        var result = productService.findProductDetails(9L, ADMIN);

        assertThat(result).isNotNull();
        assertThat(result.id()).isEqualTo(9L);
        assertThat(result.name()).isEqualTo(" X ");
        assertThat(result.description()).isEqualTo("curta");
        assertThat(result.image()).isEqualTo("imagem-legada");
        assertThat(result.price()).isEqualTo("0.00");
        assertThat(result.categories()).containsExactly(new CategoryOptionDTO(1L, "A"),
                new CategoryOptionDTO(2L, "A"), new CategoryOptionDTO(3L, "Z"));
        assertThat(legacy.getCategories()).hasSize(3);
        var ordered = inOrder(accounts, products, categories);
        ordered.verify(accounts).resolve(ADMIN.accountId());
        ordered.verify(products).findAdminByIdWithCategories(9L);
        ordered.verifyNoMoreInteractions();
    }

    @Test
    void shouldRejectAbsentDetailAfterAuthorizationWithExistingNotFoundException() {
        when(products.findAdminByIdWithCategories(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productService.findProductDetails(99L, ADMIN))
                .isInstanceOf(ProductNotFoundException.class).hasMessage("Product not found");
        var ordered = inOrder(accounts, products, categories);
        ordered.verify(accounts).resolve(ADMIN.accountId());
        ordered.verify(products).findAdminByIdWithCategories(99L);
        ordered.verifyNoMoreInteractions();
    }

    @Test
    void shouldMapAllOrderedCategoryOptionsIncludingEqualNamesWithoutMutation() {
        var options = List.of(new Category(1L, "A"), new Category(2L, "A"), new Category(3L, "Z"));
        when(categories.findAllByOrderByNameAscIdAsc()).thenReturn(options);

        var result = categoryService.listCategories(ADMIN);

        assertThat(result).containsExactly(new CategoryOptionDTO(1L, "A"),
                new CategoryOptionDTO(2L, "A"), new CategoryOptionDTO(3L, "Z"));
        assertThat(options).extracting(Category::getId).containsExactly(1L, 2L, 3L);
        var ordered = inOrder(accounts, products, categories);
        ordered.verify(accounts).resolve(ADMIN.accountId());
        ordered.verify(categories).findAllByOrderByNameAscIdAsc();
        ordered.verifyNoMoreInteractions();
    }

    @Test
    void shouldReturnEmptyOptionsAndAllowReadingLegacyDetailWithoutCategories() {
        when(categories.findAllByOrderByNameAscIdAsc()).thenReturn(List.of());
        var legacy = new Product(9L, "X", "curta", "imagem-legada", new BigDecimal("0.00"));
        when(products.findAdminByIdWithCategories(9L)).thenReturn(Optional.of(legacy));

        assertThat(categoryService.listCategories(ADMIN)).isNotNull().isEmpty();
        var detail = productService.findProductDetails(9L, ADMIN);
        assertThat(detail).isNotNull();
        assertThat(detail.categories()).isEmpty();
    }

    @ParameterizedTest
    @EnumSource(Operation.class)
    void shouldRejectClientBeforeAnyAdministrativeRepositoryLookup(Operation operation) {
        when(accounts.resolve(ADMIN.accountId())).thenReturn(resolved("ROLE_CLIENT"));

        assertThatThrownBy(() -> query(operation, ADMIN)).isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(products, categories);
    }

    @ParameterizedTest
    @EnumSource(Operation.class)
    void shouldRejectRemovedAccountBeforeAdministrativeLookup(Operation operation) {
        var failure = new BadCredentialsException("Authentication required or invalid");
        when(accounts.resolve(ADMIN.accountId())).thenThrow(failure);

        assertThatThrownBy(() -> query(operation, ADMIN)).isSameAs(failure);
        verifyNoInteractions(products, categories);
    }

    @ParameterizedTest
    @EnumSource(Operation.class)
    void shouldPropagateInternalResolutionFailureWithoutAdministrativeLookup(Operation operation) {
        var failure = new OrderAccountResolutionException("Account resolution could not be completed", new IllegalStateException());
        when(accounts.resolve(ADMIN.accountId())).thenThrow(failure);

        assertThatThrownBy(() -> query(operation, ADMIN)).isSameAs(failure);
        verifyNoInteractions(products, categories);
    }

    @ParameterizedTest
    @EnumSource(Operation.class)
    void shouldRejectMissingPrincipalBeforeAccountAndAdministrativeLookup(Operation operation) {
        assertThatThrownBy(() -> query(operation, null)).isInstanceOf(BadCredentialsException.class);
        verifyNoInteractions(accounts, products, categories);
    }

    @ParameterizedTest
    @EnumSource(Operation.class)
    void shouldResolveCurrentAdminAgainAndRejectRevocationAfterSuccessfulRead(Operation operation) {
        when(products.findAdminPage(PageRequest.of(0, 12))).thenReturn(new PageImpl<>(List.of()));
        when(products.findAdminByIdWithCategories(9L)).thenReturn(Optional.of(new Product(9L, "X", "image", BigDecimal.ZERO)));
        when(categories.findAllByOrderByNameAscIdAsc()).thenReturn(List.of());
        when(accounts.resolve(ADMIN.accountId())).thenReturn(resolved("ROLE_ADMIN"), resolved("ROLE_CLIENT"));
        assertThat(query(operation, ADMIN)).isNotNull();

        assertThatThrownBy(() -> query(operation, ADMIN)).isInstanceOf(AccessDeniedException.class);

        var ordered = inOrder(accounts, products, categories);
        ordered.verify(accounts).resolve(ADMIN.accountId());
        switch (operation) {
            case LIST -> ordered.verify(products).findAdminPage(PageRequest.of(0, 12));
            case DETAIL -> ordered.verify(products).findAdminByIdWithCategories(9L);
            case CATEGORIES -> ordered.verify(categories).findAllByOrderByNameAscIdAsc();
        }
        ordered.verify(accounts).resolve(ADMIN.accountId());
        ordered.verifyNoMoreInteractions();
    }

    private Object query(Operation operation, AuthenticatedAccountPrincipal principal) {
        return switch (operation) {
            case LIST -> productService.listProducts(null, 0, 12, principal);
            case DETAIL -> productService.findProductDetails(9L, principal);
            case CATEGORIES -> categoryService.listCategories(principal);
        };
    }

    private AuthenticatedAccountService.ResolvedAccount resolved(String role) {
        return new AuthenticatedAccountService.ResolvedAccount(ADMIN, Set.of(new SimpleGrantedAuthority(role)));
    }

    enum Operation { LIST, DETAIL, CATEGORIES }
}
