package com.chapmanjw.minecraft.fabric.mcp.tools.server;

import com.fasterxml.jackson.databind.JsonNode;

import com.chapmanjw.minecraft.fabric.mcp.protocol.Schemas;
import com.chapmanjw.minecraft.fabric.mcp.protocol.ToolContext;
import com.chapmanjw.minecraft.fabric.mcp.protocol.ToolResult;
import com.chapmanjw.minecraft.fabric.mcp.tools.BaseTool;
import com.chapmanjw.minecraft.fabric.mcp.tools.annotations.McpTool;

@McpTool(name = "server_set_motd", description = "Sets the server MOTD. Note: not persisted across restarts.")
public final class ServerSetMotdTool extends BaseTool {

    private static final JsonNode SCHEMA =
            Schemas.object().required("motd", Schemas.string("The new MOTD string.")).build();

    public ServerSetMotdTool() {
        super("server_set_motd");
    }

    @Override
    public JsonNode inputSchema() {
        return SCHEMA;
    }

    @Override
    public ToolResult execute(JsonNode arguments, ToolContext context) {
        String motd = reader(arguments).requireString("motd");
        return onMainThread(
                context,
                ignored -> {
                    context.adapter().serverSetMotd(motd);
                    return ToolResult.ofText("MOTD updated.");
                });
    }
}
