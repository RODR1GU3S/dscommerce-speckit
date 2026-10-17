package com.devsuperior.dscommerce.services;

import com.devsuperior.dscommerce.DscommerceApplication;
import com.devsuperior.dscommerce.config.AdminProductTestConfig;
import com.devsuperior.dscommerce.controllers.AdminProductReferenceRaceIntegrationTest;
import com.devsuperior.dscommerce.controllers.AdminProductReferenceRaceIntegrationTest.Point;
import com.devsuperior.dscommerce.controllers.AdminProductReferenceRaceIntegrationTest.Recorder;
import com.devsuperior.dscommerce.controllers.AdminProductReferenceRaceIntegrationTest.InputState;
import com.devsuperior.dscommerce.dto.ProductWriteRequestDTO;
import com.devsuperior.dscommerce.entities.UserRole;
import com.devsuperior.dscommerce.fixtures.AdminProductDatabaseProbe;
import com.devsuperior.dscommerce.fixtures.AdminProductTestFixture;
import com.devsuperior.dscommerce.security.AuthenticatedAccountPrincipal;
import com.devsuperior.dscommerce.services.exceptions.InvalidProductInputException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.aop.framework.Advised;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.web.context.WebApplicationContext;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/** T040 direct calls to the real REQUIRED proxy, deliberately without a Bean Validation invocation. */
@SpringBootTest(classes = DscommerceApplication.class, properties = {
        "spring.jpa.open-in-view=false", "spring.jpa.properties.hibernate.jdbc.batch_size=0"})
@ActiveProfiles("test")
@ContextConfiguration(initializers = AdminProductTestConfig.class)
@Import(AdminProductReferenceRaceIntegrationTest.ProbeConfig.class)
class ProductMaintenanceServiceTest {
    @Autowired private WebApplicationContext context;
    @Autowired private ProductMaintenanceService maintenance;
    @Autowired private Recorder recorder;
    @PersistenceContext private EntityManager entityManager;
    private AdminProductDatabaseProbe database;
    private AdminProductTestFixture fixture;
    private AuthenticatedAccountPrincipal principal;
    private long a;
    private long b;
    private long missing;
    private long created;
    private long legacyId;

    @BeforeEach
    void prepareCommittedOwnedRowsAndAnExplicitDatabaseBackedPrincipal() {
        AdminProductReferenceRaceIntegrationTest.noCallerTransaction();
        recorder.active = false;
        database = new AdminProductDatabaseProbe(context.getEnvironment().getRequiredProperty("spring.datasource.url"));
        assertThat(database.snapshot().products()).isEmpty();
        assertThat(database.snapshot().categories()).isEmpty();
        fixture = new AdminProductTestFixture(context);
        var account = fixture.createAccount("T040 service Admin", "Disposable-T040", Set.of(UserRole.ADMIN));
        principal = new AuthenticatedAccountPrincipal(account.id());
        assertThat(database.roles(account.id())).containsExactly(UserRole.ADMIN);
        var options = fixture.prepareCategorySelection();
        a = options.sameName().get(0).id();
        b = options.sameName().get(1).id();
        missing = Math.addExact(options.unused().id(), 1L);
        assertThat(database.snapshot().categories()).noneMatch(row -> row.id() == missing);
        var legacy = fixture.createProduct("T040 service legado", "Legado", "imagem-legada", new BigDecimal("0.00"));
        legacyId = legacy.id();
        fixture.associate(legacy.id(), options.unused().id());
        assertThat(maintenance).isInstanceOf(Advised.class);
        assertThat(((Advised) maintenance).getAdvisors()).anyMatch(advisor -> advisor.getAdvice() instanceof TransactionInterceptor);
        created = 0;
    }

