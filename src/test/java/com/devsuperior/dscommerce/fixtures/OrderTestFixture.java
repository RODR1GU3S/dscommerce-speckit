package com.devsuperior.dscommerce.fixtures;

import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Committed JDBC setup for isolated H2 tests, with explicit cleanup of owned rows only.
 * Accounts have no roles here; role provisioning will be added after T006 supplies the schema.
 * Future tests that persist orders must remove their dependent rows before calling cleanUp.
 */
public final class OrderTestFixture {

    private final JdbcTemplate jdbc;
    private final TransactionTemplate committed;
    private final PasswordEncoder passwordEncoder;
    private final JwtEncoder jwtEncoder;
    private final Set<Long> accounts = new LinkedHashSet<>();
    private final Set<Long> products = new LinkedHashSet<>();

    public OrderTestFixture(JdbcTemplate jdbc, PlatformTransactionManager transactionManager,
                            PasswordEncoder passwordEncoder, JwtEncoder jwtEncoder) {
        var url = jdbc.execute((ConnectionCallback<String>) connection -> connection.getMetaData().getURL());
        if (url == null || !url.startsWith("jdbc:h2:mem:order-test-")) {
            throw new IllegalArgumentException("Order fixtures require their own order-test H2 database");
        }
        this.jdbc = jdbc;
        this.passwordEncoder = passwordEncoder;
        this.jwtEncoder = jwtEncoder;
        this.committed = new TransactionTemplate(transactionManager);
        committed.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public long createAccount(String name, String rawPassword) {
        var hash = passwordEncoder.encode(rawPassword);
        var id = insert("insert into tb_user_account (name, password_hash) values (?, ?)", name, hash);
        accounts.add(id);
        return id;
    }

    public long createProduct(String name, BigDecimal price) {
        var id = insert("insert into tb_product (name, description, image, price) values (?, ?, ?, ?)",
                name, name, "https://example.test/order-fixture.jpg", price);
        products.add(id);
        return id;
    }

    /** JDBC can prepare negative prices without changing Product's ORM validation. */
    public void setProductPrice(long productId, BigDecimal price) {
        if (!products.contains(productId)) {
            throw new IllegalArgumentException("Only products created by this fixture may be changed");
        }
        committed.executeWithoutResult(status -> {
            if (jdbc.update("update tb_product set price = ? where id = ?", price, productId) != 1) {
                throw new IllegalStateException("Fixture product no longer exists");
            }
        });
    }

    /** Use the existing HS256 encoder and the disposable key from application-test.properties. */
    public String signedToken(JwtClaimsSet claims) {
        return jwtEncoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
    }

    public void cleanUp() {
        committed.executeWithoutResult(status -> {
            for (var productId : products) {
                jdbc.update("delete from tb_product_category where product_id = ?", productId);
                jdbc.update("delete from tb_product where id = ?", productId);
            }
            for (var accountId : accounts) {
                jdbc.update("delete from tb_user_account where id = ?", accountId);
            }
        });
        products.clear();
        accounts.clear();
    }

    private long insert(String sql, Object... values) {
        return Objects.requireNonNull(committed.execute(status -> {
            var key = new GeneratedKeyHolder();
            jdbc.update(connection -> {
                var statement = connection.prepareStatement(sql, new String[]{"id"});
                for (int i = 0; i < values.length; i++) {
                    statement.setObject(i + 1, values[i]);
                }
                return statement;
            }, key);
            return Objects.requireNonNull(key.getKey()).longValue();
        }));
    }
}
