package com.devsuperior.dscommerce.controllers;

import com.devsuperior.dscommerce.config.PaymentFailureInjectionTestConfig;
import com.devsuperior.dscommerce.config.PaymentFailureInjectionTestConfig.Run;
import com.devsuperior.dscommerce.config.PaymentRegistrationTestConfig.RootHttpContext;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.context.annotation.Import;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/** T025 authorship. Local HTTP/1.1 sockets, not MockMvc or a physical network/database outage. ROOT only. */
@RootHttpContext
@Import(PaymentFailureInjectionTestConfig.class)
@Timeout(value = 120, unit = TimeUnit.SECONDS)
class PaymentResponseTransportHttpIntegrationTest extends PaymentResponseRecoverySupport {
    @ParameterizedTest(name = "real socket proxy response {0}")
    @EnumSource(DeliveryMode.class)
    void proxyReceivesCommittedConfirmationThenForwardsOrClosesWithoutDeliveringIt(DeliveryMode mode) throws Exception {
        var before = database.snapshot();
        try (var run = unarmed(); var proxy = new OneShotProxy(port, paymentPath(), mode, run, frame -> {
            assertThat(frame.status()).isEqualTo(201);
            assertThat(frame.headers().get("content-type")).contains("application/json");
            assertThat(frame.headers()).doesNotContainKeys("location", "www-authenticate");
            assertConfirmation(frame.body());
            run.requireOutsideTransaction();
            assertCommittedSettlement(before, run.state());
        })) {
            var body = paymentBody();
            byte[] downstream;
            try (var client = new Socket()) {
                client.connect(new InetSocketAddress(InetAddress.getLoopbackAddress(), proxy.port()), 2000);
                var head = "POST " + paymentPath() + " HTTP/1.1\r\nHost: localhost:" + port
                        + "\r\nAuthorization: Bearer " + payerToken + "\r\n"
                        + PaymentFailureInjectionTestConfig.HEADER + ": " + run.headerValue()
                        + "\r\nAccept: application/json\r\nContent-Type: application/json\r\nContent-Length: "
                        + body.length + "\r\nConnection: close\r\n\r\n";
                client.getOutputStream().write(head.getBytes(StandardCharsets.ISO_8859_1));
                client.getOutputStream().write(body);
                client.getOutputStream().flush();
                // A raw client makes no automatic retry. EOF with zero bytes is required for WITHHOLD.
                downstream = readToEof(client, Duration.ofSeconds(12), MAX_WIRE);
            }
            var observation = proxy.await(); // Exceptions/timeouts are preparation failures, never expected delivery loss.
            run.requireOutsideTransaction();
            assertThat(observation.requestBodyBytes()).isEqualTo(body.length);
            assertThat(observation.upstreamWireBytes()).isPositive();
            assertThat(observation.upstreamBodyBytes()).isPositive();
            assertThat(observation.receivedAt()).isLessThanOrEqualTo(observation.commitObservedAt());
            assertThat(observation.commitObservedAt()).isLessThanOrEqualTo(observation.deliveryFinishedAt());
            if (mode == DeliveryMode.WITHHOLD) {
                assertThat(observation.forwardedWireBytes()).isZero();
                assertThat(downstream).isEmpty();
            } else {
                assertThat(observation.forwardedWireBytes()).isEqualTo(observation.upstreamWireBytes());
                assertThat(downstream.length).isEqualTo(observation.upstreamWireBytes());
                var received = parseResponse(downstream);
                assertThat(received.status()).isEqualTo(201);
                assertConfirmation(received.body());
                assertThat(received.body().length).isEqualTo(observation.upstreamBodyBytes());
            }
            assertCommittedSettlement(before, run.state());
            assertRecoveryWithoutReusingSuccess();
            System.out.println("T025_SOCKET context=ROOT mode=" + mode + " requestBodyBytes=" + observation.requestBodyBytes()
                    + " upstreamWireBytes=" + observation.upstreamWireBytes() + " upstreamBodyBytes=" + observation.upstreamBodyBytes()
                    + " forwardedWireBytes=" + observation.forwardedWireBytes() + " clientWireBytes=" + downstream.length
                    + " receivedAtNanos=" + observation.receivedAt() + " commitObservedAtNanos=" + observation.commitObservedAt()
                    + " deliveryFinishedAtNanos=" + observation.deliveryFinishedAt()
                    + " elapsedNanos=" + observation.elapsedNanos() + " commit=1 repeatedPost=409 ownerGet=PAID");
        }
    }

