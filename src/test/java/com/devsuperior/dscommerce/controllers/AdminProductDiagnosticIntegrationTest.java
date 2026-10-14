package com.devsuperior.dscommerce.controllers;

import com.devsuperior.dscommerce.DscommerceApplication;
import com.devsuperior.dscommerce.dto.AdminFieldErrorDTO;
import com.devsuperior.dscommerce.dto.ProductInputDiagnostic;
import com.devsuperior.dscommerce.dto.ProductWriteRequestDTO;
import com.devsuperior.dscommerce.entities.Category;
import com.devsuperior.dscommerce.entities.Product;
import com.devsuperior.dscommerce.security.OrderJwtAuthenticationConverter;
import com.devsuperior.dscommerce.services.ProductInputValidationService;
import com.devsuperior.dscommerce.validation.ValidProductInput;
import com.devsuperior.dscommerce.config.AdminProductTestConfig;
import com.devsuperior.dscommerce.entities.UserRole;
import com.devsuperior.dscommerce.fixtures.AdminProductDatabaseProbe;
import com.devsuperior.dscommerce.fixtures.AdminProductTestFixture;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.validation.Validator;
import org.hibernate.validator.engine.HibernateConstraintViolation;
import org.aopalliance.intercept.MethodInterceptor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.aop.framework.Advised;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.WebApplicationContext;

import javax.sql.DataSource;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/** T027 HTTP RED. DTO/isValid immutability is deliberately deferred to T039 after DTO creation. */
@SpringBootTest(classes = DscommerceApplication.class, properties = "spring.jpa.open-in-view=false")
@ActiveProfiles("test")
@ContextConfiguration(initializers = AdminProductTestConfig.class)
@Import(AdminProductDiagnosticIntegrationTest.ObservationConfig.class)
class AdminProductDiagnosticIntegrationTest {
    private static final String ROUTE = "/admin/products";
    @Autowired private WebApplicationContext context;
    @Autowired private ObjectMapper mapper;
    @Autowired private DiagnosticObservation observation;
    @Autowired private Validator validator;
    @PersistenceContext private EntityManager entityManager;
    private AdminProductDatabaseProbe database;
    private AdminProductTestFixture fixture;
    private MockMvc mvc;
    private String token;
    private long existingCategory;
    private long absentCategory;
    private final Set<Long> requestOwnedProducts = new HashSet<>();

    @BeforeEach
    void prepareCommittedReferencesAndRealAdminAuthentication() {
        assertNoCallerTransaction();
        database = new AdminProductDatabaseProbe(context.getEnvironment().getRequiredProperty("spring.datasource.url"));
        assertEmptyDatabase();
        fixture = new AdminProductTestFixture(context);
        var admin = fixture.createAccount("T027 Admin", "Disposable-T027", Set.of(UserRole.ADMIN));
        token = fixture.login(admin);
        assertThat(context.getBean(JwtDecoder.class).decode(token).getSubject()).isEqualTo(Long.toString(admin.id()));
        assertThat(database.roles(admin.id())).containsExactly(UserRole.ADMIN);
        existingCategory = fixture.createCategory("Categoria T027").id();
        absentCategory = Math.addExact(existingCategory, 1L);
        var legacy = fixture.createProduct("Produto preexistente", "Legado", "imagem-legada", new BigDecimal("0.00"));
        fixture.associate(legacy.id(), existingCategory);
        assertThat(database.snapshot().categories()).hasSize(1).anyMatch(row -> row.id() == existingCategory)
                .noneMatch(row -> row.id() == absentCategory);
        mvc = MockMvcBuilders.webAppContextSetup(context).addFilters(context.getBean(FilterChainProxy.class)).build();
        requestOwnedProducts.clear();
        observation.stop();
    }

