package com.chapmanjw.minecraft.fabric.mcp.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;

import org.junit.jupiter.api.Test;

class SchemasTest {

    @Test
    void stringSchema() {
        JsonNode s = Schemas.string();
        assertEquals("string", s.path("type").asText());
        assertFalse(s.has("description"));
    }

    @Test
    void stringWithDescription() {
        JsonNode s = Schemas.string("a name");
        assertEquals("string", s.path("type").asText());
        assertEquals("a name", s.path("description").asText());
    }

    @Test
    void integerSchema() {
        JsonNode s = Schemas.integer("x");
        assertEquals("integer", s.path("type").asText());
        assertEquals("x", s.path("description").asText());
        assertFalse(s.has("minimum"));
        assertFalse(s.has("maximum"));
    }

    @Test
    void integerBetween() {
        JsonNode s = Schemas.integerBetween("x", -10, 10);
        assertEquals("integer", s.path("type").asText());
        assertEquals(-10, s.path("minimum").asInt());
        assertEquals(10, s.path("maximum").asInt());
    }

    @Test
    void numberSchema() {
        JsonNode s = Schemas.number("amount");
        assertEquals("number", s.path("type").asText());
        assertEquals("amount", s.path("description").asText());
    }

    @Test
    void booleanSchema() {
        JsonNode s = Schemas.bool("flag");
        assertEquals("boolean", s.path("type").asText());
        assertEquals("flag", s.path("description").asText());
    }

    @Test
    void enumSchema() {
        JsonNode s = Schemas.enumOf("difficulty", "easy", "normal", "hard");
        assertEquals("string", s.path("type").asText());
        assertEquals("difficulty", s.path("description").asText());
        assertTrue(s.has("enum"));
        assertEquals(3, s.path("enum").size());
        assertEquals("easy", s.path("enum").get(0).asText());
        assertEquals("hard", s.path("enum").get(2).asText());
    }

    @Test
    void arrayOfSchema() {
        JsonNode items = Schemas.string();
        JsonNode s = Schemas.arrayOf("strings", items);
        assertEquals("array", s.path("type").asText());
        assertEquals("strings", s.path("description").asText());
        assertEquals("string", s.path("items").path("type").asText());
    }

    @Test
    void position3dHasXyz() {
        JsonNode s = Schemas.position3d("pos");
        assertEquals("object", s.path("type").asText());
        assertEquals("pos", s.path("description").asText());
        assertTrue(s.path("properties").has("x"));
        assertTrue(s.path("properties").has("y"));
        assertTrue(s.path("properties").has("z"));
        assertEquals(3, s.path("required").size());
    }

    @Test
    void box3dHasFromAndTo() {
        JsonNode s = Schemas.box3d("bbox");
        assertEquals("object", s.path("type").asText());
        assertTrue(s.path("properties").has("from"));
        assertTrue(s.path("properties").has("to"));
        assertEquals(2, s.path("required").size());
    }

    @Test
    void objectBuilderRequiredOptionalAndAllowAdditional() {
        JsonNode s =
                Schemas.object()
                        .description("d")
                        .required("name", Schemas.string("the name"))
                        .optional("nickname", Schemas.string("optional"))
                        .allowAdditional()
                        .build();
        assertEquals("object", s.path("type").asText());
        assertEquals("d", s.path("description").asText());
        assertTrue(s.path("properties").has("name"));
        assertTrue(s.path("properties").has("nickname"));
        // Only the required field appears in the "required" array.
        assertEquals(1, s.path("required").size());
        assertEquals("name", s.path("required").get(0).asText());
        assertTrue(s.path("additionalProperties").asBoolean());
    }

    @Test
    void objectBuilderDefaultsDisallowAdditional() {
        JsonNode s = Schemas.object().required("a", Schemas.string()).build();
        assertFalse(s.path("additionalProperties").asBoolean());
    }

    @Test
    void objectBuilderOmitsRequiredArrayWhenAllOptional() {
        JsonNode s = Schemas.object().optional("a", Schemas.string()).build();
        // When no fields are required, the "required" array is intentionally omitted.
        assertFalse(s.has("required"));
        assertTrue(s.path("properties").has("a"));
    }
}
