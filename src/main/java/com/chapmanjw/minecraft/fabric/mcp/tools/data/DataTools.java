package com.chapmanjw.minecraft.fabric.mcp.tools.data;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;

import com.chapmanjw.minecraft.fabric.mcp.protocol.Schemas;
import com.chapmanjw.minecraft.fabric.mcp.protocol.ToolContext;
import com.chapmanjw.minecraft.fabric.mcp.protocol.ToolResult;
import com.chapmanjw.minecraft.fabric.mcp.protocol.error.ErrorCodes;
import com.chapmanjw.minecraft.fabric.mcp.protocol.error.McpException;
import com.chapmanjw.minecraft.fabric.mcp.tools.BaseTool;
import com.chapmanjw.minecraft.fabric.mcp.tools.annotations.McpTool;

/** Data storage and data attachment tools. */
public final class DataTools {

    private DataTools() {}

    @McpTool(
            name = "data_storage_get",
            description = "Reads a value from vanilla data storage (e.g. /data get storage <ns> <path>).")
    public static final class StorageGet extends BaseTool {
        private static final JsonNode SCHEMA =
                Schemas.object()
                        .required("namespace", Schemas.string("Namespace id"))
                        .optional("path", Schemas.string("Path within the storage tree"))
                        .build();

        public StorageGet() {
            super("data_storage_get");
        }

        @Override
        public JsonNode inputSchema() {
            return SCHEMA;
        }

        @Override
        public ToolResult execute(JsonNode arguments, ToolContext context) {
            var r = reader(arguments);
            String ns = r.requireString("namespace");
            String path = r.optString("path", "");
            return onMainThread(
                    context,
                    ignored ->
                            ToolResult.ofText(
                                    context.adapter()
                                            .dataStorageGet(ns, path)
                                            .orElseThrow(
                                                    () ->
                                                            new McpException(
                                                                    ErrorCodes.TOOL_HANDLER_ERROR,
                                                                    "No value at storage:" + ns + " path:" + path))));
        }
    }

    @McpTool(
            name = "data_storage_set",
            description = "Writes (or merges) a value into vanilla data storage.")
    public static final class StorageSet extends BaseTool {
        private static final JsonNode SCHEMA =
                Schemas.object()
                        .required("namespace", Schemas.string("Namespace id"))
                        .required("path", Schemas.string("Path within the storage tree"))
                        .required("snbt", Schemas.string("Value (SNBT)"))
                        .optional("merge", Schemas.bool("Merge instead of overwrite (default false)"))
                        .build();

        public StorageSet() {
            super("data_storage_set");
        }

        @Override
        public JsonNode inputSchema() {
            return SCHEMA;
        }

        @Override
        public ToolResult execute(JsonNode arguments, ToolContext context) {
            var r = reader(arguments);
            String ns = r.requireString("namespace");
            String path = r.requireString("path");
            String snbt = r.requireString("snbt");
            boolean merge = r.optBoolean("merge", false);
            return onMainThread(
                    context,
                    ignored ->
                            ToolResult.ofText(
                                    context.adapter().dataStorageSet(ns, path, snbt, merge) ? "set" : "failed"));
        }
    }

    @McpTool(name = "data_storage_remove", description = "Removes a value from vanilla data storage.")
    public static final class StorageRemove extends BaseTool {
        private static final JsonNode SCHEMA =
                Schemas.object()
                        .required("namespace", Schemas.string("Namespace id"))
                        .required("path", Schemas.string("Path within the storage tree"))
                        .build();

        public StorageRemove() {
            super("data_storage_remove");
        }

        @Override
        public JsonNode inputSchema() {
            return SCHEMA;
        }

        @Override
        public ToolResult execute(JsonNode arguments, ToolContext context) {
            var r = reader(arguments);
            String ns = r.requireString("namespace");
            String path = r.requireString("path");
            return onMainThread(
                    context,
                    ignored ->
                            ToolResult.ofText(
                                    context.adapter().dataStorageRemove(ns, path) ? "removed" : "failed"));
        }
    }

    @McpTool(
            name = "data_storage_list_namespaces",
            description = "Lists every namespace that has data storage entries.")
    public static final class StorageListNamespaces extends BaseTool {
        private static final JsonNode SCHEMA = Schemas.object().description("No arguments.").build();

