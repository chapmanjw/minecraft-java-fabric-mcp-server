package com.chapmanjw.minecraft.fabric.mcp.adapter.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

/**
 * Single-class smoke test that exercises every DTO record's constructor, accessors,
 * derived methods, and null-handling. These records are simple data carriers but they
 * appear in tool result envelopes and the protocol layer — coverage here protects
 * against accidental schema drift (e.g. accessor renames).
 */
class DtoSmokeTest {

    @Test
    void vec3dCoversAccessorsAndConversion() {
        var v = new Vec3d(1.5, 2.5, 3.5);
        assertEquals(1.5, v.x());
        assertEquals(2.5, v.y());
        assertEquals(3.5, v.z());
        Vec3i block = v.toBlockPos();
        assertEquals(new Vec3i(1, 2, 3), block);

        // Floor on negative coordinates.
        var neg = new Vec3d(-0.1, -2.9, -3.0);
        assertEquals(new Vec3i(-1, -3, -3), neg.toBlockPos());
    }

    @Test
    void vec3iCoversAccessorsConversionAndBoxBuilder() {
        var a = new Vec3i(1, 2, 3);
        assertEquals(new Vec3d(1.0, 2.0, 3.0), a.toVec3d());
        var b = new Vec3i(5, 6, 7);
        BoundingBox box = a.boxTo(b);
        assertEquals(a, box.min());
        assertEquals(b, box.max());
    }

    @Test
    void boundingBoxConstructorValidatesOrdering() {
        // From with a < b: works.
        new BoundingBox(0, 0, 0, 1, 1, 1);
        // Inverted: rejected.
        assertThrows(
                IllegalArgumentException.class, () -> new BoundingBox(1, 0, 0, 0, 1, 1));
    }

    @Test
    void boundingBoxSizesAndVolumeAndContains() {
        BoundingBox box = BoundingBox.of(new Vec3i(0, 0, 0), new Vec3i(2, 3, 4));
        assertEquals(3, box.sizeX());
        assertEquals(4, box.sizeY());
        assertEquals(5, box.sizeZ());
        assertEquals(60L, box.volume());
        assertTrue(box.contains(new Vec3i(1, 1, 1)));
        assertFalse(box.contains(new Vec3i(-1, 1, 1)));
        assertFalse(box.contains(new Vec3i(1, 1, 5)));
    }

    @Test
    void boundingBoxOfNormalisesCorners() {
        BoundingBox box = BoundingBox.of(new Vec3i(5, 5, 5), new Vec3i(1, 2, 3));
        assertEquals(new Vec3i(1, 2, 3), box.min());
        assertEquals(new Vec3i(5, 5, 5), box.max());
    }

    @Test
    void blockSpecValidatesIdAndCopiesProperties() {
        var spec = new BlockSpec("minecraft:oak_log", Map.of("axis", "y"), null);
        assertEquals("minecraft:oak_log", spec.id());
        assertEquals("y", spec.properties().get("axis"));
        assertNull(spec.nbt());

        // Null properties → empty map.
        var bare = BlockSpec.of("minecraft:stone");
        assertTrue(bare.properties().isEmpty());

        assertThrows(
                IllegalArgumentException.class, () -> new BlockSpec("", Map.of(), null));
        assertThrows(
                IllegalArgumentException.class, () -> new BlockSpec("   ", Map.of(), null));
        assertThrows(
                IllegalArgumentException.class, () -> new BlockSpec(null, Map.of(), null));
    }

    @Test
    void itemSpecClampsCountAndValidatesId() {
        var s = new ItemSpec("minecraft:diamond", 64, null);
        assertEquals(64, s.count());

        // Counts below 1 clamp to 1.
        var zero = new ItemSpec("minecraft:diamond", 0, null);
        assertEquals(1, zero.count());
        var neg = new ItemSpec("minecraft:diamond", -5, null);
        assertEquals(1, neg.count());

        // Convenience constructors.
        assertEquals(1, ItemSpec.of("minecraft:diamond").count());
        assertEquals(5, ItemSpec.of("minecraft:diamond", 5).count());

        assertThrows(IllegalArgumentException.class, () -> new ItemSpec("", 1, null));
        assertThrows(IllegalArgumentException.class, () -> new ItemSpec(null, 1, null));
    }

    @Test
    void blockStateInfoCopiesProperties() {
        var b =
                new BlockStateInfo(
                        "minecraft:furnace",
                        Map.of("lit", "true", "facing", "north"),
                        14,
                        3.5f,
                        true,
                        "{}");
        assertEquals(2, b.properties().size());
        assertEquals("minecraft:furnace", b.id());
        assertEquals(14, b.lightLevel());
        assertEquals(3.5f, b.hardness());
        assertTrue(b.hasBlockEntity());
        assertEquals("{}", b.blockEntityNbt());

        // Null map → empty.
        var bare = new BlockStateInfo("minecraft:stone", null, 0, 1.5f, false, null);
        assertTrue(bare.properties().isEmpty());
    }

