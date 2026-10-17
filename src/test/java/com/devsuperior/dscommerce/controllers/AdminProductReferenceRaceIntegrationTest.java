package com.devsuperior.dscommerce.controllers;

import com.devsuperior.dscommerce.DscommerceApplication;
import com.devsuperior.dscommerce.config.AdminProductTestConfig;
import com.devsuperior.dscommerce.dto.AdminProductDetailDTO;
import com.devsuperior.dscommerce.dto.ProductInputDiagnostic;
import com.devsuperior.dscommerce.dto.ProductWriteRequestDTO;
import com.devsuperior.dscommerce.entities.Category;
import com.devsuperior.dscommerce.entities.Product;
import com.devsuperior.dscommerce.entities.UserRole;
import com.devsuperior.dscommerce.entities.UserAccount;
import com.devsuperior.dscommerce.fixtures.AdminProductDatabaseProbe;
import com.devsuperior.dscommerce.fixtures.AdminProductTestFixture;
import com.devsuperior.dscommerce.repositories.ProductRepository;
import com.devsuperior.dscommerce.repositories.CategoryRepository;
import com.devsuperior.dscommerce.repositories.UserAccountRepository;
import com.devsuperior.dscommerce.services.AdminAccessService;
import com.devsuperior.dscommerce.services.ProductCategoryDiagnosticService;
import com.devsuperior.dscommerce.services.ProductInputValidationService;
import com.devsuperior.dscommerce.services.ProductMaintenanceService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.aopalliance.intercept.MethodInterceptor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.aop.framework.Advised;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.context.WebApplicationContext;

import javax.sql.DataSource;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/** T040 only: real boundary/write races and late FK classification, with deterministic test-only barriers. */
@SpringBootTest(classes = DscommerceApplication.class, properties = "spring.jpa.properties.hibernate.jdbc.batch_size=0")
@ActiveProfiles("test")
@ContextConfiguration(initializers = AdminProductTestConfig.class)
@Import(AdminProductReferenceRaceIntegrationTest.ProbeConfig.class)
public class AdminProductReferenceRaceIntegrationTest {
    @Autowired private WebApplicationContext context;
    @Autowired private ObjectMapper mapper;
    @Autowired private Recorder recorder;
    @PersistenceContext private EntityManager entityManager;
    private AdminProductDatabaseProbe database;
    private AdminProductTestFixture fixture;
    private AdminProductTestFixture.Account admin;
    private long a;
    private long b;
    private long legacyId;
    private String token;
    private MockMvc mvc;
    private ExecutorService worker;
    private Future<MvcResult> request;
    private boolean checkInstalled;

    @BeforeEach
    void prepareCommittedSeedFreeDataAndRealAuthentication() {
        noCallerTransaction();
        recorder.active = false;
        database = new AdminProductDatabaseProbe(context.getEnvironment().getRequiredProperty("spring.datasource.url"));
        assertThat(database.snapshot().products()).isEmpty();
        assertThat(database.snapshot().categories()).isEmpty();
        fixture = new AdminProductTestFixture(context);
        admin = fixture.createAccount("T040 Admin", "Disposable-T040", Set.of(UserRole.ADMIN));
        token = fixture.login(admin);
        var categories = fixture.prepareCategorySelection();
        a = categories.sameName().get(0).id();
        b = categories.sameName().get(1).id();
        var legacy = fixture.createProduct("T040 legado", "Legado", "imagem-legada", new BigDecimal("0.00"));
        legacyId = legacy.id();
        fixture.associate(legacy.id(), categories.unused().id());
        mvc = MockMvcBuilders.webAppContextSetup(context).addFilters(context.getBean(FilterChainProxy.class)).build();
        worker = Executors.newSingleThreadExecutor();
        checkInstalled = false;
        request = null;
    }

    @AfterEach
    void joinWorkerBeforeOwnedCleanup() throws Exception {
        recorder.gate.release.countDown();
        if (request != null) request.get(30, TimeUnit.SECONDS);
        worker.shutdown();
        assertThat(worker.awaitTermination(30, TimeUnit.SECONDS)).isTrue();
        recorder.active = false;
        noCallerTransaction();
        if (checkInstalled) sql("alter table tb_product drop constraint t040_internal_check");
        // Only this isolated test's request can introduce IDs not owned by its fixture.
        for (var row : database.snapshot().products()) {
            if (row.id() != legacyId) {
                sql("delete from tb_product_category where product_id = ?", row.id());
                sql("delete from tb_product where id = ?", row.id());
            }
        }
        fixture.close();
        assertThat(database.snapshot().products()).isEmpty();
        assertThat(database.snapshot().categories()).isEmpty();
        assertThat(database.snapshot().associations()).isEmpty();
        assertThat(database.accountIds()).isEmpty();
    }

    @Test
    void shouldRejectCategoryLostAfterBoundaryBeforeWritingAndKeepTheConcurrentDeletion() throws Exception {
        start(Point.BEFORE_MAINTENANCE);
        recorder.gate.awaitReached();
        assertBoundaryPassedBeforeWrite();
        assertThat(sql("delete from tb_category where id = ?", a)).isEqualTo(1);
        var afterConcurrentCommit = database.snapshot();
        recorder.gate.release.countDown();
        var result = finish();
        assertRejectionState(afterConcurrentCommit, 0);
        assertThat(recorder.writeReferences.get()).isEqualTo(1);
        assertThat(recorder.revalidatedPasses.get()).isZero();
        assertEnvelope(result, 400, "Invalid product maintenance data");
        assertThat(diagnoses(result)).containsExactly("categoryIds[0]/CATEGORY_NOT_FOUND", "categoryIds[1]/CATEGORY_NOT_FOUND");
        proof("boundary-category", result, "noDml=true concurrentDeletionRetained=true");
    }

