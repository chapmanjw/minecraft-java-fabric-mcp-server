package com.chapmanjw.minecraft.fabric.mcp.gametest;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;

import java.util.UUID;

/** Entity-domain gametests. */
public final class EntityToolsGameTest implements FabricGameTest {

    @GameTest(template = "minecraft_fabric_mcp:gametest/empty", batch = "mcp")
    public void entity_summon_creates_armor_stand(GameTestHelper helper) {
        var b = GametestHarness.bootstrap(helper.getLevel().getServer());

        BlockPos abs = helper.absolutePos(new BlockPos(1, 2, 1));
        var args = b.mapper().createObjectNode();
        args.put("dimension", "minecraft:overworld");
        args.put("entity_type", "minecraft:armor_stand");
        var pos = args.putObject("position");
        pos.put("x", abs.getX() + 0.5);
        pos.put("y", abs.getY());
        pos.put("z", abs.getZ() + 0.5);
        var result = GametestHarness.callTool(b.dispatcher(), "entity_summon", args);
        String uuidStr = result.path("structuredContent").path("uuid").asText();
        helper.assertTrue(uuidStr.length() == 36, "entity_summon should return a UUID: " + uuidStr);

        Entity e = helper.getLevel().getEntity(UUID.fromString(uuidStr));
        helper.assertTrue(e != null, "summoned entity not present in level");
        helper.assertTrue(
                e.getType() == EntityType.ARMOR_STAND, "wrong entity type: " + e.getType());

        helper.succeed();
    }
}
