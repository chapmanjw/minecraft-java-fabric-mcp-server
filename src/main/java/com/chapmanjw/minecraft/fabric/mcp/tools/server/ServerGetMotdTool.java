package com.chapmanjw.minecraft.fabric.mcp.tools.server;

import com.fasterxml.jackson.databind.JsonNode;

import com.chapmanjw.minecraft.fabric.mcp.protocol.Schemas;
import com.chapmanjw.minecraft.fabric.mcp.protocol.ToolContext;
import com.chapmanjw.minecraft.fabric.mcp.protocol.ToolResult;
import com.chapmanjw.minecraft.fabric.mcp.tools.BaseTool;
import com.chapmanjw.minecraft.fabric.mcp.tools.annotations.McpTool;

@McpTool(name = "server_get_motd", description = "Returns the server MOTD shown in the multiplayer menu.")
public final class ServerGetMotdTool extends BaseTool {

    private static final JsonNode SCHEMA = Schemas.object().description("No arguments.").build();

    public ServerGetMotdTool() {
        super("server_get_motd");
    }

    @Override
    public JsonNode inputSchema() {
        return SCHEMA;
    }

    @Override
    public ToolResult execute(JsonNode arguments, ToolContext context) {
        return onMainThread(context, ignored -> ToolResult.ofText(context.adapter().serverGetMotd()));
    }
}
