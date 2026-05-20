package com.chapmanjw.minecraft.fabric.mcp.adapter.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

/**
 * Smoke coverage for the DTO records added with the round-out tools milestone.
 * Mirrors the existing {@link DtoSmokeTest} pattern — every record's constructor,
 * accessors, defensive copies, and factory helpers are exercised.
 */
class NewDtoSmokeTest {

    @Test
    void bossbarInfoCopiesPlayersDefensively() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        BossbarInfo info =
                new BossbarInfo(
                        "demo:bar",
                        "Hello",
                        7,
                        20,
                        "blue",
                        "progress",
                        true,
                        List.of(a, b));
        assertEquals("demo:bar", info.id());
        assertEquals("Hello", info.name());
        assertEquals(7, info.value());
        assertEquals(20, info.max());
        assertEquals("blue", info.color());
        assertEquals("progress", info.style());
        assertTrue(info.visible());
        assertEquals(2, info.players().size());

        // Null players list → empty.
        BossbarInfo nulled = new BossbarInfo("x", "y", 0, 1, "white", "progress", false, null);
        assertNotNull(nulled.players());
        assertEquals(0, nulled.players().size());
    }

    @Test
    void advancementProgressInfoCopiesAndExposesInnerRecord() {
        AdvancementProgressInfo.InProgress p =
                new AdvancementProgressInfo.InProgress(
                        "minecraft:adventure/root",
                        List.of("c1"),
                        List.of("c2", "c3"));
        AdvancementProgressInfo info =
                new AdvancementProgressInfo(List.of("minecraft:story/root"), List.of(p));
        assertEquals(1, info.granted().size());
        assertEquals(1, info.inProgress().size());
        assertEquals("minecraft:adventure/root", info.inProgress().get(0).id());
        assertEquals(1, info.inProgress().get(0).criteriaCompleted().size());
        assertEquals(2, info.inProgress().get(0).criteriaRemaining().size());

        // Null safety on outer + inner.
        AdvancementProgressInfo blank = new AdvancementProgressInfo(null, null);
        assertNotNull(blank.granted());
        assertNotNull(blank.inProgress());
        AdvancementProgressInfo.InProgress blankInner =
                new AdvancementProgressInfo.InProgress("x", null, null);
        assertNotNull(blankInner.criteriaCompleted());
        assertNotNull(blankInner.criteriaRemaining());
    }

    @Test
    void worldBorderInfoExposesEveryField() {
        WorldBorderInfo info =
                new WorldBorderInfo(
                        0.0,
                        0.0,
                        60_000_000.0,
                        5,
                        15,
                        0.2,
                        5.0,
                        -1.0,
                        -1L);
        assertEquals(0.0, info.centerX());
        assertEquals(0.0, info.centerZ());
        assertEquals(60_000_000.0, info.size());
        assertEquals(5, info.warningBlocks());
        assertEquals(15, info.warningSeconds());
        assertEquals(0.2, info.damagePerBlock());
        assertEquals(5.0, info.safeZone());
        assertEquals(-1.0, info.lerpTarget());
        assertEquals(-1L, info.lerpTimeRemainingTicks());
    }

    @Test
    void scheduledFunctionInfoExposesEveryField() {
        ScheduledFunctionInfo s = new ScheduledFunctionInfo("foo:bar", 200L);
        assertEquals("foo:bar", s.functionId());
        assertEquals(200L, s.ticksRemaining());
    }

    @Test
    void fluidStackInfoEmptyFactoryAndAccessors() {
        FluidStackInfo empty = FluidStackInfo.emptyTank();
        assertTrue(empty.empty());
        assertEquals("minecraft:empty", empty.fluidId());
        assertEquals(0L, empty.amountDroplets());
        assertEquals(0L, empty.capacityDroplets());

        FluidStackInfo water = new FluidStackInfo(false, "minecraft:water", 8_100L, 81_000L);
        assertFalse(water.empty());
        assertEquals("minecraft:water", water.fluidId());
        assertEquals(8_100L, water.amountDroplets());
        assertEquals(81_000L, water.capacityDroplets());
    }

    @Test
    void flammableBlockInfoFactoryAndAccessors() {
        FlammableBlockInfo notFlam = FlammableBlockInfo.notFlammable();
        assertFalse(notFlam.flammable());
        assertEquals(0, notFlam.spreadChance());
        assertEquals(0, notFlam.burnChance());

        FlammableBlockInfo wood = new FlammableBlockInfo(true, 20, 5);
        assertTrue(wood.flammable());
        assertEquals(20, wood.spreadChance());
        assertEquals(5, wood.burnChance());
    }

    @Test
    void compostableInfoFactoryAndAccessors() {
        CompostableInfo no = CompostableInfo.notCompostable();
        assertFalse(no.compostable());
        assertEquals(0.0f, no.chance());

        CompostableInfo cake = new CompostableInfo(true, 0.85f);
        assertTrue(cake.compostable());
        assertEquals(0.85f, cake.chance());
    }
}