        public StorageListNamespaces() {
            super("data_storage_list_namespaces");
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
                        var list = context.adapter().dataStorageListNamespaces();
                        ArrayNode arr = context.mapper().createArrayNode();
                        list.forEach(arr::add);
                        return ToolResult.ofToon(arr);
                    });
        }
    }

    @McpTool(
            name = "data_attachment_get",
            description = "Reads a Fabric data-attachment value on an entity, chunk, or world. "
                    + "The attachment type (namespace:key) must already be registered by a loaded "
                    + "mod via AttachmentRegistry -- this tool does not create new attachment types.",
            requiredFabricModules = {"fabric-data-attachment-api-v1"})
    public static final class AttachmentGet extends BaseTool {
        private static final JsonNode SCHEMA =
                Schemas.object()
                        .required("target", Schemas.string("Target: entity:<uuid> | chunk:<dim>:<x>:<z> | world:<dim>"))
                        .required("namespace", Schemas.string("Namespace"))
                        .required("key", Schemas.string("Attachment key"))
                        .build();

        public AttachmentGet() {
            super("data_attachment_get");
        }

        @Override
        public JsonNode inputSchema() {
            return SCHEMA;
        }

        @Override
        public ToolResult execute(JsonNode arguments, ToolContext context) {
            var r = reader(arguments);
            String t = r.requireString("target");
            String ns = r.requireString("namespace");
            String key = r.requireString("key");
            return onMainThread(
                    context,
                    ignored ->
                            ToolResult.ofText(
                                    context.adapter()
                                            .dataAttachmentGet(t, ns, key)
                                            .orElseThrow(
                                                    () ->
                                                            new McpException(
                                                                    ErrorCodes.TOOL_HANDLER_ERROR,
                                                                    "No attachment value"))));
        }
    }

    @McpTool(
            name = "data_attachment_set",
            description = "Writes a Fabric data-attachment value. The attachment type "
                    + "(namespace:key) must already be registered by a loaded mod via "
                    + "AttachmentRegistry -- this tool does not create new attachment types, "
                    + "and a set against an unregistered type returns \"failed\".",
            requiredFabricModules = {"fabric-data-attachment-api-v1"})
    public static final class AttachmentSet extends BaseTool {
        private static final JsonNode SCHEMA =
                Schemas.object()
                        .required("target", Schemas.string("Target identifier"))
                        .required("namespace", Schemas.string("Namespace"))
                        .required("key", Schemas.string("Attachment key"))
                        .required("snbt", Schemas.string("Value (SNBT)"))
                        .build();

        public AttachmentSet() {
            super("data_attachment_set");
        }

        @Override
        public JsonNode inputSchema() {
            return SCHEMA;
        }

        @Override
        public ToolResult execute(JsonNode arguments, ToolContext context) {
            var r = reader(arguments);
            return onMainThread(
                    context,
                    ignored ->
                            ToolResult.ofText(
                                    context.adapter()
                                                    .dataAttachmentSet(
                                                            r.requireString("target"),
                                                            r.requireString("namespace"),
                                                            r.requireString("key"),
                                                            r.requireString("snbt"))
                                            ? "set"
                                            : "failed"));
        }
    }

    @McpTool(
            name = "data_attachment_remove",
            description = "Removes a Fabric data-attachment value. The attachment type "
                    + "(namespace:key) must already be registered by a loaded mod via "
                    + "AttachmentRegistry.",
            requiredFabricModules = {"fabric-data-attachment-api-v1"})
    public static final class AttachmentRemove extends BaseTool {
        private static final JsonNode SCHEMA =
                Schemas.object()
                        .required("target", Schemas.string("Target identifier"))
                        .required("namespace", Schemas.string("Namespace"))
                        .required("key", Schemas.string("Attachment key"))
                        .build();

        public AttachmentRemove() {
            super("data_attachment_remove");
        }

        @Override
        public JsonNode inputSchema() {
            return SCHEMA;
        }

        @Override
        public ToolResult execute(JsonNode arguments, ToolContext context) {
            var r = reader(arguments);
            return onMainThread(
                    context,
                    ignored ->
                            ToolResult.ofText(
                                    context.adapter()
                                                    .dataAttachmentRemove(
                                                            r.requireString("target"),
                                                            r.requireString("namespace"),
                                                            r.requireString("key"))
                                            ? "removed"
                                            : "failed"));
        }
    }

    @McpTool(
            name = "data_attachment_list_keys",
            description = "Lists all data-attachment keys present for a target/namespace.",
            requiredFabricModules = {"fabric-data-attachment-api-v1"})
    public static final class AttachmentListKeys extends BaseTool {
        private static final JsonNode SCHEMA =
                Schemas.object()
                        .required("target", Schemas.string("Target identifier"))
                        .required("namespace", Schemas.string("Namespace"))
                        .build();

        public AttachmentListKeys() {
            super("data_attachment_list_keys");
        }

        @Override
        public JsonNode inputSchema() {
            return SCHEMA;
        }

        @Override
        public ToolResult execute(JsonNode arguments, ToolContext context) {
            var r = reader(arguments);
            String t = r.requireString("target");
            String ns = r.requireString("namespace");
            return onMainThread(
                    context,
                    ignored -> {
                        var keys = context.adapter().dataAttachmentListKeys(t, ns);
                        ArrayNode arr = context.mapper().createArrayNode();
                        keys.forEach(arr::add);
                        return ToolResult.ofToon(arr);
                    });
        }
    }
}
