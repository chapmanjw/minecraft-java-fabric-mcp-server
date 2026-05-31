package com.chapmanjw.minecraft.fabric.mcp.compat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ToolCategoryTest {

    @Test
    void tenCategoriesExist() {
        assertEquals(10, ToolCategory.values().length);
    }

    @Test
    void firstSegmentBucketsResolve() {
        assertEquals(ToolCategory.BLOCKS, ToolCategory.forToolName("block_get_state"));
        assertEquals(ToolCategory.STRUCTURES, ToolCategory.forToolName("structure_load_to_world"));
        assertEquals(ToolCategory.WORLD, ToolCategory.forToolName("level_set_time"));
        assertEquals(ToolCategory.WORLD, ToolCategory.forToolName("worldborder_get"));
        assertEquals(ToolCategory.ENTITIES, ToolCategory.forToolName("entity_summon"));
        assertEquals(ToolCategory.PLAYERS, ToolCategory.forToolName("player_kick"));
        assertEquals(ToolCategory.ITEMS, ToolCategory.forToolName("inventory_set_slot"));
        assertEquals(ToolCategory.ITEMS, ToolCategory.forToolName("itemstack_describe"));
        assertEquals(ToolCategory.GAMEPLAY, ToolCategory.forToolName("scoreboard_add_objective"));
        assertEquals(ToolCategory.GAMEPLAY, ToolCategory.forToolName("bossbar_add"));
        assertEquals(ToolCategory.GAMEPLAY, ToolCategory.forToolName("advancement_grant"));
        assertEquals(ToolCategory.SCRIPTING, ToolCategory.forToolName("command_execute"));
        assertEquals(ToolCategory.SCRIPTING, ToolCategory.forToolName("function_run"));
        assertEquals(ToolCategory.SCRIPTING, ToolCategory.forToolName("schedule_function"));
        assertEquals(ToolCategory.SCRIPTING, ToolCategory.forToolName("events_subscribe"));
        assertEquals(ToolCategory.REGISTRIES, ToolCategory.forToolName("recipe_list"));
        assertEquals(ToolCategory.REGISTRIES, ToolCategory.forToolName("tag_get_members"));
        assertEquals(ToolCategory.SERVER, ToolCategory.forToolName("server_get_status"));
        assertEquals(ToolCategory.SERVER, ToolCategory.forToolName("datapack_enable"));
    }

    @Test
    void twoSegmentDomainsTakePrecedence() {
        // block_entity_* must land in BLOCKS, not be confused with bare "block".
        assertEquals(ToolCategory.BLOCKS, ToolCategory.forToolName("block_entity_get_nbt"));
        // content_registry_* must resolve via the two-segment domain → REGISTRIES.
        assertEquals(ToolCategory.REGISTRIES, ToolCategory.forToolName("content_registry_get_fuel"));
        assertEquals(ToolCategory.REGISTRIES, ToolCategory.forToolName("loot_table_generate"));
        assertEquals(ToolCategory.REGISTRIES, ToolCategory.forToolName("fluid_storage_get"));
        assertEquals(ToolCategory.REGISTRIES, ToolCategory.forToolName("resource_loader_get_resource"));
        assertEquals(ToolCategory.REGISTRIES, ToolCategory.forToolName("resource_condition_evaluate"));
        // data_storage_* / data_attachment_* → SCRIPTING.
        assertEquals(ToolCategory.SCRIPTING, ToolCategory.forToolName("data_storage_get"));
        assertEquals(ToolCategory.SCRIPTING, ToolCategory.forToolName("data_attachment_set"));
        // player_screen_* → PLAYERS; item_modify_* → ITEMS.
        assertEquals(ToolCategory.PLAYERS, ToolCategory.forToolName("player_screen_open"));
        assertEquals(ToolCategory.ITEMS, ToolCategory.forToolName("item_modify_block_slot"));
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
        assertTrue(ToolCategory.fromWireName("blocks").isPresent());
        assertEquals(ToolCategory.BLOCKS, ToolCategory.fromWireName("blocks").get());
        assertEquals(ToolCategory.BLOCKS, ToolCategory.fromWireName("BLOCKS").get());
        assertEquals(ToolCategory.BLOCKS, ToolCategory.fromWireName("Blocks").get());
        assertEquals(ToolCategory.SCRIPTING, ToolCategory.fromWireName("scripting").get());
    }

    @Test
    void fromWireNameRejectsUnknown() {
        assertFalse(ToolCategory.fromWireName("garbage").isPresent());
        assertFalse(ToolCategory.fromWireName("actors").isPresent(), "old 'actors' name is retired");
        assertFalse(ToolCategory.fromWireName(null).isPresent());
    }

    @Test
    void wireNameIsLowerCase() {
        assertEquals("blocks", ToolCategory.BLOCKS.wireName());
        assertEquals("structures", ToolCategory.STRUCTURES.wireName());
        assertEquals("world", ToolCategory.WORLD.wireName());
        assertEquals("entities", ToolCategory.ENTITIES.wireName());
        assertEquals("players", ToolCategory.PLAYERS.wireName());
        assertEquals("items", ToolCategory.ITEMS.wireName());
        assertEquals("gameplay", ToolCategory.GAMEPLAY.wireName());
        assertEquals("scripting", ToolCategory.SCRIPTING.wireName());
        assertEquals("registries", ToolCategory.REGISTRIES.wireName());
        assertEquals("server", ToolCategory.SERVER.wireName());
    }

    @Test
    void enabledByDefaultMatchesSpec() {
        // Default-on: blocks, structures, world, entities, items, scripting, server.
        assertTrue(ToolCategory.BLOCKS.enabledByDefault());
        assertTrue(ToolCategory.STRUCTURES.enabledByDefault());
        assertTrue(ToolCategory.WORLD.enabledByDefault());
        assertTrue(ToolCategory.ENTITIES.enabledByDefault());
        assertTrue(ToolCategory.ITEMS.enabledByDefault());
        assertTrue(ToolCategory.SCRIPTING.enabledByDefault());
        assertTrue(ToolCategory.SERVER.enabledByDefault());
        // Opt-in: players, gameplay, registries.
        assertFalse(ToolCategory.PLAYERS.enabledByDefault());
        assertFalse(ToolCategory.GAMEPLAY.enabledByDefault());
        assertFalse(ToolCategory.REGISTRIES.enabledByDefault());
    }
}
