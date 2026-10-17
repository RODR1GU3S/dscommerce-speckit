package com.devsuperior.dscommerce.fixtures;

import com.devsuperior.dscommerce.dto.TokenResponseDTO;
import com.devsuperior.dscommerce.entities.UserRole;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Profiles;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Explicit, committed setup for a full context initialized by AdminProductTestConfig.
 * Construction creates no data. Use a fresh scenario or clean owned rows between scenarios.
 * One fixture instance has one owner and is not intended for concurrent setup/cleanup.
 * Consumers must remove any dependent rows they create before cleanup; foreign rows are not erased.
 */
public final class AdminProductTestFixture implements AutoCloseable {

    private static final String DESCRIPTION = "Descricao da fixture V03";
    private static final String IMAGE = "https://example.invalid/v03.jpg";
    private static final BigDecimal PRICE = new BigDecimal("10.00");

    private final JdbcTemplate jdbc;
    private final TransactionTemplate committed;
    private final PasswordEncoder passwordEncoder;
    private final ObjectMapper mapper;
    private final JwtDecoder decoder;
    private final MockMvc loginMvc;
    private final Rows owned = new Rows();

    public AdminProductTestFixture(WebApplicationContext context) {
        if (!(context.getEnvironment() instanceof ConfigurableEnvironment environment)) {
            throw new IllegalArgumentException("Admin fixtures require a configurable test environment");
        }
        var settings = environment.getPropertySources().get("adminProductTestContext");
        if (!environment.acceptsProfiles(Profiles.of("test")) || settings == null) {
            throw new IllegalArgumentException("Admin fixtures require explicit AdminProductTestConfig activation");
        }
        jdbc = context.getBean(JdbcTemplate.class);
        var url = jdbc.execute((ConnectionCallback<String>) connection -> connection.getMetaData().getURL());
        // H2 metadata identifies the database without the connection options following ';'.
        var configuredDatabase = Objects.toString(settings.getProperty("spring.datasource.url"), "").split(";", 2)[0];
        if (url == null || !url.startsWith("jdbc:h2:mem:admin-product-test-")
                || !url.equals(configuredDatabase)) {
            throw new IllegalArgumentException("Admin fixtures require their own configured H2 database");
        }
        committed = new TransactionTemplate(context.getBean(PlatformTransactionManager.class));
        committed.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        passwordEncoder = context.getBean(PasswordEncoder.class);
        mapper = context.getBean(ObjectMapper.class);
        decoder = context.getBean(JwtDecoder.class);
        // No result printer, mocked token or replacement security chain is installed.
        loginMvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilters(context.getBean(FilterChainProxy.class)).build();
    }

    public Account createAccount(String name, String password, Set<UserRole> roles) {
        return prepare(rows -> rows.account(name, password, roles));
    }

    public AccessAccounts prepareAccessAccounts() {
        return prepare(rows -> new AccessAccounts(
                rows.disposableAccount("admin", Set.of(UserRole.ADMIN)),
                rows.disposableAccount("client", Set.of(UserRole.CLIENT)),
                rows.disposableAccount("both", Set.of(UserRole.ADMIN, UserRole.CLIENT)),
                rows.disposableAccount("none", Set.of())));
    }

    /** A buyer is an ordinary committed CLIENT account; no order is created here. */
    public Account prepareBuyer() {
        return prepare(rows -> rows.disposableAccount("buyer", Set.of(UserRole.CLIENT)));
    }

    public CategoryData createCategory(String name) {
        return prepare(rows -> rows.category(name));
    }

    public CategorySelection prepareCategorySelection() {
        requireFreshCatalog();
        return prepare(rows -> new CategorySelection(
                List.of(rows.category("Categoria repetida"), rows.category("Categoria repetida")),
                rows.category("Categoria sem produto")));
    }

    public ProductData createProduct(String name, String description, String image, BigDecimal price) {
        return prepare(rows -> rows.product(name, description, image, price));
    }

    public ProductCategory associate(long productId, long categoryId) {
        return prepare(rows -> rows.associate(productId, categoryId));
    }

    public V03Manifest prepareV03() {
        requireFreshCatalog();
        return prepare(rows -> {
            var products = List.of(rows.standardProduct("Telefone D"), rows.standardProduct("Fone C"),
                    rows.standardProduct("Fone B"), rows.standardProduct("Fone B"),
                    rows.standardProduct("Fone A"), rows.standardProduct("Cabo"));
            var categories = List.of(rows.category("Categoria V03"), rows.category("Categoria V03"));
            var pairs = Set.of(rows.associate(products.get(4).id(), categories.get(0).id()),
                    rows.associate(products.get(4).id(), categories.get(1).id()));
            var tied = List.of(products.get(2), products.get(3)).stream()
                    .sorted(Comparator.comparingLong(ProductData::id)).toList();
            var filtered = List.of(products.get(4).id(), tied.get(0).id(), tied.get(1).id(),
                    products.get(1).id(), products.get(0).id());
            var unfiltered = new ArrayList<Long>();
            unfiltered.add(products.get(5).id());
            unfiltered.addAll(filtered);
            return new V03Manifest(products, categories, pairs, filtered, unfiltered);
        });
    }

