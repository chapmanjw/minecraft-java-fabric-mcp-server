package com.chapmanjw.minecraft.fabric.mcp.tools.entity;

import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import com.chapmanjw.minecraft.fabric.mcp.adapter.dto.EntityInfo;
import com.chapmanjw.minecraft.fabric.mcp.adapter.dto.StatusEffectInfo;
import com.chapmanjw.minecraft.fabric.mcp.adapter.dto.Vec3d;
import com.chapmanjw.minecraft.fabric.mcp.protocol.Schemas;
import com.chapmanjw.minecraft.fabric.mcp.protocol.ToolContext;
import com.chapmanjw.minecraft.fabric.mcp.protocol.ToolResult;
import com.chapmanjw.minecraft.fabric.mcp.protocol.error.ErrorCodes;
import com.chapmanjw.minecraft.fabric.mcp.protocol.error.McpException;
import com.chapmanjw.minecraft.fabric.mcp.tools.BaseTool;
import com.chapmanjw.minecraft.fabric.mcp.tools.Jsons;
import com.chapmanjw.minecraft.fabric.mcp.tools.annotations.McpTool;

/** Non-player entity tools. */
public final class EntityTools {

    private EntityTools() {}

    private static UUID readUuid(String name, String s) {
        try {
            return UUID.fromString(s);
        } catch (IllegalArgumentException e) {
            throw new McpException(ErrorCodes.TOOL_INPUT_INVALID, "Invalid UUID for '" + name + "': " + s);
        }
    }

    private static Vec3d readVec3d(JsonNode node) {
        return new Vec3d(
                node.get("x").asDouble(), node.get("y").asDouble(), node.get("z").asDouble());
    }

    private static JsonNode uuidSchema() {
        return Schemas.string("Entity UUID");
    }

    @McpTool(name = "entity_summon", description = "Summons an entity at a position with optional SNBT.")
    public static final class Summon extends BaseTool {
        private static final JsonNode SCHEMA =
                Schemas.object()
                        .required("dimension", Schemas.string("Dimension identifier"))
                        .required("entity_type", Schemas.string("Entity type id (e.g. minecraft:zombie)"))
                        .required("position", Schemas.position3d("Spawn position"))
                        .optional("nbt", Schemas.string("SNBT applied at spawn time"))
                        .build();

        public Summon() {
            super("entity_summon");
        }

        @Override
        public JsonNode inputSchema() {
            return SCHEMA;
        }

        @Override
        public ToolResult execute(JsonNode arguments, ToolContext context) {
            var r = reader(arguments);
            String dim = r.requireString("dimension");
            String type = r.requireString("entity_type");
            Vec3d pos = readVec3d(r.requireObject("position"));
            String nbt = r.optString("nbt", null);
            return onMainThread(
                    context,
                    ignored -> {
                        UUID uuid =
                                context.adapter()
                                        .entitySummon(dim, type, pos, nbt)
                                        .orElseThrow(
                                                () ->
                                                        new McpException(
                                                                ErrorCodes.TOOL_HANDLER_ERROR,
                                                                "Failed to summon " + type));
                        ObjectNode n = context.mapper().createObjectNode();
                        n.put("uuid", uuid.toString());
                        n.put("type", type);
                        return ToolResult.ofToon(n);
                    });
        }
    }

    @McpTool(name = "entity_get", description = "Looks up an entity by UUID and returns its current state.")
    public static final class Get extends BaseTool {
        private static final JsonNode SCHEMA =
                Schemas.object().required("uuid", uuidSchema()).build();

        public Get() {
            super("entity_get");
        }

        @Override
        public JsonNode inputSchema() {
            return SCHEMA;
        }

