package com.chapmanjw.minecraft.fabric.mcp.transport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class HttpRequestTest {

    private static HttpRequest req(Map<String, List<String>> headers, byte[] body) {
        return new HttpRequest(
                HttpMethod.POST,
                "/mcp",
                null,
                headers,
                body,
                new InetSocketAddress("127.0.0.1", 12345));
    }

    @Test
    void headerLookupIsCaseInsensitive() {
        HttpRequest r =
                req(
                        Map.of(
                                "Content-Type", List.of("application/json"),
                                "X-Custom", List.of("v1")),
                        new byte[0]);
        assertEquals("application/json", r.header("Content-Type").orElseThrow());
        assertEquals("application/json", r.header("content-type").orElseThrow());
        assertEquals("application/json", r.header("CONTENT-TYPE").orElseThrow());
        assertEquals("v1", r.header("x-custom").orElseThrow());
    }

    @Test
    void missingHeaderReturnsEmpty() {
        HttpRequest r = req(Map.of("Host", List.of("127.0.0.1:8765")), new byte[0]);
        assertTrue(r.header("Authorization").isEmpty());
    }

    @Test
    void headerWithEmptyValueListReturnsEmpty() {
        HttpRequest r = req(Map.of("Empty", List.of()), new byte[0]);
        assertTrue(r.header("Empty").isEmpty());
    }

    @Test
    void headerReturnsFirstValueWhenMultiple() {
        HttpRequest r = req(Map.of("X-Forwarded", List.of("first", "second")), new byte[0]);
        assertEquals("first", r.header("X-Forwarded").orElseThrow());
    }

    @Test
    void bodyAsStringDecodesUtf8() {
        byte[] body = "héllo".getBytes(StandardCharsets.UTF_8);
        HttpRequest r = req(Map.of(), body);
        assertEquals("héllo", r.bodyAsString());
    }

    @Test
    void bodyAsStringHandlesNullBody() {
        HttpRequest r =
                new HttpRequest(
                        HttpMethod.GET,
                        "/",
                        null,
                        Map.of(),
                        null,
                        new InetSocketAddress("127.0.0.1", 12345));
        assertEquals("", r.bodyAsString());
        assertEquals(0, r.contentLength());
    }

    @Test
    void contentLengthMatchesBody() {
        byte[] body = "abcde".getBytes(StandardCharsets.UTF_8);
        HttpRequest r = req(Map.of(), body);
        assertEquals(5, r.contentLength());
    }

    @Test
    void recordAccessorsReturnConstructorValues() {
        InetSocketAddress addr = new InetSocketAddress("10.0.0.1", 9000);
        HttpRequest r =
                new HttpRequest(
                        HttpMethod.PUT,
                        "/path",
                        "q=1",
                        Map.of("H", List.of("v")),
                        new byte[] {1, 2, 3},
                        addr);
        assertEquals(HttpMethod.PUT, r.method());
        assertEquals("/path", r.path());
        assertEquals("q=1", r.query());
        assertEquals(3, r.body().length);
        assertEquals(addr, r.remoteAddress());
        assertEquals(1, r.headers().size());
        assertFalse(r.header("Missing").isPresent());
    }
}