    @AfterEach
    void cleanOnlyOwnedRowsAfterIndependentAssertions() throws Exception {
        recorder.active = false;
        AdminProductReferenceRaceIntegrationTest.noCallerTransaction();
        if (recorder.putEnabled) {
            AdminProductReferenceRaceIntegrationTest.executeIndependent(database,
                    "delete from tb_product_category where product_id = ?", legacyId);
        }
        for (var product : database.snapshot().products()) {
            if (product.id() != legacyId) {
                AdminProductReferenceRaceIntegrationTest.executeIndependent(database, "delete from tb_product_category where product_id = ?", product.id());
                AdminProductReferenceRaceIntegrationTest.executeIndependent(database, "delete from tb_product where id = ?", product.id());
            }
        }
        fixture.close();
        assertThat(database.snapshot().products()).isEmpty();
        assertThat(database.snapshot().categories()).isEmpty();
        assertThat(database.snapshot().associations()).isEmpty();
        assertThat(database.accountIds()).isEmpty();
    }

    @Test
    void shouldRevalidateAndCommitDirectInputWithoutValidBeforeReturningTheProxyResult() {
        var before = database.snapshot();
        var input = valid(List.of(a, a, b));
        recorder.start(Point.NONE);
        var result = maintenance.createProduct(input, principal);
        created = result.id();
        AdminProductReferenceRaceIntegrationTest.noCallerTransaction();
        assertThat(recorder.boundaryPasses.get()).isZero();
        assertThat(recorder.writeLocal.get()).isEqualTo(1);
        assertThat(recorder.writeReferences.get()).isEqualTo(1);
        assertThat(recorder.revalidatedPasses.get()).isEqualTo(1);
        assertThat(recorder.flushReturns.get()).isEqualTo(1);
        assertThat(recorder.completions).containsExactly(TransactionSynchronization.STATUS_COMMITTED);
        assertThat(recorder.commits.get()).isEqualTo(1);
        assertThat(recorder.returns.get()).isEqualTo(1);
        assertThat(recorder.succeededDml()).hasSize(3);
        assertThat(result.name()).isEqualTo("T040 Fone");
        assertThat(result.price()).isEqualTo("1.00");
        assertThat(input.name()).isEqualTo(" T040 Fone ");
        assertThat(input.price()).isEqualTo("1.000");
        assertThat(input.categoryIds()).containsExactly(a, a, b);
        var after = database.snapshot();
        assertThat(after.products()).containsAll(before.products()).hasSize(before.products().size() + 1)
                .contains(new AdminProductDatabaseProbe.ProductRow(created, result.name(), result.description(), result.image(), new BigDecimal("1.00")));
        assertThat(after.associations()).containsAll(before.associations()).hasSize(before.associations().size() + 2)
                .contains(new AdminProductTestFixture.ProductCategory(created, a), new AdminProductTestFixture.ProductCategory(created, b));
        assertThat(after.categories()).isEqualTo(before.categories());
        assertThat(after.orders()).isEqualTo(before.orders());
        assertThat(after.items()).isEqualTo(before.items());
        System.out.println("T040_DIRECT case=valid boundaryValidInvoked=false revalidationPassed=true flushReturned=true commitReturned=true independentProductVisible=true uniquePairs=2");
    }