    @AfterEach
    void cleanOnlyOwnedRowsAfterIndependentAssertions() throws Exception {
        observation.stop();
        assertNoCallerTransaction();
        if (!requestOwnedProducts.isEmpty()) {
            try (var connection = database.openConnection()) {
                connection.setAutoCommit(false);
                try (var pairs = connection.prepareStatement("delete from tb_product_category where product_id = ?");
                     var products = connection.prepareStatement("delete from tb_product where id = ?")) {
                    for (long id : requestOwnedProducts) {
                        pairs.setLong(1, id); pairs.executeUpdate();
                        products.setLong(1, id); products.executeUpdate();
                    }
                    connection.commit();
                } catch (Exception failure) { connection.rollback(); throw failure; }
            }
        }
        if (fixture != null) { fixture.close(); assertEmptyDatabase(); }
    }

    @Test
    void shouldCalibrateSqlAndMaintenanceEntryObservationWithoutCommittedChanges() throws Exception {
        var before = database.snapshot();
        observation.start();
        try (var connection = context.getBean(DataSource.class).getConnection()) {
            connection.setAutoCommit(false);
            try (var read = connection.prepareStatement("select id from tb_category where id = ?");
                 var write = connection.prepareStatement("insert into tb_category (name) values (?)")) {
                read.setLong(1, existingCategory);
                try (var rows = read.executeQuery()) { assertThat(rows.next()).isTrue(); }
                write.setString(1, "T027 rolled back calibration");
                assertThat(write.executeUpdate()).isEqualTo(1);
            } finally { connection.rollback(); }
            var proxy = new ProxyFactory(new MaintenanceCalibration());
            proxy.setProxyTargetClass(true);
            proxy.addAdvice(observation.maintenanceAdvice());
            ((MaintenanceCalibration) proxy.getProxy()).enter();
        } finally { observation.stop(); }
        assertThat(observation.sql()).anyMatch(DiagnosticObservation::isCategoryRead);
        assertThat(observation.sql()).anyMatch(DiagnosticObservation::isDml);
        assertThat(observation.maintenanceEntries()).isEqualTo(1);
        assertThat(database.snapshot()).isEqualTo(before);
        System.out.println("T027_CALIBRATION categoryReadObserved=true dmlObserved=true maintenanceAdviceObserved=true rollbackVisible=true");
    }

    static Stream<Arguments> diagnosticCases() {
        return Stream.of("", "/api").flatMap(externalContext -> Stream.of(false, true)
                .map(partiallyMalformed -> Arguments.of(externalContext, partiallyMalformed)));
    }

    @ParameterizedTest(name = "T027 context={0}, partiallyMalformed={1}")
    @MethodSource("diagnosticCases")
    void shouldDiagnoseAllFiveFieldsAndEveryLegibleReferenceBeforeMaintenance(
            String externalContext, boolean partiallyMalformed) throws Exception {
        // The malformed string cannot hide the positive, JDBC-proven absent ID or the existing ID.
        String ids = partiallyMalformed
                ? "[\"invalid-id\"," + existingCategory + "," + absentCategory + "," + absentCategory + "]"
                : "[" + absentCategory + "]";
        String body = "{\"name\":\"ab\",\"description\":\"123456789\",\"price\":\"0\","
                + "\"image\":\"/relative-t027.jpg\",\"categoryIds\":" + ids + "}";
        var before = database.snapshot();
        var accounts = database.accountIds();
        assertNoCallerTransaction();
        observation.start();
        org.springframework.test.web.servlet.MvcResult result;
        AdminProductDatabaseProbe.Snapshot after;
        try {
            result = mvc.perform(post(externalContext + ROUTE).contextPath(externalContext).servletPath(ROUTE)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON).content(body)).andReturn();
        } finally {
            observation.stop();
            after = database.snapshot();
            after.products().stream().filter(row -> before.products().stream().noneMatch(old -> old.id() == row.id()))
                    .forEach(row -> requestOwnedProducts.add(row.id()));
        }
        assertNoCallerTransaction();
        System.out.printf("T027_HTTP context=%s partiallyMalformed=%s expected=400 actual=%d cause=%s snapshotEqual=%s jdbcDml=%d categoryReads=%d maintenanceEntries=%d maintenanceBeans=%d%n",
                externalContext, partiallyMalformed, result.getResponse().getStatus(),
                result.getResolvedException() == null ? "NONE" : result.getResolvedException().getClass().getSimpleName(),
                after.equals(before), observation.sql().stream().filter(DiagnosticObservation::isDml).count(),
                observation.sql().stream().filter(DiagnosticObservation::isCategoryRead).count(),
                observation.maintenanceEntries(), observation.maintenanceBeans());
        assertThat(after).as("independent five-table snapshot before cleanup").isEqualTo(before);
        assertThat(database.accountIds()).isEqualTo(accounts);
        assertThat(database.roles(accounts.getFirst())).containsExactly(UserRole.ADMIN);
        // Gate IV permits diagnostic/guard reads in services; prohibit writes and maintenance entry only.
        assertThat(observation.sql()).as("SQL executed through the application DataSource").isNotEmpty();
        assertThat(observation.sql()).noneMatch(DiagnosticObservation::isDml);
        assertThat(observation.maintenanceEntries()).isZero();
        assertThat(result.getResponse().getStatus()).as("complete rejected diagnosis requires HTTP 400").isEqualTo(400);

