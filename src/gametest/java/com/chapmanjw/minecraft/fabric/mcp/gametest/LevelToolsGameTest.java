package com.chapmanjw.minecraft.fabric.mcp.gametest;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * In-world smoke tests for the {@code level_*} domain. We mutate the test world
 * via the tool surface and verify direct Minecraft state matches.
 */
public final class LevelToolsGameTest implements FabricGameTest {

    @GameTest(template = "minecraft_fabric_mcp:gametest/empty", batch = "mcp")
    public void level_set_time_changes_world_time(GameTestHelper helper) {
        var b = GametestHarness.bootstrap(helper.getLevel().getServer());

        var args = b.mapper().createObjectNode();
        args.put("dimension", "minecraft:overworld");
        args.put("time", 6000L);
        GametestHarness.callTool(b.dispatcher(), "level_set_time", args);

        // Re-read via the tool — round-trip check.
        var getArgs = b.mapper().createObjectNode().put("dimension", "minecraft:overworld");
        var got = GametestHarness.callTool(b.dispatcher(), "level_get_time", getArgs);
        long observed = Long.parseLong(got.path("content").get(0).path("text").asText());
        helper.assertTrue(observed == 6000L, "level_set_time did not change time, got " + observed);

        helper.succeed();
    }

    @GameTest(template = "minecraft_fabric_mcp:gametest/empty", batch = "mcp")
    public void level_set_weather_to_clear_returns_clear(GameTestHelper helper) {
        var b = GametestHarness.bootstrap(helper.getLevel().getServer());

        var setArgs = b.mapper().createObjectNode();
        setArgs.put("dimension", "minecraft:overworld");
        setArgs.put("weather", "clear");
        setArgs.put("duration_ticks", 6000);
        GametestHarness.callTool(b.dispatcher(), "level_set_weather", setArgs);

        var getArgs = b.mapper().createObjectNode().put("dimension", "minecraft:overworld");
        var got = GametestHarness.callTool(b.dispatcher(), "level_get_weather", getArgs);
        String weather = got.path("content").get(0).path("text").asText();
        helper.assertTrue("clear".equals(weather), "expected clear, got " + weather);

        helper.succeed();
    }
}