    @Test
    void shouldRejectAdminRevokedAfterBoundaryBeforeAnyWriteReferenceLookup() throws Exception {
        start(Point.BEFORE_MAINTENANCE);
        recorder.gate.awaitReached();
        assertBoundaryPassedBeforeWrite();
        assertThat(sql("delete from tb_user_account_role where user_account_id = ? and role = 'ADMIN'", admin.id())).isEqualTo(1);
        var afterConcurrentCommit = database.snapshot();
        assertThat(database.roles(admin.id())).isEmpty();
        recorder.gate.release.countDown();
        var result = finish();
        proof("boundary-admin-observed", result, "revocationCommitted=" + database.roles(admin.id()).isEmpty()
                + " snapshotEqual=" + database.snapshot().equals(afterConcurrentCommit));
        var beforeWrite = recorder.accounts.stream().filter(read -> !read.write()).toList();
        var insideWrite = recorder.accounts.stream().filter(AccountRead::write).toList();
        boolean sameManagedAccount = !beforeWrite.isEmpty() && !insideWrite.isEmpty()
                && beforeWrite.getLast().instance() == insideWrite.getFirst().instance();
        System.out.printf("T040_ACCOUNT_CACHE databaseAdminAbsent=%s boundaryAndWriteSameEntity=%s writeEntityHasAdmin=%s writeAccountReads=%d%n",
                database.roles(admin.id()).isEmpty(), sameManagedAccount,
                insideWrite.stream().anyMatch(read -> read.roles().contains(UserRole.ADMIN)), insideWrite.size());
        assertRejectionState(afterConcurrentCommit, 0);
        assertThat(recorder.writeGuards.get()).isEqualTo(1);
        assertThat(recorder.writeLocal.get()).isZero();
        assertThat(recorder.writeReferences.get()).isZero();
        assertThat(recorder.sql.stream().filter(event -> event.write() && event.sql().contains("tb_category"))).isEmpty();
        assertThat(database.roles(admin.id())).isEmpty();
        assertEnvelope(result, 403, "Product maintenance not permitted");
        assertThat(diagnoses(result)).isEmpty();
        proof("boundary-admin", result, "noDml=true noWriteCategoryLookup=true revocationRetained=true");
    }

    @Test
    void shouldClassifyProvenLateMissingCategoryFkAs400AfterRealInsertAndRollback() throws Exception {
        String categoryFk = categoryFkName();
        start(Point.AFTER_REFERENCE_CHECK);
        recorder.gate.awaitReached();
        assertThat(recorder.boundaryPasses.get()).isEqualTo(1);
        assertThat(recorder.revalidatedPasses.get()).isEqualTo(1);
        assertThat(recorder.succeededDml()).isEmpty();
        assertThat(sql("delete from tb_category where id = ?", a)).isEqualTo(1);
        var afterConcurrentCommit = database.snapshot();
        recorder.gate.release.countDown();
        var result = finish();
        assertRejectionState(afterConcurrentCommit, 1);
        assertThat(recorder.flushReturns.get()).isZero();
        assertThat(recorder.succeededDml()).anyMatch(event -> event.sql().startsWith("insert into tb_product ("));
        var failedPair = recorder.sql.stream().filter(event -> "23506".equals(event.state())).findFirst().orElseThrow();
        assertThat(failedPair.sql()).startsWith("insert into tb_product_category ");
        assertThat(((Number) failedPair.columnBinding("category_id")).longValue()).isEqualTo(a);
        assertThat(database.snapshot().categories()).noneMatch(row -> row.id() == a);
        assertThat(categoryFk).isNotBlank();
        assertThat(result.getResolvedException()).isNotNull();
        // Record atomicity/cause before the contract assertion, so a real RED cannot hide the reached proof.
        proof("late-category-fk", result, "sqlState=23506 knownFk=" + categoryFk
                + " initialValidationPassed=true revalidationPassed=true productInsertSucceeded=true flushFailed=true rollbackVisible=true");
        assertEnvelope(result, 400, "Invalid product maintenance data");
        assertThat(diagnoses(result)).containsExactly("categoryIds/CATEGORY_NOT_FOUND");
    }

    @Test
    void shouldKeepUnclassifiedRealCheckFailureAsSafe500WithRollback() throws Exception {
        sql("alter table tb_product add constraint t040_internal_check check (price <> 1.00)");
        checkInstalled = true;
        var before = database.snapshot();
        start(Point.NONE);
        var result = finish();
        assertRejectionState(before, 1);
        assertThat(recorder.revalidatedPasses.get()).isEqualTo(1);
        assertThat(recorder.sql).anyMatch(event -> "23513".equals(event.state()));
        assertThat(recorder.succeededDml()).isEmpty();
        assertThat(recorder.flushReturns.get()).isZero();
        assertEnvelope(result, 500, "Product maintenance could not be completed");
        assertThat(diagnoses(result)).isEmpty();
        proof("internal-check", result, "sqlState=23513 knownCategoryFk=false failedInsert=true noSuccessfulDml=true");
    }

