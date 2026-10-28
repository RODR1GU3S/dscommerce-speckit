package com.devsuperior.dscommerce.controllers;

import com.devsuperior.dscommerce.config.CustomerRegistrationFailureInjectionConfig;
import com.devsuperior.dscommerce.config.CustomerRegistrationObservationConfig;
import com.devsuperior.dscommerce.config.CustomerRegistrationTestConfig.RootHttpContext;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.time.Duration;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@RootHttpContext
@Import({CustomerRegistrationObservationConfig.class, CustomerRegistrationFailureInjectionConfig.class})
class CustomerRegistrationTransportHttpIntegrationTest extends CustomerRegistrationTransportScenarios {
    @Override protected String expectedContextPath() { return ""; }
}

/** T074 authorship only. No delivery filter is imported by either concrete Transport child. */
abstract class CustomerRegistrationTransportScenarios extends CustomerRegistrationDeliveryStateSupport {
    @Test
    void unarmedSocketProxyDeliversTheRealUpstreamConfirmation() throws SQLException {
        exerciseProxy(false);
    }

    @Test
    void socketProxyClosesDownstreamAfterObservedCommitWithoutDeliveringConfirmation() throws SQLException {
        exerciseProxy(true);
    }

    private void exerciseProxy(boolean drop) throws SQLException {
        try (var observed = openObserved(getClass().getSimpleName() + (drop ? "-SOCKET_DROPPED" : "-SOCKET_CONTROL"));
             var proxy = new OneShotProxy(observed, uri("/customers"), drop)) {
            proxy.start();
            observed.submitted();
            byte[] received = rawClientExchange(proxy.port(), expectedContextPath() + "/customers",
                    observed.attempt().headerValue(), signupBody());
            proxy.recordClientBytes(received.length);
            var relay = proxy.awaitAndJoin();
            observed.finish();
            assertOriginalCommit(observed.events());
            assertThat(relay.upstream().statusCode()).isEqualTo(201);
            assertJson(relay.upstream());
            assertThat(relay.upstreamCalls()).as("one upstream POST, no proxy retry").isEqualTo(1);
            assertThat(relay.commitBeforeDelivery()).as("original commit observed before downstream write or close").isTrue();
            assertThat(relay.receivedBodyBytes() == relay.upstream().body().length && relay.receivedBodyBytes() > 0)
                    .as("proxy received a complete actual upstream confirmation body").isTrue();
            long id = confirmationId(relay.upstream().body());
            if (drop) {
                assertThat(received.length).as("client reached EOF without HTTP headers or confirmation bytes").isZero();
                assertThat(relay.downstreamWireBytes() == 0 && relay.downstreamBodyBytes() == 0
                        && relay.suppressedBodyBytes() == relay.receivedBodyBytes())
                        .as("proxy wrote no downstream bytes after receiving the committed confirmation").isTrue();
            } else {
                var reply = parseReply(received);
                assertThat(reply.status()).isEqualTo(201);
                assertThat(relay.upstream().headers().firstValue("Content-Type").orElse("")
                        .equals(reply.headers().get("content-type")))
                        .as("real upstream JSON content type is forwarded").isTrue();
                for (String header : Set.of("set-cookie", "authorization", "location")) {
                    assertThat(!reply.headers().containsKey(header)).as("no %s header", header).isTrue();
                }
                assertThat(Arrays.equals(reply.body(), relay.upstream().body())
                        && relay.downstreamBodyBytes() == reply.body().length
                        && relay.downstreamWireBytes() == received.length && relay.suppressedBodyBytes() == 0)
                        .as("positive control forwards the entire original body and accounts for received wire bytes").isTrue();
                assertThat(confirmationId(reply.body())).isEqualTo(id);
            }
            assertRetainedAccountLoginAndReplay(observed, id);
        }
    }

