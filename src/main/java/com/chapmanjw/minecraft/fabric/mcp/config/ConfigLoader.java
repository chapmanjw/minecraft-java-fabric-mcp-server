package com.chapmanjw.minecraft.fabric.mcp.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Loads, validates, and (when necessary) mutates the MCP server's {@link Config}.
 *
 * <p>Resolution precedence, lowest priority first:
 *
 * <ol>
 *   <li>Hard-coded defaults from {@link Config#defaults()}.
 *   <li>Values in {@code config/minecraft_fabric_mcp/config.json} relative to the server run dir.
 *   <li>{@code MCP_*} environment variables.
 * </ol>
 *
 * <p>If {@code authRequired} ends up true and no bearer token has been provided through
 * any layer, the loader generates a 32-byte hex token, persists it back to the config
 * file (creating the file if needed), tightens POSIX permissions to {@code 600}, and
 * logs the value once at INFO with a "save this" preamble.
 *
 * <p>This class is intentionally stateless — call {@link #load(Path)} once at server
 * startup and pass the returned {@link Config} to every downstream layer.
 */
public final class ConfigLoader {

    private static final Logger LOGGER = LoggerFactory.getLogger("minecraft_fabric_mcp/config");

    private static final String ENV_PREFIX = "MCP_";
    private static final int TOKEN_BYTES = 32;

    private final ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    /**
     * Loads the configuration from disk + environment, validates it, and returns the
     * frozen {@link Config}.
     *
     * @param configFile path to the on-disk JSON config. Must be a regular file or
     *     missing; a missing file is treated as "all defaults". Permission failures
     *     when reading the file surface as {@link ConfigException}.
     */
    public Config load(Path configFile) {
        Config base = Config.defaults();
        Config fromFile = applyFileOverrides(base, configFile);
        Config fromEnv = applyEnvOverrides(fromFile);
        Config validated = validate(fromEnv);
        return ensureBearerToken(validated, configFile);
    }

    // --- file ----------------------------------------------------------------

    private Config applyFileOverrides(Config base, Path configFile) {
        if (configFile == null || !Files.exists(configFile)) {
            return base;
        }
        try (var reader = Files.newBufferedReader(configFile)) {
            JsonNode root = mapper.readTree(reader);
            if (root == null || root.isNull() || !root.isObject()) {
                return base;
            }
            return overlay(base, root);
        } catch (IOException e) {
            throw new ConfigException("Failed to read config file at " + configFile, e);
        }
    }

    private Config overlay(Config base, JsonNode root) {
        String host = textOrDefault(root, "host", base.host());
        int port = intOrDefault(root, "port", base.port());
        boolean authRequired = boolOrDefault(root, "auth_required", base.authRequired());
        String bearerToken = textOrDefault(root, "bearer_token", base.bearerToken());
        boolean allowRemote = boolOrDefault(root, "allow_remote", base.allowRemote());
        List<String> origins = stringArrayOrDefault(root, "allowed_origins", base.allowedOrigins());
        long timeoutMs = longOrDefault(root, "command_timeout_ms", base.commandTimeoutMs());
        int rateLimit = intOrDefault(root, "rate_limit_rpm", base.rateLimitRpm());
        int maxBody = intOrDefault(root, "max_body_bytes", base.maxBodyBytes());
        int eventBuf = intOrDefault(root, "event_buffer_size", base.eventBufferSize());
        int queueMax = intOrDefault(root, "queue_max", base.queueMax());
        String logLevel = textOrDefault(root, "log_level", base.logLevel());
        String tlsCert = textOrDefault(root, "tls_cert_path", base.tlsCertPath());
        String tlsKey = textOrDefault(root, "tls_key_path", base.tlsKeyPath());
        boolean metrics = boolOrDefault(root, "metrics_enabled", base.metricsEnabled());
        List<String> includedCats = stringArrayOrDefault(root, "included_categories", base.includedCategories());
        List<String> excludedCats = stringArrayOrDefault(root, "excluded_categories", base.excludedCategories());
        boolean excludeWrites = boolOrDefault(root, "exclude_write_tools", base.excludeWriteTools());

        return new Config(
                host,
                port,
                authRequired,
                bearerToken,
                allowRemote,
                origins,
                timeoutMs,
                rateLimit,
                maxBody,
                eventBuf,
                queueMax,
                logLevel,
                tlsCert,
                tlsKey,
                metrics,
                includedCats,
                excludedCats,
                excludeWrites);
    }

    // --- env -----------------------------------------------------------------

    private Config applyEnvOverrides(Config base) {
        String host = env("HOST", base.host());
        int port = envInt("PORT", base.port());
        boolean authRequired = envBool("AUTH_REQUIRED", base.authRequired());
        String bearerToken = env("BEARER_TOKEN", base.bearerToken());
        boolean allowRemote = envBool("ALLOW_REMOTE", base.allowRemote());
        List<String> origins = envCsv("ALLOWED_ORIGINS", base.allowedOrigins());
        long timeoutMs = envLong("COMMAND_TIMEOUT_MS", base.commandTimeoutMs());
        int rateLimit = envInt("RATE_LIMIT_RPM", base.rateLimitRpm());
        int maxBody = envInt("MAX_BODY_BYTES", base.maxBodyBytes());
        int eventBuf = envInt("EVENT_BUFFER_SIZE", base.eventBufferSize());
        int queueMax = envInt("QUEUE_MAX", base.queueMax());
        String logLevel = env("LOG_LEVEL", base.logLevel());
        String tlsCert = env("TLS_CERT_PATH", base.tlsCertPath());
        String tlsKey = env("TLS_KEY_PATH", base.tlsKeyPath());
        boolean metrics = envBool("METRICS_ENABLED", base.metricsEnabled());
        List<String> includedCats = envCsv("INCLUDED_CATEGORIES", base.includedCategories());
        List<String> excludedCats = envCsv("EXCLUDED_CATEGORIES", base.excludedCategories());
        boolean excludeWrites = envBool("EXCLUDE_WRITE_TOOLS", base.excludeWriteTools());

        return new Config(
                host,
                port,
                authRequired,
                bearerToken,
                allowRemote,
                origins,
                timeoutMs,
                rateLimit,
                maxBody,
                eventBuf,
                queueMax,
                logLevel,
                tlsCert,
                tlsKey,
                metrics,
                includedCats,
                excludedCats,
                excludeWrites);
    }

    private static String env(String suffix, String fallback) {
        String v = System.getenv(ENV_PREFIX + suffix);
        return v == null || v.isBlank() ? fallback : v;
    }

    private static int envInt(String suffix, int fallback) {
        String v = System.getenv(ENV_PREFIX + suffix);
        if (v == null || v.isBlank()) {
            return fallback;
        }
        try {
            return Integer.parseInt(v.trim());
        } catch (NumberFormatException e) {
            throw new ConfigException("Env " + ENV_PREFIX + suffix + " is not an integer: " + v, e);
        }
    }

    private static long envLong(String suffix, long fallback) {
        String v = System.getenv(ENV_PREFIX + suffix);
        if (v == null || v.isBlank()) {
            return fallback;
        }
        try {
            return Long.parseLong(v.trim());
        } catch (NumberFormatException e) {
            throw new ConfigException("Env " + ENV_PREFIX + suffix + " is not a long: " + v, e);
        }
    }

    private static boolean envBool(String suffix, boolean fallback) {
        String v = System.getenv(ENV_PREFIX + suffix);
        if (v == null || v.isBlank()) {
            return fallback;
        }
        return parseBool(v, ENV_PREFIX + suffix);
    }

    private static List<String> envCsv(String suffix, List<String> fallback) {
        String v = System.getenv(ENV_PREFIX + suffix);
        if (v == null || v.isBlank()) {
            return fallback;
        }
        List<String> parts = new ArrayList<>();
        for (String p : v.split(",")) {
            String t = p.trim();
            if (!t.isEmpty()) {
                parts.add(t);
            }
        }
        return parts;
    }

    private static boolean parseBool(String v, String source) {
        String s = v.trim().toLowerCase(Locale.ROOT);
        return switch (s) {
            case "true", "1", "yes", "on" -> true;
            case "false", "0", "no", "off" -> false;
            default -> throw new ConfigException(source + " is not a boolean: " + v);
        };
    }

    // --- json helpers --------------------------------------------------------

    private static String textOrDefault(JsonNode root, String key, String fallback) {
        JsonNode n = root.get(key);
        if (n == null || n.isNull()) {
            return fallback;
        }
        if (!n.isTextual()) {
            throw new ConfigException("config." + key + " must be a string");
        }
        return n.asText();
    }

    private static int intOrDefault(JsonNode root, String key, int fallback) {
        JsonNode n = root.get(key);
        if (n == null || n.isNull()) {
            return fallback;
        }
        if (!n.canConvertToInt()) {
            throw new ConfigException("config." + key + " must be an integer");
        }
        return n.intValue();
    }

    private static long longOrDefault(JsonNode root, String key, long fallback) {
        JsonNode n = root.get(key);
        if (n == null || n.isNull()) {
            return fallback;
        }
        if (!n.canConvertToLong()) {
            throw new ConfigException("config." + key + " must be a long integer");
        }
        return n.longValue();
    }

    private static boolean boolOrDefault(JsonNode root, String key, boolean fallback) {
        JsonNode n = root.get(key);
        if (n == null || n.isNull()) {
            return fallback;
        }
        if (!n.isBoolean()) {
            throw new ConfigException("config." + key + " must be a boolean");
        }
        return n.booleanValue();
    }

    private static List<String> stringArrayOrDefault(JsonNode root, String key, List<String> fallback) {
        JsonNode n = root.get(key);
        if (n == null || n.isNull()) {
            return fallback;
        }
        if (!n.isArray()) {
            throw new ConfigException("config." + key + " must be an array of strings");
        }
        List<String> out = new ArrayList<>(n.size());
        for (JsonNode element : n) {
            if (!element.isTextual()) {
                throw new ConfigException("config." + key + " must contain only strings");
            }
            out.add(element.asText());
        }
        return out;
    }

    // --- validation ----------------------------------------------------------

    private Config validate(Config c) {
        if (c.port() < 1 || c.port() > 65535) {
            throw new ConfigException("port must be 1..65535 (got " + c.port() + ")");
        }
        if (c.commandTimeoutMs() < 100 || c.commandTimeoutMs() > 600_000L) {
            throw new ConfigException(
                    "command_timeout_ms must be 100..600000 (got " + c.commandTimeoutMs() + ")");
        }
        if (c.rateLimitRpm() < 1) {
            throw new ConfigException("rate_limit_rpm must be >= 1 (got " + c.rateLimitRpm() + ")");
        }
        if (c.maxBodyBytes() < 1024) {
            throw new ConfigException(
                    "max_body_bytes must be >= 1024 (got " + c.maxBodyBytes() + ")");
        }
        if (c.eventBufferSize() < 16) {
            throw new ConfigException(
                    "event_buffer_size must be >= 16 (got " + c.eventBufferSize() + ")");
        }
        if (c.queueMax() < 1) {
            throw new ConfigException("queue_max must be >= 1 (got " + c.queueMax() + ")");
        }
        if ((c.tlsCertPath() == null) != (c.tlsKeyPath() == null)) {
            throw new ConfigException(
                    "tls_cert_path and tls_key_path must both be set or both null");
        }
        if (!c.isLoopback()) {
            // Non-loopback binding is high-risk; require both auth and an explicit opt-in.
            if (!c.allowRemote()) {
                throw new ConfigException(
                        "host '"
                                + c.host()
                                + "' is non-loopback; set allow_remote=true to opt into LAN/remote access");
            }
            if (!c.authRequired()) {
                throw new ConfigException(
                        "host '"
                                + c.host()
                                + "' is non-loopback; auth_required=true is mandatory for remote binds");
            }
        }
        return c;
    }

    // --- token generation ----------------------------------------------------

    private Config ensureBearerToken(Config c, Path configFile) {
        if (!c.authRequired() || (c.bearerToken() != null && !c.bearerToken().isBlank())) {
            return c;
        }
        String token = generateToken();
        Config withToken = c.withBearerToken(token);
        persistTokenToConfigFile(withToken, configFile);
        LOGGER.info(
                "Generated bearer token for MCP server. Save this value — it is shown only once:\n"
                        + "  Authorization: Bearer {}",
                token);
        return withToken;
    }

    private static String generateToken() {
        byte[] buf = new byte[TOKEN_BYTES];
        new SecureRandom().nextBytes(buf);
        return HexFormat.of().formatHex(buf);
    }

    private void persistTokenToConfigFile(Config c, Path configFile) {
        if (configFile == null) {
            LOGGER.warn(
                    "auth_required is set but no config file path was provided — the generated token"
                            + " will not be persisted and will be re-generated on the next launch.");
            return;
        }
        try {
            // configFile may be a bare filename (no parent component) — guard against NPE.
            Path parent = configFile.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            if (Files.isDirectory(configFile)) {
                throw new ConfigException(
                        "Config path " + configFile + " is a directory; cannot persist token");
            }
            ObjectNode root = mapper.createObjectNode();
            root.put("host", c.host());
            root.put("port", c.port());
            root.put("auth_required", c.authRequired());
            root.put("bearer_token", c.bearerToken());
            root.put("allow_remote", c.allowRemote());
            root.set("allowed_origins", mapper.valueToTree(c.allowedOrigins()));
            root.put("command_timeout_ms", c.commandTimeoutMs());
            root.put("rate_limit_rpm", c.rateLimitRpm());
            root.put("max_body_bytes", c.maxBodyBytes());
            root.put("event_buffer_size", c.eventBufferSize());
            root.put("queue_max", c.queueMax());
            root.put("log_level", c.logLevel());
            root.put("tls_cert_path", c.tlsCertPath());
            root.put("tls_key_path", c.tlsKeyPath());
            root.put("metrics_enabled", c.metricsEnabled());
            root.set("included_categories", mapper.valueToTree(c.includedCategories()));
            root.set("excluded_categories", mapper.valueToTree(c.excludedCategories()));
            root.put("exclude_write_tools", c.excludeWriteTools());

            mapper.writeValue(configFile.toFile(), root);
            tightenPermissions(configFile);
        } catch (IOException e) {
            throw new ConfigException("Failed to persist generated token to " + configFile, e);
        }
    }

    /**
     * Restricts the config file to user-read-write on POSIX systems. Windows is silently
     * skipped — the JDK does not expose a clean equivalent and most Windows users keep
     * the config file under their profile directory anyway.
     */
    private static void tightenPermissions(Path file) {
        try {
            Set<PosixFilePermission> only600 =
                    EnumSet.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE);
            Files.setPosixFilePermissions(file, only600);
        } catch (UnsupportedOperationException | IOException ignored) {
            // POSIX permissions not supported — accept the platform default.
        }
    }
}
