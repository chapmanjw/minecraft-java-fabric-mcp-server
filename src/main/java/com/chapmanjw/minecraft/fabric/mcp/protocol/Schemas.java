package com.chapmanjw.minecraft.fabric.mcp.protocol;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Small DSL for building JSON Schema fragments used by tool input/output schemas.
 *
 * <p>JSON Schema-as-Java-builder gives us compile-time refactoring safety on field
 * names while still producing draft-2020-12 schemas at the wire level.
 */
public final class Schemas {

    private static final JsonNodeFactory NODES = JsonNodeFactory.instance;

    private Schemas() {}

    /** Start a new {@code object} schema. */
    public static ObjectSchema object() {
        return new ObjectSchema();
    }

    public static JsonNode string() {
        ObjectNode n = NODES.objectNode();
        n.put("type", "string");
        return n;
    }

    public static JsonNode string(String description) {
        ObjectNode n = NODES.objectNode();
        n.put("type", "string");
        n.put("description", description);
        return n;
    }

    public static JsonNode integer(String description) {
        ObjectNode n = NODES.objectNode();
        n.put("type", "integer");
        n.put("description", description);
        return n;
    }

    public static JsonNode integerBetween(String description, int min, int max) {
        ObjectNode n = (ObjectNode) integer(description);
        n.put("minimum", min);
        n.put("maximum", max);
        return n;
    }

    public static JsonNode number(String description) {
        ObjectNode n = NODES.objectNode();
        n.put("type", "number");
        n.put("description", description);
        return n;
    }

    public static JsonNode bool(String description) {
        ObjectNode n = NODES.objectNode();
        n.put("type", "boolean");
        n.put("description", description);
        return n;
    }

    public static JsonNode enumOf(String description, String... values) {
        ObjectNode n = NODES.objectNode();
        n.put("type", "string");
        n.put("description", description);
        ArrayNode arr = n.putArray("enum");
        for (String v : values) {
            arr.add(v);
        }
        return n;
    }

    public static JsonNode arrayOf(String description, JsonNode items) {
        ObjectNode n = NODES.objectNode();
        n.put("type", "array");
        n.put("description", description);
        n.set("items", items);
        return n;
    }

    /** Common 3D position object: {@code { x: number, y: number, z: number }}. */
    public static JsonNode position3d(String description) {
        return object()
                .description(description)
                .required("x", number("X coordinate"))
                .required("y", number("Y coordinate"))
                .required("z", number("Z coordinate"))
                .build();
    }

    /** Common axis-aligned bounding box: {@code { from: pos, to: pos }}. */
    public static JsonNode box3d(String description) {
        return object()
                .description(description)
                .required("from", position3d("Inclusive minimum corner"))
                .required("to", position3d("Inclusive maximum corner"))
                .build();
    }

    /** Builder for {@code object} schemas — required and optional properties. */
    public static final class ObjectSchema {
        private final ObjectNode root = NODES.objectNode();
        private final ObjectNode properties = NODES.objectNode();
        private final ArrayNode required = NODES.arrayNode();
        private boolean additionalProperties;

        private ObjectSchema() {
            root.put("type", "object");
        }

        public ObjectSchema description(String d) {
            root.put("description", d);
            return this;
        }

        public ObjectSchema required(String name, JsonNode schema) {
            properties.set(name, schema);
            required.add(name);
            return this;
        }

        public ObjectSchema optional(String name, JsonNode schema) {
            properties.set(name, schema);
            return this;
        }

        public ObjectSchema allowAdditional() {
            this.additionalProperties = true;
            return this;
        }

        public JsonNode build() {
            root.set("properties", properties);
            if (required.size() > 0) {
                root.set("required", required);
            }
            root.put("additionalProperties", additionalProperties);
            return root;
        }
    }
}
