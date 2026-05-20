package com.chapmanjw.minecraft.fabric.mcp.tools.datapack;

import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import com.chapmanjw.minecraft.fabric.mcp.adapter.dto.DatapackInfo;
import com.chapmanjw.minecraft.fabric.mcp.protocol.Schemas;
import com.chapmanjw.minecraft.fabric.mcp.protocol.ToolContext;
import com.chapmanjw.minecraft.fabric.mcp.protocol.ToolResult;
import com.chapmanjw.minecraft.fabric.mcp.tools.BaseTool;
import com.chapmanjw.minecraft.fabric.mcp.tools.annotations.McpTool;

/** Datapack tools. */
public final class DatapackTools {

    private DatapackTools() {}

    private static ObjectNode toJson(ObjectNode parent, DatapackInfo d) {
        parent.put("id", d.id());
        parent.put("displayName", d.displayName());
        parent.put("enabled", d.enabled());
        parent.put("builtin", d.builtin());
        return parent;
    }

    @McpTool(name = "datapack_list_available", description = "Lists every discovered datapack.")
    public static final class ListAvailable extends BaseTool {
        private static final JsonNode SCHEMA = Schemas.object().description("No arguments.").build();

        public ListAvailable() {
            super("datapack_list_available");
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
                        List<DatapackInfo> list = context.adapter().datapackListAvailable();
                        ArrayNode arr = context.mapper().createArrayNode();
                        for (DatapackInfo d : list) {
                            toJson(arr.addObject(), d);
                        }
                        return ToolResult.ofToon(arr);
                    });
        }
    }

    @McpTool(name = "datapack_list_enabled", description = "Lists currently-enabled datapacks.")
    public static final class ListEnabled extends BaseTool {
        private static final JsonNode SCHEMA = Schemas.object().description("No arguments.").build();

        public ListEnabled() {
            super("datapack_list_enabled");
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
                        List<DatapackInfo> list = context.adapter().datapackListEnabled();
                        ArrayNode arr = context.mapper().createArrayNode();
                        for (DatapackInfo d : list) {
                            toJson(arr.addObject(), d);
                        }
                        return ToolResult.ofToon(arr);
                    });
        }
    }

    @McpTool(name = "datapack_enable", description = "Enables a datapack by id.")
    public static final class Enable extends BaseTool {
        private static final JsonNode SCHEMA =
                Schemas.object().required("id", Schemas.string("Datapack id")).build();

        public Enable() {
            super("datapack_enable");
        }

        @Override
        public JsonNode inputSchema() {
            return SCHEMA;
        }

        @Override
        public ToolResult execute(JsonNode arguments, ToolContext context) {
            String id = reader(arguments).requireString("id");
            return onMainThread(
                    context,
                    ignored ->
                            ToolResult.ofText(context.adapter().datapackEnable(id) ? "enabled" : "failed"));
        }
    }

    @McpTool(name = "datapack_disable", description = "Disables a datapack by id.")
    public static final class Disable extends BaseTool {
        private static final JsonNode SCHEMA =
                Schemas.object().required("id", Schemas.string("Datapack id")).build();

        public Disable() {
            super("datapack_disable");
        }

        @Override
        public JsonNode inputSchema() {
            return SCHEMA;
        }

        @Override
        public ToolResult execute(JsonNode arguments, ToolContext context) {
            String id = reader(arguments).requireString("id");
            return onMainThread(
                    context,
                    ignored ->
                            ToolResult.ofText(context.adapter().datapackDisable(id) ? "disabled" : "failed"));
        }
    }
}