    public ProductData prepareLegacyProduct() {
        requireFreshCatalog();
        return createProduct("Produto legado", "Legado", "imagem-legada", new BigDecimal("0.00"));
    }

    /** Starts in the seed-free context and inserts only an unassociated legacy product. */
    public ProductData prepareWithoutCategories() {
        return prepareLegacyProduct();
    }

    public SearchManifest prepareAccentScenario() {
        requireFreshCatalog();
        return prepare(rows -> {
            var accented = rows.standardProduct("Café");
            var plain = rows.standardProduct("Cafe");
            return new SearchManifest(List.of(accented, plain),
                    Map.of("café", List.of(accented.id()), "cafe", List.of(plain.id())));
        });
    }

    public SearchManifest prepareSpacingScenario() {
        requireFreshCatalog();
        return prepare(rows -> {
            var single = rows.standardProduct("Fone A");
            var doubled = rows.standardProduct("Fone  A");
            return new SearchManifest(List.of(single, doubled),
                    Map.of("fone a", List.of(single.id()), "fone  a", List.of(doubled.id())));
        });
    }

    public SearchManifest prepareSpecialCharactersScenario() {
        requireFreshCatalog();
        return prepare(rows -> {
            var products = new ArrayList<ProductData>();
            var matches = new LinkedHashMap<String, List<Long>>();
            for (var literal : List.of("%", "_", "/", "'")) {
                var product = rows.standardProduct("Fone " + literal);
                products.add(product);
                matches.put(literal, List.of(product.id()));
            }
            return new SearchManifest(products, matches);
        });
    }

    /** Obtain an actual signed token through the real controller/service/filter setup. */
    public String login(Account account) {
        if (!owned.accounts.contains(account.id())) {
            throw new IllegalArgumentException("Only an owned fixture account may be used for login");
        }
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("Fixture HTTP login must run without a caller transaction");
        }
        try {
            var response = loginMvc.perform(post("/login").contentType(MediaType.APPLICATION_JSON)
                    .content(mapper.writeValueAsBytes(Map.of("name", account.name(),
                            "password", account.password())))).andReturn().getResponse();
            if (response.getStatus() != 200) {
                throw new IllegalStateException("Fixture login did not return HTTP 200");
            }
            var token = mapper.readValue(response.getContentAsByteArray(), TokenResponseDTO.class);
            if (!"Bearer".equals(token.tokenType()) || token.accessToken() == null
                    || !Long.toString(account.id()).equals(decoder.decode(token.accessToken()).getSubject())) {
                throw new IllegalStateException("Fixture login did not return the expected identity");
            }
            return token.accessToken();
        } catch (Exception failure) {
            // Do not include the response, credentials, JWT or a potentially sensitive cause.
            if (failure instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new IllegalStateException("Fixture login could not be completed");
        }
    }

    public void cleanUp() {
        committed.executeWithoutResult(status -> {
            for (var pair : owned.pairs) {
                jdbc.update("delete from tb_product_category where product_id = ? and category_id = ?",
                        pair.productId(), pair.categoryId());
            }
            for (var id : owned.products) {
                jdbc.update("delete from tb_product where id = ?", id);
            }
            for (var id : owned.categories) {
                jdbc.update("delete from tb_category where id = ?", id);
            }
            for (var role : owned.roles) {
                jdbc.update("delete from tb_user_account_role where user_account_id = ? and role = ?",
                        role.accountId(), role.role().name());
            }
            for (var id : owned.accounts) {
                jdbc.update("delete from tb_user_account where id = ?", id);
            }
        });
        // Keep ownership on failure so cleanup can be retried after the consumer removes dependencies.
        owned.clear();
    }

    @Override
    public void close() {
        cleanUp();
    }

    private void requireFreshCatalog() {
        if (!owned.products.isEmpty() || !owned.categories.isEmpty()) {
            throw new IllegalStateException("Clean the previous owned catalog scenario first");
        }
        // Do not delete or assume the absence of another owner's rows; T004 checks the context separately.
    }