    @Test
    void itemStackInfoStoresAllFields() {
        // Bug 7 fix: ItemStackInfo now carries List<String> componentKeys instead of a
        // raw SNBT string. The pre-fix value (`s.getComponents().toString()`) emitted
        // intermediary class names like `class_10711[...]` because Component.toString()
        // is not stable under Fabric Loader runtime mappings, and the full SNBT blob
        // was 50+ KB per stack. Tests now exercise the new accessor.
        var s =
                new ItemStackInfo(
                        "minecraft:iron_pickaxe",
                        1,
                        List.of("minecraft:damage", "minecraft:max_stack_size"),
                        1,
                        250,
                        42);
        assertEquals("minecraft:iron_pickaxe", s.id());
        assertEquals(1, s.count());
        assertEquals(2, s.componentKeys().size());
        assertEquals(1, s.maxStackSize());
        assertEquals(250, s.maxDurability());
        assertEquals(42, s.damage());

        // Null componentKeys → empty list (defensive copy).
        var bare = new ItemStackInfo("minecraft:stone", 1, null, 64, 0, 0);
        assertTrue(bare.componentKeys().isEmpty());
    }

    @Test
    void inventoryInfoCopiesSlotList() {
        var slots =
                List.of(
                        new ItemStackInfo("minecraft:stone", 1, null, 64, 0, 0),
                        new ItemStackInfo("minecraft:dirt", 32, null, 64, 0, 0));
        var inv = new InventoryInfo(2, slots);
        assertEquals(2, inv.size());
        assertEquals("minecraft:stone", inv.slots().get(0).id());

        // Null list → empty.
        var bare = new InventoryInfo(0, null);
        assertTrue(bare.slots().isEmpty());
    }

    @Test
    void playerInfoStoresAllFields() {
        UUID uuid = UUID.randomUUID();
        var p =
                new PlayerInfo(
                        uuid,
                        "Steve",
                        "minecraft:overworld",
                        new Vec3d(0, 64, 0),
                        90f,
                        0f,
                        "creative",
                        20f,
                        20f,
                        20,
                        5f,
                        30,
                        0.5f,
                        42L);
        assertEquals("Steve", p.name());
        assertEquals(uuid, p.uuid());
        assertEquals(42L, p.latencyMs());
    }

    @Test
    void entityInfoCopiesTags() {
        UUID uuid = UUID.randomUUID();
        var e =
                new EntityInfo(
                        uuid,
                        "minecraft:cow",
                        "Daisy",
                        "minecraft:overworld",
                        new Vec3d(0, 64, 0),
                        new Vec3d(0, 0, 0),
                        0,
                        0,
                        10f,
                        10f,
                        true,
                        true,
                        List.of("milkable", "named"));
        assertEquals(2, e.tags().size());
        assertEquals("Daisy", e.customName());

        var noTags =
                new EntityInfo(
                        uuid,
                        "minecraft:zombie",
                        null,
                        "minecraft:overworld",
                        new Vec3d(0, 0, 0),
                        new Vec3d(0, 0, 0),
                        0,
                        0,
                        20f,
                        20f,
                        false,
                        true,
                        null);
        assertTrue(noTags.tags().isEmpty());
    }

    @Test
    void dimensionInfoStoresAllFields() {
        // Bug 7 fix: the legacy `biomeSource` field was removed in v0.2.x -- it never
        // populated to anything but the empty string and clients hit it expecting useful
        // data. Constructor surface now has nine fields instead of ten.
        var d =
                new DimensionInfo(
                        "minecraft:overworld",
                        "minecraft:overworld",
                        -64,
                        320,
                        6000L,
                        false,
                        false,
                        true,
                        true);
        assertEquals("minecraft:overworld", d.id());
        assertEquals(-64, d.minY());
        assertTrue(d.piglinSafe());
        assertTrue(d.natural());
    }

    @Test
    void levelInfoStoresAllFields() {
        var l =
                new LevelInfo(
                        "minecraft:overworld",
                        1000,
                        500,
                        "rain",
                        2400,
                        "normal",
                        false,
                        "survival",
                        new Vec3i(0, 64, 0),
                        false);
        assertEquals("rain", l.weather());
        assertFalse(l.difficultyLocked());
        assertEquals(2400, l.weatherRemainingTicks());
    }

