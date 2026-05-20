package com.chapmanjw.minecraft.fabric.mcp.tools.registry;

import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import com.chapmanjw.minecraft.fabric.mcp.adapter.dto.LootDropInfo;
import com.chapmanjw.minecraft.fabric.mcp.adapter.dto.RecipeInfo;
import com.chapmanjw.minecraft.fabric.mcp.adapter.dto.Vec3d;
import com.chapmanjw.minecraft.fabric.mcp.protocol.Schemas;
import com.chapmanjw.minecraft.fabric.mcp.protocol.ToolContext;
import com.chapmanjw.minecraft.fabric.mcp.protocol.ToolResult;
import com.chapmanjw.minecraft.fabric.mcp.protocol.error.ErrorCodes;
import com.chapmanjw.minecraft.fabric.mcp.protocol.error.McpException;
import com.chapmanjw.minecraft.fabric.mcp.tools.BaseTool;
import com.chapmanjw.minecraft.fabric.mcp.tools.Jsons;
import com.chapmanjw.minecraft.fabric.mcp.tools.annotations.McpTool;

/**
 * Loot, recipe, tag, and resource-loader tools — grouped because they share the
 * "read-only registry-style" pattern.
 */
public final class RegistryAccessTools {

    private RegistryAccessTools() {}

    // ----- loot ----------------------------------------------------------

    @McpTool(
            name = "loot_table_list",
            description = "Lists every registered loot table.",
            requiredFabricModules = {"fabric-loot-api-v3"})
    public static final class LootList extends BaseTool {
        private static final JsonNode SCHEMA = Schemas.object().description("No arguments.").build();