    private <T> T prepare(Function<Rows, T> action) {
        var pending = new Rows();
        var result = Objects.requireNonNull(committed.execute(status -> action.apply(pending)));
        owned.accounts.addAll(pending.accounts);
        owned.roles.addAll(pending.roles);
        owned.products.addAll(pending.products);
        owned.categories.addAll(pending.categories);
        owned.pairs.addAll(pending.pairs);
        return result;
    }

    private long insertRow(String sql, Object... values) {
        var key = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            var statement = connection.prepareStatement(sql, new String[]{"id"});
            for (int index = 0; index < values.length; index++) {
                statement.setObject(index + 1, values[index]);
            }
            return statement;
        }, key);
        return Objects.requireNonNull(key.getKey()).longValue();
    }

    private final class Rows {
        private final Set<Long> accounts = new LinkedHashSet<>();
        private final Set<AccountRole> roles = new LinkedHashSet<>();
        private final Set<Long> products = new LinkedHashSet<>();
        private final Set<Long> categories = new LinkedHashSet<>();
        private final Set<ProductCategory> pairs = new LinkedHashSet<>();

        private Account disposableAccount(String label, Set<UserRole> requestedRoles) {
            return account("admin-fixture-" + label + "-" + UUID.randomUUID(),
                    UUID.randomUUID().toString(), requestedRoles);
        }

        private Account account(String name, String password, Set<UserRole> requestedRoles) {
            var copiedRoles = Set.copyOf(requestedRoles);
            var id = insertRow("insert into tb_user_account (name, password_hash) values (?, ?)",
                    name, passwordEncoder.encode(password));
            accounts.add(id);
            for (var role : copiedRoles) {
                jdbc.update("insert into tb_user_account_role (user_account_id, role) values (?, ?)", id, role.name());
                roles.add(new AccountRole(id, role));
            }
            return new Account(id, name, password, copiedRoles);
        }

        private CategoryData category(String name) {
            var id = insertRow("insert into tb_category (name) values (?)", name);
            categories.add(id);
            return new CategoryData(id, name);
        }

        private ProductData standardProduct(String name) {
            return product(name, DESCRIPTION, IMAGE, PRICE);
        }

        private ProductData product(String name, String description, String image, BigDecimal price) {
            var id = insertRow("insert into tb_product (name, description, image, price) values (?, ?, ?, ?)",
                    name, description, image, price);
            products.add(id);
            return new ProductData(id, name, description, image, price);
        }

        private ProductCategory associate(long productId, long categoryId) {
            if (!(products.contains(productId) || owned.products.contains(productId))
                    || !(categories.contains(categoryId) || owned.categories.contains(categoryId))) {
                throw new IllegalArgumentException("Only owned fixture products/categories may be associated");
            }
            var pair = new ProductCategory(productId, categoryId);
            if (pairs.contains(pair) || owned.pairs.contains(pair)) {
                return pair;
            }
            jdbc.update("insert into tb_product_category (product_id, category_id) values (?, ?)", productId, categoryId);
            pairs.add(pair);
            return pair;
        }

        private void clear() {
            accounts.clear();
            roles.clear();
            products.clear();
            categories.clear();
            pairs.clear();
        }
    }

    private record AccountRole(long accountId, UserRole role) { }

    public record Account(long id, String name, String password, Set<UserRole> roles) {
        public Account {
            roles = Set.copyOf(roles);
        }

        @Override
        public String toString() {
            return "FixtureAccount[id=" + id + ", roles=" + roles + ", credentials=redacted]";
        }
    }

    public record AccessAccounts(Account admin, Account client, Account both, Account none) { }

    public record ProductData(long id, String name, String description, String image, BigDecimal price) { }

    public record CategoryData(long id, String name) { }

    public record ProductCategory(long productId, long categoryId) { }

    public record CategorySelection(List<CategoryData> sameName, CategoryData unused) {
        public CategorySelection {
            sameName = List.copyOf(sameName);
        }
    }

    public record V03Manifest(List<ProductData> insertedProducts, List<CategoryData> categories,
                              Set<ProductCategory> associations, List<Long> filteredOrder,
                              List<Long> unfilteredOrder) {
        public V03Manifest {
            insertedProducts = List.copyOf(insertedProducts);
            categories = List.copyOf(categories);
            associations = Set.copyOf(associations);
            filteredOrder = List.copyOf(filteredOrder);
            unfilteredOrder = List.copyOf(unfilteredOrder);
        }
    }

    public record SearchManifest(List<ProductData> products, Map<String, List<Long>> expectedMatches) {
        public SearchManifest {
            products = List.copyOf(products);
            var immutableMatches = new LinkedHashMap<String, List<Long>>();
            expectedMatches.forEach((literal, ids) -> immutableMatches.put(literal, List.copyOf(ids)));
            expectedMatches = Map.copyOf(immutableMatches);
        }
    }
}
