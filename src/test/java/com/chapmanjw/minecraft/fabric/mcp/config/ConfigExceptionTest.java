package com.chapmanjw.minecraft.fabric.mcp.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.Test;

class ConfigExceptionTest {

    @Test
    void messageOnlyConstructor() {
        ConfigException ex = new ConfigException("boom");
        assertEquals("boom", ex.getMessage());
        assertNull(ex.getCause());
    }

    @Test
    void messageAndCauseConstructor() {
        Throwable cause = new IllegalArgumentException("inner");
        ConfigException ex = new ConfigException("boom", cause);
        assertEquals("boom", ex.getMessage());
        assertSame(cause, ex.getCause());
    }

    @Test
    void isRuntimeException() {
        // The class is intentionally a RuntimeException so callers don't have to
        // declare checked-throws all the way up to the Fabric entry point.
        assertNotNull(new ConfigException("x"));
        assertEquals(
                RuntimeException.class, ConfigException.class.getSuperclass());
    }
}
