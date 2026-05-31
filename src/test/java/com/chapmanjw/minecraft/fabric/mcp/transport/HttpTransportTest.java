package com.chapmanjw.minecraft.fabric.mcp.transport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.chapmanjw.minecraft.fabric.mcp.config.Config;

/**
 * Integration-style tests for the embedded {@link HttpTransport}. These spin up a real
 * {@code com.sun.net.httpserver.HttpServer} on an ephemeral port and exercise the routing,
 * security, and rate-limiting paths via a real {@link HttpClient}.
 *
 * <p>This is the only way to verify the transport's main {@code dispatch} method end-to-end
 * without faking out {@code HttpExchange}, which is sealed against the JDK internals.
 */
class HttpTransportTest {

    private HttpTransport transport;
    private Config config;
    private int port;

    @BeforeEach
    void setUp() throws IOException {
        // Pick a free port up front to avoid the start() retry dance.
        try (ServerSocket s = new ServerSocket(0)) {
            port = s.getLocalPort();
        }
        config =
                new Config(
                        "127.0.0.1",
                        port,
                        false,
                        null,
                        false,
                        List.of(),
                        15000L,
                        600,
                        4096,
                        16,
                        1,
                        "info",
                        null,
                        null,
                        false,
                        List.of(),
                        List.of(),
                        "write",
                        false);
        transport = new HttpTransport(config);
    }

    @AfterEach
    void tearDown() {
        if (transport != null) {
            transport.stop();
        }
    }

    private HttpClient newClient() {
        return HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
    }

    @Test
    void healthEndpointBypassesSecurity() throws Exception {
        // No registered route AND no Host header — should still return 200.
        transport.start();
        var req =
                java.net.http.HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/healthz"))
                        .GET()
                        .build();
        var resp = newClient().send(req, BodyHandlers.ofString());
        assertEquals(200, resp.statusCode());
        assertTrue(resp.body().contains("\"status\":\"ok\""), resp.body());
    }

    @Test
    void registeredRouteAnswers() throws Exception {
        // The JDK HttpClient derives the Host header from the URI host:port, which
        // is exactly the value SecurityFilter wants on a loopback bind. We don't
        // need to set it explicitly (and the client forbids it as a restricted header).
        transport.registerRoute(
                "/echo",
                req ->
                        HttpResponse.builder(200).text(req.bodyAsString()).build());
        transport.start();
        var req =
                java.net.http.HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/echo"))
                        .POST(BodyPublishers.ofString("hello"))
                        .build();
        var resp = newClient().send(req, BodyHandlers.ofString());
        assertEquals(200, resp.statusCode());
        assertEquals("hello", resp.body());
    }

    @Test
    void unknownRouteReturns404() throws Exception {
        transport.start();
        var req =
                java.net.http.HttpRequest.newBuilder(
                                URI.create("http://127.0.0.1:" + port + "/no-such-thing"))
                        .GET()
                        .build();
        var resp = newClient().send(req, BodyHandlers.ofString());
        assertEquals(404, resp.statusCode());
        assertTrue(resp.body().contains("Not Found"));
    }

    @Test
    void handlerExceptionReturns500WithEscapedMessage() throws Exception {
        transport.registerRoute(
                "/boom",
                req -> {
                    throw new RuntimeException("kaboom \"quoted\"");
                });
        transport.start();
        var req =
                java.net.http.HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/boom"))
                        .GET()
                        .build();
        var resp = newClient().send(req, BodyHandlers.ofString());
        assertEquals(500, resp.statusCode());
        // The quote must have been escaped so the response is valid JSON.
        assertTrue(resp.body().contains("\\\"quoted\\\""), resp.body());
    }

    @Test
    void rateLimitTriggers429AfterBurst() throws Exception {
        // 1 rpm — exactly one request gets through, the rest are 429s.
        Config tight =
                new Config(
                        "127.0.0.1",
                        port,
                        false,
                        null,
                        false,
                        List.of(),
                        15000L,
                        1,
                        4096,
                        16,
                        1,
                        "info",
                        null,
                        null,
                        false,
                        List.of(),
                        List.of(),
                        "write",
                        false);
        transport.stop();
        transport = new HttpTransport(tight);
        transport.registerRoute("/x", req -> HttpResponse.json(200, "{}"));
        transport.start();
        HttpClient client = newClient();
        java.net.http.HttpRequest req =
                java.net.http.HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/x"))
                        .GET()
                        .build();
        var first = client.send(req, BodyHandlers.ofString());
        assertEquals(200, first.statusCode());
        var second = client.send(req, BodyHandlers.ofString());
        assertEquals(429, second.statusCode());
        assertEquals("60", second.headers().firstValue("Retry-After").orElse(null));
    }

    @Test
    void securityRejectsBadHostHeader() throws Exception {
        // The JDK HttpClient blocks setting the Host header, so we go straight to
        // a raw socket to exercise the SecurityFilter rejection path end-to-end.
        transport.registerRoute("/echo", req -> HttpResponse.json(200, "{}"));
        transport.start();
        int status = rawRequestStatus(port, "POST /echo HTTP/1.1\r\nHost: attacker.example.com:8765\r\nContent-Length: 0\r\n\r\n");
        assertEquals(403, status);
    }

    private static int rawRequestStatus(int port, String request) throws IOException {
        try (Socket s = new Socket("127.0.0.1", port)) {
            s.setSoTimeout(3000);
            try (OutputStream out = s.getOutputStream();
                    BufferedReader in =
                            new BufferedReader(
                                    new InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8))) {
                out.write(request.getBytes(StandardCharsets.UTF_8));
                out.flush();
                String statusLine = in.readLine();
                if (statusLine == null) {
                    return -1;
                }
                String[] parts = statusLine.split(" ");
                return Integer.parseInt(parts[1]);
            }
        }
    }

    @Test
    void stopIsIdempotent() throws Exception {
        transport.start();
        transport.stop();
        // A second stop must be a no-op.
        transport.stop();
    }

    @Test
    void constructorBuildsCollaboratorsWithoutBinding() {
        // Constructor alone should not bind a socket.
        HttpTransport t = new HttpTransport(Config.defaults());
        assertNotNull(t);
    }

    @Test
    void emptyBodyResponseSendsContentLengthMinusOne() throws Exception {
        // Exercises the `body == null || body.length == 0` branch of writeResponse —
        // a route that returns a status-only response should still deliver the status
        // line + headers and close cleanly.
        transport.registerRoute("/empty", req -> HttpResponse.builder(204).build());
        transport.start();
        var req =
                java.net.http.HttpRequest.newBuilder(
                                URI.create("http://127.0.0.1:" + port + "/empty"))
                        .GET()
                        .build();
        var resp = newClient().send(req, BodyHandlers.ofString());
        assertEquals(204, resp.statusCode());
        assertEquals("", resp.body(), "body should be empty");
    }
}