    @Test
    void shouldExposeFlushBeforeCommitAndReturnSuccessOnlyAfterVisibleCommit() throws Exception {
        var before = database.snapshot();
        start(Point.AFTER_FLUSH);
        recorder.gate.awaitReached();
        assertThat(recorder.revalidatedPasses.get()).isEqualTo(1);
        assertThat(recorder.flushReturns.get()).isEqualTo(1);
        assertThat(recorder.succeededDml()).hasSize(3);
        assertThat(recorder.completions).isEmpty();
        assertThat(recorder.commits.get()).isZero();
        assertThat(database.snapshot()).as("successful flush is still invisible on another connection").isEqualTo(before);
        long id = recorder.flushedId;
        assertThat(id).isPositive();
        recorder.gate.release.countDown();
        var result = finish();
        assertThat(result.getResponse().getStatus()).isEqualTo(201);
        assertThat(recorder.completions).containsExactly(TransactionSynchronization.STATUS_COMMITTED);
        assertThat(recorder.commits.get()).isEqualTo(1);
        assertThat(recorder.rollbacks.get()).isZero();
        assertThat(recorder.returns.get()).isEqualTo(1);
        var after = database.snapshot();
        assertThat(after.products()).contains(new AdminProductDatabaseProbe.ProductRow(id, "T040 Fone",
                "Descricao completa T040", "https://example.invalid/t040.jpg", new BigDecimal("1.00")));
        assertThat(after.products()).hasSize(before.products().size() + 1).containsAll(before.products());
        assertThat(after.associations()).containsAll(before.associations()).hasSize(before.associations().size() + 2)
                .contains(new AdminProductTestFixture.ProductCategory(id, a), new AdminProductTestFixture.ProductCategory(id, b));
        assertThat(after.categories()).isEqualTo(before.categories());
        assertThat(after.orders()).isEqualTo(before.orders());
        assertThat(after.items()).isEqualTo(before.items());
        assertThat(json(result).path("id").asLong()).isEqualTo(id);
        assertThat(java.net.URI.create(result.getResponse().getHeader(HttpHeaders.LOCATION)).getPath())
                .isEqualTo("/api/admin/products/" + id);
        proof("flush-commit-control", result, "flushInvisibleBeforeCommit=true committedProductVisible=true uniquePairs=2");
    }

    private void start(Point point) {
        recorder.start(point);
        request = worker.submit(() -> {
            noCallerTransaction();
            var result = mvc.perform(post("/api/admin/products").contextPath("/api").servletPath("/admin/products")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON).content("{\"name\":\" T040 Fone \",\"description\":\" Descricao completa T040 \","
                            + "\"price\":\"1.000\",\"image\":\"https://example.invalid/t040.jpg\",\"categoryIds\":[" + a + "," + a + "," + b + "]}")).andReturn();
            noCallerTransaction();
            return result;
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"category", "removed-account", "revoked-admin"})
    void t051RechecksChangesCommittedAfterPutBoundaryBeforeMaintenance(String change) throws Exception {
        recorder.putManaged = entityManager::contains;
        startPut(Point.BEFORE_MAINTENANCE, putBody("valid"));
        recorder.gate.awaitReached();
        assertBoundaryPassedBeforeWrite();
        assertThat(recorder.putEntries.get()).isEqualTo(1); // outer advice reached; business body still blocked
        assertThat(recorder.putTargetLookups.get()).isZero();
        assertThat(recorder.sql).noneMatch(SqlEvent::isDml);
        if (change.equals("category")) {
            assertThat(sql("delete from tb_category where id = ?", a)).isEqualTo(1);
        } else {
            assertThat(sql("delete from tb_user_account_role where user_account_id = ? and role = 'ADMIN'", admin.id())).isEqualTo(1);
            if (change.equals("removed-account")) assertThat(sql("delete from tb_user_account where id = ?", admin.id())).isEqualTo(1);
        }
        var afterConcurrentCommit = database.snapshot();
        var expectedAccounts = database.accountIds();
        var expectedRoles = database.roles(admin.id());
        recorder.gate.release.countDown();
        var result = finish();
        assertThat(database.snapshot()).isEqualTo(afterConcurrentCommit);
        assertThat(database.accountIds()).isEqualTo(expectedAccounts);
        assertThat(database.roles(admin.id())).isEqualTo(expectedRoles);
        assertPutNoDmlAndMemory();
        assertThat(recorder.completions).containsExactly(TransactionSynchronization.STATUS_ROLLED_BACK);
        assertThat(recorder.rollbacks.get()).isEqualTo(1);
        assertThat(recorder.commits.get()).isZero();
        assertThat(recorder.returns.get()).isZero();
        if (change.equals("category")) {
            assertThat(recorder.putTargetLookups.get()).isEqualTo(1);
            assertThat(recorder.putCalls).startsWith("guard", "effective-roles", "target", "local", "write-diagnose");
            assertThat(recorder.writeReferences.get()).isEqualTo(1);
            assertThat(recorder.revalidatedPasses.get()).isZero();
            assertThat(recorder.putTargetAfter).isEqualTo(recorder.putTargetBefore);
            assertPutEnvelope(result, 400, "Invalid product maintenance data");
            assertThat(diagnoses(result)).containsExactly("categoryIds[0]/CATEGORY_NOT_FOUND", "categoryIds[1]/CATEGORY_NOT_FOUND");
        } else {
            if (change.equals("removed-account")) {
                assertThat(recorder.putCalls).containsExactly("guard");
                assertThat(recorder.failure).isInstanceOf(org.springframework.security.authentication.BadCredentialsException.class);
            } else {
                assertThat(recorder.putCalls).containsExactly("guard", "effective-roles");
                assertThat(recorder.failure).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
            }
            assertThat(recorder.putTargetLookups.get()).isZero();
            assertThat(recorder.putTarget).isNull();
            assertThat(recorder.writeLocal.get()).isZero();
            assertThat(recorder.writeReferences.get()).isZero();
            assertThat(recorder.sql).noneMatch(event -> event.write() && (event.sql().contains("tb_product") || event.sql().contains("tb_category")));
            assertPutEnvelope(result, change.equals("removed-account") ? 401 : 403,
                    change.equals("removed-account") ? "Authentication required or invalid" : "Product maintenance not permitted");
            assertThat(diagnoses(result)).isEmpty();
        }
        putProof(change, result);
    }

