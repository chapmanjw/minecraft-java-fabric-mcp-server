package com.chapmanjw.minecraft.fabric.mcp.compat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ToolCategoryTest {

    @Test
    void firstSegmentBucketsResolve() {
        assertEquals(ToolCategory.WORLD, ToolCategory.forToolName("block_get_at"));
        assertEquals(ToolCategory.WORLD, ToolCategory.forToolName("level_set_time"));
        assertEquals(ToolCategory.ACTORS, ToolCategory.forToolName("entity_spawn"));
        assertEquals(ToolCategory.ACTORS, ToolCategory.forToolName("player_kick"));
        assertEquals(ToolCategory.ACTORS, ToolCategory.forToolName("inventory_set_slot"));
        assertEquals(ToolCategory.GAMEPLAY, ToolCategory.forToolName("scoreboard_add"));
        assertEquals(ToolCategory.REGISTRIES, ToolCategory.forToolName("recipe_get"));
        assertEquals(ToolCategory.SERVER, ToolCategory.forToolName("server_status"));
    }

    @Test
    void twoSegmentDomainsTakePrecedence() {
        // block_entity_* must land in WORLD, not be confused with bare "block".
        assertEquals(ToolCategory.WORLD, ToolCategory.forToolName("block_entity_get"));
        // content_registry_* must resolve via the two-segment domain.
        assertEquals(ToolCategory.REGISTRIES, ToolCategory.forToolName("content_registry_describe"));
        assertEquals(ToolCategory.REGISTRIES, ToolCategory.forToolName("data_storage_read"));
        assertEquals(ToolCategory.REGISTRIES, ToolCategory.forToolName("data_attachment_get"));
        assertEquals(ToolCategory.REGISTRIES, ToolCategory.forToolName("fluid_storage_query"));
        assertEquals(ToolCategory.REGISTRIES, ToolCategory.forToolName("resource_loader_list"));
        assertEquals(ToolCategory.REGISTRIES, ToolCategory.forToolName("resource_condition_evaluate"));
        assertEquals(ToolCategory.ACTORS, ToolCategory.forToolName("player_screen_open"));
        assertEquals(ToolCategory.ACTORS, ToolCategory.forToolName("item_modify_apply"));
    }

    @Test
    void unknownDomainThrows() {
        assertThrows(IllegalArgumentException.class, () -> ToolCategory.forToolName("mystery_tool"));
    }

    @Test
    void blankToolNameThrows() {
        assertThrows(IllegalArgumentException.class, () -> ToolCategory.forToolName(""));
        assertThrows(IllegalArgumentException.class, () -> ToolCategory.forToolName(null));
    }

    @Test
    void fromWireNameIsCaseInsensitive() {
        assertTrue(ToolCategory.fromWireName("world").isPresent());
        assertEquals(ToolCategory.WORLD, ToolCategory.fromWireName("world").get());
        assertEquals(ToolCategory.WORLD, ToolCategory.fromWireName("WORLD").get());
        assertEquals(ToolCategory.WORLD, ToolCategory.fromWireName("World").get());
    }

    @Test
    void fromWireNameRejectsUnknown() {
        assertFalse(ToolCategory.fromWireName("garbage").isPresent());
        assertFalse(ToolCategory.fromWireName(null).isPresent());
    }

    @Test
    void wireNameIsLowerCase() {
        assertEquals("world", ToolCategory.WORLD.wireName());
        assertEquals("actors", ToolCategory.ACTORS.wireName());
        assertEquals("gameplay", ToolCategory.GAMEPLAY.wireName());
        assertEquals("registries", ToolCategory.REGISTRIES.wireName());
        assertEquals("server", ToolCategory.SERVER.wireName());
    }
}