        @Override
        public ToolResult execute(JsonNode arguments, ToolContext context) {
            UUID uuid = readUuid("uuid", reader(arguments).requireString("uuid"));
            return onMainThread(
                    context,
                    ignored -> {
                        EntityInfo info =
                                context.adapter()
                                        .entityGet(uuid)
                                        .orElseThrow(
                                                () ->
                                                        new McpException(
                                                                ErrorCodes.TOOL_HANDLER_ERROR,
                                                                "Entity not found: " + uuid));
                        return ToolResult.ofToon(Jsons.entity(context.mapper(), info));
                    });
        }
    }

    @McpTool(
            name = "entity_query",
            description =
                    "Returns entities matching a vanilla selector. Simple selectors (@e, @a) are"
                            + " enumerated directly; complex selectors are limited in v0.1.0.")
    public static final class Query extends BaseTool {
        private static final JsonNode SCHEMA =
                Schemas.object()
                        .required("dimension", Schemas.string("Dimension identifier"))
                        .required("selector", Schemas.string("Vanilla selector, e.g. @e, @a"))
                        .optional("limit", Schemas.integerBetween("Max results", 1, 1024))
                        .build();

        public Query() {
            super("entity_query");
        }

        @Override
        public JsonNode inputSchema() {
            return SCHEMA;
        }

        @Override
        public ToolResult execute(JsonNode arguments, ToolContext context) {
            var r = reader(arguments);
            String dim = r.requireString("dimension");
            String sel = r.requireString("selector");
            int limit = r.optInt("limit", 100);
            return onMainThread(
                    context,
                    ignored -> {
                        List<EntityInfo> entities = context.adapter().entityQuery(dim, sel, limit);
                        ArrayNode arr = context.mapper().createArrayNode();
                        for (EntityInfo e : entities) {
                            arr.add(Jsons.entity(context.mapper(), e));
                        }
                        return ToolResult.ofToon(arr);
                    });
        }
    }

    @McpTool(
            name = "entity_get_components",
            description = "Return the entity's data-component map. Note: currently returns an empty map for non-player entities — vanilla's component API for entities is limited. Use entity_get_nbt for full fidelity.")
    public static final class GetComponents extends BaseTool {
        private static final JsonNode SCHEMA =
                Schemas.object().required("uuid", uuidSchema()).build();

        public GetComponents() {
            super("entity_get_components");
        }

        @Override
        public JsonNode inputSchema() {
            return SCHEMA;
        }

        @Override
        public ToolResult execute(JsonNode arguments, ToolContext context) {
            UUID uuid = readUuid("uuid", reader(arguments).requireString("uuid"));
            return onMainThread(
                    context,
                    ignored -> {
                        var map =
                                context.adapter()
                                        .entityGetComponents(uuid)
                                        .orElseThrow(
                                                () ->
                                                        new McpException(
                                                                ErrorCodes.TOOL_HANDLER_ERROR,
                                                                "Entity not found"));
                        ObjectNode payload = context.mapper().createObjectNode();
                        map.forEach(payload::put);
                        return ToolResult.ofToon(payload);
                    });
        }
    }

    @McpTool(name = "entity_get_nbt", description = "Returns the entity's full NBT as SNBT.")
    public static final class GetNbt extends BaseTool {
        private static final JsonNode SCHEMA =
                Schemas.object().required("uuid", uuidSchema()).build();

        public GetNbt() {
            super("entity_get_nbt");
        }

        @Override
        public JsonNode inputSchema() {
            return SCHEMA;
        }

        @Override
        public ToolResult execute(JsonNode arguments, ToolContext context) {
            UUID uuid = readUuid("uuid", reader(arguments).requireString("uuid"));
            return onMainThread(
                    context,
                    ignored ->
                            ToolResult.ofText(
                                    context.adapter()
                                            .entityGetNbt(uuid)
                                            .orElseThrow(
                                                    () ->
                                                            new McpException(
                                                                    ErrorCodes.TOOL_HANDLER_ERROR,
                                                                    "Entity not found"))));
        }
    }

