package com.chapmanjw.minecraft.fabric.mcp.transport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.chapmanjw.minecraft.fabric.mcp.config.Config;

class SecurityFilterTest {

    private static HttpRequest req(Map<String, List<String>> headers) {
        return new HttpRequest(
                HttpMethod.POST,
                "/mcp",
                null,
                headers,
                new byte[0],
                new InetSocketAddress("127.0.0.1", 12345));
    }

    @Test
    void localhostHostAllowed() {
        SecurityFilter filter = new SecurityFilter(Config.defaults());
        SecurityFilter.Decision d =
                filter.evaluate(req(Map.of("Host", List.of("localhost:8765"))));
        assertInstanceOf(SecurityFilter.Decision.Allowed.class, d);
    }

    @Test
    void loopbackIpHostAllowed() {
        SecurityFilter filter = new SecurityFilter(Config.defaults());
        SecurityFilter.Decision d =
                filter.evaluate(req(Map.of("Host", List.of("127.0.0.1:8765"))));
        assertInstanceOf(SecurityFilter.Decision.Allowed.class, d);
    }

    @Test
    void dnsRebindAttemptRejected() {
        SecurityFilter filter = new SecurityFilter(Config.defaults());
        SecurityFilter.Decision d =
                filter.evaluate(req(Map.of("Host", List.of("attacker.example.com:8765"))));
        var r = assertInstanceOf(SecurityFilter.Decision.Rejected.class, d);
        assertEquals(403, r.status());
    }

    @Test
    void missingHostHeaderRejected() {
        SecurityFilter filter = new SecurityFilter(Config.defaults());
        SecurityFilter.Decision d = filter.evaluate(req(Map.of()));
        var r = assertInstanceOf(SecurityFilter.Decision.Rejected.class, d);
        assertEquals(400, r.status());
    }

    @Test
    void originHeaderRejectedWhenEmptyAllowlist() {
        SecurityFilter filter = new SecurityFilter(Config.defaults());
        SecurityFilter.Decision d =
                filter.evaluate(
                        req(
                                Map.of(
                                        "Host",
                                        List.of("127.0.0.1:8765"),
                                        "Origin",
                                        List.of("https://attacker.example.com"))));
        var r = assertInstanceOf(SecurityFilter.Decision.Rejected.class, d);
        assertEquals(403, r.status());
    }

    @Test
    void originHeaderAllowedWhenInAllowlist() {
        Config c = Config.defaults();
        Config withOrigin =
                new Config(
                        c.host(),
                        c.port(),
                        c.authRequired(),
                        c.bearerToken(),
                        c.allowRemote(),
                        List.of("https://myapp.example.com"),
                        c.commandTimeoutMs(),
                        c.rateLimitRpm(),
                        c.maxBodyBytes(),
                        c.eventBufferSize(),
                        c.queueMax(),
                        c.logLevel(),
                        c.tlsCertPath(),
                        c.tlsKeyPath(),
                        c.metricsEnabled(),
                        c.includedCategories(),
                        c.excludedCategories(),
                        c.maxAccess(),
                        c.excludeWriteTools());
        SecurityFilter filter = new SecurityFilter(withOrigin);
        SecurityFilter.Decision d =
                filter.evaluate(
                        req(
                                Map.of(
                                        "Host",
                                        List.of("127.0.0.1:8765"),
                                        "Origin",
                                        List.of("https://myapp.example.com"))));
        assertInstanceOf(SecurityFilter.Decision.Allowed.class, d);
    }

    @Test
    void bearerAuthRequired() {
        Config c =
                new Config(
                        "127.0.0.1",
                        8765,
                        true,
                        "the-token",
                        false,
                        List.of(),
                        15000L,
                        60,
                        16_777_216,
                        1024,
                        256,
                        "info",
                        null,
                        null,
                        false,
                        List.of(),
                        List.of(),
                        "write",
                        false);
        SecurityFilter filter = new SecurityFilter(c);

        // No Authorization header.
        var d1 =
                filter.evaluate(req(Map.of("Host", List.of("127.0.0.1:8765"))));
        assertEquals(401, assertInstanceOf(SecurityFilter.Decision.Rejected.class, d1).status());

        // Wrong scheme.
        var d2 =
                filter.evaluate(
                        req(
                                Map.of(
                                        "Host",
                                        List.of("127.0.0.1:8765"),
                                        "Authorization",
                                        List.of("Basic abc"))));
        assertEquals(401, assertInstanceOf(SecurityFilter.Decision.Rejected.class, d2).status());

        // Wrong token.
        var d3 =
                filter.evaluate(
                        req(
                                Map.of(
                                        "Host",
                                        List.of("127.0.0.1:8765"),
                                        "Authorization",
                                        List.of("Bearer wrong"))));
        assertEquals(401, assertInstanceOf(SecurityFilter.Decision.Rejected.class, d3).status());

        // Correct token.
        var d4 =
                filter.evaluate(
                        req(
                                Map.of(
                                        "Host",
                                        List.of("127.0.0.1:8765"),
                                        "Authorization",
                                        List.of("Bearer the-token"))));
        assertInstanceOf(SecurityFilter.Decision.Allowed.class, d4);
    }
}