    @ParameterizedTest
    @ValueSource(strings = {"aggregate", "mixed"})
    void t051RejectsMixedAndRepeatedMissingReferencesInPutBoundaryWithoutMaintenance(String kind) throws Exception {
        var before = database.snapshot();
        recorder.putManaged = entityManager::contains;
        startPut(Point.NONE, putBody(kind));
        var result = finish();
        assertThat(database.snapshot()).isEqualTo(before);
        assertThat(database.accountIds()).containsExactly(admin.id());
        assertThat(database.roles(admin.id())).containsExactly(UserRole.ADMIN);
        assertThat(recorder.putEntries.get()).isZero();
        assertThat(recorder.putTargetLookups.get()).isZero();
        assertThat(recorder.writeGuards.get()).isZero();
        assertThat(recorder.writeLocal.get()).isZero();
        assertThat(recorder.writeReferences.get()).isZero();
        assertThat(recorder.completions).isEmpty();
        assertThat(recorder.commits.get()).isZero();
        assertThat(recorder.rollbacks.get()).isZero();
        assertThat(recorder.putInputs).hasSize(2); // local policy + reference diagnosis during @Valid
        assertPutNoDmlAndMemory();
        assertPutEnvelope(result, 400, "Invalid product maintenance data");
        assertThat(diagnoses(result)).containsExactlyElementsOf(kind.equals("aggregate")
                ? List.of("name/INVALID_LENGTH", "description/INVALID_LENGTH", "price/NOT_POSITIVE", "image/INVALID_IMAGE_URL",
                    "categoryIds[1]/CATEGORY_NOT_FOUND", "categoryIds[2]/CATEGORY_NOT_FOUND")
                : List.of("categoryIds[1]/INVALID_TYPE", "categoryIds[2]/CATEGORY_NOT_FOUND", "categoryIds[3]/CATEGORY_NOT_FOUND"));
        putProof(kind, result);
    }

    private String putBody(String kind) {
        String fields = kind.equals("aggregate")
                ? "\"name\":\" ab \",\"description\":\"short\",\"price\":\"0\",\"image\":\"/relative\""
                : "\"name\":\" T051 Fone \",\"description\":\" Descricao completa T051 \",\"price\":\"1.000\",\"image\":\"https://example.invalid/t051.jpg\"";
        String ids = switch (kind) {
            case "aggregate" -> a + "," + Long.MAX_VALUE + "," + Long.MAX_VALUE + "," + b;
            case "mixed" -> a + ",true," + Long.MAX_VALUE + "," + Long.MAX_VALUE + "," + b;
            default -> a + "," + a + "," + b;
        };
        return "{" + fields + ",\"categoryIds\":[" + ids + "]}";
    }