    @McpTool(name = "entity_set_nbt", description = "Merges SNBT into the entity (vanilla /data merge entity).")
    public static final class SetNbt extends BaseTool {
        private static final JsonNode SCHEMA =
                Schemas.object()
                        .required("uuid", uuidSchema())
                        .required("nbt", Schemas.string("SNBT to merge"))
                        .build();

        public SetNbt() {
            super("entity_set_nbt");
        }

        @Override
        public JsonNode inputSchema() {
            return SCHEMA;
        }

        @Override
        public ToolResult execute(JsonNode arguments, ToolContext context) {
            var r = reader(arguments);
            UUID uuid = readUuid("uuid", r.requireString("uuid"));
            String snbt = r.requireString("nbt");
            return onMainThread(
                    context,
                    ignored ->
                            ToolResult.ofText(
                                    context.adapter().entitySetNbt(uuid, snbt) ? "merged" : "no change"));
        }
    }

    @McpTool(name = "entity_teleport", description = "Teleports an entity, optionally facing a point.")
    public static final class Teleport extends BaseTool {
        private static final JsonNode SCHEMA =
                Schemas.object()
                        .required("uuid", uuidSchema())
                        .required("dimension", Schemas.string("Destination dimension"))
                        .required("position", Schemas.position3d("Destination position"))
                        .optional("facing", Schemas.position3d("World position to face (optional)"))
                        .build();

        public Teleport() {
            super("entity_teleport");
        }

        @Override
        public JsonNode inputSchema() {
            return SCHEMA;
        }

        @Override
        public ToolResult execute(JsonNode arguments, ToolContext context) {
            var r = reader(arguments);
            UUID uuid = readUuid("uuid", r.requireString("uuid"));
            String dim = r.requireString("dimension");
            Vec3d pos = readVec3d(r.requireObject("position"));
            JsonNode facingNode = r.optObject("facing");
            Vec3d facing = facingNode == null ? null : readVec3d(facingNode);
            return onMainThread(
                    context,
                    ignored ->
                            ToolResult.ofText(
                                    context.adapter().entityTeleport(uuid, dim, pos, facing) ? "teleported" : "failed"));
        }
    }

    @McpTool(name = "entity_apply_damage", description = "Applies damage to an entity from a named source.")
    public static final class ApplyDamage extends BaseTool {
        private static final JsonNode SCHEMA =
                Schemas.object()
                        .required("uuid", uuidSchema())
                        .required("amount", Schemas.number("Damage amount"))
                        .optional("damage_type", Schemas.string("Damage type id (default minecraft:generic)"))
                        .build();

        public ApplyDamage() {
            super("entity_apply_damage");
        }

        @Override
        public JsonNode inputSchema() {
            return SCHEMA;
        }

        @Override
        public ToolResult execute(JsonNode arguments, ToolContext context) {
            var r = reader(arguments);
            UUID uuid = readUuid("uuid", r.requireString("uuid"));
            float amount = (float) r.requireDouble("amount");
            String type = r.optString("damage_type", "minecraft:generic");
            return onMainThread(
                    context,
                    ignored ->
                            ToolResult.ofText(
                                    context.adapter().entityApplyDamage(uuid, amount, type) ? "applied" : "failed"));
        }
    }

    @McpTool(name = "entity_set_velocity", description = "Sets the entity's motion vector.")
    public static final class SetVelocity extends BaseTool {
        private static final JsonNode SCHEMA =
                Schemas.object()
                        .required("uuid", uuidSchema())
                        .required("velocity", Schemas.position3d("Velocity vector"))
                        .build();

        public SetVelocity() {
            super("entity_set_velocity");
        }

        @Override
        public JsonNode inputSchema() {
            return SCHEMA;
        }

        @Override
        public ToolResult execute(JsonNode arguments, ToolContext context) {
            var r = reader(arguments);
            UUID uuid = readUuid("uuid", r.requireString("uuid"));
            Vec3d v = readVec3d(r.requireObject("velocity"));
            return onMainThread(
                    context,
                    ignored ->
                            ToolResult.ofText(context.adapter().entitySetVelocity(uuid, v) ? "set" : "failed"));
        }
    }