    /** Raw socket client: no automatic HTTP retry; timeout/IOException never counts as expected EOF. */
    private static byte[] rawClientExchange(int port, String path, String attempt, byte[] payload) {
        try (var socket = new Socket()) {
            socket.connect(new InetSocketAddress(InetAddress.getLoopbackAddress(), port), 5_000);
            socket.setSoTimeout(45_000);
            String header = "POST " + path + " HTTP/1.1\r\nHost: localhost:" + port
                    + "\r\nAccept: application/json\r\nContent-Type: application/json\r\nContent-Length: " + payload.length
                    + "\r\n" + CustomerRegistrationObservationConfig.HEADER + ": " + attempt + "\r\nConnection: close\r\n\r\n";
            var output = socket.getOutputStream();
            output.write(header.getBytes(StandardCharsets.US_ASCII));
            output.write(payload);
            output.flush();
            var received = new ByteArrayOutputStream();
            var input = socket.getInputStream();
            byte[] buffer = new byte[1_024];
            int count;
            while ((count = input.read(buffer)) != -1) {
                if (received.size() + count > 65_536) { throw new AssertionError("Controlled downstream response exceeded its bound"); }
                received.write(buffer, 0, count);
            }
            return received.toByteArray();
        } catch (IOException failure) {
            throw new AssertionError("Controlled downstream socket failed; no commit or delivery result is inferred");
        }
    }

    /** One accepted loopback connection, one real upstream HTTP call, no general-purpose proxy service. */
    private static final class OneShotProxy implements AutoCloseable {
        private final ObservedRequest observed;
        private final URI upstream;
        private final boolean drop;
        private final ServerSocket listener;
        private final ExecutorService executor = Executors.newSingleThreadExecutor();
        private final AtomicInteger calls = new AtomicInteger();
        private volatile Socket downstream;
        private volatile String stage = "CREATED";
        private volatile String guardFailure = "NONE";
        private volatile String joinOutcome = "NOT_JOINED";
        private volatile String causeType = "NONE";
        private volatile int upstreamStatus = -1;
        private volatile int upstreamBodyBytes = -1;
        private volatile boolean commitObserved;
        private volatile boolean actionReached;
        private volatile int downstreamWireBytes;
        private volatile int downstreamBodyBytes;
        private volatile int clientWireBytes = -1;
        private volatile long beforeDeliverySequence;
        private Future<Relay> job;

