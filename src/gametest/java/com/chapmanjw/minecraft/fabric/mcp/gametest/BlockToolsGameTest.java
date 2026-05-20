package com.chapmanjw.minecraft.fabric.mcp.gametest;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;

/**
 * Block-domain gametests — place a block via the tool surface, verify direct
 * Minecraft API observes it.
 */
public final class BlockToolsGameTest implements FabricGameTest {

    @GameTest(template = "minecraft_fabric_mcp:gametest/empty", batch = "mcp")
    public void block_set_state_places_block_in_world(GameTestHelper helper) {
        var b = GametestHarness.bootstrap(helper.getLevel().getServer());

        // GameTestHelper coords are relative to the structure origin; the tool needs absolute.
        BlockPos absolute = helper.absolutePos(new BlockPos(1, 2, 1));

        var args = b.mapper().createObjectNode();
        args.put("dimension", "minecraft:overworld");
        var pos = args.putObject("position");
        pos.put("x", absolute.getX());
        pos.put("y", absolute.getY());
        pos.put("z", absolute.getZ());
        var block = args.putObject("block");
        block.put("id", "minecraft:diamond_block");
        GametestHarness.callTool(b.dispatcher(), "block_set_state", args);

        var state = helper.getLevel().getBlockState(absolute);
        helper.assertTrue(
                state.is(Blocks.DIAMOND_BLOCK),
                "block_set_state did not place a diamond block at " + absolute + ": " + state);

        helper.succeed();
    }

    @GameTest(template = "minecraft_fabric_mcp:gametest/empty", batch = "mcp")
    public void block_get_state_reports_placed_block(GameTestHelper helper) {
        var b = GametestHarness.bootstrap(helper.getLevel().getServer());

        BlockPos absolute = helper.absolutePos(new BlockPos(1, 2, 1));
        helper.getLevel().setBlockAndUpdate(absolute, Blocks.GOLD_BLOCK.defaultBlockState());

        var args = b.mapper().createObjectNode();
        args.put("dimension", "minecraft:overworld");
        var pos = args.putObject("position");
        pos.put("x", absolute.getX());
        pos.put("y", absolute.getY());
        pos.put("z", absolute.getZ());
        var got = GametestHarness.callTool(b.dispatcher(), "block_get_state", args);
        String id = got.path("structuredContent").path("id").asText();
        helper.assertTrue("minecraft:gold_block".equals(id), "expected minecraft:gold_block, got " + id);

        helper.succeed();
    }
}