    @McpTool(name = "entity_apply_effect", description = "Applies a status effect to an entity.")
    public static final class ApplyEffect extends BaseTool {
        private static final JsonNode SCHEMA =
                Schemas.object()
                        .required("uuid", uuidSchema())
                        .required("effect", Schemas.string("Effect id (e.g. minecraft:speed)"))
                        .required("duration_ticks", Schemas.integer("Duration in ticks"))
                        .optional("amplifier", Schemas.integer("Amplifier (0..255, default 0)"))
                        .optional("ambient", Schemas.bool("Ambient (default false)"))
                        .optional("show_particles", Schemas.bool("Show particles (default true)"))
                        .optional("show_icon", Schemas.bool("Show icon (default true)"))
                        .build();

        public ApplyEffect() {
            super("entity_apply_effect");
        }

        @Override
        public JsonNode inputSchema() {
            return SCHEMA;
        }

        @Override
        public ToolResult execute(JsonNode arguments, ToolContext context) {
            var r = reader(arguments);
            UUID uuid = readUuid("uuid", r.requireString("uuid"));
            String effect = r.requireString("effect");
            int dur = r.requireInt("duration_ticks");
            int amp = r.optInt("amplifier", 0);
            boolean ambient = r.optBoolean("ambient", false);
            boolean parts = r.optBoolean("show_particles", true);
            boolean icon = r.optBoolean("show_icon", true);
            return onMainThread(
                    context,
                    ignored ->
                            ToolResult.ofText(
                                    context.adapter()
                                                    .entityApplyEffect(uuid, effect, dur, amp, ambient, parts, icon)
                                            ? "applied"
                                            : "failed"));
        }
    }

    @McpTool(name = "entity_remove_effect", description = "Removes a status effect from an entity.")
    public static final class RemoveEffect extends BaseTool {
        private static final JsonNode SCHEMA =
                Schemas.object()
                        .required("uuid", uuidSchema())
                        .required("effect", Schemas.string("Effect id to remove"))
                        .build();

        public RemoveEffect() {
            super("entity_remove_effect");
        }

        @Override
        public JsonNode inputSchema() {
            return SCHEMA;
        }

        @Override
        public ToolResult execute(JsonNode arguments, ToolContext context) {
            var r = reader(arguments);
            UUID uuid = readUuid("uuid", r.requireString("uuid"));
            String effect = r.requireString("effect");
            return onMainThread(
                    context,
                    ignored ->
                            ToolResult.ofText(
                                    context.adapter().entityRemoveEffect(uuid, effect) ? "removed" : "failed"));
        }
    }

    @McpTool(name = "entity_get_effects", description = "Returns the active status effects on an entity.")
    public static final class GetEffects extends BaseTool {
        private static final JsonNode SCHEMA =
                Schemas.object().required("uuid", uuidSchema()).build();

        public GetEffects() {
            super("entity_get_effects");
        }

        @Override
        public JsonNode inputSchema() {
            return SCHEMA;
        }

        @Override
        public ToolResult execute(JsonNode arguments, ToolContext context) {
            UUID uuid = readUuid("uuid", reader(arguments).requireString("uuid"));
            return onMainThread(
                    context,
                    ignored -> {
                        List<StatusEffectInfo> effects = context.adapter().entityGetEffects(uuid);
                        ArrayNode arr = context.mapper().createArrayNode();
                        for (StatusEffectInfo e : effects) {
                            arr.add(Jsons.statusEffect(context.mapper(), e));
                        }
                        return ToolResult.ofToon(arr);
                    });
        }
    }

    @McpTool(name = "entity_kill", description = "Kills an entity via the standard damage pipeline.")
    public static final class Kill extends BaseTool {
        private static final JsonNode SCHEMA =
                Schemas.object().required("uuid", uuidSchema()).build();

