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

    @GameTest(template = "minecraft_fabric_mcp:gametest/empty", batch = "mcp")
    public void block_fill_batch_places_each_region(GameTestHelper helper) {
        var b = GametestHarness.bootstrap(helper.getLevel().getServer());
        BlockPos one = helper.absolutePos(new BlockPos(1, 2, 1));
        BlockPos two = helper.absolutePos(new BlockPos(2, 2, 2));

        var args = b.mapper().createObjectNode();
        args.put("dimension", "minecraft:overworld");
        var fills = args.putArray("fills");
        var first = fills.addObject();
        var from1 = first.putArray("from");
        from1.add(one.getX());
        from1.add(one.getY());
        from1.add(one.getZ());
        var to1 = first.putArray("to");
        to1.add(one.getX());
        to1.add(one.getY());
        to1.add(one.getZ());
        first.put("block", "minecraft:diamond_block");
        var second = fills.addObject();
        var from2 = second.putArray("from");
        from2.add(two.getX());
        from2.add(two.getY());
        from2.add(two.getZ());
        var to2 = second.putArray("to");
        to2.add(two.getX());
        to2.add(two.getY());
        to2.add(two.getZ());
        second.put("block", "minecraft:gold_block");
        GametestHarness.callTool(b.dispatcher(), "block_fill_batch", args);

        helper.assertTrue(
                helper.getLevel().getBlockState(one).is(Blocks.DIAMOND_BLOCK),
                "block_fill_batch did not place the diamond fill at " + one);
        helper.assertTrue(
                helper.getLevel().getBlockState(two).is(Blocks.GOLD_BLOCK),
                "block_fill_batch did not place the gold fill at " + two);

        helper.succeed();
    }

    @GameTest(template = "minecraft_fabric_mcp:gametest/empty", batch = "mcp")
    public void block_render_region_returns_png_image(GameTestHelper helper) {
        var b = GametestHarness.bootstrap(helper.getLevel().getServer());
        BlockPos p = helper.absolutePos(new BlockPos(1, 2, 1));
        helper.getLevel().setBlockAndUpdate(p, Blocks.LIME_CONCRETE.defaultBlockState());

        var args = b.mapper().createObjectNode();
        args.put("dimension", "minecraft:overworld");
        var box = args.putObject("box");
        var from = box.putObject("from");
        from.put("x", p.getX());
        from.put("y", p.getY());
        from.put("z", p.getZ());
        var to = box.putObject("to");
        to.put("x", p.getX());
        to.put("y", p.getY());
        to.put("z", p.getZ());
        args.put("view", "iso");
        var result = GametestHarness.callTool(b.dispatcher(), "block_render_region", args);

        var content = result.path("content");
        helper.assertTrue(content.isArray() && !content.isEmpty(), "render returned no content blocks");
        var firstBlock = content.get(0);
        helper.assertTrue(
                "image".equals(firstBlock.path("type").asText()),
                "expected an image content block, got: " + firstBlock.path("type").asText());
        helper.assertTrue(
                !firstBlock.path("data").asText().isEmpty(), "image data was empty");

        helper.succeed();
    }

    @GameTest(template = "minecraft_fabric_mcp:gametest/empty", batch = "mcp")
    public void block_scan_summary_reports_non_air(GameTestHelper helper) {
        var b = GametestHarness.bootstrap(helper.getLevel().getServer());
        BlockPos p = helper.absolutePos(new BlockPos(1, 2, 1));
        helper.getLevel().setBlockAndUpdate(p, Blocks.GOLD_BLOCK.defaultBlockState());

        var args = b.mapper().createObjectNode();
        args.put("dimension", "minecraft:overworld");
        var box = args.putObject("box");
        var from = box.putObject("from");
        from.put("x", p.getX());
        from.put("y", p.getY());
        from.put("z", p.getZ());
        var to = box.putObject("to");
        to.put("x", p.getX());
        to.put("y", p.getY());
        to.put("z", p.getZ());
        var result = GametestHarness.callTool(b.dispatcher(), "block_scan_summary", args);

        String text = result.path("content").get(0).path("text").asText();
        helper.assertTrue(text.contains("non_air"), "scan summary missing non_air: " + text);
        helper.assertTrue(text.contains("minecraft:gold_block"), "histogram missing gold block: " + text);

        helper.succeed();
    }

    @GameTest(template = "minecraft_fabric_mcp:gametest/empty", batch = "mcp")
    public void block_get_map_color_returns_rgb(GameTestHelper helper) {
        var b = GametestHarness.bootstrap(helper.getLevel().getServer());
        BlockPos p = helper.absolutePos(new BlockPos(1, 2, 1));
        helper.getLevel().setBlockAndUpdate(p, Blocks.LIME_CONCRETE.defaultBlockState());

        var args = b.mapper().createObjectNode();
        args.put("dimension", "minecraft:overworld");
        var pos = args.putObject("position");
        pos.put("x", p.getX());
        pos.put("y", p.getY());
        pos.put("z", p.getZ());
        var result = GametestHarness.callTool(b.dispatcher(), "block_get_map_color", args);

        String text = result.path("content").get(0).path("text").asText();
        helper.assertTrue(text.contains("rgb"), "map color result missing rgb: " + text);
        helper.assertTrue(text.contains("hex"), "map color result missing hex: " + text);

        helper.succeed();
    }
}
