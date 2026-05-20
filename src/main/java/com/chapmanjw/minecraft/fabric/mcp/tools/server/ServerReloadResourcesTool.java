package com.chapmanjw.minecraft.fabric.mcp.tools.server;

import com.fasterxml.jackson.databind.JsonNode;

import com.chapmanjw.minecraft.fabric.mcp.protocol.Schemas;
import com.chapmanjw.minecraft.fabric.mcp.protocol.ToolContext;
import com.chapmanjw.minecraft.fabric.mcp.protocol.ToolResult;
import com.chapmanjw.minecraft.fabric.mcp.tools.BaseTool;
import com.chapmanjw.minecraft.fabric.mcp.tools.annotations.McpTool;

@McpTool(name = "server_reload_resources", description = "Reloads datapacks and resources. Equivalent to /reload.")
public final class ServerReloadResourcesTool extends BaseTool {

    private static final JsonNode SCHEMA = Schemas.object().description("No arguments.").build();

    public ServerReloadResourcesTool() {
        super("server_reload_resources");
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
                    context.adapter().serverReloadResources();
                    return ToolResult.ofText("Resources reloading…");
                });
    }
}
