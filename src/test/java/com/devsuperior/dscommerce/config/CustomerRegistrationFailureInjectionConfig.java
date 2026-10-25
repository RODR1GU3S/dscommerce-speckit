package com.devsuperior.dscommerce.config;

import com.devsuperior.dscommerce.config.CustomerRegistrationObservationConfig.Attempt;
import com.devsuperior.dscommerce.config.CustomerRegistrationObservationConfig.Observation;
import com.devsuperior.dscommerce.config.CustomerRegistrationObservationConfig.Point;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;

import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Explicit @Import only, alongside the observation config and the T006 initializer.
 * Lite @Bean class without component stereotypes. Never changes public controllers/services.
 * This is support authorship, not an executed rollback/concurrency/transport test.
 */
@Profile("test")
public class CustomerRegistrationFailureInjectionConfig {

    @Bean
    public Failures customerRegistrationFailures(Observation observation) {
        return new Failures(observation);
    }

    /** Fixed message and no cause/input: a sentinel for a deliberate test fault, not an application error. */
    public static final class InjectedFailure extends RuntimeException {
        private InjectedFailure() { super("Controlled customer registration test failure"); }
    }

    public static final class Failures {
        private final Observation observation;

        private Failures(Observation observation) { this.observation = Objects.requireNonNull(observation); }

        /** An unarmed positive control has exactly the same JDBC/Hibernate/Spring instrumentation. */
        public Attempt openControl() { return observation.open(); }

        /** The chosen boundary must actually be reached; assertReached after awaitFinished. */
        public Fault failOnce(Attempt attempt, Point point) {
            observation.requireOwned(attempt);
            requireInjectable(point);
            var fault = new Fault();
            attempt.on(point, () -> {
                if (fault.hits.incrementAndGet() == 1) {
                    fault.injected.set(true);
                    throw fault.sentinel;
                }
            });
            return fault;
        }

        /**
         * Bounded rendezvous, normally before the first DML after availability reads.
         * Share one gate between distinct attempts. Never gate after an INSERT holding a unique lock.
         */
        public Gate gateBeforeWrites(int participants) { return new Gate(observation, participants); }
    }

    public static final class Fault {
        private final InjectedFailure sentinel = new InjectedFailure();
        private final AtomicInteger hits = new AtomicInteger();
        private final AtomicBoolean injected = new AtomicBoolean();

        private Fault() { }
        public InjectedFailure sentinel() { return sentinel; }
        public int hits() { return hits.get(); }
        public boolean injected() { return injected.get(); }
        public void assertReached() {
            if (!injected() || hits() < 1) {
                throw new AssertionError("The requested test failure boundary was not reached");
            }
        }
    }

    public static final class Gate {
        private final CountDownLatch arrived;
        private final CountDownLatch release = new CountDownLatch(1);
        private final Set<Attempt> attached = java.util.concurrent.ConcurrentHashMap.newKeySet();
        private final int participants;
        private final Observation observation;

        private Gate(Observation observation, int participants) {
            if (participants < 2 || participants > 8) {
                throw new IllegalArgumentException("A test gate requires two to eight independent attempts");
            }
            this.participants = participants;
            this.observation = observation;
            arrived = new CountDownLatch(participants);
        }

        public synchronized void attach(Attempt attempt) {
            Objects.requireNonNull(attempt);
            observation.requireOwned(attempt);
            if (attached.size() >= participants || !attached.add(attempt)) {
                throw new IllegalStateException("Attach each participant exactly once before starting workers");
            }
            var firstWrite = new AtomicBoolean();
            // Restrict this gate to pre-INSERT, not every SQL call/second write/commit.
            attempt.on(Point.ACCOUNT_INSERT_BEFORE, () -> {
                if (firstWrite.compareAndSet(false, true)) {
                    arrived.countDown();
                    await(release);
                }
            });
        }

        public void awaitAll() { await(arrived); }
        public void release() { release.countDown(); }
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) { throw new AssertionError("Controlled test gate timed out"); }
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Controlled test gate was interrupted");
        }
    }

    private static void requireInjectable(Point point) {
        Objects.requireNonNull(point);
        // Completion/failure reporting and DELEGATED remain observational, never swallowed callbacks.
        switch (point) {
            case WORKER_ENTRY, WORKER_RETURNED, SQL_BEFORE, SQL_RETURNED,
                    ACCOUNT_INSERT_BEFORE, ACCOUNT_INSERT_RETURNED, ROLE_INSERT_BEFORE, ROLE_INSERT_RETURNED,
                    FLUSH_BEFORE, FLUSH_RETURNED, AUTO_FLUSH_BEFORE, AUTO_FLUSH_RETURNED,
                    BEFORE_COMMIT, JDBC_COMMIT_BEFORE_DELEGATE, JDBC_COMMIT_RETURNED -> { }
            default -> throw new IllegalArgumentException("This boundary is observation-only");
        }
    }
}
