package com.chapmanjw.minecraft.fabric.mcp.tools.command;

import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import com.chapmanjw.minecraft.fabric.mcp.adapter.dto.CommandResult;
import com.chapmanjw.minecraft.fabric.mcp.protocol.Schemas;
import com.chapmanjw.minecraft.fabric.mcp.protocol.ToolContext;
import com.chapmanjw.minecraft.fabric.mcp.protocol.ToolResult;
import com.chapmanjw.minecraft.fabric.mcp.protocol.error.ErrorCodes;
import com.chapmanjw.minecraft.fabric.mcp.protocol.error.McpException;
import com.chapmanjw.minecraft.fabric.mcp.tools.BaseTool;
import com.chapmanjw.minecraft.fabric.mcp.tools.annotations.McpTool;

/** Command dispatch tools. */
public final class CommandTools {

    private CommandTools() {}

    private static ObjectNode toJson(ObjectNode parent, CommandResult res) {
        parent.put("successCount", res.successCount());
        if (res.error() != null) {
            parent.put("error", res.error());
        }
        ArrayNode out = parent.putArray("output");
        for (String line : res.output()) {
            out.add(line);
        }
        return parent;
    }

    @McpTool(
            name = "command_execute",
            description =
                    "Runs a slash command as the console source. Captures /tellraw and feedback messages"
                            + " into the output array.",
            requiredFabricModules = {"fabric-command-api-v2"})
    public static final class Execute extends BaseTool {
        private static final JsonNode SCHEMA =
                Schemas.object()
                        .required("command", Schemas.string("Command (with or without leading slash)"))
                        .build();

        public Execute() {
            super("command_execute");
        }

        @Override
        public JsonNode inputSchema() {
            return SCHEMA;
        }

        @Override
        public ToolResult execute(JsonNode arguments, ToolContext context) {
            String cmd = reader(arguments).requireString("command");
            if (cmd.startsWith("/")) {
                cmd = cmd.substring(1);
            }
            String finalCmd = cmd;
            return onMainThread(
                    context,
                    ignored -> {
                        CommandResult res = context.adapter().commandExecute(finalCmd);
                        ObjectNode payload = context.mapper().createObjectNode();
                        toJson(payload, res);
                        return ToolResult.ofToon(payload);
                    });
        }
    }

    @McpTool(
            name = "command_execute_as",
            description = "Runs a slash command as a specific entity (vanilla /execute as semantics).",
            requiredFabricModules = {"fabric-command-api-v2"})
    public static final class ExecuteAs extends BaseTool {
        private static final JsonNode SCHEMA =
                Schemas.object()
                        .required("command", Schemas.string("Command to run"))
                        .required("actor", Schemas.string("Entity UUID to act as"))
                        .build();

        public ExecuteAs() {
            super("command_execute_as");
        }

        @Override
        public JsonNode inputSchema() {
            return SCHEMA;
        }

        @Override
        public ToolResult execute(JsonNode arguments, ToolContext context) {
            var r = reader(arguments);
            String cmd = r.requireString("command");
            if (cmd.startsWith("/")) {
                cmd = cmd.substring(1);
            }
            UUID actor;
            try {
                actor = UUID.fromString(r.requireString("actor"));
            } catch (IllegalArgumentException e) {
                throw new McpException(ErrorCodes.TOOL_INPUT_INVALID, "Invalid actor UUID");
            }
            String finalCmd = cmd;
            UUID finalActor = actor;
            return onMainThread(
                    context,
                    ignored -> {
                        CommandResult res = context.adapter().commandExecuteAs(finalCmd, finalActor);
                        ObjectNode payload = context.mapper().createObjectNode();
                        toJson(payload, res);
                        return ToolResult.ofToon(payload);
                    });
        }
    }

    @McpTool(
            name = "command_register",
            description =
                    "Registers a custom slash command callable from in-game. Reserved for v0.2.0; v0.1.0"
                            + " returns an actionable error.",
            requiredFabricModules = {"fabric-command-api-v2"},
            admin = true)
    public static final class Register extends BaseTool {
        private static final JsonNode SCHEMA =
                Schemas.object()
                        .required("name", Schemas.string("Command name"))
                        .required("handler_url", Schemas.string("Webhook URL that will receive invocations"))
                        .build();

        public Register() {
            super("command_register");
        }

        @Override
        public JsonNode inputSchema() {
            return SCHEMA;
        }

        @Override
        public ToolResult execute(JsonNode arguments, ToolContext context) {
            throw new McpException(
                    ErrorCodes.TOOL_HANDLER_ERROR,
                    "command_register is reserved for v0.2.0. Use command_execute with a custom command name for now.");
        }
    }
}