        public Kill() {
            super("entity_kill");
        }

        @Override
        public JsonNode inputSchema() {
            return SCHEMA;
        }

        @Override
        public ToolResult execute(JsonNode arguments, ToolContext context) {
            UUID uuid = readUuid("uuid", reader(arguments).requireString("uuid"));
            return onMainThread(
                    context,
                    ignored ->
                            ToolResult.ofText(context.adapter().entityKill(uuid) ? "killed" : "failed"));
        }
    }

    @McpTool(name = "entity_despawn", description = "Silently removes an entity (no death animation).")
    public static final class Despawn extends BaseTool {
        private static final JsonNode SCHEMA =
                Schemas.object().required("uuid", uuidSchema()).build();

        public Despawn() {
            super("entity_despawn");
        }

        @Override
        public JsonNode inputSchema() {
            return SCHEMA;
        }

        @Override
        public ToolResult execute(JsonNode arguments, ToolContext context) {
            UUID uuid = readUuid("uuid", reader(arguments).requireString("uuid"));
            return onMainThread(
                    context,
                    ignored ->
                            ToolResult.ofText(context.adapter().entityDespawn(uuid) ? "despawned" : "failed"));
        }
    }

    @McpTool(name = "entity_add_tag", description = "Adds a scoreboard tag to an entity.")
    public static final class AddTag extends BaseTool {
        private static final JsonNode SCHEMA =
                Schemas.object()
                        .required("uuid", uuidSchema())
                        .required("tag", Schemas.string("Tag to add"))
                        .build();

        public AddTag() {
            super("entity_add_tag");
        }

        @Override
        public JsonNode inputSchema() {
            return SCHEMA;
        }

        @Override
        public ToolResult execute(JsonNode arguments, ToolContext context) {
            var r = reader(arguments);
            UUID uuid = readUuid("uuid", r.requireString("uuid"));
            String tag = r.requireString("tag");
            return onMainThread(
                    context,
                    ignored ->
                            ToolResult.ofText(
                                    context.adapter().entityAddTag(uuid, tag) ? "added" : "already present"));
        }
    }

    @McpTool(name = "entity_remove_tag", description = "Removes a scoreboard tag from an entity.")
    public static final class RemoveTag extends BaseTool {
        private static final JsonNode SCHEMA =
                Schemas.object()
                        .required("uuid", uuidSchema())
                        .required("tag", Schemas.string("Tag to remove"))
                        .build();

        public RemoveTag() {
            super("entity_remove_tag");
        }

        @Override
        public JsonNode inputSchema() {
            return SCHEMA;
        }

        @Override
        public ToolResult execute(JsonNode arguments, ToolContext context) {
            var r = reader(arguments);
            UUID uuid = readUuid("uuid", r.requireString("uuid"));
            String tag = r.requireString("tag");
            return onMainThread(
                    context,
                    ignored ->
                            ToolResult.ofText(
                                    context.adapter().entityRemoveTag(uuid, tag) ? "removed" : "not present"));
        }
    }

    @McpTool(name = "entity_list_tags", description = "Lists every scoreboard tag on an entity.")
    public static final class ListTags extends BaseTool {
        private static final JsonNode SCHEMA =
                Schemas.object().required("uuid", uuidSchema()).build();

        public ListTags() {
            super("entity_list_tags");
        }

        @Override
        public JsonNode inputSchema() {
            return SCHEMA;
        }

        @Override
        public ToolResult execute(JsonNode arguments, ToolContext context) {
            UUID uuid = readUuid("uuid", reader(arguments).requireString("uuid"));
            return onMainThread(
                    context,
                    ignored -> {
                        List<String> tags = context.adapter().entityListTags(uuid);
                        ArrayNode arr = context.mapper().createArrayNode();
                        tags.forEach(arr::add);
                        return ToolResult.ofToon(arr);
                    });
        }
    }
}
