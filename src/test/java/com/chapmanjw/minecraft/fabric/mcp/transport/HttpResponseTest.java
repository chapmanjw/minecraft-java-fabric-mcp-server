package com.chapmanjw.minecraft.fabric.mcp.transport;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

class HttpResponseTest {

    @Test
    void jsonResponseSetsContentTypeAndBody() {
        HttpResponse r = HttpResponse.json(200, "{\"a\":1}");
        assertEquals(200, r.status());
        assertEquals("application/json; charset=utf-8", r.headers().get("Content-Type"));
        assertEquals("no-store", r.headers().get("Cache-Control"));
        assertArrayEquals("{\"a\":1}".getBytes(StandardCharsets.UTF_8), r.body());
    }

    @Test
    void textResponseSetsTextContentType() {
        HttpResponse r = HttpResponse.text(418, "I'm a teapot");
        assertEquals(418, r.status());
        assertEquals("text/plain; charset=utf-8", r.headers().get("Content-Type"));
        assertEquals("no-store", r.headers().get("Cache-Control"));
        assertArrayEquals("I'm a teapot".getBytes(StandardCharsets.UTF_8), r.body());
    }

    @Test
    void emptyResponseHasNoBody() {
        HttpResponse r = HttpResponse.empty(204);
        assertEquals(204, r.status());
        assertEquals(0, r.body().length);
        assertTrue(r.headers().isEmpty(), "empty() response has no headers");
    }

    @Test
    void builderJsonBuild() {
        HttpResponse r =
                HttpResponse.builder(201)
                        .json("{\"id\":\"x\"}")
                        .header("Location", "/things/x")
                        .build();
        assertEquals(201, r.status());
        assertEquals("application/json; charset=utf-8", r.headers().get("Content-Type"));
        assertEquals("/things/x", r.headers().get("Location"));
        // Builder injects Cache-Control by default.
        assertEquals("no-store", r.headers().get("Cache-Control"));
        assertArrayEquals("{\"id\":\"x\"}".getBytes(StandardCharsets.UTF_8), r.body());
    }

    @Test
    void builderTextBuild() {
        HttpResponse r =
                HttpResponse.builder(200)
                        .text("hello")
                        .header("X-Custom", "v")
                        .build();
        assertEquals("text/plain; charset=utf-8", r.headers().get("Content-Type"));
        assertEquals("v", r.headers().get("X-Custom"));
        assertEquals("no-store", r.headers().get("Cache-Control"));
        assertArrayEquals("hello".getBytes(StandardCharsets.UTF_8), r.body());
    }

    @Test
    void builderBytesPreservesContentType() {
        byte[] payload = new byte[] {1, 2, 3, 4};
        HttpResponse r =
                HttpResponse.builder(200)
                        .bytes(payload, "application/octet-stream")
                        .build();
        assertEquals("application/octet-stream", r.headers().get("Content-Type"));
        assertArrayEquals(payload, r.body());
    }

    @Test
    void builderRespectsExplicitCacheControlOverride() {
        HttpResponse r =
                HttpResponse.builder(200)
                        .header("Cache-Control", "max-age=60")
                        .json("{}")
                        .build();
        // Existing Cache-Control should NOT be overwritten by the default.
        assertEquals("max-age=60", r.headers().get("Cache-Control"));
    }

    @Test
    void builderEmptyBodyAllowed() {
        HttpResponse r = HttpResponse.builder(204).header("X-Foo", "bar").build();
        assertEquals(204, r.status());
        assertEquals(0, r.body().length);
        assertEquals("bar", r.headers().get("X-Foo"));
        assertEquals("no-store", r.headers().get("Cache-Control"));
    }
}
