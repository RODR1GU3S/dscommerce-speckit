package com.devsuperior.dscommerce.services;

import com.devsuperior.dscommerce.controllers.handlers.GlobalExceptionHandler;
import com.devsuperior.dscommerce.services.exceptions.ProductCreationPersistenceException;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.mock.web.MockHttpServletRequest;

import java.sql.SQLException;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/** T041 classification boundary only; real JDBC/rollback/HTTP evidence remains in the unchanged T040 tests. */
class ProductCreationPersistenceClassificationTest {
    private static final String FK = "known_category_fk";
    private static final String INSERT = "insert into tb_product_category (product_id,category_id) values (?,?)";

    @Test
    void shouldExposeOnlyTheProvenMissingCategoryDiagnosisForCreation() {
        var exception = failure("23506", FK, INSERT);
        var request = request("POST", "/admin/products");
        var response = new GlobalExceptionHandler().handleProductCreationPersistence(exception, request);
        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody().message()).isEqualTo("Invalid product maintenance data");
        assertThat(response.getBody().errors()).singleElement().satisfies(error -> {
            assertThat(error.field()).isEqualTo("categoryIds");
            assertThat(error.code()).isEqualTo("CATEGORY_NOT_FOUND");
            assertThat(error.message()).isEqualTo("Selected category does not exist");
        });
        assertThat(response.getHeaders().getLocation()).isNull();
        assertThat(exception.getCause().getCause()).isInstanceOf(ConstraintViolationException.class);
    }

    @ParameterizedTest(name = "unclassified: {0}")
    @MethodSource("unclassified")
    void shouldKeepUnprovenIntegrityFailuresInternal(String label, ProductCreationPersistenceException exception) {
        assertThat(exception.isMissingCategoryReference()).isFalse();
        var response = new GlobalExceptionHandler().handleProductCreationPersistence(
                exception, request("POST", "/admin/products"));
        assertThat(response.getStatusCode().value()).isEqualTo(500);
        assertThat(response.getBody().message()).isEqualTo("Product maintenance could not be completed");
        assertThat(response.getBody().errors()).isEmpty();
        assertThat(response.getBody().toString()).doesNotContain("private SQL", FK, "23506", INSERT);
        assertThat(response.getHeaders().getLocation()).isNull();
    }

    static Stream<Arguments> unclassified() {
        return Stream.of(
                Arguments.of("CHECK", failure("23513", FK, INSERT)),
                Arguments.of("unique", failure("23505", FK, INSERT)),
                Arguments.of("referenced parent deletion", failure("23503", FK, INSERT)),
                Arguments.of("unknown FK", failure("23506", "another_fk", INSERT)),
                Arguments.of("missing FK identity", failure("23506", null, INSERT)),
                Arguments.of("product INSERT", failure("23506", FK, "insert into tb_product (name) values (?)")),
                Arguments.of("DELETE", failure("23506", FK, "delete from tb_product_category where product_id=?")),
                Arguments.of("unavailable SQL", failure("23506", FK, null)),
                Arguments.of("unproven metadata", new ProductCreationPersistenceException(Set.of(),
                        new DataIntegrityViolationException("private SQL", constraint("23506", FK, INSERT)))),
                Arguments.of("raw message alone", new ProductCreationPersistenceException(Set.of(FK),
                        new DataIntegrityViolationException("private SQL 23506 " + FK,
                                new SQLException("private SQL " + FK, "23506")))),
                Arguments.of("same truncated suffix but different full identity", new ProductCreationPersistenceException(Set.of(FK),
                        new DataIntegrityViolationException("private SQL", new ConstraintViolationException("private SQL",
                                sqlCause("23506", "x" + FK.substring(1)), INSERT, FK.substring(1))))),
                Arguments.of("identity embedded in unrelated text", new ProductCreationPersistenceException(Set.of(FK),
                        new DataIntegrityViolationException("private SQL", new ConstraintViolationException("private SQL",
                                new SQLException("private SQL " + sqlCause("23506", FK).getMessage(), "23506", 23506), INSERT, FK)))));
    }

    @ParameterizedTest
    @MethodSource("otherOperations")
    void shouldNotApplyTheCreationDiagnosisToOtherOperations(String method, String path) {
        var response = new GlobalExceptionHandler().handleProductCreationPersistence(
                failure("23506", FK, INSERT), request(method, path));
        assertThat(response.getStatusCode().value()).isEqualTo(500);
        assertThat(response.getBody().errors()).isEmpty();
    }

    static Stream<Arguments> otherOperations() {
        return Stream.of(Arguments.of("PUT", "/admin/products/1"), Arguments.of("POST", "/admin/categories"));
    }

    private static ProductCreationPersistenceException failure(String state, String name, String sql) {
        return new ProductCreationPersistenceException(Set.of(FK),
                new DataIntegrityViolationException("private SQL", constraint(state, name, sql)));
    }

    private static ConstraintViolationException constraint(String state, String name, String sql) {
        return new ConstraintViolationException("private SQL", sqlCause(state, name), sql, name);
    }

    private static SQLException sqlCause(String state, String name) {
        return new SQLException("Referential integrity constraint violation: \"" + name
                + ": PUBLIC.TB_PRODUCT_CATEGORY FOREIGN KEY(CATEGORY_ID) REFERENCES PUBLIC.TB_CATEGORY(ID) (99)\"; SQL statement:\n"
                + "private SQL", state, Integer.parseInt(state));
    }

    private static MockHttpServletRequest request(String method, String path) {
        var request = new MockHttpServletRequest(method, "/api" + path);
        request.setContextPath("/api");
        return request;
    }
}
