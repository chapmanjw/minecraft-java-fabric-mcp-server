package com.chapmanjw.minecraft.fabric.mcp.tools.level;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.TextNode;

import org.junit.jupiter.api.Test;

import com.chapmanjw.minecraft.fabric.mcp.protocol.error.McpException;

class LevelPlaceFeatureToolTest {

    private static final ObjectMapper M = new ObjectMapper();

    private static JsonNode def() throws Exception {
        return M.readTree("{\"type\":\"minecraft:simple_block\",\"to_place\":{\"id\":\"minecraft:gold_block\"}}");
    }

    @Test
    void featureIdPassesThrough() {
        assertEquals(
                "minecraft:fancy_oak",
                LevelPlaceFeatureTool.featureArgument(TextNode.valueOf("minecraft:fancy_oak"), null, ""));
    }

    @Test
    void needsExactlyOneOfFeatureOrDefinition() throws Exception {
        assertThrows(McpException.class, () -> LevelPlaceFeatureTool.featureArgument(null, null, ""));
        JsonNode d = def();
        assertThrows(
                McpException.class,
                () -> LevelPlaceFeatureTool.featureArgument(TextNode.valueOf("minecraft:oak"), d, ""));
    }

    @Test
    void definitionIsRenderedOnlyWhereTheTargetSupportsIt() throws Exception {
        JsonNode d = def();
        if (LevelPlaceFeatureTool.INLINE_DEFINITIONS) {
            String arg = LevelPlaceFeatureTool.featureArgument(null, d, "");
            assertEquals(d, M.readTree(arg));
        } else {
            McpException e = assertThrows(
                    McpException.class, () -> LevelPlaceFeatureTool.featureArgument(null, d, "features[2] "));
            assertTrue(e.getMessage().contains("26.3"), e.getMessage());
            assertTrue(e.getMessage().startsWith("features[2] "), e.getMessage());
        }
    }

    @Test
    void definitionMustBeAnObject() {
        if (LevelPlaceFeatureTool.INLINE_DEFINITIONS) {
            assertThrows(
                    McpException.class,
                    () -> LevelPlaceFeatureTool.featureArgument(null, TextNode.valueOf("x"), ""));
        }
    }
}
