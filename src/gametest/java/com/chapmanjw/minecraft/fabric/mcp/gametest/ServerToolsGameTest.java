package com.chapmanjw.minecraft.fabric.mcp.gametest;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * Smoke tests for the {@code server_*} tool domain. Verifies that the dispatcher
 * can be wired against a live Minecraft server and that the canonical
 * introspection tools return non-empty payloads.
 */
public final class ServerToolsGameTest implements FabricGameTest {

    @GameTest(template = "minecraft_fabric_mcp:gametest/empty", batch = "mcp")
    public void server_get_status_returns_minecraft_version(GameTestHelper helper) {
        GametestHarness.Bootstrap b = GametestHarness.bootstrap(helper.getLevel().getServer());
        var result = GametestHarness.callTool(b.dispatcher(), "server_get_status", null);
        helper.assertTrue(
                result.path("structuredContent").path("minecraftVersion").asText().length() > 0,
                "server_get_status should report a minecraftVersion");
        helper.succeed();
    }

    @GameTest(template = "minecraft_fabric_mcp:gametest/empty", batch = "mcp")
    public void server_get_motd_round_trips(GameTestHelper helper) {
        GametestHarness.Bootstrap b = GametestHarness.bootstrap(helper.getLevel().getServer());

        // Set then get — the MOTD should persist for the duration of the test.
        var setArgs = b.mapper().createObjectNode().put("motd", "gametest motd");
        GametestHarness.callTool(b.dispatcher(), "server_set_motd", setArgs);

        var got = GametestHarness.callTool(b.dispatcher(), "server_get_motd", null);
        String motd = got.path("content").get(0).path("text").asText();
        helper.assertTrue("gametest motd".equals(motd), "MOTD round-trip mismatch: " + motd);

        helper.succeed();
    }
}
