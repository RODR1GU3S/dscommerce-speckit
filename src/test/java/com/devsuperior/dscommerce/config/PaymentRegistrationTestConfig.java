package com.devsuperior.dscommerce.config;

import com.devsuperior.dscommerce.DscommerceApplication;
import com.devsuperior.dscommerce.config.OrderTestConfig.ControlledClock;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Objects;

/** Explicit test import only; no payment model, fixtures or replacement security beans. */
@TestConfiguration(proxyBeanMethods = false)
@Profile("test")
public class PaymentRegistrationTestConfig {

    @Bean
    @Primary
    public ControlledClock paymentTestClock(ConfigurableEnvironment environment) {
        // Reuse the approved initializer: one UUID/URL and disposable JWT key per context.
        // Keeping its property source and URL prefix also permits composition of the 007 fixture.
        var settings = environment.getPropertySources().get("adminProductTestContext");
        if (settings == null || !Objects.equals(settings.getProperty("spring.datasource.url"),
                environment.getRequiredProperty("spring.datasource.url"))) {
            throw new IllegalStateException("Payment tests require explicit isolated database initialization");
        }
        // Importing OrderTestConfig itself would also install its unrelated order fixture bean.
        return new ControlledClock(OrderTestConfig.INITIAL_INSTANT);
    }

    /** Apply to a concrete ROOT HTTP test extending PaymentRegistrationHttpTestSupport. */
    @Target(ElementType.TYPE)
    @Retention(RetentionPolicy.RUNTIME)
    @Inherited
    @SpringBootTest(classes = DscommerceApplication.class,
            webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
            properties = {"server.servlet.context-path=", "spring.jpa.open-in-view=false",
                    "spring.h2.console.enabled=false", "feature008.payment-test-context=root"})
    @ActiveProfiles("test")
    @ContextConfiguration(initializers = AdminProductTestConfig.class)
    @Import(PaymentRegistrationTestConfig.class)
    public @interface RootHttpContext { }

    /** A distinct Spring context/cache key and H2 database for the /api HTTP variant. */
    @Target(ElementType.TYPE)
    @Retention(RetentionPolicy.RUNTIME)
    @Inherited
    @SpringBootTest(classes = DscommerceApplication.class,
            webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
            properties = {"server.servlet.context-path=/api", "spring.jpa.open-in-view=false",
                    "spring.h2.console.enabled=false", "feature008.payment-test-context=api"})
    @ActiveProfiles("test")
    @ContextConfiguration(initializers = AdminProductTestConfig.class)
    @Import(PaymentRegistrationTestConfig.class)
    public @interface ApiHttpContext { }
}
