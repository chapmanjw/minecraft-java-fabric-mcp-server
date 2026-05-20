package com.chapmanjw.minecraft.fabric.mcp.transport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class HttpMethodTest {

    @Test
    void parsesAllKnownMethods() {
        assertEquals(HttpMethod.GET, HttpMethod.from("GET"));
        assertEquals(HttpMethod.POST, HttpMethod.from("POST"));
        assertEquals(HttpMethod.PUT, HttpMethod.from("PUT"));
        assertEquals(HttpMethod.DELETE, HttpMethod.from("DELETE"));
        assertEquals(HttpMethod.OPTIONS, HttpMethod.from("OPTIONS"));
        assertEquals(HttpMethod.HEAD, HttpMethod.from("HEAD"));
    }

    @Test
    void parseIsCaseInsensitive() {
        assertEquals(HttpMethod.POST, HttpMethod.from("post"));
        assertEquals(HttpMethod.GET, HttpMethod.from("Get"));
        assertEquals(HttpMethod.DELETE, HttpMethod.from("DeLeTe"));
    }

    @Test
    void unknownMethodReturnsNull() {
        assertNull(HttpMethod.from("PATCH"));
        assertNull(HttpMethod.from("bogus"));
        assertNull(HttpMethod.from(""));
    }

    @Test
    void nullInputReturnsNull() {
        assertNull(HttpMethod.from(null));
    }
}
