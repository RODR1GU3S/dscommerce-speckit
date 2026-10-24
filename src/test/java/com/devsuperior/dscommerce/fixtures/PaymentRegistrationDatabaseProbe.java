package com.devsuperior.dscommerce.fixtures;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Independent committed observations: the seven tables of probe007 plus the real tb_payment.
 * Composed reads use separate connections, not one atomic snapshot during concurrent writes.
 * Finish all writers first and keep setup/login/cleanup outside the observation window.
 * No component stereotype, production payment type, DDL or missing-table-as-empty fallback.
 */
public final class PaymentRegistrationDatabaseProbe {

    private final OwnOrderQueryDatabaseProbe previous;

    public PaymentRegistrationDatabaseProbe(String databaseUrl) {
        // Preserve the existing unique-H2 guard and opaque account/credential observation.
        previous = new OwnOrderQueryDatabaseProbe(databaseUrl);
    }

    /** Caller closes each new physical driver connection with try-with-resources. */
    public Connection openConnection() throws SQLException {
        return previous.openConnection();
    }

    /**
     * Strict functional snapshot: tb_payment is always queried, including when it has zero rows.
     * For the initial route RED, assert the expected HTTP result BEFORE calling this method;
     * do not let a missing table replace the absent-operation assertion with a setup failure.
     */
    public Snapshot snapshot() {
        var payments = readPayments();
        return new Snapshot(previous.snapshot(), payments);
    }

    private List<PaymentRow> readPayments() {
        try (var connection = openConnection()) {
            if (connection.getTransactionIsolation() != Connection.TRANSACTION_READ_COMMITTED) {
                throw new IllegalStateException("Independent payment connection must use READ_COMMITTED");
            }
            connection.setReadOnly(true);
            connection.setAutoCommit(false);
            try {
                var payments = new ArrayList<PaymentRow>();
                try (var statement = connection.prepareStatement(
                        "select order_id, occurred_at from tb_payment order by order_id");
                     var rows = statement.executeQuery()) {
                    while (rows.next()) {
                        var occurredAt = rows.getObject("occurred_at", OffsetDateTime.class);
                        if (occurredAt == null) {
                            throw new IllegalStateException("Observed tb_payment.occurred_at must not be null");
                        }
                        payments.add(new PaymentRow(rows.getLong("order_id"), occurredAt.toInstant()));
                    }
                }
                connection.commit();
                return List.copyOf(payments);
            } catch (SQLException | RuntimeException | Error failure) {
                try { connection.rollback(); } catch (SQLException rollbackFailure) {
                    failure.addSuppressed(rollbackFailure);
                }
                throw failure;
            }
        } catch (SQLException failure) {
            // No SQL, account values, credentials or driver cause in assertion diagnostics.
            // In particular, absent tb_payment is an explicit failure, never an empty list.
            throw new IllegalStateException("Payment snapshot requires the readable real tb_payment(order_id, occurred_at) schema");
        }
    }

    /** Safe comparison diagnostics: table names only, with no account values or password hashes. */
    public static void assertUnchanged(Snapshot before, Snapshot after) {
        Objects.requireNonNull(before);
        Objects.requireNonNull(after);
        if (!before.payments().equals(after.payments())) {
            throw new AssertionError("Observed database state changed: tb_payment"
                    + (before.previous().equals(after.previous()) ? "" : ", tables observed by probe007"));
        }
        OwnOrderQueryDatabaseProbe.assertUnchanged(before.previous(), after.previous());
    }

    public record PaymentRow(long orderId, Instant occurredAt) {
        public PaymentRow {
            Objects.requireNonNull(occurredAt);
        }
    }

    public record Snapshot(OwnOrderQueryDatabaseProbe.Snapshot previous, List<PaymentRow> payments) {
        public Snapshot {
            Objects.requireNonNull(previous);
            payments = List.copyOf(payments);
        }

        @Override
        public String toString() {
            return "PaymentRegistrationSnapshot[previous=" + previous + ", payments=" + payments.size()
                    + ", credentials=redacted]";
        }
    }
}
