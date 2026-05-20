package com.chapmanjw.minecraft.fabric.mcp.config;

/**
 * Thrown when configuration loading fails: malformed JSON, invalid values, or a
 * combination of options that the validation rules in {@link ConfigLoader} forbid.
 *
 * <p>This is a runtime exception so the mod's startup hook can let it propagate to the
 * Fabric logger and fail fast — the HTTP listener never binds with bad config, and the
 * Minecraft server itself keeps running.
 */
public final class ConfigException extends RuntimeException {

    public ConfigException(String message) {
        super(message);
    }

    public ConfigException(String message, Throwable cause) {
        super(message, cause);
    }
}
