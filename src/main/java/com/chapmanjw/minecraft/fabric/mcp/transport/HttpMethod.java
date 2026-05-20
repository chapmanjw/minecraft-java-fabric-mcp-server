package com.chapmanjw.minecraft.fabric.mcp.transport;

/** HTTP methods this transport supports. */
public enum HttpMethod {
    GET,
    POST,
    PUT,
    DELETE,
    OPTIONS,
    HEAD;

    public static HttpMethod from(String s) {
        if (s == null) {
            return null;
        }
        try {
            return HttpMethod.valueOf(s.toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