        public LootList() {
            super("loot_table_list");
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
                        List<String> list = context.adapter().lootTableList();
                        ArrayNode arr = context.mapper().createArrayNode();
                        list.forEach(arr::add);
                        return ToolResult.ofToon(arr);
                    });
        }
    }

    @McpTool(
            name = "loot_table_get_definition",
            description = "Returns the raw JSON definition for a loot table, if available.",
            requiredFabricModules = {"fabric-loot-api-v3"})
    public static final class LootGetDef extends BaseTool {
        private static final JsonNode SCHEMA =
                Schemas.object().required("id", Schemas.string("Loot table id")).build();

        public LootGetDef() {
            super("loot_table_get_definition");
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
                            ToolResult.ofText(
                                    context.adapter()
                                            .lootTableGetDefinition(id)
                                            .orElseThrow(
                                                    () ->
                                                            new McpException(
                                                                    ErrorCodes.TOOL_HANDLER_ERROR,
                                                                    "Definition not available for: " + id))));
        }
    }

    @McpTool(
            name = "loot_table_generate",
            description = "Generates the drops from a loot table without actually dropping them.",
            requiredFabricModules = {"fabric-loot-api-v3"})
    public static final class LootGenerate extends BaseTool {
        private static final JsonNode SCHEMA =
                Schemas.object()
                        .required("id", Schemas.string("Loot table id"))
                        .optional("position", Schemas.position3d("Origin position (for biome-aware tables)"))
                        .optional("killer_uuid", Schemas.string("Killer entity UUID"))
                        .optional("looting_uuid", Schemas.string("Entity holding the looting enchantment"))
                        .build();

        public LootGenerate() {
            super("loot_table_generate");
        }

        @Override
        public JsonNode inputSchema() {
            return SCHEMA;
        }

        @Override
        public ToolResult execute(JsonNode arguments, ToolContext context) {
            var r = reader(arguments);
            String id = r.requireString("id");
            JsonNode posN = r.optObject("position");
            Vec3d pos =
                    posN == null
                            ? new Vec3d(0, 0, 0)
                            : new Vec3d(
                                    posN.get("x").asDouble(),
                                    posN.get("y").asDouble(),
                                    posN.get("z").asDouble());
            UUID killer = parseUuid(r.optString("killer_uuid", null));
            UUID looting = parseUuid(r.optString("looting_uuid", null));
            return onMainThread(
                    context,
                    ignored -> {
                        LootDropInfo drops = context.adapter().lootTableGenerate(id, pos, killer, looting);
                        ArrayNode arr = context.mapper().createArrayNode();
                        drops.drops().forEach(d -> arr.add(Jsons.itemStack(context.mapper(), d)));
                        return ToolResult.ofToon(arr);
                    });
        }
    }

    private static UUID parseUuid(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(s);
        } catch (IllegalArgumentException e) {
            throw new McpException(ErrorCodes.TOOL_INPUT_INVALID, "Invalid UUID: " + s);
        }
    }

    // ----- recipe --------------------------------------------------------

    @McpTool(
            name = "recipe_list",
            description = "Lists every registered recipe of a given type (or all types when omitted). "
                    + "Valid type ids come from the minecraft:recipe_type registry: minecraft:crafting "
                    + "(covers both shaped and shapeless -- Mojang collapsed those into one type in 1.21+), "
                    + "minecraft:smelting, minecraft:blasting, minecraft:smoking, minecraft:campfire_cooking, "
                    + "minecraft:stonecutting, minecraft:smithing_transform, minecraft:smithing_trim.",
            requiredFabricModules = {"fabric-recipe-api-v1"})
    public static final class RecipeList extends BaseTool {
        private static final JsonNode SCHEMA =
                Schemas.object()
                        .optional(
                                "type",
                                Schemas.string(
                                        "Recipe type id from minecraft:recipe_type "
                                                + "(e.g. minecraft:crafting, minecraft:smelting). Shaped vs "
                                                + "shapeless are both minecraft:crafting in 1.21+."))
                        .build();

        public RecipeList() {
            super("recipe_list");
        }

        @Override
        public JsonNode inputSchema() {
            return SCHEMA;
        }

        @Override
        public ToolResult execute(JsonNode arguments, ToolContext context) {
            String type = reader(arguments).optString("type", null);
            return onMainThread(
                    context,
                    ignored -> {
                        var recipes = context.adapter().recipeList(type);
                        ArrayNode arr = context.mapper().createArrayNode();
                        for (RecipeInfo r : recipes) {
                            arr.add(toJson(context, r));
                        }
                        return ToolResult.ofToon(arr);
                    });
        }
    }

    @McpTool(
            name = "recipe_get_definition",
            description = "Returns a recipe definition by id.",
            requiredFabricModules = {"fabric-recipe-api-v1"})
    public static final class RecipeGetDef extends BaseTool {
        private static final JsonNode SCHEMA =
                Schemas.object().required("id", Schemas.string("Recipe id")).build();

        public RecipeGetDef() {
            super("recipe_get_definition");
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
                    ignored -> {
                        RecipeInfo r =
                                context.adapter()
                                        .recipeGetDefinition(id)
                                        .orElseThrow(
                                                () ->
                                                        new McpException(
                                                                ErrorCodes.TOOL_HANDLER_ERROR,
                                                                "Recipe not found: " + id));
                        return ToolResult.ofToon(toJson(context, r));
                    });
        }
    }

    @McpTool(
            name = "recipe_find_by_result",
            description = "Finds recipes whose result is the given item id.",
            requiredFabricModules = {"fabric-recipe-api-v1"})
    public static final class RecipeFindResult extends BaseTool {
        private static final JsonNode SCHEMA =
                Schemas.object().required("item_id", Schemas.string("Result item id")).build();

        public RecipeFindResult() {
            super("recipe_find_by_result");
        }

        @Override
        public JsonNode inputSchema() {
            return SCHEMA;
        }

        @Override
        public ToolResult execute(JsonNode arguments, ToolContext context) {
            String id = reader(arguments).requireString("item_id");
            return onMainThread(
                    context,
                    ignored -> {
                        var recipes = context.adapter().recipeFindByResult(id);
                        ArrayNode arr = context.mapper().createArrayNode();
                        for (RecipeInfo r : recipes) {
                            arr.add(toJson(context, r));
                        }
                        return ToolResult.ofToon(arr);
                    });
        }
    }

    @McpTool(
            name = "recipe_find_by_ingredient",
            description = "Finds recipes that consume the given item id as an ingredient.",
            requiredFabricModules = {"fabric-recipe-api-v1"})
    public static final class RecipeFindIngredient extends BaseTool {
        private static final JsonNode SCHEMA =
                Schemas.object().required("item_id", Schemas.string("Ingredient item id")).build();

        public RecipeFindIngredient() {
            super("recipe_find_by_ingredient");
        }

        @Override
        public JsonNode inputSchema() {
            return SCHEMA;
        }

        @Override
        public ToolResult execute(JsonNode arguments, ToolContext context) {
            String id = reader(arguments).requireString("item_id");
            return onMainThread(
                    context,
                    ignored -> {
                        var recipes = context.adapter().recipeFindByIngredient(id);
                        ArrayNode arr = context.mapper().createArrayNode();
                        for (RecipeInfo r : recipes) {
                            arr.add(toJson(context, r));
                        }
                        return ToolResult.ofToon(arr);
                    });
        }
    }

    private static ObjectNode toJson(ToolContext context, RecipeInfo r) {
        ObjectNode n = context.mapper().createObjectNode();
        n.put("id", r.id());
        n.put("type", r.type());
        n.put("group", r.group());
        ArrayNode ing = n.putArray("ingredients");
        r.ingredients().forEach(ing::add);
        n.put("result", r.result());
        n.put("resultCount", r.resultCount());
        return n;
    }

    // ----- tag -----------------------------------------------------------

    @McpTool(
            name = "tag_list_in_registry",
            description = "Lists every tag defined in a registry (block/item/biome/...).",
            requiredFabricModules = {"fabric-convention-tags-v2"})
    public static final class TagListInRegistry extends BaseTool {
        private static final JsonNode SCHEMA =
                Schemas.object()
                        .required("registry", Schemas.string("Registry id (e.g. minecraft:block, minecraft:item)"))
                        .build();

        public TagListInRegistry() {
            super("tag_list_in_registry");
        }

        @Override
        public JsonNode inputSchema() {
            return SCHEMA;
        }

        @Override
        public ToolResult execute(JsonNode arguments, ToolContext context) {
            String reg = reader(arguments).requireString("registry");
            return onMainThread(
                    context,
                    ignored -> {
                        List<String> list = context.adapter().tagListInRegistry(reg);
                        ArrayNode arr = context.mapper().createArrayNode();
                        list.forEach(arr::add);
                        return ToolResult.ofToon(arr);
                    });
        }
    }

    @McpTool(
            name = "tag_get_members",
            description = "Returns every member of a tag in a registry.",
            requiredFabricModules = {"fabric-convention-tags-v2"})
    public static final class TagGetMembers extends BaseTool {
        private static final JsonNode SCHEMA =
                Schemas.object()
                        .required("registry", Schemas.string("Registry id"))
                        .required("tag", Schemas.string("Tag id"))
                        .build();

        public TagGetMembers() {
            super("tag_get_members");
        }

        @Override
        public JsonNode inputSchema() {
            return SCHEMA;
        }

        @Override
        public ToolResult execute(JsonNode arguments, ToolContext context) {
            var r = reader(arguments);
            String reg = r.requireString("registry");
            String tag = r.requireString("tag");
            return onMainThread(
                    context,
                    ignored -> {
                        List<String> list = context.adapter().tagGetMembers(reg, tag);
                        ArrayNode arr = context.mapper().createArrayNode();
                        list.forEach(arr::add);
                        return ToolResult.ofToon(arr);
                    });
        }
    }

    @McpTool(
            name = "tag_check_membership",
            description = "Returns whether a given member belongs to a tag in a registry.",
            requiredFabricModules = {"fabric-convention-tags-v2"})
    public static final class TagCheckMembership extends BaseTool {
        private static final JsonNode SCHEMA =
                Schemas.object()
                        .required("registry", Schemas.string("Registry id"))
                        .required("tag", Schemas.string("Tag id"))
                        .required("member", Schemas.string("Member id"))
                        .build();

        public TagCheckMembership() {
            super("tag_check_membership");
        }

        @Override
        public JsonNode inputSchema() {
            return SCHEMA;
        }

        @Override
        public ToolResult execute(JsonNode arguments, ToolContext context) {
            var r = reader(arguments);
            String reg = r.requireString("registry");
            String tag = r.requireString("tag");
            String mem = r.requireString("member");
            return onMainThread(
                    context,
                    ignored ->
                            ToolResult.ofText(
                                    Boolean.toString(
                                            context.adapter().tagCheckMembership(reg, tag, mem))));
        }
    }

    // ----- resource ------------------------------------------------------

    @McpTool(
            name = "resource_loader_list_namespaces",
            description = "Lists every namespace registered with the resource manager.",
            requiredFabricModules = {"fabric-resource-loader-v0"})
    public static final class ResourceListNs extends BaseTool {
        private static final JsonNode SCHEMA = Schemas.object().description("No arguments.").build();

        public ResourceListNs() {
            super("resource_loader_list_namespaces");
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
                        List<String> list = context.adapter().resourceLoaderListNamespaces();
                        ArrayNode arr = context.mapper().createArrayNode();
                        list.forEach(arr::add);
                        return ToolResult.ofToon(arr);
                    });
        }
    }

    @McpTool(
            name = "resource_loader_get_resource",
            description =
                    "Reads a resource by location and returns its bytes as base64. Useful for inspecting"
                            + " loaded datapacks and resource packs.",
            requiredFabricModules = {"fabric-resource-loader-v0"})
    public static final class ResourceGet extends BaseTool {
        private static final JsonNode SCHEMA =
                Schemas.object()
                        .required("namespace", Schemas.string("Resource namespace"))
                        .required("path", Schemas.string("Resource path"))
                        .build();

        public ResourceGet() {
            super("resource_loader_get_resource");
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
                    ignored -> {
                        byte[] bytes =
                                context.adapter()
                                        .resourceLoaderGetResource(ns, path)
                                        .orElseThrow(
                                                () ->
                                                        new McpException(
                                                                ErrorCodes.TOOL_HANDLER_ERROR,
                                                                "Resource not found: " + ns + ":" + path));
                        return ToolResult.ofText(
                                java.util.Base64.getEncoder().encodeToString(bytes));
                    });
        }
    }
}