        private OneShotProxy(ObservedRequest observed, URI upstream, boolean drop) {
            this.observed = observed;
            this.upstream = upstream;
            this.drop = drop;
            try {
                listener = new ServerSocket();
            } catch (IOException failure) {
                executor.shutdownNow();
                throw new AssertionError("Controlled loopback listener could not be created");
            }
            try {
                if (!"http".equals(upstream.getScheme()) || !"localhost".equals(upstream.getHost())
                        || upstream.getPort() <= 0 || upstream.getQuery() != null) {
                    throw new AssertionError("Controlled proxy requires the selected local server");
                }
                listener.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0));
                listener.setSoTimeout(10_000);
            } catch (IOException | RuntimeException | Error failure) {
                closeListener();
                executor.shutdownNow();
                throw new AssertionError("Controlled loopback proxy preparation failed");
            }
        }

        int port() { return listener.getLocalPort(); }
        void recordClientBytes(int bytes) { clientWireBytes = bytes; }
        void start() {
            observed.actorStarted();
            job = executor.submit(this::relayOnce);
        }

        private Relay relayOnce() {
            stage = "ACCEPTING";
            try (var socket = listener.accept(); var client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NEVER)
                    .version(HttpClient.Version.HTTP_1_1).build()) {
                downstream = socket;
                socket.setSoTimeout(10_000);
                var input = socket.getInputStream();
                String header = readHeader(input);
                var lines = header.split("\r\n");
                if (!lines[0].equals("POST " + upstream.getRawPath() + " HTTP/1.1")) {
                    throw new AssertionError("Controlled proxy received an unexpected request target");
                }
                var headers = parseHeaders(lines, true);
                if (!"application/json".equals(headers.get("content-type"))
                        || !"application/json".equals(headers.get("accept"))
                        || !observed.attempt().headerValue().equals(headers.get(
                        CustomerRegistrationObservationConfig.HEADER.toLowerCase(Locale.ROOT)))
                        || headers.containsKey("transfer-encoding")) {
                    throw new AssertionError("Controlled proxy request framing or observation header is invalid");
                }
                int length = length(headers);
                if (length <= 0 || length > 8_192) { throw new AssertionError("Controlled request body length is invalid"); }
                byte[] payload = input.readNBytes(length);
                if (payload.length != length || calls.incrementAndGet() != 1) {
                    throw new AssertionError("Controlled proxy requires exactly one complete request");
                }
                var request = HttpRequest.newBuilder(upstream).timeout(Duration.ofSeconds(20))
                        .header("Accept", "application/json").header("Content-Type", "application/json")
                        .header(CustomerRegistrationObservationConfig.HEADER, observed.attempt().headerValue())
                        .POST(HttpRequest.BodyPublishers.ofByteArray(payload)).build();
                stage = "UPSTREAM_REQUEST";
                var response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
                upstreamStatus = response.statusCode();
                upstreamBodyBytes = response.body().length;
                stage = "UPSTREAM_RECEIVED";
                observed.attempt().awaitFinished();
                stage = "ATTEMPT_FINISHED";
                boolean commit = committed(observed.attempt().events());
                commitObserved = commit;
                if (response.statusCode() != 201 || response.body().length == 0 || response.body().length > 4_096 || !commit) {
                    guardFailure = response.statusCode() != 201 ? "UPSTREAM_STATUS"
                            : response.body().length == 0 || response.body().length > 4_096 ? "UPSTREAM_LENGTH" : "COMMIT_PREDICATE_FALSE";
                    throw new AssertionError("Original committed success was not received before controlled transport action");
                }
                assertOriginalCommit(observed.attempt().events());
                beforeDeliverySequence = observed.attempt().events().getLast().sequence();
                stage = "COMMIT_VERIFIED";
                int wireBytes = 0;
                int bodyBytes = 0;
                if (!drop) {
                    actionReached = true;
                    byte[] forwardHeader = forwardedHeader(response);
                    var output = socket.getOutputStream();
                    output.write(forwardHeader);
                    output.write(response.body());
                    output.flush();
                    wireBytes = forwardHeader.length + response.body().length;
                    bodyBytes = response.body().length;
                    downstreamWireBytes = wireBytes;
                    downstreamBodyBytes = bodyBytes;
                }
                if (drop) { actionReached = true; }
                stage = drop ? "DELIBERATE_CLOSE" : "FORWARDED";
                // Socket close follows original commit AND complete upstream response; drop writes nothing.
                return new Relay(response, response.body().length, wireBytes, bodyBytes, commit, calls.get());
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new AssertionError("Controlled proxy upstream wait was interrupted");
            } catch (IOException failure) {
                throw new AssertionError("Controlled proxy upstream transport did not complete");
            } finally { downstream = null; }
        }

        Relay awaitAndJoin() {
            try {
                var result = job.get(35, TimeUnit.SECONDS);
                executor.shutdown();
                boolean stopped = executor.awaitTermination(10, TimeUnit.SECONDS);
                observed.actorsStopped(stopped);
                if (!stopped) { throw new AssertionError("Controlled proxy worker did not terminate"); }
                joinOutcome = "JOINED";
                return result;
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new AssertionError("Controlled proxy join was interrupted");
            } catch (ExecutionException failure) {
                joinOutcome = "EXECUTION_EXCEPTION";
                causeType = failure.getCause() == null ? "NONE" : failure.getCause().getClass().getName();
                throw new AssertionError("Controlled proxy did not complete; no delivery outcome is inferred");
            } catch (TimeoutException failure) {
                joinOutcome = "TIMEOUT";
                throw new AssertionError("Controlled proxy did not complete; no delivery outcome is inferred");
            }
        }

        @Override public void close() {
            closeListener();
            var accepted = downstream;
            if (accepted != null) {
                try { accepted.close(); } catch (IOException ignored) { /* Join still required below. */ }
            }
            if (job != null && !job.isDone()) { job.cancel(true); }
            executor.shutdownNow();
            boolean stopped;
            try { stopped = executor.awaitTermination(10, TimeUnit.SECONDS); }
            catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); stopped = false; }
            observed.actorsStopped(stopped);
            auditDelivery(observed.label(), "mechanism=SOCKET\nstage=" + stage + "\nguardFailure=" + guardFailure
                    + "\njoinOutcome=" + joinOutcome + "\ncauseType=" + causeType + "\nupstreamStatus=" + upstreamStatus
                    + "\nreceivedBodyBytes=" + upstreamBodyBytes + "\ncommitPredicate=" + commitObserved
                    + "\nactionReached=" + actionReached + "\nactorsStopped=" + stopped
                    + "\nbeforeDeliverySequence=" + beforeDeliverySequence + "\ndownstreamWireBytes=" + downstreamWireBytes
                    + "\ndeliveredBodyBytes=" + downstreamBodyBytes + "\nclientWireBytes=" + clientWireBytes
                    + "\nsuppressedBodyBytes=" + (actionReached ? upstreamBodyBytes - downstreamBodyBytes : -1) + "\n");
            if (!stopped) { throw new AssertionError("Controlled proxy termination is unproved; cleanup must remain withheld"); }
        }

        private void closeListener() {
            try { listener.close(); } catch (IOException ignored) { /* No new requests; worker join remains mandatory. */ }
        }
        @Override public String toString() { return "ControlledLoopbackProxy[redacted]"; }
    }

    private static byte[] forwardedHeader(HttpResponse<byte[]> upstream) {
        var header = new StringBuilder("HTTP/1.1 201 Created\r\n");
        upstream.headers().map().forEach((name, values) -> {
            if (!Set.of("content-length", "transfer-encoding", "connection", "keep-alive", "trailer", "upgrade")
                    .contains(name.toLowerCase(Locale.ROOT))) {
                for (String value : values) {
                    if (name.contains("\r") || name.contains("\n") || value.contains("\r") || value.contains("\n")) {
                        throw new AssertionError("Controlled upstream response header is invalid");
                    }
                    header.append(name).append(": ").append(value).append("\r\n");
                }
            }
        });
        header.append("Content-Length: ").append(upstream.body().length).append("\r\nConnection: close\r\n\r\n");
        return header.toString().getBytes(StandardCharsets.ISO_8859_1);
    }

    private static String readHeader(InputStream input) throws IOException {
        var bytes = new ByteArrayOutputStream();
        int tail = 0;
        while (bytes.size() < 16_384) {
            int value = input.read();
            if (value < 0) { throw new AssertionError("Controlled HTTP headers ended early"); }
            bytes.write(value);
            tail = (tail << 8) | value;
            if (tail == 0x0D0A0D0A) { return bytes.toString(StandardCharsets.ISO_8859_1); }
        }
        throw new AssertionError("Controlled HTTP headers exceeded their bound");
    }

    private static Map<String, String> parseHeaders(String[] lines, boolean unique) {
        var headers = new LinkedHashMap<String, String>();
        for (int index = 1; index < lines.length; index++) {
            if (lines[index].isEmpty()) { continue; }
            int colon = lines[index].indexOf(':');
            if (colon <= 0) { throw new AssertionError("Controlled HTTP header is malformed"); }
            String name = lines[index].substring(0, colon).toLowerCase(Locale.ROOT);
            String value = lines[index].substring(colon + 1).trim();
            if (unique && headers.containsKey(name)) { throw new AssertionError("Controlled request has duplicate headers"); }
            headers.merge(name, value, (previous, next) -> previous + "," + next);
        }
        return headers;
    }

    private static int length(Map<String, String> headers) {
        try { return Integer.parseInt(headers.getOrDefault("content-length", "")); }
        catch (NumberFormatException invalid) { throw new AssertionError("Controlled Content-Length is invalid"); }
    }

    private static RawReply parseReply(byte[] wire) {
        int end = -1;
        for (int index = 0; index + 3 < wire.length; index++) {
            if (wire[index] == 13 && wire[index + 1] == 10 && wire[index + 2] == 13 && wire[index + 3] == 10) {
                end = index + 4; break;
            }
        }
        if (end < 0 || end > 16_384) { throw new AssertionError("Controlled response headers are missing or too large"); }
        var lines = new String(wire, 0, end, StandardCharsets.ISO_8859_1).split("\r\n");
        if (!"HTTP/1.1 201 Created".equals(lines[0])) { throw new AssertionError("Controlled positive response status is unexpected"); }
        var headers = parseHeaders(lines, false);
        byte[] body = Arrays.copyOfRange(wire, end, wire.length);
        if (length(headers) != body.length || body.length == 0) { throw new AssertionError("Controlled response body is incomplete"); }
        return new RawReply(201, headers, body);
    }

    private record Relay(HttpResponse<byte[]> upstream, int receivedBodyBytes, int downstreamWireBytes,
                         int downstreamBodyBytes, boolean commitBeforeDelivery, int upstreamCalls) {
        int suppressedBodyBytes() { return receivedBodyBytes - downstreamBodyBytes; }
        @Override public String toString() { return "ControlledRelay[redacted]"; }
    }
    private record RawReply(int status, Map<String, String> headers, byte[] body) {
        @Override public String toString() { return "ControlledRawReply[redacted]"; }
    }
}
