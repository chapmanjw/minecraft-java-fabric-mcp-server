package com.chapmanjw.minecraft.fabric.mcp.transport;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ConstantTimeEqualsTest {

    @Test
    void equalStringsMatch() {
        assertTrue(ConstantTimeEquals.equals("abc", "abc"));
        assertTrue(ConstantTimeEquals.equals("", ""));
    }

    @Test
    void differingStringsDoNotMatch() {
        assertFalse(ConstantTimeEquals.equals("abc", "abd"));
        assertFalse(ConstantTimeEquals.equals("abc", "abcd"));
        assertFalse(ConstantTimeEquals.equals("abcd", "abc"));
    }

    @Test
    void nullSafe() {
        assertFalse(ConstantTimeEquals.equals(null, null));
        assertFalse(ConstantTimeEquals.equals("abc", null));
        assertFalse(ConstantTimeEquals.equals(null, "abc"));
    }

    @Test
    void unicodeEqualityWorks() {
        assertTrue(ConstantTimeEquals.equals("héllo", "héllo"));
        assertFalse(ConstantTimeEquals.equals("héllo", "hello"));
    }
}
