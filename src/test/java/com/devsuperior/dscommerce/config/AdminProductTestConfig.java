package com.devsuperior.dscommerce.config;

import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.Profiles;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.UUID;

/**
 * Activate explicitly via {@code @ContextConfiguration(initializers = AdminProductTestConfig.class)}
 * with {@code @ActiveProfiles("test")} and the full application context.
 * This initializer has no component/configuration stereotype and is never component-scanned.
 * Use real MockMvc filters; production security and JWT beans are not replaced here.
 */
public final class AdminProductTestConfig
        implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    @Override
    public void initialize(ConfigurableApplicationContext context) {
        var environment = context.getEnvironment();
        if (!environment.acceptsProfiles(Profiles.of("test"))) {
            throw new IllegalStateException("AdminProductTestConfig requires the test profile");
        }

        // Generate once per context, not per datasource connection or property lookup.
        var databaseUrl = "jdbc:h2:mem:admin-product-test-" + UUID.randomUUID()
                + ";DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE";
        var secret = new byte[32];
        new SecureRandom().nextBytes(secret);

        var properties = new LinkedHashMap<String, Object>();
        properties.put("spring.datasource.url", databaseUrl);
        properties.put("spring.datasource.driver-class-name", "org.h2.Driver");
        properties.put("spring.datasource.username", "sa");
        properties.put("spring.datasource.password", "");
        properties.put("spring.jpa.hibernate.ddl-auto", "create-drop");
        // Spring SQL initialization and Hibernate's independent import.sql loader are separate.
        properties.put("spring.sql.init.mode", "never");
        properties.put("spring.jpa.properties.hibernate.hbm2ddl.import_files", "");
        properties.put("security.jwt.secret-base64", Base64.getEncoder().encodeToString(secret));
        properties.put("security.jwt.ttl", "PT15M");

        // Apply before bean creation, including datasource, EntityManagerFactory and JWT binding.
        environment.getPropertySources().addFirst(
                new MapPropertySource("adminProductTestContext", properties));
    }
}