        // These assertions remain blocked by the missing POST during T027 RED, and must pass in T038.
        assertThat(observation.maintenanceBeans()).as("observe the real maintenance bean once implemented").isPositive();
        assertThat(observation.sql()).as("local invalidity must not skip legible reference lookup")
                .anyMatch(DiagnosticObservation::isCategoryRead);
        assertThat(result.getResponse().getContentType()).startsWith(MediaType.APPLICATION_JSON_VALUE);
        var actual = mapper.readTree(result.getResponse().getContentAsByteArray());
        var envelope = new HashSet<String>(); actual.fieldNames().forEachRemaining(envelope::add);
        assertThat(envelope).containsExactlyInAnyOrder("status", "error", "message", "path", "errors");
        assertThat(actual.get("status").asInt()).isEqualTo(400);
        assertThat(actual.get("error").asText()).isEqualTo("Bad Request");
        assertThat(actual.get("message").asText()).isEqualTo("Invalid product maintenance data");
        assertThat(actual.get("path").asText()).isEqualTo(externalContext + ROUTE);
        assertThat(actual.get("errors").isArray()).isTrue();
        var errors = new ArrayList<Diagnosis>();
        var triples = new HashSet<List<String>>();
        actual.get("errors").forEach(error -> {
            var fields = new HashSet<String>(); error.fieldNames().forEachRemaining(fields::add);
            assertThat(fields).containsExactlyInAnyOrder("field", "code", "message");
            assertThat(error.get("field").isTextual()).isTrue();
            assertThat(error.get("code").isTextual()).isTrue();
            assertThat(error.get("message").isTextual()).isTrue();
            assertThat(error.get("message").asText()).isNotBlank();
            assertThat(triples.add(List.of(error.get("field").asText(), error.get("code").asText(),
                    error.get("message").asText()))).as("no repeated field/code/message triple").isTrue();
            errors.add(new Diagnosis(error.get("field").asText(), error.get("code").asText()));
        });
        var expected = new ArrayList<>(List.of(new Diagnosis("name", "INVALID_LENGTH"),
                new Diagnosis("description", "INVALID_LENGTH"), new Diagnosis("price", "NOT_POSITIVE"),
                new Diagnosis("image", "INVALID_IMAGE_URL")));
        if (partiallyMalformed) {
            expected.add(new Diagnosis("categoryIds[0]", "INVALID_TYPE"));
            expected.add(new Diagnosis("categoryIds[2]", "CATEGORY_NOT_FOUND"));
            expected.add(new Diagnosis("categoryIds[3]", "CATEGORY_NOT_FOUND"));
        } else expected.add(new Diagnosis("categoryIds[0]", "CATEGORY_NOT_FOUND"));
        assertThat(errors).containsExactlyElementsOf(expected);
        assertThat(errors.stream().map(error -> error.field().replaceAll("\\[\\d+\\]", "")))
                .containsExactlyInAnyOrderElementsOf(partiallyMalformed
                        ? List.of("name", "description", "price", "image", "categoryIds", "categoryIds", "categoryIds")
                        : List.of("name", "description", "price", "image", "categoryIds"));
        assertThat(result.getResponse().getHeader(HttpHeaders.LOCATION)).isNull();
        assertThat(result.getResponse().getHeader(HttpHeaders.WWW_AUTHENTICATE)).isNull();
        assertThat(result.getResponse().getContentAsString()).doesNotContain(token, "123456789", "relative-t027.jpg",
                "invalid-id", "SQLException", "stackTrace", "password", "Disposable-T027");
        System.out.printf("T027_DIAGNOSED context=%s partiallyMalformed=%s errors=%d noDml=true noMaintenance=true%n",
                externalContext, partiallyMalformed, errors.size());
    }

    @ParameterizedTest(name = "T039 isolated diagnosis {0}")
    @ValueSource(strings = {"valid", "invalid", "partial", "structural", "empty", "absent"})
    void shouldKeepIsolatedConstraintValidationReadOnlyAndInputImmutable(String kind) throws Exception {
        var second = fixture.createCategory("Categoria T027");
        var input = diagnosticInput(kind, second.id());
        var beforeInput = InputState.capture(input);
        var beforeDatabase = database.snapshot();
        var accounts = database.accountIds();
        var originalDiagnostic = input.diagnostic();
        var originalContext = SecurityContextHolder.getContext();
        try {
            authenticateRealFixtureToken();
            assertNoCallerTransaction();
            observation.start();
            for (int evaluation = 0; evaluation < 2; evaluation++) {
                assertAdapterViolations(input, kind);
                assertThat(InputState.capture(input)).isEqualTo(beforeInput);
                assertThat(input.diagnostic()).isSameAs(originalDiagnostic);
                assertNoCallerTransaction();
            }
        } finally {
            observation.stop();
            SecurityContextHolder.setContext(originalContext);
        }
        assertDiagnosticOnlyCalls(input, kind, 2);
        assertThat(database.snapshot()).isEqualTo(beforeDatabase);
        assertThat(database.accountIds()).isEqualTo(accounts);
        assertThat(database.roles(accounts.getFirst())).containsExactly(UserRole.ADMIN);
        System.out.printf("T039_ISOLATED case=%s evaluations=2 inputUnchanged=true jdbcDml=0 maintenanceEntries=0 categoryReads=%d snapshotEqual=true%n",
                kind, observation.sql().stream().filter(DiagnosticObservation::isCategoryRead).count());
    }

    @ParameterizedTest(name = "T039 managed memory {0}")
    @ValueSource(strings = {"valid", "partial"})
    void shouldPreserveManagedEntityMemoryAsWellAsInputSqlAndIndependentDatabase(String kind) throws Exception {
        var second = fixture.createCategory("Categoria T027");
        var input = diagnosticInput(kind, second.id());
        var beforeInput = InputState.capture(input);
        var beforeDatabase = database.snapshot();
        var originalContext = SecurityContextHolder.getContext();
        var transaction = new TransactionTemplate(context.getBean(PlatformTransactionManager.class));
        transaction.setReadOnly(true);
        try {
            authenticateRealFixtureToken();
            assertNoCallerTransaction();
            transaction.executeWithoutResult(status -> {
                assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
                assertThat(TransactionSynchronizationManager.isCurrentTransactionReadOnly()).isTrue();
                var product = entityManager.find(Product.class, beforeDatabase.products().getFirst().id());
                var firstCategory = entityManager.find(Category.class, existingCategory);
                var secondCategory = entityManager.find(Category.class, second.id());
                assertThat(entityManager.contains(product)).isTrue();
                assertThat(entityManager.contains(firstCategory)).isTrue();
                assertThat(entityManager.contains(secondCategory)).isTrue();
                var beforeMemory = ManagedState.capture(product, firstCategory, secondCategory);
                observation.start();
                for (int evaluation = 0; evaluation < 2; evaluation++) {
                    assertAdapterViolations(input, kind);
                    assertThat(InputState.capture(input)).isEqualTo(beforeInput);
                    // Read-only JPA can suppress flush: these assertions detect memory changes independently of SQL.
                    assertThat(ManagedState.capture(product, firstCategory, secondCategory)).isEqualTo(beforeMemory);
                    assertThat(TransactionSynchronizationManager.isCurrentTransactionReadOnly()).isTrue();
                }
                assertThat(database.snapshot()).isEqualTo(beforeDatabase);
            });
            // Observation extends through transaction completion, rather than stopping before an eventual flush.
            assertNoCallerTransaction();
        } finally {
            observation.stop();
            SecurityContextHolder.setContext(originalContext);
        }
        assertDiagnosticOnlyCalls(input, kind, 2);
        assertThat(database.snapshot()).isEqualTo(beforeDatabase);
        System.out.printf("T039_MANAGED case=%s evaluations=2 managedMemoryUnchanged=true inputUnchanged=true jdbcDml=0 maintenanceEntries=0 snapshotEqualAfterCompletion=true%n", kind);
    }

    private void authenticateRealFixtureToken() {
        var decoded = context.getBean(JwtDecoder.class).decode(token);
        var authentication = context.getBean(OrderJwtAuthenticationConverter.class).convert(decoded);
        assertThat(authentication).isNotNull();
        assertThat(authentication.isAuthenticated()).isTrue();
        assertThat(authentication.getAuthorities()).extracting(Object::toString).containsExactly("ROLE_ADMIN");
        var authenticatedContext = SecurityContextHolder.createEmptyContext();
        authenticatedContext.setAuthentication(authentication);
        SecurityContextHolder.setContext(authenticatedContext);
    }

    private ProductWriteRequestDTO diagnosticInput(String kind, long secondCategory) throws Exception {
        long missing = Math.addExact(secondCategory, 1L);
        assertThat(database.snapshot().categories()).noneMatch(category -> category.id() == missing);
        String ids = switch (kind) {
            case "invalid" -> "[" + missing + "]";
            case "partial" -> "[\"invalid-id\"," + existingCategory + "," + missing + "," + missing + "]";
            case "structural" -> "[" + existingCategory + ",true," + secondCategory + "]";
            case "empty" -> "[]";
            default -> "[" + existingCategory + "," + existingCategory + "," + secondCategory + "]";
        };
        String json;
        if (kind.equals("invalid") || kind.equals("partial")) {
            json = "{\"name\":\" ab \",\"description\":\"123456789\",\"price\":\"0\",\"image\":\"/relative\",\"categoryIds\":" + ids + "}";
        } else if (kind.equals("structural")) {
            json = "{\"name\":\"abc\",\"name\":null,\"description\":\"  Descricao  completa  \",\"price\":13,"
                    + "\"image\":\"https://example.invalid/a\",\"categoryIds\":" + ids + ",\"extra\":false}";
        } else if (kind.equals("absent")) {
            json = "{\"name\":\"  Fone  Azul  \",\"price\":\"1.000\",\"image\":null,\"categoryIds\":" + ids + "}";
        } else {
            json = "{\"name\":\"\u00a0  Fone  Azul \u2003\",\"description\":\"  Descricao  completa  \",\"price\":\"1.000\","
                    + "\"image\":\"HTTPS://example.invalid/a?x=1#part\",\"categoryIds\":" + ids + "}";
        }
        return mapper.readValue(json, ProductWriteRequestDTO.class);
    }

    private void assertAdapterViolations(ProductWriteRequestDTO input, String kind) {
        var violations = validator.validate(input); // Runs the application's real ConstraintValidator.isValid.
        var adapted = new ArrayList<AdminFieldErrorDTO>();
        for (var violation : violations) {
            assertThat(violation.getRootBean()).isSameAs(input);
            assertThat(violation.getConstraintDescriptor().getAnnotation()).isInstanceOf(ValidProductInput.class);
            HibernateConstraintViolation<?> coded = violation.unwrap(HibernateConstraintViolation.class);
            var diagnostic = coded.getDynamicPayload(AdminFieldErrorDTO.class);
            assertThat(diagnostic).isNotNull();
            assertThat(violation.getMessage()).isEqualTo(diagnostic.message());
            assertThat(violation.getPropertyPath().toString()).isEqualTo(diagnostic.field());
            adapted.add(diagnostic);
        }
        assertThat(adapted.stream().map(error -> error.field() + "/" + error.code()))
                .containsExactlyInAnyOrderElementsOf(expectedDiagnostics(kind));
        var complete = observation.references().getLast().result();
        // The adapter must preserve the entire service diagnosis, including messages and indexed field identities.
        assertThat(adapted).containsExactlyInAnyOrderElementsOf(complete.errors());
    }

    private static List<String> expectedDiagnostics(String kind) {
        return switch (kind) {
            case "valid" -> List.of();
            case "invalid" -> List.of("name/INVALID_LENGTH", "description/INVALID_LENGTH", "price/NOT_POSITIVE",
                    "image/INVALID_IMAGE_URL", "categoryIds[0]/CATEGORY_NOT_FOUND");
            case "partial" -> List.of("name/INVALID_LENGTH", "description/INVALID_LENGTH", "price/NOT_POSITIVE",
                    "image/INVALID_IMAGE_URL", "categoryIds[0]/INVALID_TYPE", "categoryIds[2]/CATEGORY_NOT_FOUND",
                    "categoryIds[3]/CATEGORY_NOT_FOUND");
            case "structural" -> List.of("body/UNKNOWN_FIELD", "name/DUPLICATE_FIELD", "price/INVALID_TYPE", "categoryIds[1]/INVALID_TYPE");
            case "empty" -> List.of("categoryIds/EMPTY_CATEGORIES");
            case "absent" -> List.of("description/REQUIRED", "image/REQUIRED");
            default -> throw new IllegalArgumentException("Unknown test scenario");
        };
    }

    private void assertDiagnosticOnlyCalls(ProductWriteRequestDTO input, String kind, int evaluations) {
        assertThat(observation.calls()).containsExactly("local-policy", "references", "local-policy", "references");
        assertThat(observation.policies()).hasSize(evaluations);
        assertThat(observation.references()).hasSize(evaluations);
        for (int index = 0; index < evaluations; index++) {
            var policy = observation.policies().get(index);
            var references = observation.references().get(index);
            assertThat(policy.input()).isSameAs(input);
            assertThat(references.input()).isSameAs(input);
            assertThat(references.local()).isSameAs(policy.result().diagnostic());
            assertThat(policy.result().diagnostic()).isNotSameAs(input.diagnostic());
            assertThat(references.result()).isNotSameAs(policy.result().diagnostic());
            assertThat(references.result().fields()).isEqualTo(input.diagnostic().fields());
            assertThat(references.result().categoryElements()).isEqualTo(input.diagnostic().categoryElements());
            if (kind.equals("valid")) {
                var normalized = policy.result().normalizedValues();
                assertThat(normalized.name()).isEqualTo("Fone  Azul");
                assertThat(normalized.description()).isEqualTo("Descricao  completa");
                assertThat(normalized.price()).isEqualTo(new BigDecimal("1.00"));
                assertThat(normalized.categoryIds()).isEqualTo(input.categoryIds());
            }
        }
        assertThat(observation.sql()).isNotEmpty().noneMatch(DiagnosticObservation::isDml);
        assertThat(observation.sql().stream().filter(DiagnosticObservation::isCategoryRead).count())
                .isEqualTo(kind.equals("empty") ? 0L : (long) evaluations);
        assertThat(observation.maintenanceBeans()).isEqualTo(1);
        assertThat(observation.maintenanceEntries()).isZero();
    }

    private record InputState(String name, String description, String price, String image, List<Long> ids,
                              Map<ProductInputDiagnostic.Field, ProductInputDiagnostic.Representation> fields,
                              Map<Integer, ProductInputDiagnostic.Representation> elements, List<AdminFieldErrorDTO> errors) {
        static InputState capture(ProductWriteRequestDTO input) {
            return new InputState(input.name(), input.description(), input.price(), input.image(),
                    input.categoryIds() == null ? null : Collections.unmodifiableList(new ArrayList<>(input.categoryIds())),
                    Map.copyOf(input.diagnostic().fields()), Map.copyOf(input.diagnostic().categoryElements()),
                    List.copyOf(input.diagnostic().errors()));
        }
    }

    private record CategoryState(Long id, String name) {
        static CategoryState capture(Category category) { return new CategoryState(category.getId(), category.getName()); }
    }

    private record ManagedState(Long id, String name, String description, String image, BigDecimal price,
                                List<CategoryState> associations, CategoryState first, CategoryState second) {
        static ManagedState capture(Product product, Category first, Category second) {
            return new ManagedState(product.getId(), product.getName(), product.getDescription(), product.getImage(),
                    product.getPrice(), product.getCategories().stream().map(CategoryState::capture)
                    .sorted(Comparator.comparing(CategoryState::id)).toList(), CategoryState.capture(first), CategoryState.capture(second));
        }
    }

    private static void assertNoCallerTransaction() {
        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
        assertThat(TransactionSynchronizationManager.isSynchronizationActive()).isFalse();
    }
    private void assertEmptyDatabase() {
        var snapshot = database.snapshot();
        assertThat(snapshot.products()).isEmpty(); assertThat(snapshot.categories()).isEmpty();
        assertThat(snapshot.associations()).isEmpty(); assertThat(snapshot.orders()).isEmpty();
        assertThat(snapshot.items()).isEmpty(); assertThat(database.accountIds()).isEmpty();
    }
    private record Diagnosis(String field, String code) { }
    public static class MaintenanceCalibration { public void enter() { } }

    @TestConfiguration(proxyBeanMethods = false)
    static class ObservationConfig {
        @Bean static DiagnosticObservation diagnosticObservation() { return new DiagnosticObservation(); }
    }

    /** Test-only application JDBC/proxy observation. Independent probe/fixture JDBC is outside this scope. */
    static final class DiagnosticObservation implements BeanPostProcessor {
        private static final String MAINTENANCE = "com.devsuperior.dscommerce.services.ProductMaintenanceService";
        private final List<String> sql = new CopyOnWriteArrayList<>();
        private final AtomicInteger entries = new AtomicInteger();
        private final AtomicInteger beans = new AtomicInteger();
        private final List<String> calls = new CopyOnWriteArrayList<>();
        private final List<PolicyCall> policies = new CopyOnWriteArrayList<>();
        private final List<ReferenceCall> references = new CopyOnWriteArrayList<>();
        private volatile boolean observing;
        void start() { sql.clear(); entries.set(0); calls.clear(); policies.clear(); references.clear(); observing = true; }
        void stop() { observing = false; }
        List<String> sql() { return List.copyOf(sql); }
        int maintenanceEntries() { return entries.get(); }
        int maintenanceBeans() { return beans.get(); }
        List<String> calls() { return List.copyOf(calls); }
        List<PolicyCall> policies() { return List.copyOf(policies); }
        List<ReferenceCall> references() { return List.copyOf(references); }
        private record PolicyCall(ProductWriteRequestDTO input, ProductInputValidationService.Result result) { }
        private record ReferenceCall(ProductWriteRequestDTO input, ProductInputDiagnostic local, ProductInputDiagnostic result) { }
        MethodInterceptor maintenanceAdvice() {
            return invocation -> { if (observing) entries.incrementAndGet(); return invocation.proceed(); };
        }
        @Override public Object postProcessAfterInitialization(Object bean, String name) {
            String type = AopUtils.getTargetClass(bean).getName();
            boolean policy = type.equals("com.devsuperior.dscommerce.services.ProductInputValidationService");
            boolean reference = type.equals("com.devsuperior.dscommerce.services.ProductCategoryDiagnosticService");
            if (policy || reference) {
                MethodInterceptor trace = invocation -> {
                    boolean record = observing && invocation.getMethod().getName().equals(policy ? "validate" : "diagnose");
                    if (record) calls.add(policy ? "local-policy" : "references");
                    var result = invocation.proceed();
                    if (record && policy) policies.add(new PolicyCall((ProductWriteRequestDTO) invocation.getArguments()[0],
                            (ProductInputValidationService.Result) result));
                    if (record && reference) references.add(new ReferenceCall((ProductWriteRequestDTO) invocation.getArguments()[0],
                            (ProductInputDiagnostic) invocation.getArguments()[1], (ProductInputDiagnostic) result));
                    return result;
                };
                if (bean instanceof Advised advised) { advised.addAdvice(0, trace); return bean; }
                var proxy = new ProxyFactory(bean); proxy.setProxyTargetClass(true); proxy.addAdvice(trace); return proxy.getProxy();
            }
            if (AopUtils.getTargetClass(bean).getName().equals(MAINTENANCE)) {
                beans.incrementAndGet();
                if (bean instanceof Advised advised) { advised.addAdvice(0, maintenanceAdvice()); return bean; }
                var proxy = new ProxyFactory(bean); proxy.setProxyTargetClass(true);
                proxy.addAdvice(maintenanceAdvice()); return proxy.getProxy();
            }
            if (bean instanceof DataSource source) {
                return Proxy.newProxyInstance(DataSource.class.getClassLoader(), new Class<?>[]{DataSource.class},
                        (proxy, method, args) -> {
                            var value = delegate(source, method, args);
                            return value instanceof Connection connection ? connection(connection) : value;
                        });
            }
            return bean;
        }
        private Connection connection(Connection target) {
            return (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(), new Class<?>[]{Connection.class},
                    (proxy, method, args) -> {
                        var value = delegate(target, method, args);
                        if (value instanceof Statement statement) {
                            String prepared = args != null && args.length > 0 && args[0] instanceof String text ? text : null;
                            return statement(statement, prepared);
                        }
                        return value;
                    });
        }
        private Statement statement(Statement target, String prepared) {
            Class<?> type = target instanceof CallableStatement ? CallableStatement.class
                    : target instanceof PreparedStatement ? PreparedStatement.class : Statement.class;
            var batch = new ArrayList<String>();
            return (Statement) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (proxy, method, args) -> {
                String text = prepared != null ? prepared
                        : args != null && args.length > 0 && args[0] instanceof String query ? query : null;
                if (method.getName().equals("addBatch") && text != null) batch.add(text);
                if (method.getName().equals("clearBatch")) batch.clear();
                if (method.getName().startsWith("execute")) {
                    if (observing) {
                        if (method.getName().endsWith("Batch")) sql.addAll(batch);
                        else if (text != null) sql.add(text);
                    }
                    if (method.getName().endsWith("Batch")) batch.clear();
                }
                return delegate(target, method, args);
            });
        }
        private static Object delegate(Object target, Method method, Object[] args) throws Throwable {
            try { return method.invoke(target, args); }
            catch (InvocationTargetException failure) { throw failure.getCause(); }
        }
        private static String normalized(String statement) {
            return statement.replaceAll("(?s)/\\*.*?\\*/", " ").stripLeading().toLowerCase(Locale.ROOT);
        }
        static boolean isDml(String statement) {
            String text = normalized(statement);
            return Stream.of("insert ", "update ", "delete ", "merge ").anyMatch(text::startsWith);
        }
        static boolean isCategoryRead(String statement) {
            String text = normalized(statement);
            return text.startsWith("select ") && text.contains("tb_category");
        }
    }
}
