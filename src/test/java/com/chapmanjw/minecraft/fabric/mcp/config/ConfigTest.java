package com.chapmanjw.minecraft.fabric.mcp.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class ConfigTest {

    @Test
    void defaultsArePopulatedAsDocumented() {
        Config c = Config.defaults();
        assertEquals("127.0.0.1", c.host());
        assertEquals(8765, c.port());
        assertFalse(c.authRequired());
        assertNull(c.bearerToken());
        assertFalse(c.allowRemote());
        assertEquals(List.of(), c.allowedOrigins());
        assertEquals(15_000L, c.commandTimeoutMs());
        assertEquals(60, c.rateLimitRpm());
        assertEquals(16 * 1024 * 1024, c.maxBodyBytes());
        assertEquals(1024, c.eventBufferSize());
        assertEquals(256, c.queueMax());
        assertEquals("info", c.logLevel());
        assertNull(c.tlsCertPath());
        assertNull(c.tlsKeyPath());
        assertFalse(c.metricsEnabled());
        assertEquals(List.of(), c.includedCategories());
        assertEquals(List.of(), c.excludedCategories());
        assertFalse(c.excludeWriteTools());
    }

    @Test
    void isLoopbackMatchesAllAliases() {
        assertTrue(Config.defaults().isLoopback());
        assertTrue(loopbackHost("127.0.0.1").isLoopback());
        assertTrue(loopbackHost("::1").isLoopback());
        assertTrue(loopbackHost("localhost").isLoopback());
        // Case-insensitive on "localhost"
        assertTrue(loopbackHost("LocalHost").isLoopback());
        // Non-loopback rejected
        assertFalse(loopbackHost("10.0.0.5").isLoopback());
        assertFalse(loopbackHost("0.0.0.0").isLoopback());
    }

    @Test
    void tlsEnabledWhenBothPathsSet() {
        Config c = Config.defaults();
        assertFalse(c.tlsEnabled());
        Config tls = withTls(c, "cert.pem", "key.pem");
        assertTrue(tls.tlsEnabled());
        // Only cert is null
        assertFalse(withTls(c, null, "key.pem").tlsEnabled());
        // Only key is null
        assertFalse(withTls(c, "cert.pem", null).tlsEnabled());
    }

    @Test
    void endpointBaseUsesHttpForPlain() {
        assertEquals("http://127.0.0.1:8765", Config.defaults().endpointBase());
    }

    @Test
    void endpointBaseUsesHttpsWhenTlsEnabled() {
        Config c = withTls(Config.defaults(), "cert.pem", "key.pem");
        assertEquals("https://127.0.0.1:8765", c.endpointBase());
    }

    @Test
    void endpointBaseRewritesLocalhostToIp() {
        Config c = loopbackHost("localhost");
        assertTrue(c.endpointBase().startsWith("http://127.0.0.1:"));
    }

    @Test
    void endpointBasePreservesNonLoopbackHost() {
        // A non-loopback host won't normally pass validation in ConfigLoader, but the
        // record method itself is pure and must reflect what was constructed.
        Config c = build("192.168.1.10", 7000, true, true);
        assertEquals("http://192.168.1.10:7000", c.endpointBase());
    }

    @Test
    void withBearerTokenReplacesOnlyToken() {
        Config original = Config.defaults();
        Config updated = original.withBearerToken("hex-token");
        assertEquals("hex-token", updated.bearerToken());
        // Everything else unchanged
        assertEquals(original.host(), updated.host());
        assertEquals(original.port(), updated.port());
        assertEquals(original.authRequired(), updated.authRequired());
        assertEquals(original.allowRemote(), updated.allowRemote());
        assertEquals(original.allowedOrigins(), updated.allowedOrigins());
        assertEquals(original.commandTimeoutMs(), updated.commandTimeoutMs());
        assertEquals(original.rateLimitRpm(), updated.rateLimitRpm());
        assertEquals(original.maxBodyBytes(), updated.maxBodyBytes());
        assertEquals(original.eventBufferSize(), updated.eventBufferSize());
        assertEquals(original.queueMax(), updated.queueMax());
        assertEquals(original.logLevel(), updated.logLevel());
        assertEquals(original.tlsCertPath(), updated.tlsCertPath());
        assertEquals(original.tlsKeyPath(), updated.tlsKeyPath());
        assertEquals(original.metricsEnabled(), updated.metricsEnabled());
        assertEquals(original.includedCategories(), updated.includedCategories());
        assertEquals(original.excludedCategories(), updated.excludedCategories());
        assertEquals(original.excludeWriteTools(), updated.excludeWriteTools());
    }

    @Test
    void nullHostRejected() {
        assertThrows(NullPointerException.class, () -> build(null, 8765, false, false));
    }

    @Test
    void nullLogLevelRejected() {
        assertThrows(
                NullPointerException.class,
                () ->
                        new Config(
                                "127.0.0.1",
                                8765,
                                false,
                                null,
                                false,
                                List.of(),
                                15000L,
                                60,
                                1024,
                                16,
                                1,
                                null,
                                null,
                                null,
                                false,
                                List.of(),
                                List.of(),
                                false));
    }

    @Test
    void nullAllowedOriginsBecomesEmptyList() {
        Config c =
                new Config(
                        "127.0.0.1",
                        8765,
                        false,
                        null,
                        false,
                        null,
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
        assertEquals(List.of(), c.allowedOrigins());
    }

    @Test
    void nullCategoryListsBecomeEmpty() {
        Config c =
                new Config(
                        "127.0.0.1",
                        8765,
                        false,
                        null,
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
                        null,
                        null,
                        false);
        assertEquals(List.of(), c.includedCategories());
        assertEquals(List.of(), c.excludedCategories());
    }

    // --- helpers ----

    private static Config loopbackHost(String host) {
        return build(host, 8765, false, false);
    }

    private static Config build(String host, int port, boolean allowRemote, boolean auth) {
        return new Config(
                host,
                port,
                auth,
                null,
                allowRemote,
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
    }

    private static Config withTls(Config base, String cert, String key) {
        return new Config(
                base.host(),
                base.port(),
                base.authRequired(),
                base.bearerToken(),
                base.allowRemote(),
                base.allowedOrigins(),
                base.commandTimeoutMs(),
                base.rateLimitRpm(),
                base.maxBodyBytes(),
                base.eventBufferSize(),
                base.queueMax(),
                base.logLevel(),
                cert,
                key,
                base.metricsEnabled(),
                base.includedCategories(),
                base.excludedCategories(),
                base.excludeWriteTools());
    }
}
