package com.chapmanjw.minecraft.fabric.mcp.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ConfigLoaderTest {

    @Test
    void defaultsLoadWhenFileMissing(@TempDir Path tmp) {
        Config c = new ConfigLoader().load(tmp.resolve("does-not-exist.json"));
        assertEquals("127.0.0.1", c.host());
        assertEquals(8765, c.port());
        assertFalse(c.authRequired());
        assertTrue(c.isLoopback());
        assertEquals(List.of(), c.allowedOrigins());
        assertEquals(List.of(), c.includedCategories());
        assertEquals(List.of(), c.excludedCategories());
        assertFalse(c.excludeWriteTools());
    }

    @Test
    void categoryFieldsReadFromFile(@TempDir Path tmp) throws IOException {
        Path f = tmp.resolve("config.json");
        Files.writeString(
                f,
                "{ \"included_categories\": [\"world\", \"actors\"],"
                        + " \"excluded_categories\": [\"server\"],"
                        + " \"exclude_write_tools\": true }");
        Config c = new ConfigLoader().load(f);
        assertEquals(List.of("world", "actors"), c.includedCategories());
        assertEquals(List.of("server"), c.excludedCategories());
        assertTrue(c.excludeWriteTools());
    }

    @Test
    void categoryFieldsRejectNonArray(@TempDir Path tmp) throws IOException {
        Path f = tmp.resolve("config.json");
        Files.writeString(f, "{ \"included_categories\": \"world\" }");
        ConfigException ex =
                assertThrows(ConfigException.class, () -> new ConfigLoader().load(f));
        assertTrue(ex.getMessage().contains("included_categories"));
    }

    @Test
    void fileValuesOverrideDefaults(@TempDir Path tmp) throws IOException {
        Path f = tmp.resolve("config.json");
        Files.writeString(
                f,
                "{ \"host\": \"127.0.0.1\", \"port\": 9000, \"rate_limit_rpm\": 30 }");
        Config c = new ConfigLoader().load(f);
        assertEquals(9000, c.port());
        assertEquals(30, c.rateLimitRpm());
    }

    @Test
    void nonLoopbackWithoutAllowRemoteRejected(@TempDir Path tmp) throws IOException {
        Path f = tmp.resolve("config.json");
        Files.writeString(
                f,
                "{ \"host\": \"0.0.0.0\", \"auth_required\": true }");
        ConfigException ex =
                assertThrows(ConfigException.class, () -> new ConfigLoader().load(f));
        assertTrue(ex.getMessage().contains("allow_remote"));
    }

    @Test
    void nonLoopbackWithoutAuthRejected(@TempDir Path tmp) throws IOException {
        Path f = tmp.resolve("config.json");
        Files.writeString(
                f,
                "{ \"host\": \"0.0.0.0\", \"allow_remote\": true }");
        ConfigException ex =
                assertThrows(ConfigException.class, () -> new ConfigLoader().load(f));
        assertTrue(ex.getMessage().contains("auth_required"));
    }

    @Test
    void tlsPairingValidated(@TempDir Path tmp) throws IOException {
        Path f = tmp.resolve("config.json");
        Files.writeString(f, "{ \"tls_cert_path\": \"a.pem\" }");
        ConfigException ex =
                assertThrows(ConfigException.class, () -> new ConfigLoader().load(f));
        assertTrue(ex.getMessage().contains("tls_cert_path") && ex.getMessage().contains("tls_key_path"));
    }

    @Test
    void portOutOfRangeRejected(@TempDir Path tmp) throws IOException {
        Path f = tmp.resolve("config.json");
        Files.writeString(f, "{ \"port\": 99999 }");
        assertThrows(ConfigException.class, () -> new ConfigLoader().load(f));
    }

    @Test
    void authRequiredTokenGeneratedAndPersisted(@TempDir Path tmp) {
        Path f = tmp.resolve("config.json");
        // No file; rely on defaults overridden by an explicit auth-required + loopback host.
        // Use a child config dir; the loader will create it.
        // Skip env vars: we don't have MCP_* set in tests by default.
        // Use a synthetic file path with parent dir.
        Path target = tmp.resolve("nested/config.json");
        ConfigLoader loader = new ConfigLoader();
        // Initial load — no file, will use defaults (auth_required=false), no token.
        Config first = loader.load(target);
        assertFalse(first.authRequired());
        assertEquals(null, first.bearerToken());
        // Now write a file that flips auth_required and reload.
        try {
            Files.createDirectories(target.getParent());
            Files.writeString(target, "{ \"auth_required\": true }");
        } catch (IOException e) {
            throw new AssertionError(e);
        }
        Config c = loader.load(target);
        assertTrue(c.authRequired());
        assertNotNull(c.bearerToken());
        assertEquals(64, c.bearerToken().length(), "32-byte hex token = 64 chars");
        // The token persists across reloads.
        Config c2 = loader.load(target);
        assertEquals(c.bearerToken(), c2.bearerToken());
    }

    @Test
    void rootArrayInFileIsIgnoredAndDefaultsUsed(@TempDir Path tmp) throws IOException {
        // A JSON file whose root is an array (not an object) should be ignored — the
        // loader returns defaults rather than throwing.
        Path f = tmp.resolve("config.json");
        Files.writeString(f, "[1, 2, 3]");
        Config c = new ConfigLoader().load(f);
        assertEquals(8765, c.port(), "defaults should apply when root is not an object");
    }

    @Test
    void rootJsonNullIsIgnoredAndDefaultsUsed(@TempDir Path tmp) throws IOException {
        Path f = tmp.resolve("config.json");
        Files.writeString(f, "null");
        Config c = new ConfigLoader().load(f);
        assertEquals(8765, c.port());
    }

    @Test
    void malformedJsonFileRaisesConfigException(@TempDir Path tmp) throws IOException {
        Path f = tmp.resolve("config.json");
        Files.writeString(f, "{ this is not json");
        ConfigException ex =
                assertThrows(ConfigException.class, () -> new ConfigLoader().load(f));
        assertTrue(ex.getMessage().toLowerCase().contains("failed to read"));
    }

    @Test
    void nonIntegerFieldRaisesConfigException(@TempDir Path tmp) throws IOException {
        Path f = tmp.resolve("config.json");
        Files.writeString(f, "{ \"port\": \"not-a-number\" }");
        ConfigException ex =
                assertThrows(ConfigException.class, () -> new ConfigLoader().load(f));
        assertTrue(ex.getMessage().contains("port"));
    }

    @Test
    void nonBooleanFieldRaisesConfigException(@TempDir Path tmp) throws IOException {
        Path f = tmp.resolve("config.json");
        Files.writeString(f, "{ \"auth_required\": \"yes\" }");
        ConfigException ex =
                assertThrows(ConfigException.class, () -> new ConfigLoader().load(f));
        assertTrue(ex.getMessage().contains("auth_required"));
    }

    @Test
    void nonArrayAllowedOriginsRaisesConfigException(@TempDir Path tmp) throws IOException {
        Path f = tmp.resolve("config.json");
        Files.writeString(f, "{ \"allowed_origins\": \"https://a\" }");
        ConfigException ex =
                assertThrows(ConfigException.class, () -> new ConfigLoader().load(f));
        assertTrue(ex.getMessage().contains("allowed_origins"));
    }

    @Test
    void mustReject_eventBufferSizeBelow16(@TempDir Path tmp) throws IOException {
        Path f = tmp.resolve("config.json");
        Files.writeString(f, "{ \"event_buffer_size\": 8 }");
        ConfigException ex =
                assertThrows(ConfigException.class, () -> new ConfigLoader().load(f));
        assertTrue(ex.getMessage().contains("event_buffer_size"));
    }

    @Test
    void mustReject_maxBodyBytesBelow1024(@TempDir Path tmp) throws IOException {
        Path f = tmp.resolve("config.json");
        Files.writeString(f, "{ \"max_body_bytes\": 256 }");
        ConfigException ex =
                assertThrows(ConfigException.class, () -> new ConfigLoader().load(f));
        assertTrue(ex.getMessage().contains("max_body_bytes"));
    }

    @Test
    void mustReject_commandTimeoutAboveTenMinutes(@TempDir Path tmp) throws IOException {
        Path f = tmp.resolve("config.json");
        Files.writeString(f, "{ \"command_timeout_ms\": 600001 }");
        ConfigException ex =
                assertThrows(ConfigException.class, () -> new ConfigLoader().load(f));
        assertTrue(ex.getMessage().contains("command_timeout_ms"));
    }

    @Test
    void mustReject_rateLimitZero(@TempDir Path tmp) throws IOException {
        Path f = tmp.resolve("config.json");
        Files.writeString(f, "{ \"rate_limit_rpm\": 0 }");
        ConfigException ex =
                assertThrows(ConfigException.class, () -> new ConfigLoader().load(f));
        assertTrue(ex.getMessage().contains("rate_limit_rpm"));
    }

    @Test
    void mustReject_queueMaxZero(@TempDir Path tmp) throws IOException {
        Path f = tmp.resolve("config.json");
        Files.writeString(f, "{ \"queue_max\": 0 }");
        ConfigException ex =
                assertThrows(ConfigException.class, () -> new ConfigLoader().load(f));
        assertTrue(ex.getMessage().contains("queue_max"));
    }

    /**
     * Regression test for a bug fixed in this commit: when {@code auth_required=true}
     * triggers the token-generation persistence path, the rewritten file MUST include
     * every config field — including the category-filter fields added later. Without
     * this, a user with category includes/excludes set in their file would have those
     * values silently wiped on the first run with auth enabled.
     */
    @Test
    void tokenPersistencePreservesCategoryFields(@TempDir Path tmp) throws IOException {
        Path target = tmp.resolve("config.json");
        Files.writeString(
                target,
                "{\"auth_required\":true,"
                        + "\"included_categories\":[\"world\",\"actors\"],"
                        + "\"excluded_categories\":[\"server\"],"
                        + "\"exclude_write_tools\":true}");
        ConfigLoader loader = new ConfigLoader();
        Config first = loader.load(target);
        assertNotNull(first.bearerToken(), "auth_required should trigger token generation");
        // Re-read the persisted file and make sure the category fields survived.
        Config rehydrated = loader.load(target);
        assertEquals(List.of("world", "actors"), rehydrated.includedCategories());
        assertEquals(List.of("server"), rehydrated.excludedCategories());
        assertTrue(rehydrated.excludeWriteTools());
        // Token must round-trip too.
        assertEquals(first.bearerToken(), rehydrated.bearerToken());
    }
}