    private void startPut(Point point, String body) {
        recorder.startPut(point);
        request = worker.submit(() -> {
            noCallerTransaction();
            String route = "/admin/products/" + legacyId;
            var result = mvc.perform(put("/api" + route).contextPath("/api").servletPath(route)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON).content(body)).andReturn();
            noCallerTransaction();
            return result;
        });
    }

    private void assertPutNoDmlAndMemory() {
        noCallerTransaction();
        assertThat(recorder.sql).noneMatch(SqlEvent::isDml); // all observed JDBC, including read-only boundary
        assertThat(recorder.saveAttempts.get()).isZero();
        assertThat(recorder.flushReturns.get()).isZero();
        recorder.assertPutMemory();
        assertThat(recorder.putCategories).isNotEmpty();
    }

    private void assertPutEnvelope(MvcResult result, int status, String message) throws Exception {
        assertThat(result.getResponse().getStatus()).isEqualTo(status);
        assertThat(result.getResponse().getHeader(HttpHeaders.LOCATION)).isNull();
        assertThat(result.getResponse().getHeader(HttpHeaders.WWW_AUTHENTICATE)).isEqualTo(status == 401 ? "Bearer" : null);
        var body = json(result);
        var fields = new ArrayList<String>(); body.fieldNames().forEachRemaining(fields::add);
        assertThat(fields).containsExactlyInAnyOrder("status", "error", "message", "path", "errors");
        assertThat(body.path("status").asInt()).isEqualTo(status);
        assertThat(body.path("error").asText()).isEqualTo(org.springframework.http.HttpStatus.valueOf(status).getReasonPhrase());
        assertThat(body.path("message").asText()).isEqualTo(message);
        assertThat(body.path("path").asText()).isEqualTo("/api/admin/products/" + legacyId);
        assertThat(body.path("errors").isArray()).isTrue();
        assertThat(result.getResponse().getContentAsString()).doesNotContain(token, "Disposable-T040", "select ", "SQLException");
    }

    private void putProof(String kind, MvcResult result) {
        System.out.printf("T051_HTTP case=%s actual=%d proxyEntries=%d writeGuards=%d targetLookups=%d writeReferences=%d dml=%d flushes=%d commits=%d rollbacks=%d inputObservations=%d categoryObservations=%d memoryChecked=true snapshotChecked=true%n",
                kind, result.getResponse().getStatus(), recorder.putEntries.get(), recorder.writeGuards.get(), recorder.putTargetLookups.get(),
                recorder.writeReferences.get(), recorder.sql.stream().filter(SqlEvent::isDml).count(), recorder.flushReturns.get(), recorder.commits.get(),
                recorder.rollbacks.get(), recorder.putInputs.size(), recorder.putCategories.size());
    }

    private MvcResult finish() throws Exception { return request.get(30, TimeUnit.SECONDS); }

    private void assertBoundaryPassedBeforeWrite() {
        assertThat(recorder.boundaryPasses.get()).isEqualTo(1);
        assertThat(recorder.writeGuards.get()).isZero();
        assertThat(recorder.writeReferences.get()).isZero();
        assertThat(recorder.succeededDml()).isEmpty();
    }

    private void assertRejectionState(AdminProductDatabaseProbe.Snapshot expected, int saves) {
        assertThat(database.snapshot()).isEqualTo(expected);
        assertThat(database.accountIds()).containsExactly(admin.id());
        assertThat(recorder.saveAttempts.get()).isEqualTo(saves);
        assertThat(recorder.completions).containsExactly(TransactionSynchronization.STATUS_ROLLED_BACK);
        assertThat(recorder.commits.get()).isZero();
        assertThat(recorder.rollbacks.get()).isEqualTo(1);
        assertThat(recorder.returns.get()).isZero();
        if (saves == 0) assertThat(recorder.sql).noneMatch(event -> event.write() && event.isDml());
    }

    private void assertEnvelope(MvcResult result, int status, String message) throws Exception {
        assertThat(result.getResponse().getHeader(HttpHeaders.LOCATION)).isNull();
        assertThat(result.getResponse().getHeader(HttpHeaders.WWW_AUTHENTICATE)).isNull();
        var body = json(result);
        assertThat(result.getResponse().getStatus()).isEqualTo(status);
        assertThat(body.size()).isEqualTo(5);
        assertThat(body.path("status").asInt()).isEqualTo(status);
        assertThat(body.path("error").asText()).isEqualTo(org.springframework.http.HttpStatus.valueOf(status).getReasonPhrase());
        assertThat(body.path("message").asText()).isEqualTo(message);
        assertThat(body.path("path").asText()).isEqualTo("/api/admin/products");
        assertThat(result.getResponse().getContentAsString()).doesNotContain(token, "Disposable-T040", "23506", "23513", "insert into", "SQLException");
    }

    private JsonNode json(MvcResult result) throws Exception {
        assertThat(result.getResponse().getContentType()).startsWith(MediaType.APPLICATION_JSON_VALUE);
        return mapper.readTree(result.getResponse().getContentAsByteArray());
    }

    private List<String> diagnoses(MvcResult result) throws Exception {
        var errors = new ArrayList<String>();
        json(result).path("errors").forEach(error -> {
            assertThat(error.size()).isEqualTo(3);
            assertThat(error.path("message").asText()).isNotBlank();
            errors.add(error.path("field").asText() + "/" + error.path("code").asText());
        });
        return errors;
    }

    private String categoryFkName() throws Exception {
        try (var connection = database.openConnection();
             var keys = connection.getMetaData().getImportedKeys(null, "PUBLIC", "TB_PRODUCT_CATEGORY")) {
            while (keys.next()) {
                if (keys.getString("FKCOLUMN_NAME").equals("CATEGORY_ID")) {
                    assertThat(keys.getString("PKTABLE_NAME")).isEqualTo("TB_CATEGORY");
                    assertThat(keys.getString("PKCOLUMN_NAME")).isEqualTo("ID");
                    return keys.getString("FK_NAME");
                }
            }
        }
        throw new IllegalStateException("T040 category FK metadata missing");
    }

    private int sql(String statement, Object... values) throws Exception {
        return executeIndependent(database, statement, values);
    }

    public static int executeIndependent(AdminProductDatabaseProbe database, String sql, Object... values) throws Exception {
        try (var connection = database.openConnection()) {
            connection.setAutoCommit(false);
            try (var statement = connection.prepareStatement(sql)) {
                for (int index = 0; index < values.length; index++) statement.setObject(index + 1, values[index]);
                int rows = statement.executeUpdate();
                connection.commit();
                return rows;
            } catch (Exception failure) { connection.rollback(); throw failure; }
        }
    }

    private void proof(String scenario, MvcResult result, String details) {
        System.out.printf("T040_HTTP case=%s actual=%d initialPasses=%d revalidationPasses=%d saves=%d flushReturns=%d commits=%d rollbacks=%d snapshotChecked=true %s%n",
                scenario, result.getResponse().getStatus(), recorder.boundaryPasses.get(), recorder.revalidatedPasses.get(),
                recorder.saveAttempts.get(), recorder.flushReturns.get(), recorder.commits.get(), recorder.rollbacks.get(), details);
    }

    public static void noCallerTransaction() {
        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
        assertThat(TransactionSynchronizationManager.isSynchronizationActive()).isFalse();
    }

    public enum Point { NONE, BEFORE_MAINTENANCE, AFTER_REFERENCE_CHECK, AFTER_FLUSH }

    public static final class Gate {
        final Point point;
        final CountDownLatch reached = new CountDownLatch(1);
        final CountDownLatch release = new CountDownLatch(1);
        final AtomicBoolean once = new AtomicBoolean();
        Gate(Point point) { this.point = point; }
        void hit(Point candidate) throws InterruptedException {
            if (point == candidate && once.compareAndSet(false, true)) {
                reached.countDown();
                if (!release.await(30, TimeUnit.SECONDS)) throw new IllegalStateException("T040 barrier not released");
            }
        }
        void awaitReached() throws InterruptedException {
            assertThat(reached.await(30, TimeUnit.SECONDS)).as("real request must reach the selected phase").isTrue();
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    public static class ProbeConfig {
        @Bean public static Recorder t040Recorder() { return new Recorder(); }
    }

    /** Test-only JDBC and proxy observation; all real invocations and exceptions are delegated unchanged. */
    public static final class Recorder implements BeanPostProcessor {
        public volatile boolean active;
        public volatile Gate gate = new Gate(Point.NONE);
        public volatile long flushedId;
        public volatile Throwable failure;
        public final AtomicInteger boundaryPasses = new AtomicInteger();
        public final AtomicInteger writeGuards = new AtomicInteger();
        public final AtomicInteger writeLocal = new AtomicInteger();
        public final AtomicInteger writeReferences = new AtomicInteger();
        public final AtomicInteger revalidatedPasses = new AtomicInteger();
        public final AtomicInteger saveAttempts = new AtomicInteger();
        public final AtomicInteger flushReturns = new AtomicInteger();
        public final AtomicInteger commits = new AtomicInteger();
        public final AtomicInteger rollbacks = new AtomicInteger();
        public final AtomicInteger returns = new AtomicInteger();
        public final List<Integer> completions = new CopyOnWriteArrayList<>();
        public final List<SqlEvent> sql = new CopyOnWriteArrayList<>();
        public final List<AccountRead> accounts = new CopyOnWriteArrayList<>();
        public volatile boolean putEnabled;
        public Predicate<Object> putManaged = ignored -> false;
        public final AtomicInteger putEntries = new AtomicInteger();
        public final AtomicInteger putTargetLookups = new AtomicInteger();
        public final List<String> putCalls = new CopyOnWriteArrayList<>();
        public final List<InputObservation> putInputs = new CopyOnWriteArrayList<>();
        public final List<CategoryRead> putCategories = new CopyOnWriteArrayList<>();
        public volatile Product putTarget;
        public volatile ProductState putTargetBefore;
        public volatile ProductState putTargetAfterDiagnosis;
        public volatile ProductState putTargetAfter;
        private Object putCollection;
        private final ThreadLocal<Boolean> write = ThreadLocal.withInitial(() -> false);
        private final ThreadLocal<Boolean> registered = ThreadLocal.withInitial(() -> false);

        public void start(Point point) {
            putEnabled = false;
            gate = new Gate(point);
            for (var counter : List.of(boundaryPasses, writeGuards, writeLocal, writeReferences, revalidatedPasses,
                    saveAttempts, flushReturns, commits, rollbacks, returns)) counter.set(0);
            completions.clear(); sql.clear(); accounts.clear(); flushedId = 0; failure = null; active = true;
        }

        public void startPut(Point point) {
            start(point);
            putEntries.set(0); putTargetLookups.set(0);
            putCalls.clear(); putInputs.clear(); putCategories.clear();
            putTarget = null; putTargetBefore = putTargetAfterDiagnosis = putTargetAfter = null; putCollection = null;
            putEnabled = true;
        }

        public void assertPutMemory() {
            for (var input : putInputs) {
                assertThat(input.after()).as(input.phase()).isEqualTo(input.before());
                assertThat(input.sameMetadata()).isTrue();
            }
            for (var category : putCategories) {
                assertThat(category.managedAtRead()).isTrue();
                assertThat(CategoryState.capture(category.instance())).isEqualTo(category.before());
            }
            if (putTargetBefore != null) {
                assertThat(putTargetAfterDiagnosis).isEqualTo(putTargetBefore);
                assertThat(putTarget.getCategories()).isSameAs(putCollection);
            }
        }

        public List<SqlEvent> succeededDml() {
            return sql.stream().filter(event -> event.succeeded() && event.write() && event.isDml()).toList();
        }

        @Override public Object postProcessAfterInitialization(Object bean, String name) {
            if (bean instanceof DataSource source) {
                return Proxy.newProxyInstance(DataSource.class.getClassLoader(), new Class<?>[]{DataSource.class}, (proxy, method, args) -> {
                    var value = delegate(source, method, args);
                    return value instanceof Connection connection ? connection(connection) : value;
                });
            }
            boolean maintenance = bean instanceof ProductMaintenanceService;
            boolean guard = bean instanceof AdminAccessService;
            boolean local = bean instanceof ProductInputValidationService;
            boolean reference = bean instanceof ProductCategoryDiagnosticService;
            boolean repository = bean instanceof ProductRepository;
            boolean accountRepository = bean instanceof UserAccountRepository;
            boolean categoryRepository = bean instanceof CategoryRepository;
            if (!(maintenance || guard || local || reference || repository || accountRepository || categoryRepository)) return bean;
            MethodInterceptor advice = invocation -> {
                String method = invocation.getMethod().getName();
                if (!active) return invocation.proceed();
                if (putEnabled && maintenance && method.equals("replaceProduct")) {
                    noCallerTransaction();
                    putEntries.incrementAndGet();
                    write.set(true);
                    try {
                        gate.hit(Point.BEFORE_MAINTENANCE);
                        var result = invocation.proceed();
                        noCallerTransaction();
                        assertThat(result).isInstanceOf(AdminProductDetailDTO.class);
                        returns.incrementAndGet();
                        return result;
                    } catch (Throwable cause) { failure = cause; throw cause; }
                    finally {
                        if (putTarget != null) putTargetAfter = ProductState.capture(putTarget);
                        write.remove(); registered.remove();
                    }
                }
                if (putEnabled && accountRepository && method.equals("findEffectiveAccountRolesById") && write.get()) {
                    putCalls.add("effective-roles");
                }
                if (putEnabled && repository && method.equals("findAdminByIdForUpdate")) {
                    putTargetLookups.incrementAndGet(); putCalls.add("target");
                    var result = (java.util.Optional<?>) invocation.proceed();
                    result.ifPresent(value -> {
                        putTarget = (Product) value;
                        assertThat(putManaged.test(putTarget)).isTrue();
                        // Test-only initialization allows a memory comparison before validation/mutation.
                        putTargetBefore = ProductState.capture(putTarget);
                        putCollection = putTarget.getCategories();
                        putTargetAfterDiagnosis = putTargetBefore; // null-input paths have no diagnosis
                    });
                    return result;
                }
                if (putEnabled && categoryRepository && method.equals("findAllByIdIn")) {
                    var result = (List<?>) invocation.proceed();
                    result.forEach(value -> {
                        var category = (Category) value;
                        putCategories.add(new CategoryRead(category, CategoryState.capture(category), putManaged.test(category)));
                    });
                    return result;
                }
                if (putEnabled && repository && method.equals("flush") && write.get()) {
                    var result = invocation.proceed(); flushReturns.incrementAndGet(); return result;
                }
                if (accountRepository && method.equals("findByIdWithRoles")) {
                    var result = (java.util.Optional<?>) invocation.proceed();
                    result.ifPresent(value -> {
                        var account = (UserAccount) value;
                        accounts.add(new AccountRead(write.get(), account, Set.copyOf(account.getRoles())));
                    });
                    return result;
                }
                if (maintenance && method.equals("createProduct")) {
                    noCallerTransaction();
                    write.set(true);
                    try {
                        gate.hit(Point.BEFORE_MAINTENANCE);
                        var result = invocation.proceed();
                        noCallerTransaction();
                        assertThat(result).isInstanceOf(AdminProductDetailDTO.class);
                        returns.incrementAndGet();
                        return result;
                    } catch (Throwable cause) { failure = cause; throw cause; }
                    finally { write.remove(); registered.remove(); }
                }
                if (guard && method.equals("requireAdmin") && write.get()) {
                    if (putEnabled) putCalls.add("guard");
                    assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
                    assertThat(TransactionSynchronizationManager.isCurrentTransactionReadOnly()).isFalse();
                    writeGuards.incrementAndGet();
                    if (!registered.get()) {
                        registered.set(true);
                        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                            @Override public void afterCompletion(int status) { completions.add(status); }
                        });
                    }
                }
                if (local && method.equals("validate") && write.get()) writeLocal.incrementAndGet();
                if (putEnabled && local && method.equals("validate")) {
                    if (write.get()) putCalls.add("local");
                    var input = (ProductWriteRequestDTO) invocation.getArguments()[0];
                    var before = InputState.capture(input); var metadata = input.diagnostic();
                    try { return invocation.proceed(); }
                    finally { putInputs.add(new InputObservation("local", before, InputState.capture(input), input.diagnostic() == metadata)); }
                }
                if (reference && method.equals("diagnose")) {
                    boolean writing = write.get();
                    if (writing) writeReferences.incrementAndGet();
                    var input = putEnabled ? (ProductWriteRequestDTO) invocation.getArguments()[0] : null;
                    var before = input == null ? null : InputState.capture(input);
                    var metadata = input == null ? null : input.diagnostic();
                    if (putEnabled && writing) putCalls.add("write-diagnose");
                    var result = (ProductInputDiagnostic) invocation.proceed();
                    if (putEnabled) {
                        putInputs.add(new InputObservation("diagnose", before, InputState.capture(input), input.diagnostic() == metadata));
                        if (putTarget != null) putTargetAfterDiagnosis = ProductState.capture(putTarget);
                    }
                    if (result.errors().isEmpty()) {
                        if (writing) revalidatedPasses.incrementAndGet(); else boundaryPasses.incrementAndGet();
                    }
                    return result;
                }
                if (repository && method.equals("saveAndFlush") && write.get()) {
                    saveAttempts.incrementAndGet();
                    gate.hit(Point.AFTER_REFERENCE_CHECK);
                    var saved = (Product) invocation.proceed();
                    flushedId = saved.getId();
                    flushReturns.incrementAndGet();
                    gate.hit(Point.AFTER_FLUSH);
                    return saved;
                }
                return invocation.proceed();
            };
            if (bean instanceof Advised advised) { advised.addAdvice(0, advice); return bean; }
            var proxy = new ProxyFactory(bean); proxy.setProxyTargetClass(true); proxy.addAdvice(advice); return proxy.getProxy();
        }

        private Connection connection(Connection target) {
            return (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(), new Class<?>[]{Connection.class}, (proxy, method, args) -> {
                var value = delegate(target, method, args);
                if (active && write.get() && method.getName().equals("commit")) commits.incrementAndGet();
                if (active && write.get() && method.getName().equals("rollback")) rollbacks.incrementAndGet();
                if (value instanceof Statement statement) {
                    String prepared = args != null && args.length > 0 && args[0] instanceof String text ? text : null;
                    return statement(statement, prepared);
                }
                return value;
            });
        }

        private Statement statement(Statement target, String prepared) {
            Class<?> type = target instanceof PreparedStatement ? PreparedStatement.class : Statement.class;
            var bindings = new HashMap<Integer, Object>();
            return (Statement) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (proxy, method, args) -> {
                if (prepared != null && method.getName().startsWith("set") && args != null && args.length >= 2 && args[0] instanceof Integer index) {
                    bindings.put(index, method.getName().equals("setNull") ? null : args[1]);
                }
                String query = prepared != null ? prepared : args != null && args.length > 0 && args[0] instanceof String text ? text : null;
                boolean observed = active && query != null && method.getName().startsWith("execute");
                try {
                    var value = delegate(target, method, args);
                    if (observed) sql.add(new SqlEvent(normalized(query), true, write.get(), null,
                            Collections.unmodifiableMap(new HashMap<>(bindings))));
                    return value;
                } catch (SQLException cause) {
                    if (observed) sql.add(new SqlEvent(normalized(query), false, write.get(), cause.getSQLState(),
                            Collections.unmodifiableMap(new HashMap<>(bindings))));
                    throw cause;
                }
            });
        }

        private static Object delegate(Object target, Method method, Object[] args) throws Throwable {
            try { return method.invoke(target, args); }
            catch (InvocationTargetException cause) { throw cause.getCause(); }
        }

        private static String normalized(String text) { return text.replaceAll("(?s)/\\*.*?\\*/", " ").stripLeading().toLowerCase(Locale.ROOT); }
    }

    public record SqlEvent(String sql, boolean succeeded, boolean write, String state, Map<Integer, Object> bindings) {
        public boolean isDml() { return sql.startsWith("insert ") || sql.startsWith("update ") || sql.startsWith("delete ") || sql.startsWith("merge "); }
        public Object columnBinding(String column) {
            String[] columns = sql.substring(sql.indexOf('(') + 1, sql.indexOf(')')).split(",");
            for (int index = 0; index < columns.length; index++) if (columns[index].strip().equals(column)) return bindings.get(index + 1);
            throw new IllegalArgumentException("Expected insert column missing");
        }
    }

    public record AccountRead(boolean write, UserAccount instance, Set<UserRole> roles) { }

    public record InputState(String name, String description, String price, String image, List<Long> ids,
                             Map<ProductInputDiagnostic.Field, ProductInputDiagnostic.Representation> fields,
                             Map<Integer, ProductInputDiagnostic.Representation> elements,
                             List<com.devsuperior.dscommerce.dto.AdminFieldErrorDTO> errors) {
        public static InputState capture(ProductWriteRequestDTO input) {
            return new InputState(input.name(), input.description(), input.price(), input.image(),
                    input.categoryIds() == null ? null : Collections.unmodifiableList(new ArrayList<>(input.categoryIds())),
                    Map.copyOf(input.diagnostic().fields()), Map.copyOf(input.diagnostic().categoryElements()), List.copyOf(input.diagnostic().errors()));
        }
    }
    public record InputObservation(String phase, InputState before, InputState after, boolean sameMetadata) { }
    public record CategoryState(Long id, String name) {
        static CategoryState capture(Category category) { return new CategoryState(category.getId(), category.getName()); }
    }
    public record CategoryRead(Category instance, CategoryState before, boolean managedAtRead) { }
    public record ProductState(Long id, String name, String description, String image, BigDecimal price, List<CategoryState> categories) {
        static ProductState capture(Product product) {
            return new ProductState(product.getId(), product.getName(), product.getDescription(), product.getImage(), product.getPrice(),
                    product.getCategories().stream().map(CategoryState::capture).sorted(java.util.Comparator.comparing(CategoryState::id)).toList());
        }
    }
}