    @Test
    void serverStatusCopiesDimensionsList() {
        var s =
                new ServerStatus(
                        "1.21.11",
                        "0.19.2",
                        "0.1.0+1.21.11",
                        "Test",
                        1000L,
                        20.0,
                        50.0,
                        2,
                        20,
                        List.of("minecraft:overworld"),
                        110,
                        4082,
                        81,
                        64);
        assertEquals(1, s.loadedDimensions().size());

        var bare = new ServerStatus("v", "l", "m", "motd", 0, 20.0, 50.0, 0, 0, null, 0, 0, 0, 0);
        assertTrue(bare.loadedDimensions().isEmpty());
    }

    @Test
    void statusEffectInfoStoresAllFields() {
        var e = new StatusEffectInfo("minecraft:speed", 2, 600, false, true, true);
        assertEquals(2, e.amplifier());
        assertEquals(600, e.remainingDurationTicks());
        assertTrue(e.showParticles());
    }

    @Test
    void scoreboardObjectiveInfoStoresAllFields() {
        var o = new ScoreboardObjectiveInfo("kills", "Kills", "dummy", "sidebar");
        assertEquals("kills", o.name());
        assertEquals("dummy", o.criterion());
    }

    @Test
    void teamInfoCopiesMembers() {
        var t = new TeamInfo("red", "Red", "red", true, false, List.of("Alice", "Bob"));
        assertEquals(2, t.members().size());

        var bare = new TeamInfo("blue", "Blue", "blue", false, false, null);
        assertTrue(bare.members().isEmpty());
    }

    @Test
    void commandResultCopiesOutput() {
        var r = new CommandResult(1, List.of("ok"), null);
        assertEquals(1, r.successCount());
        assertEquals(1, r.output().size());

        var bare = new CommandResult(0, null, "syntax error");
        assertTrue(bare.output().isEmpty());
        assertEquals("syntax error", bare.error());
    }

    @Test
    void structureInfoStoresAllFields() {
        var s = new StructureInfo("name", 1, 2, 3, 100L, true, false);
        assertEquals("name", s.name());
        assertEquals(3, s.sizeZ());
        assertEquals(100L, s.fileSizeBytes());
        assertTrue(s.onDisk());
    }

    @Test
    void gameRuleInfoStoresAllFields() {
        var g = new GameRuleInfo("doDaylightCycle", "true", "BOOLEAN");
        assertEquals("doDaylightCycle", g.name());
        assertEquals("true", g.value());
    }

    @Test
    void biomeInfoStoresAllFields() {
        var b = new BiomeInfo("minecraft:plains", 0.8f, 0.4f, true);
        assertEquals("minecraft:plains", b.id());
        assertEquals(0.8f, b.temperature());
        assertTrue(b.hasPrecipitation());
    }

    @Test
    void recipeInfoCopiesIngredients() {
        var r =
                new RecipeInfo(
                        "minecraft:diamond_pickaxe",
                        "minecraft:crafting_shaped",
                        "tools",
                        List.of("minecraft:diamond", "minecraft:stick"),
                        "minecraft:diamond_pickaxe",
                        1);
        assertEquals(2, r.ingredients().size());

        var bare = new RecipeInfo("x", "x", "", null, "", 0);
        assertTrue(bare.ingredients().isEmpty());
    }

    @Test
    void datapackInfoStoresAllFields() {
        var d = new DatapackInfo("vanilla", "Vanilla", true, true);
        assertTrue(d.enabled());
        assertTrue(d.builtin());
    }

    @Test
    void lootDropInfoCopiesDrops() {
        var info =
                new LootDropInfo(
                        List.of(new ItemStackInfo("minecraft:diamond", 1, null, 64, 0, 0)));
        assertEquals(1, info.drops().size());

        var bare = new LootDropInfo(null);
        assertTrue(bare.drops().isEmpty());
    }

    @Test
    void uuidIsCarriedThroughEntityInfo() {
        // Sanity check that record-generated equals/hashCode/toString work for UUID fields.
        UUID id = UUID.randomUUID();
        var a =
                new EntityInfo(
                        id,
                        "minecraft:cow",
                        null,
                        "minecraft:overworld",
                        new Vec3d(0, 0, 0),
                        new Vec3d(0, 0, 0),
                        0,
                        0,
                        10f,
                        10f,
                        true,
                        true,
                        List.of());
        var b =
                new EntityInfo(
                        id,
                        "minecraft:cow",
                        null,
                        "minecraft:overworld",
                        new Vec3d(0, 0, 0),
                        new Vec3d(0, 0, 0),
                        0,
                        0,
                        10f,
                        10f,
                        true,
                        true,
                        List.of());
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        assertNotNull(a.toString());
    }
}
