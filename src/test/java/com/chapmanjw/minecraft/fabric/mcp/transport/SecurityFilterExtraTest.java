package com.chapmanjw.minecraft.fabric.mcp.transport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.chapmanjw.minecraft.fabric.mcp.config.Config;

/**
 * Cases not covered by {@link SecurityFilterTest}: non-loopback bind, IPv6 loopback,
 * multiple allowed origins, body-size enforcement, and rate-limit-key derivation.
 */
class SecurityFilterExtraTest {

    private static HttpRequest req(Map<String, List<String>> headers, byte[] body) {
        return new HttpRequest(
                HttpMethod.POST,
                "/mcp",
                null,
                headers,
                body,
                new InetSocketAddress("10.0.0.5", 33445));
    }

    private static Config nonLoopback() {
        return new Config(
                "10.0.0.5",
                9000,
                true,
                "tok-1234567890123456abc",
                true,
                List.of(),
                15000L,
                60,
                512,
                16,
                1,
                "info",
                null,
                null,
                false,
                List.of(),
                List.of(),
                false);
    }

    @Test
    void ipv6LoopbackHostAccepted() {
        SecurityFilter filter = new SecurityFilter(Config.defaults());
        var d = filter.evaluate(req(Map.of("Host", List.of("[::1]:8765")), new byte[0]));
        assertInstanceOf(SecurityFilter.Decision.Allowed.class, d);
    }

    @Test
    void nonLoopbackMatchingHostAccepted() {
        SecurityFilter filter = new SecurityFilter(nonLoopback());
        var d =
                filter.evaluate(
                        req(
                                Map.of(
                                        "Host", List.of("10.0.0.5:9000"),
                                        "Authorization", List.of("Bearer tok-1234567890123456abc")),
                                new byte[0]));
        assertInstanceOf(SecurityFilter.Decision.Allowed.class, d);
    }

    @Test
    void nonLoopbackMismatchedHostRejected() {
        SecurityFilter filter = new SecurityFilter(nonLoopback());
        var d =
                filter.evaluate(
                        req(
                                Map.of(
                                        "Host", List.of("attacker.example.com:9000"),
                                        "Authorization", List.of("Bearer tok-1234567890123456abc")),
                                new byte[0]));
        SecurityFilter.Decision.Rejected r =
                assertInstanceOf(SecurityFilter.Decision.Rejected.class, d);
        assertEquals(403, r.status());
        assertTrue(r.reason().contains("does not match bind address"));
    }

    @Test
    void multipleAllowedOriginsSecondMatches() {
        Config c =
                new Config(
                        "127.0.0.1",
                        8765,
                        false,
                        null,
                        false,
                        List.of("https://a.example.com", "https://b.example.com"),
                        15000L,
                        60,
                        1024,
                        16,
                        1,
                        "info",
                        null,
                        null,
                        false,
                        List.of(),
                        List.of(),
                        false);
        SecurityFilter filter = new SecurityFilter(c);
        var d =
                filter.evaluate(
                        req(
                                Map.of(
                                        "Host", List.of("127.0.0.1:8765"),
                                        "Origin", List.of("https://b.example.com")),
                                new byte[0]));
        assertInstanceOf(SecurityFilter.Decision.Allowed.class, d);
    }

    @Test
    void multipleAllowedOriginsNoneMatchRejected() {
        Config c =
                new Config(
                        "127.0.0.1",
                        8765,
                        false,
                        null,
                        false,
                        List.of("https://a.example.com", "https://b.example.com"),
                        15000L,
                        60,
                        1024,
                        16,
                        1,
                        "info",
                        null,
                        null,
                        false,
                        List.of(),
                        List.of(),
                        false);
        SecurityFilter filter = new SecurityFilter(c);
        var d =
                filter.evaluate(
                        req(
                                Map.of(
                                        "Host", List.of("127.0.0.1:8765"),
                                        "Origin", List.of("https://elsewhere.example.com")),
                                new byte[0]));
        SecurityFilter.Decision.Rejected r =
                assertInstanceOf(SecurityFilter.Decision.Rejected.class, d);
        assertEquals(403, r.status());
    }

    @Test
    void oversizedBodyRejected() {
        Config c = Config.defaults();
        SecurityFilter filter = new SecurityFilter(c);
        byte[] big = new byte[c.maxBodyBytes() + 1];
        var d = filter.evaluate(req(Map.of("Host", List.of("127.0.0.1:8765")), big));
        SecurityFilter.Decision.Rejected r =
                assertInstanceOf(SecurityFilter.Decision.Rejected.class, d);
        assertEquals(413, r.status());
    }

    @Test
    void rateLimitKeyDerivedFromBearerWhenAuthOn() {
        Config c =
                new Config(
                        "127.0.0.1",
                        8765,
                        true,
                        "the-super-long-bearer-token-value",
                        false,
                        List.of(),
                        15000L,
                        60,
                        1024,
                        16,
                        1,
                        "info",
                        null,
                        null,
                        false,
                        List.of(),
                        List.of(),
                        false);
        SecurityFilter filter = new SecurityFilter(c);
        HttpRequest r =
                req(
                        Map.of(
                                "Host", List.of("127.0.0.1:8765"),
                                "Authorization",
                                        List.of("Bearer the-super-long-bearer-token-value")),
                        new byte[0]);
        String key = filter.rateLimitKey(r);
        assertTrue(key.startsWith("tok:"), key);
        // Only the first 16 chars of the token should be in the key for log hygiene.
        assertEquals("tok:the-super-long-be".substring(0, "tok:".length() + 16), key);
    }

    @Test
    void rateLimitKeyFallsBackToIpWhenAuthOff() {
        SecurityFilter filter = new SecurityFilter(Config.defaults());
        HttpRequest r = req(Map.of("Host", List.of("127.0.0.1:8765")), new byte[0]);
        String key = filter.rateLimitKey(r);
        assertTrue(key.startsWith("ip:"), key);
        assertTrue(key.contains("10.0.0.5") || key.contains("0:0:0"), key);
    }

    @Test
    void rateLimitKeyHandlesMissingRemoteAddressGracefully() {
        SecurityFilter filter = new SecurityFilter(Config.defaults());
        HttpRequest r =
                new HttpRequest(
                        HttpMethod.GET,
                        "/",
                        null,
                        Map.of(),
                        new byte[0],
                        null);
        assertEquals("ip:unknown", filter.rateLimitKey(r));
    }

    @Test
    void rateLimitKeyHandlesShortToken() {
        Config c =
                new Config(
                        "127.0.0.1",
                        8765,
                        true,
                        "short",
                        false,
                        List.of(),
                        15000L,
                        60,
                        1024,
                        16,
                        1,
                        "info",
                        null,
                        null,
                        false,
                        List.of(),
                        List.of(),
                        false);
        SecurityFilter filter = new SecurityFilter(c);
        HttpRequest r =
                req(
                        Map.of(
                                "Host", List.of("127.0.0.1:8765"),
                                "Authorization", List.of("Bearer short")),
                        new byte[0]);
        assertEquals("tok:short", filter.rateLimitKey(r));
    }
}
