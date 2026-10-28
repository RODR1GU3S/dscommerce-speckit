package com.devsuperior.dscommerce.config;

import com.devsuperior.dscommerce.config.CustomerRegistrationObservationConfig.Event;
import com.devsuperior.dscommerce.config.CustomerRegistrationObservationConfig.Point;
import com.devsuperior.dscommerce.config.CustomerRegistrationObservationConfig.SqlKind;
import org.springframework.transaction.support.TransactionSynchronization;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** Optional local evidence: enum/transaction identities only, never SQL, binds, DTOs or exceptions. */
public final class CustomerRegistrationTransactionOracle {
    private CustomerRegistrationTransactionOracle() { }

    /** Validate the earlier read transaction rather than silently discarding its commit. */
    public static List<Event> workerEvents(List<Event> events) {
        var entries = events.stream().filter(event -> event.point() == Point.PROXY_ENTRY).toList();
        assertThat(entries.size()).as("at most one registration proxy entry").isLessThanOrEqualTo(1);
        long start = entries.isEmpty() ? Long.MAX_VALUE : entries.getFirst().sequence();
        var before = events.stream().filter(event -> event.sequence() < start).toList();
        assertThat(before.stream().noneMatch(event -> Set.of(SqlKind.INSERT, SqlKind.UPDATE, SqlKind.DELETE)
                .contains(event.sqlKind()))).as("no domain DML before the worker").isTrue();
        var metadataEntries = before.stream().filter(event -> event.point() == Point.METADATA_ENTRY).toList();
        assertThat(metadataEntries.size()).as("at most one healthy metadata acquisition").isLessThanOrEqualTo(1);
        if (!entries.isEmpty()) { assertThat(metadataEntries).as("metadata captured before registration").hasSize(1); }
        if (!metadataEntries.isEmpty()) {
            var returned = before.stream().filter(event -> event.point() == Point.METADATA_RETURNED).toList();
            assertThat(returned).as("metadata proxy completed before the worker").hasSize(1);
            var metadata = before.stream().filter(event -> event.sequence() > metadataEntries.getFirst().sequence()
                    && event.sequence() < returned.getFirst().sequence()).toList();
            var commits = metadata.stream().filter(event -> event.point() == Point.JDBC_COMMIT_DELEGATED).toList();
            assertThat(commits).as("one legitimate metadata transaction commit").hasSize(1);
            var commit = commits.getFirst();
            assertThat(commit.transactionActive() && commit.readOnly() && !commit.autoCommit()
                    && commit.transactionId() > 0 && commit.connectionId() > 0 && commit.sessionId() > 0)
                    .as("metadata commit belongs to an identified read-only physical transaction").isTrue();
            assertThat(metadata.stream().allMatch(event -> event.transactionId() == commit.transactionId()
                    && event.connectionId() == commit.connectionId() && event.sessionId() == commit.sessionId()))
                    .as("metadata SQL and completion keep their own transaction and connection").isTrue();
            for (Point point : List.of(Point.JDBC_COMMIT_RETURNED, Point.AFTER_COMMIT, Point.AFTER_COMPLETION)) {
                assertThat(metadata.stream().filter(event -> event.point() == point).count()).as("metadata %s", point).isEqualTo(1);
            }
            assertThat(metadata.stream().filter(event -> event.point() == Point.AFTER_COMPLETION)
                    .allMatch(event -> event.completion() == TransactionSynchronization.STATUS_COMMITTED)).isTrue();
            assertThat(metadata.stream().anyMatch(event -> event.point() == Point.SQL_RETURNED
                    && event.sqlKind() == SqlKind.SELECT)).as("real metadata SQL returned").isTrue();
            assertThat(metadata.stream().noneMatch(event -> Set.of(Point.SQL_FAILED, Point.JDBC_COMMIT_FAILED,
                    Point.JDBC_ROLLBACK_BEFORE_DELEGATE).contains(event.point()))).isTrue();
            if (!entries.isEmpty()) {
                var worker = events.stream().filter(event -> event.point() == Point.WORKER_ENTRY).findFirst().orElseThrow();
                assertThat(worker.transactionId()).as("registration is a later distinct transaction").isGreaterThan(commit.transactionId());
                assertThat(worker.connectionId()).as("separate pool borrow for registration").isNotEqualTo(commit.connectionId());
                // A pool may legitimately reuse the physical session after the read transaction has ended.
            }
        }
        return entries.isEmpty() ? List.of() : events.stream().filter(event -> event.sequence() >= start).toList();
    }

    public static void completed(String label) {
        String directory = System.getProperty("feature009.oracle.auditDirectory");
        if (directory == null) { return; }
        if (!label.matches("[A-Za-z0-9._-]+")) { throw new AssertionError("Invalid oracle evidence label"); }
        try {
            Files.createDirectories(Path.of(directory));
            Files.writeString(Path.of(directory).resolve(label + ".assertions-reached"),
                    "All scenario assertions, including final independent persistence oracles, reached.\n", StandardCharsets.UTF_8);
        } catch (IOException failure) { throw new AssertionError("Could not preserve assertion completion"); }
    }

    public static void audit(String label, List<Event> events) {
        String directory = System.getProperty("feature009.oracle.auditDirectory");
        if (directory == null) { return; }
        if (!label.matches("[A-Za-z0-9._-]+")) { throw new AssertionError("Invalid oracle evidence label"); }
        var text = new StringBuilder("sequence\tpoint\tthread\ttransaction\tconnection\tsession\tactive\treadOnly\tautoCommit\tisolation\tkind\ttable\tspringCompletion\n");
        for (var event : events) {
            text.append(event.sequence()).append('\t').append(event.point()).append('\t')
                    .append(event.threadId()).append('\t').append(event.transactionId()).append('\t')
                    .append(event.connectionId()).append('\t').append(event.sessionId()).append('\t')
                    .append(event.transactionActive()).append('\t').append(event.readOnly()).append('\t')
                    .append(event.autoCommit()).append('\t').append(event.isolation()).append('\t')
                    .append(event.sqlKind()).append('\t').append(event.table()).append('\t')
                    .append(event.completion()).append('\n');
        }
        try {
            Files.createDirectories(Path.of(directory));
            Files.writeString(Path.of(directory).resolve(label + ".tsv"), text, StandardCharsets.UTF_8);
        } catch (IOException failure) { throw new AssertionError("Could not preserve oracle observation"); }
    }
}
