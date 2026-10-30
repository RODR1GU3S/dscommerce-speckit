package com.devsuperior.dscommerce.config;

import com.devsuperior.dscommerce.DscommerceApplication;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.Profiles;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

/** Explicit initializer only: no component stereotype, global profile change or legacy fixture import. */
public final class CustomerRegistrationTestConfig
        implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    public static final String PROPERTY_SOURCE_NAME = "customerRegistrationTestContext";
    public static final String CLOCK_BEAN_NAME = "customerRegistrationClock";
    public static final Instant INITIAL_INSTANT = Instant.parse("2030-01-01T12:00:00.123Z");

    @Override
    public void initialize(ConfigurableApplicationContext context) {
        var environment = context.getEnvironment();
        if (!environment.acceptsProfiles(Profiles.of("test"))) {
            throw new IllegalStateException("CustomerRegistrationTestConfig requires the test profile");
        }
        if (environment.getPropertySources().contains(PROPERTY_SOURCE_NAME)) {
            throw new IllegalStateException("Initialize the customer registration context only once");
        }

        // Stable for this context's lifetime, distinct from every other context and connection lookup.
        var databaseUrl = "jdbc:h2:mem:customer-registration-test-" + UUID.randomUUID()
                + ";DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE";
        var secret = new byte[32];
        new SecureRandom().nextBytes(secret);

        var properties = new LinkedHashMap<String, Object>();
        properties.put("spring.datasource.url", databaseUrl);
        properties.put("spring.datasource.driver-class-name", "org.h2.Driver");
        properties.put("spring.datasource.username", "sa");
        properties.put("spring.datasource.password", "");
        properties.put("spring.jpa.hibernate.ddl-auto", "create-drop");
        properties.put("spring.sql.init.mode", "never");
        properties.put("spring.jpa.properties.hibernate.hbm2ddl.import_files", "");
        properties.put("spring.jpa.open-in-view", "false");
        properties.put("spring.h2.console.enabled", "false");
        properties.put("security.jwt.secret-base64", Base64.getEncoder().encodeToString(secret));
        properties.put("security.jwt.ttl", "PT15M");
        environment.getPropertySources().addFirst(new MapPropertySource(PROPERTY_SOURCE_NAME, properties));

        // Register only in the explicitly initialized factory, before singleton creation.
        // The production Clock definition and all unrelated test contexts remain unchanged.
        context.addBeanFactoryPostProcessor(beanFactory -> {
            if (!databaseUrl.equals(environment.getRequiredProperty("spring.datasource.url"))) {
                throw new IllegalStateException("Keep the customer registration database isolated");
            }
            if (!(beanFactory instanceof BeanDefinitionRegistry registry)) {
                throw new IllegalStateException("Customer registration tests require a bean definition registry");
            }
            if (beanFactory.containsBean(CLOCK_BEAN_NAME) || registry.containsBeanDefinition(CLOCK_BEAN_NAME)) {
                throw new IllegalStateException("Customer registration Clock is already registered");
            }
            var definition = new RootBeanDefinition(ControlledClock.class);
            definition.setInstanceSupplier(() -> new ControlledClock(INITIAL_INSTANT));
            definition.setPrimary(true);
            registry.registerBeanDefinition(CLOCK_BEAN_NAME, definition);
        });
    }

    /** Distinct test cache key and database for the root servlet context. */
    @Target(ElementType.TYPE)
    @Retention(RetentionPolicy.RUNTIME)
    @Inherited
    @SpringBootTest(classes = DscommerceApplication.class,
            webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
            properties = {"server.servlet.context-path=", "feature009.customer-registration-test-context=root"})
    @ActiveProfiles("test")
    @ContextConfiguration(initializers = CustomerRegistrationTestConfig.class)
    public @interface RootHttpContext { }

    /** A separate test cache key and initializer invocation for the /api servlet context. */
    @Target(ElementType.TYPE)
    @Retention(RetentionPolicy.RUNTIME)
    @Inherited
    @SpringBootTest(classes = DscommerceApplication.class,
            webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
            properties = {"server.servlet.context-path=/api", "feature009.customer-registration-test-context=api"})
    @ActiveProfiles("test")
    @ContextConfiguration(initializers = CustomerRegistrationTestConfig.class)
    public @interface ApiHttpContext { }

    /** Mutable time belongs to one context; withZone shares only that context's timeline. */
    public static final class ControlledClock extends Clock {

        private final AtomicReference<Instant> current;
        private final ZoneId zone;

        public ControlledClock(Instant initialInstant) {
            this(new AtomicReference<>(Objects.requireNonNull(initialInstant)), ZoneOffset.UTC);
        }

        private ControlledClock(AtomicReference<Instant> current, ZoneId zone) {
            this.current = current;
            this.zone = Objects.requireNonNull(zone);
        }

        public void setInstant(Instant instant) {
            current.set(Objects.requireNonNull(instant));
        }

        public void advance(Duration duration) {
            Objects.requireNonNull(duration);
            current.updateAndGet(instant -> instant.plus(duration));
        }

        @Override
        public ZoneId getZone() {
            return zone;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return new ControlledClock(current, zone);
        }

        @Override
        public Instant instant() {
            return current.get();
        }
    }
}