    @ParameterizedTest
    @ValueSource(strings = {"local-and-references", "malformed-and-repeated-references", "null-body", "empty-selection"})
    void shouldRejectDirectInvalidInputBeforeDmlWithCompleteTransactionalDiagnostics(String kind) {
        ProductWriteRequestDTO input = switch (kind) {
            case "local-and-references" -> new ProductWriteRequestDTO("ab", "123456789", "0", "/relative", List.of(a, missing, missing));
            case "malformed-and-repeated-references" -> valid(Arrays.asList(null, 0L, -1L, a, missing, missing));
            case "empty-selection" -> valid(List.of());
            default -> null;
        };
        var before = database.snapshot();
        recorder.start(Point.NONE);
        var failure = catchThrowable(() -> maintenance.createProduct(input, principal));
        assertThat(failure).isInstanceOf(InvalidProductInputException.class);
        var expected = switch (kind) {
            case "local-and-references" -> List.of("name/INVALID_LENGTH", "description/INVALID_LENGTH", "price/NOT_POSITIVE",
                    "image/INVALID_IMAGE_URL", "categoryIds[1]/CATEGORY_NOT_FOUND", "categoryIds[2]/CATEGORY_NOT_FOUND");
            case "malformed-and-repeated-references" -> List.of("categoryIds[0]/INVALID_TYPE", "categoryIds[1]/NOT_POSITIVE",
                    "categoryIds[2]/NOT_POSITIVE", "categoryIds[4]/CATEGORY_NOT_FOUND", "categoryIds[5]/CATEGORY_NOT_FOUND");
            case "empty-selection" -> List.of("categoryIds/EMPTY_CATEGORIES");
            default -> List.of("body/INVALID_BODY");
        };
        assertThat(((InvalidProductInputException) failure).getErrors().stream().map(error -> error.field() + "/" + error.code()))
                .containsExactlyInAnyOrderElementsOf(expected);
        assertRejected(before);
        assertThat(recorder.writeReferences.get()).isEqualTo(kind.equals("null-body") ? 0 : 1);
        assertThat(recorder.revalidatedPasses.get()).isZero();
        if (kind.contains("references")) assertThat(recorder.sql.stream().filter(event -> event.write() && event.sql().contains("from tb_category"))).hasSize(1);
        System.out.printf("T040_DIRECT case=%s boundaryValidInvoked=false errors=%d dml=0 saveAttempts=0 transactionRolledBack=true independentSnapshotEqual=true%n", kind, expected.size());
    }

    @Test
    void shouldCheckCurrentAdminBeforeExaminingDirectInvalidDataOrReadingCategories() {
        var client = fixture.createAccount("T040 service Client", "Disposable-T040", Set.of(UserRole.CLIENT));
        var before = database.snapshot();
        recorder.start(Point.NONE);
        var failure = catchThrowable(() -> maintenance.createProduct(null, new AuthenticatedAccountPrincipal(client.id())));
        assertThat(failure).isInstanceOf(AccessDeniedException.class);
        assertRejected(before);
        assertThat(recorder.writeGuards.get()).isEqualTo(1);
        assertThat(recorder.writeLocal.get()).isZero();
        assertThat(recorder.writeReferences.get()).isZero();
        assertThat(recorder.sql).noneMatch(event -> event.sql().contains("tb_category"));
        System.out.println("T040_DIRECT case=client-before-null-body boundaryValidInvoked=false guardRejected=true localCalls=0 categoryReads=0 dml=0 independentSnapshotEqual=true");
    }

