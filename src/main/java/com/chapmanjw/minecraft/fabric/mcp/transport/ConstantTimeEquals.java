package com.chapmanjw.minecraft.fabric.mcp.transport;

import java.nio.charset.StandardCharsets;

/**
 * Constant-time string equality for secret comparisons.
 *
 * <p>The standard {@link String#equals(Object)} short-circuits on the first differing
 * character, leaking the length of the matching prefix through timing. For bearer-token
 * comparison we want a check whose running time is independent of how much of the
 * supplied token matches.
 */
public final class ConstantTimeEquals {

    private ConstantTimeEquals() {}

    /**
     * Compare two strings for byte-equality in constant time relative to the longer
     * input. Null-safe: any null returns false.
     */
    public static boolean equals(String a, String b) {
        if (a == null || b == null) {
            return false;
        }
        byte[] ab = a.getBytes(StandardCharsets.UTF_8);
        byte[] bb = b.getBytes(StandardCharsets.UTF_8);
        // Always compare against the length of the longer input to avoid leaking length
        // through the running time of this method.
        int len = Math.max(ab.length, bb.length);
        int diff = ab.length ^ bb.length;
        for (int i = 0; i < len; i++) {
            byte av = i < ab.length ? ab[i] : 0;
            byte bv = i < bb.length ? bb[i] : 0;
            diff |= av ^ bv;
        }
        return diff == 0;
    }
}