    private static final int MAX_HEADERS = 16384, MAX_WIRE = 65536;
    @FunctionalInterface private interface ReceivedProof { void verify(ResponseFrame response) throws Exception; }
    private record ResponseFrame(int status, Map<String, String> headers, byte[] body) { }
    private record TransportObservation(int requestBodyBytes, int upstreamWireBytes, int upstreamBodyBytes,
                                        int forwardedWireBytes, long receivedAt, long commitObservedAt,
                                        long deliveryFinishedAt, long elapsedNanos) { }

    /** Exactly one accepted request, one upstream request, no retry, no sleep and no external address. */
    private static final class OneShotProxy implements AutoCloseable, Callable<TransportObservation> {
        private final int upstreamPort;
        private final String path;
        private final DeliveryMode mode;
        private final Run run;
        private final ReceivedProof proof;
        private final ServerSocket listener;
        private final ExecutorService worker;
        private final Future<TransportObservation> result;
        private volatile Socket downstream, upstream;
        private boolean closing;

        OneShotProxy(int upstreamPort, String path, DeliveryMode mode, Run run, ReceivedProof proof) throws IOException {
            this.upstreamPort = upstreamPort; this.path = path; this.mode = mode; this.run = run; this.proof = proof;
            listener = new ServerSocket();
            try {
                listener.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 1);
                listener.setSoTimeout(8000);
                worker = Executors.newSingleThreadExecutor();
                try { result = worker.submit(this); }
                catch (RuntimeException | Error failure) { worker.shutdownNow(); throw failure; }
            } catch (IOException | RuntimeException | Error failure) {
                try { listener.close(); } catch (IOException closeFailure) { failure.addSuppressed(closeFailure); }
                throw failure;
            }
        }
        int port() { return listener.getLocalPort(); }
        TransportObservation await() throws Exception {
            try { return result.get(12, TimeUnit.SECONDS); }
            catch (InterruptedException failure) { Thread.currentThread().interrupt(); throw failure; }
        }

        private synchronized void own(Socket socket, boolean isUpstream) throws IOException {
            if (closing) { socket.close(); throw new IOException("T025 proxy was closed during preparation"); }
            if (isUpstream) { upstream = socket; } else { downstream = socket; }
        }

        @Override public TransportObservation call() throws Exception {
            var started = System.nanoTime();
            try (var client = listener.accept()) {
                own(client, false);
                var request = readRequest(client, path, run.headerValue());
                try (var server = new Socket()) {
                    own(server, true);
                    server.connect(new InetSocketAddress(InetAddress.getLoopbackAddress(), upstreamPort), 2000);
                    server.getOutputStream().write(request.wire());
                    server.getOutputStream().flush();
                    // The upstream receives Connection: close; EOF bounds the entire framing, including chunk terminator.
                    var wire = readToEof(server, Duration.ofSeconds(8), MAX_WIRE);
                    var frame = parseResponse(wire);
                    long receivedAt = System.nanoTime();
                    proof.verify(frame); // Requires real original commit and independent database state before any delivery.
                    long commitObservedAt = System.nanoTime();
                    int forwarded = 0;
                    if (mode == DeliveryMode.FORWARD) {
                        client.getOutputStream().write(wire);
                        client.getOutputStream().flush();
                        forwarded = wire.length;
                    }
                    // WITHHOLD writes no response status, header or body byte to the downstream socket.
                    client.shutdownOutput();
                    long finished = System.nanoTime();
                    return new TransportObservation(request.bodyBytes(), wire.length, frame.body().length,
                            forwarded, receivedAt, commitObservedAt, finished, finished - started);
                }
            } finally { listener.close(); }
        }