    @ParameterizedTest
    @ValueSource(strings = {"valid", "local-and-references", "malformed-and-repeated-references", "null-body", "empty-selection"})
    void t051RevalidatesDirectReplacementWithoutValidAndObservesMemoryBeforeMutation(String kind) {
        ProductWriteRequestDTO input = switch (kind) {
            case "local-and-references" -> new ProductWriteRequestDTO("ab", "123456789", "0", "/relative", List.of(a, missing, missing));
            case "malformed-and-repeated-references" -> valid(Arrays.asList(null, 0L, -1L, a, missing, missing, b));
            case "empty-selection" -> valid(List.of());
            case "valid" -> valid(List.of(a, a, b));
            default -> null;
        };
        var before = database.snapshot();
        var raw = input == null ? null : InputState.capture(input);
        recorder.putManaged = entityManager::contains;
        recorder.startPut(Point.NONE);
        var failure = catchThrowable(() -> maintenance.replaceProduct(legacyId, input, principal));
        AdminProductReferenceRaceIntegrationTest.noCallerTransaction();
        assertThat(recorder.boundaryPasses.get()).isZero(); // no Bean Validation or HTTP invocation
        assertThat(recorder.putEntries.get()).isEqualTo(1);
        assertThat(recorder.putTargetLookups.get()).isEqualTo(1);
        assertThat(recorder.putCalls).startsWith("guard", "effective-roles", "target");
        assertThat(recorder.putTargetBefore).isNotNull();
        recorder.assertPutMemory(); // independent from SQL and final database-state checks
        if (input != null) assertThat(InputState.capture(input)).isEqualTo(raw);
        assertThat(database.roles(principal.accountId())).containsExactly(UserRole.ADMIN);
        assertThat(database.accountIds()).containsExactly(principal.accountId());
        if (kind.equals("valid")) {
            assertThat(failure).isNull();
            assertThat(recorder.putCalls).containsExactly("guard", "effective-roles", "target", "local", "write-diagnose", "guard", "effective-roles");
            assertThat(recorder.writeLocal.get()).isEqualTo(1);
            assertThat(recorder.writeReferences.get()).isEqualTo(1);
            assertThat(recorder.revalidatedPasses.get()).isEqualTo(1);
            assertThat(recorder.putInputs).hasSize(2);
            assertThat(recorder.flushReturns.get()).isEqualTo(1);
            assertThat(recorder.returns.get()).isEqualTo(1);
            assertThat(recorder.completions).containsExactly(TransactionSynchronization.STATUS_COMMITTED);
            assertThat(recorder.commits.get()).isEqualTo(1); assertThat(recorder.rollbacks.get()).isZero();
            assertThat(recorder.succeededDml()).hasSize(4);
            var expected = new AdminProductDatabaseProbe.ProductRow(legacyId, "T040 Fone", "Descricao completa T040",
                    "https://example.invalid/t040.jpg", new BigDecimal("1.00"));
            assertThat(database.snapshot()).isEqualTo(new AdminProductDatabaseProbe.Snapshot(List.of(expected), before.categories(),
                    Set.of(new AdminProductTestFixture.ProductCategory(legacyId, a), new AdminProductTestFixture.ProductCategory(legacyId, b)),
                    before.orders(), before.items()));
            assertThat(recorder.putTargetAfter).isNotEqualTo(recorder.putTargetBefore);
            assertThat(recorder.sql.stream().filter(AdminProductReferenceRaceIntegrationTest.SqlEvent::isDml))
                    .allMatch(event -> event.succeeded() && (event.sql().contains("tb_product ") || event.sql().contains("tb_product_category ")));
        } else {
            assertThat(failure).isInstanceOf(InvalidProductInputException.class);
            var expected = switch (kind) {
                case "local-and-references" -> List.of("name/INVALID_LENGTH", "description/INVALID_LENGTH", "price/NOT_POSITIVE",
                        "image/INVALID_IMAGE_URL", "categoryIds[1]/CATEGORY_NOT_FOUND", "categoryIds[2]/CATEGORY_NOT_FOUND");
                case "malformed-and-repeated-references" -> List.of("categoryIds[0]/INVALID_TYPE", "categoryIds[1]/NOT_POSITIVE",
                        "categoryIds[2]/NOT_POSITIVE", "categoryIds[4]/CATEGORY_NOT_FOUND", "categoryIds[5]/CATEGORY_NOT_FOUND");
                case "empty-selection" -> List.of("categoryIds/EMPTY_CATEGORIES");
                default -> List.of("body/INVALID_BODY");
            };
            assertThat(((InvalidProductInputException) failure).getErrors().stream().map(error -> error.field() + "/" + error.code()))
                    .containsExactlyInAnyOrderElementsOf(expected);
            assertThat(recorder.putTargetAfter).isEqualTo(recorder.putTargetBefore);
            assertThat(recorder.sql).noneMatch(AdminProductReferenceRaceIntegrationTest.SqlEvent::isDml);
            assertThat(recorder.saveAttempts.get()).isZero(); assertThat(recorder.flushReturns.get()).isZero();
            assertThat(recorder.returns.get()).isZero(); assertThat(recorder.revalidatedPasses.get()).isZero();
            assertThat(recorder.writeLocal.get()).isEqualTo(kind.equals("null-body") ? 0 : 1);
            assertThat(recorder.writeReferences.get()).isEqualTo(kind.equals("null-body") ? 0 : 1);
            assertThat(recorder.putInputs).hasSize(kind.equals("null-body") ? 0 : 2);
            assertThat(recorder.completions).containsExactly(TransactionSynchronization.STATUS_ROLLED_BACK);
            assertThat(recorder.commits.get()).isZero(); assertThat(recorder.rollbacks.get()).isEqualTo(1);
            assertThat(database.snapshot()).isEqualTo(before);
        }
        System.out.printf("T051_DIRECT case=%s boundaryValidInvoked=false proxyEntries=%d targetLookups=%d references=%d dml=%d flushes=%d commits=%d rollbacks=%d inputObservations=%d categoryObservations=%d memoryChecked=true snapshotChecked=true%n",
                kind, recorder.putEntries.get(), recorder.putTargetLookups.get(), recorder.writeReferences.get(),
                recorder.sql.stream().filter(AdminProductReferenceRaceIntegrationTest.SqlEvent::isDml).count(), recorder.flushReturns.get(),
                recorder.commits.get(), recorder.rollbacks.get(), recorder.putInputs.size(), recorder.putCategories.size());
    }

