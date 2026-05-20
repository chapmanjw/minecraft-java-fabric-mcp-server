package com.chapmanjw.minecraft.fabric.mcp.gametest;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/** Scoreboard-domain gametests. */
public final class ScoreboardToolsGameTest implements FabricGameTest {

    @GameTest(template = "minecraft_fabric_mcp:gametest/empty", batch = "mcp")
    public void scoreboard_add_then_remove_objective(GameTestHelper helper) {
        var b = GametestHarness.bootstrap(helper.getLevel().getServer());

        var addArgs = b.mapper().createObjectNode();
        addArgs.put("name", "test_obj");
        addArgs.put("criterion", "dummy");
        GametestHarness.callTool(b.dispatcher(), "scoreboard_add_objective", addArgs);
        helper.assertTrue(
                helper.getLevel().getServer().getScoreboard().getObjective("test_obj") != null,
                "objective not registered");

        var rmArgs = b.mapper().createObjectNode();
        rmArgs.put("name", "test_obj");
        GametestHarness.callTool(b.dispatcher(), "scoreboard_remove_objective", rmArgs);
        helper.assertTrue(
                helper.getLevel().getServer().getScoreboard().getObjective("test_obj") == null,
                "objective should be removed");

        helper.succeed();
    }
}
