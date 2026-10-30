package com.devsuperior.dscommerce.services;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreType;
import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.RequestScope;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;

/** Request-local, single-assignment holder; no clock capture or fallback during validation. */
@Component
@RequestScope
@JsonIgnoreType
public class RegistrationAttemptReference {

    @JsonIgnore
    private final AtomicReference<Snapshot> value = new AtomicReference<>();

    public void initialize(Instant instant) {
        var snapshot = new Snapshot(instant);
        if (!value.compareAndSet(null, snapshot)) {
            throw new IllegalStateException("Registration attempt reference is already initialized");
        }
    }

    @JsonIgnore
    public Snapshot snapshot() {
        var snapshot = value.get();
        if (snapshot == null) {
            throw new IllegalStateException("Registration attempt reference is not initialized");
        }
        return snapshot;
    }

    /** Explicit fixed reference for non-HTTP validation; does not resolve a request scope or read a clock. */
    public static RegistrationAttemptReference fixed(Instant instant) {
        var reference = new RegistrationAttemptReference();
        reference.initialize(instant);
        return reference;
    }

    @Override
    public String toString() {
        return "RegistrationAttemptReference[internal]";
    }

    @JsonIgnoreType
    public static final class Snapshot {
        @JsonIgnore
        private final Instant instant;
        @JsonIgnore
        private final LocalDate utcDate;

        private Snapshot(Instant instant) {
            if (instant == null) {
                throw new IllegalArgumentException("Invalid registration attempt reference");
            }
            this.instant = instant;
            try {
                this.utcDate = LocalDate.ofInstant(instant, ZoneOffset.UTC);
            } catch (DateTimeException failure) {
                throw new IllegalArgumentException("Invalid registration attempt reference");
            }
        }

        @JsonIgnore
        public Instant instant() {
            return instant;
        }

        @JsonIgnore
        public LocalDate utcDate() {
            return utcDate;
        }

        @Override
        public String toString() {
            return "RegistrationAttemptReference.Snapshot[internal]";
        }
    }
}
