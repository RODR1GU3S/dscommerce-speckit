package com.devsuperior.dscommerce.config;

import com.devsuperior.dscommerce.fixtures.OrderTestFixture;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

/** Import explicitly with the test profile; production JWT/password beans remain real. */
@TestConfiguration(proxyBeanMethods = false)
@Profile("test")
public class OrderTestConfig {

    public static final Instant INITIAL_INSTANT = Instant.parse("2030-01-01T12:00:00.123Z");

    /** Call once per context, never once per connection or datasource lookup. */
    public static String isolatedDatabaseUrl() {
        return "jdbc:h2:mem:order-test-" + UUID.randomUUID()
                + ";DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE";
    }

    /** Use from each mutating test class's own DynamicPropertySource method. */
    public static void isolatedDatabase(DynamicPropertyRegistry registry) {
        var url = isolatedDatabaseUrl();
        registry.add("spring.datasource.url", () -> url);
    }

    @Bean
    @Primary
    public ControlledClock orderTestClock() {
        return new ControlledClock(INITIAL_INSTANT);
    }

    @Bean
    public OrderTestFixture orderTestFixture(JdbcTemplate jdbc, PlatformTransactionManager transactionManager,
                                             PasswordEncoder passwordEncoder, JwtEncoder jwtEncoder) {
        return new OrderTestFixture(jdbc, transactionManager, passwordEncoder, jwtEncoder);
    }

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
