package com.chapmanjw.minecraft.fabric.mcp.tools.server;

import com.fasterxml.jackson.databind.JsonNode;

import com.chapmanjw.minecraft.fabric.mcp.adapter.dto.ServerStatus;
import com.chapmanjw.minecraft.fabric.mcp.protocol.Schemas;
import com.chapmanjw.minecraft.fabric.mcp.protocol.ToolContext;
import com.chapmanjw.minecraft.fabric.mcp.protocol.ToolResult;
import com.chapmanjw.minecraft.fabric.mcp.tools.BaseTool;
import com.chapmanjw.minecraft.fabric.mcp.tools.Jsons;
import com.chapmanjw.minecraft.fabric.mcp.tools.annotations.McpTool;

@McpTool(
        name = "server_get_status",
        description =
                "Returns Minecraft server status — version, uptime, TPS/MSPT, online player count,"
                        + " loaded dimensions, mod version, registered tool count.")
public final class ServerGetStatusTool extends BaseTool {

    private static final JsonNode SCHEMA = Schemas.object().description("No arguments.").build();

    public ServerGetStatusTool() {
        super("server_get_status");
    }

    @Override
    public JsonNode inputSchema() {
        return SCHEMA;
    }

    @Override
    public ToolResult execute(JsonNode arguments, ToolContext context) {
        return onMainThread(
                context,
                ignored -> {
                    ServerStatus s = context.adapter().serverGetStatus();
                    return ToolResult.ofToon(Jsons.serverStatus(context.mapper(), s));
                });
    }
}