        @Override public void close() throws Exception {
            IOException closeFailure = null;
            synchronized (this) {
                closing = true;
                try { listener.close(); } catch (IOException failure) { closeFailure = failure; }
                for (var socket : new Socket[]{downstream, upstream}) {
                    if (socket != null) {
                        try { socket.close(); }
                        catch (IOException failure) { if (closeFailure == null) { closeFailure = failure; } else { closeFailure.addSuppressed(failure); } }
                    }
                }
            }
            result.cancel(true);
            worker.shutdownNow();
            try { assertThat(worker.awaitTermination(3, TimeUnit.SECONDS)).as("Proxy worker must terminate before fixture cleanup").isTrue(); }
            catch (InterruptedException failure) { Thread.currentThread().interrupt(); throw failure; }
            if (closeFailure != null) { throw closeFailure; }
        }
    }

    private record RequestFrame(byte[] wire, int bodyBytes) { }
    private static RequestFrame readRequest(Socket socket, String path, String scope) throws IOException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(8);
        var bytes = new ByteArrayOutputStream();
        var input = socket.getInputStream();
        while (true) {
            setRemainingTimeout(socket, deadline);
            int next = input.read();
            if (next < 0) { throw new EOFException("T025 incomplete downstream request headers"); }
            bytes.write(next);
            if (bytes.size() > MAX_HEADERS) { throw new IOException("T025 request headers exceed bound"); }
            var content = bytes.toByteArray();
            if (content.length >= 4 && content[content.length - 4] == '\r' && content[content.length - 3] == '\n'
                    && content[content.length - 2] == '\r' && content[content.length - 1] == '\n') { break; }
        }
        String head = bytes.toString(StandardCharsets.ISO_8859_1);
        String[] lines = head.substring(0, head.length() - 4).split("\r\n");
        // Do not include raw request headers in diagnostics: they contain the real disposable credential.
        if (!lines[0].equals("POST " + path + " HTTP/1.1")) { throw new IOException("T025 unexpected request line"); }
        var headers = headers(lines);
        if (!scope.equals(headers.get(PaymentFailureInjectionTestConfig.HEADER.toLowerCase(Locale.ROOT)))
                || !"close".equalsIgnoreCase(headers.get("connection")) || headers.containsKey("transfer-encoding")) {
            throw new IOException("T025 unexpected request scope/framing");
        }
        int length = decimalLength(headers.get("content-length"), 2048);
        byte[] body = new byte[length];
        int read = 0;
        while (read < length) {
            setRemainingTimeout(socket, deadline);
            int count = input.read(body, read, length - read);
            if (count < 0) { throw new EOFException("T025 incomplete downstream request body"); }
            read += count;
        }
        bytes.write(body);
        return new RequestFrame(bytes.toByteArray(), length);
    }

    private static byte[] readToEof(Socket socket, Duration limit, int max) throws IOException {
        long deadline = System.nanoTime() + limit.toNanos();
        var bytes = new ByteArrayOutputStream();
        var buffer = new byte[2048];
        while (true) {
            setRemainingTimeout(socket, deadline);
            int count = socket.getInputStream().read(buffer);
            if (count < 0) { return bytes.toByteArray(); }
            if (bytes.size() + count > max) { throw new IOException("T025 HTTP wire exceeds bound"); }
            bytes.write(buffer, 0, count);
        }
    }
    private static void setRemainingTimeout(Socket socket, long deadline) throws IOException {
        long nanos = deadline - System.nanoTime();
        if (nanos <= 0) { throw new SocketTimeoutException("T025 bounded socket observation expired"); }
        socket.setSoTimeout((int) Math.min(Integer.MAX_VALUE, Math.max(1, TimeUnit.NANOSECONDS.toMillis(nanos))));
    }

    /** Small strict test-only HTTP/1.1 framing reader; no token/header dump and no numeric coercion. */
    private static ResponseFrame parseResponse(byte[] wire) throws IOException {
        int end = -1;
        for (int i = 0; i + 3 < wire.length && i < MAX_HEADERS; i++) {
            if (wire[i] == '\r' && wire[i + 1] == '\n' && wire[i + 2] == '\r' && wire[i + 3] == '\n') { end = i; break; }
        }
        if (end < 0) { throw new IOException("T025 missing complete upstream response headers"); }
        String[] lines = new String(wire, 0, end, StandardCharsets.ISO_8859_1).split("\r\n");
        if (!lines[0].matches("HTTP/1\\.1 [0-9]{3}(?: .*)?")) { throw new IOException("T025 unexpected upstream status framing"); }
        int status = Integer.parseInt(lines[0].substring(9, 12));
        var headers = headers(lines);
        byte[] encoded = Arrays.copyOfRange(wire, end + 4, wire.length);
        byte[] body;
        if (headers.containsKey("transfer-encoding")) {
            if (!"chunked".equalsIgnoreCase(headers.get("transfer-encoding")) || headers.containsKey("content-length")) {
                throw new IOException("T025 unsupported/ambiguous upstream framing");
            }
            body = decodeChunks(encoded);
        } else if (headers.containsKey("content-length")) {
            if (decimalLength(headers.get("content-length"), MAX_WIRE) != encoded.length) {
                throw new IOException("T025 incomplete/extra upstream content-length bytes");
            }
            body = encoded;
        } else {
            if (!"close".equalsIgnoreCase(headers.get("connection"))) { throw new IOException("T025 missing response framing"); }
            body = encoded;
        }
        return new ResponseFrame(status, Map.copyOf(headers), body);
    }
    private static Map<String, String> headers(String[] lines) throws IOException {
        var result = new LinkedHashMap<String, String>();
        for (int i = 1; i < lines.length; i++) {
            int colon = lines[i].indexOf(':');
            if (colon <= 0) { throw new IOException("T025 malformed HTTP header"); }
            var name = lines[i].substring(0, colon).toLowerCase(Locale.ROOT);
            if (!name.matches("[a-z0-9-]+")) { throw new IOException("T025 unsupported HTTP header name"); }
            if (result.containsKey(name) && List.of("content-length", "transfer-encoding", "connection", "host",
                    "authorization", "content-type", PaymentFailureInjectionTestConfig.HEADER.toLowerCase(Locale.ROOT)).contains(name)) {
                throw new IOException("T025 duplicate framing/scope header");
            }
            // Security/CORS may emit several legal Vary headers; only framing/scope duplicates are ambiguous here.
            result.merge(name, lines[i].substring(colon + 1).trim(), (previous, next) -> previous + ", " + next);
        }
        return result;
    }
    private static int decimalLength(String value, int max) throws IOException {
        if (value == null || !value.matches("[0-9]{1,8}")) { throw new IOException("T025 invalid content length"); }
        int length = Integer.parseInt(value);
        if (length > max) { throw new IOException("T025 content length exceeds bound"); }
        return length;
    }
    private static byte[] decodeChunks(byte[] encoded) throws IOException {
        var input = new ByteArrayInputStream(encoded);
        var body = new ByteArrayOutputStream();
        while (true) {
            var size = line(input);
            if (!size.matches("[0-9a-fA-F]{1,8}")) { throw new IOException("T025 unsupported chunk size"); }
            long length = Long.parseLong(size, 16);
            if (length > MAX_WIRE || body.size() + length > MAX_WIRE) { throw new IOException("T025 chunk exceeds bound"); }
            if (length == 0) {
                if (!line(input).isEmpty() || input.available() != 0) { throw new IOException("T025 unsupported trailers/extra response bytes"); }
                return body.toByteArray();
            }
            byte[] chunk = input.readNBytes((int) length);
            if (chunk.length != length || input.read() != '\r' || input.read() != '\n') { throw new IOException("T025 incomplete chunk"); }
            body.write(chunk);
        }
    }
    private static String line(InputStream input) throws IOException {
        var bytes = new ByteArrayOutputStream();
        while (bytes.size() < MAX_HEADERS) {
            int next = input.read();
            if (next < 0) { throw new EOFException("T025 incomplete chunk line"); }
            if (next == '\r') {
                if (input.read() != '\n') { throw new IOException("T025 malformed chunk line"); }
                return bytes.toString(StandardCharsets.ISO_8859_1);
            }
            bytes.write(next);
        }
        throw new IOException("T025 chunk line exceeds bound");
    }
}