    @Test
    void t051RejectsDirectClientBeforeTargetLookupAndBeforeInvalidInput() {
        var client = fixture.createAccount("T051 direct Client", "Disposable-T051", Set.of(UserRole.CLIENT));
        var before = database.snapshot();
        recorder.putManaged = entityManager::contains;
        recorder.startPut(Point.NONE);
        var failure = catchThrowable(() -> maintenance.replaceProduct(legacyId, null, new AuthenticatedAccountPrincipal(client.id())));
        AdminProductReferenceRaceIntegrationTest.noCallerTransaction();
        assertThat(failure).isInstanceOf(AccessDeniedException.class);
        assertThat(recorder.putCalls).containsExactly("guard");
        assertThat(recorder.putEntries.get()).isEqualTo(1); assertThat(recorder.putTargetLookups.get()).isZero();
        assertThat(recorder.putTarget).isNull(); assertThat(recorder.putInputs).isEmpty(); assertThat(recorder.putCategories).isEmpty();
        assertThat(recorder.writeLocal.get()).isZero(); assertThat(recorder.writeReferences.get()).isZero();
        assertThat(recorder.sql).noneMatch(event -> event.isDml() || event.sql().contains("tb_product") || event.sql().contains("tb_category"));
        assertThat(recorder.flushReturns.get()).isZero(); assertThat(recorder.returns.get()).isZero();
        assertThat(recorder.completions).containsExactly(TransactionSynchronization.STATUS_ROLLED_BACK);
        assertThat(recorder.commits.get()).isZero(); assertThat(recorder.rollbacks.get()).isEqualTo(1);
        assertThat(database.snapshot()).isEqualTo(before);
        assertThat(database.accountIds()).containsExactlyInAnyOrder(principal.accountId(), client.id());
        assertThat(database.roles(client.id())).containsExactly(UserRole.CLIENT);
        System.out.println("T051_DIRECT case=client-before-target-and-input boundaryValidInvoked=false targetLookups=0 references=0 dml=0 snapshotChecked=true");
    }

    private void assertRejected(AdminProductDatabaseProbe.Snapshot before) {
        AdminProductReferenceRaceIntegrationTest.noCallerTransaction();
        assertThat(database.snapshot()).isEqualTo(before);
        assertThat(recorder.boundaryPasses.get()).isZero();
        assertThat(recorder.saveAttempts.get()).isZero();
        assertThat(recorder.sql).noneMatch(event -> event.write() && event.isDml());
        assertThat(recorder.completions).containsExactly(TransactionSynchronization.STATUS_ROLLED_BACK);
        assertThat(recorder.rollbacks.get()).isEqualTo(1);
        assertThat(recorder.commits.get()).isZero();
        assertThat(recorder.returns.get()).isZero();
    }

    private ProductWriteRequestDTO valid(List<Long> ids) {
        return new ProductWriteRequestDTO(" T040 Fone ", " Descricao completa T040 ", "1.000", "https://example.invalid/t040.jpg", ids);
    }
}
