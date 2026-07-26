package com.chapmanjw.minecraft.fabric.mcp.tools;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import com.chapmanjw.minecraft.fabric.mcp.adapter.dto.BlockStateInfo;
import com.chapmanjw.minecraft.fabric.mcp.adapter.dto.EntityInfo;
import com.chapmanjw.minecraft.fabric.mcp.adapter.dto.InventoryInfo;
import com.chapmanjw.minecraft.fabric.mcp.adapter.dto.ItemStackInfo;
import com.chapmanjw.minecraft.fabric.mcp.adapter.dto.LevelInfo;
import com.chapmanjw.minecraft.fabric.mcp.adapter.dto.PlayerInfo;
import com.chapmanjw.minecraft.fabric.mcp.adapter.dto.ServerStatus;
import com.chapmanjw.minecraft.fabric.mcp.adapter.dto.StatusEffectInfo;
import com.chapmanjw.minecraft.fabric.mcp.adapter.dto.Vec3d;
import com.chapmanjw.minecraft.fabric.mcp.adapter.dto.Vec3i;

/** Shared DTO → JsonNode conversion helpers used across the tool layer. */
public final class Jsons {

    private Jsons() {}

    public static ObjectNode vec3d(ObjectMapper m, Vec3d v) {
        ObjectNode n = m.createObjectNode();
        n.put("x", v.x());
        n.put("y", v.y());
        n.put("z", v.z());
        return n;
    }

    public static ObjectNode vec3i(ObjectMapper m, Vec3i v) {
        ObjectNode n = m.createObjectNode();
        n.put("x", v.x());
        n.put("y", v.y());
        n.put("z", v.z());
        return n;
    }

    public static ObjectNode serverStatus(ObjectMapper m, ServerStatus s) {
        ObjectNode n = m.createObjectNode();
        n.put("minecraftVersion", s.minecraftVersion());
        n.put("fabricLoaderVersion", s.fabricLoaderVersion());
        n.put("modVersion", s.modVersion());
        n.put("motd", s.motd());
        n.put("uptimeMillis", s.uptimeMillis());
        n.put("averageTps", s.averageTps());
        n.put("averageMspt", s.averageMspt());
        n.put("onlinePlayerCount", s.onlinePlayerCount());
        n.put("maxPlayers", s.maxPlayers());
        n.put("registeredToolCount", s.registeredToolCount());
        // World/pack format numbers. structure_file_write needs dataVersion embedded in the NBT it
        // writes, and callers previously had to hardcode a value they could not query — which then
        // silently rots across Minecraft versions.
        n.put("dataVersion", s.dataVersion());
        n.put("datapackFormat", s.datapackFormat());
        n.put("resourcePackFormat", s.resourcePackFormat());
        ArrayNode dims = n.putArray("loadedDimensions");
        for (String d : s.loadedDimensions()) {
            dims.add(d);
        }
        return n;
    }

    public static ObjectNode levelInfo(ObjectMapper m, LevelInfo info) {
        ObjectNode n = m.createObjectNode();
        n.put("dimensionId", info.dimensionId());
        n.put("timeOfDay", info.timeOfDay());
        n.put("gameTime", info.gameTime());
        n.put("weather", info.weather());
        n.put("weatherRemainingTicks", info.weatherRemainingTicks());
        n.put("difficulty", info.difficulty());
        n.put("difficultyLocked", info.difficultyLocked());
        n.put("defaultGameMode", info.defaultGameMode());
        n.set("spawnPoint", vec3i(m, info.spawnPoint()));
        n.put("hardcore", info.hardcore());
        return n;
    }

    public static ObjectNode blockState(ObjectMapper m, BlockStateInfo b) {
        ObjectNode n = m.createObjectNode();
        n.put("id", b.id());
        n.put("lightLevel", b.lightLevel());
        n.put("hardness", b.hardness());
        n.put("hasBlockEntity", b.hasBlockEntity());
        if (b.blockEntityNbt() != null) {
            n.put("blockEntityNbt", b.blockEntityNbt());
        }
        ObjectNode props = n.putObject("properties");
        for (Map.Entry<String, String> e : b.properties().entrySet()) {
            props.put(e.getKey(), e.getValue());
        }
        return n;
    }

    public static ObjectNode player(ObjectMapper m, PlayerInfo p) {
        ObjectNode n = m.createObjectNode();
        n.put("uuid", uuid(p.uuid()));
        n.put("name", p.name());
        n.put("dimensionId", p.dimensionId());
        n.set("position", vec3d(m, p.position()));
        n.put("yaw", p.yaw());
        n.put("pitch", p.pitch());
        n.put("gameMode", p.gameMode());
        n.put("health", p.health());
        n.put("maxHealth", p.maxHealth());
        n.put("foodLevel", p.foodLevel());
        n.put("saturation", p.saturation());
        n.put("xpLevel", p.xpLevel());
        n.put("xpProgress", p.xpProgress());
        n.put("latencyMs", p.latencyMs());
        return n;
    }

    public static ObjectNode entity(ObjectMapper m, EntityInfo e) {
        ObjectNode n = m.createObjectNode();
        n.put("uuid", uuid(e.uuid()));
        n.put("type", e.type());
        if (e.customName() != null) {
            n.put("customName", e.customName());
        }
        n.put("dimensionId", e.dimensionId());
        n.set("position", vec3d(m, e.position()));
        n.set("velocity", vec3d(m, e.velocity()));
        n.put("yaw", e.yaw());
        n.put("pitch", e.pitch());
        n.put("health", e.health());
        n.put("maxHealth", e.maxHealth());
        n.put("onGround", e.onGround());
        n.put("alive", e.alive());
        ArrayNode tags = n.putArray("tags");
        for (String t : e.tags()) {
            tags.add(t);
        }
        return n;
    }

    public static ObjectNode itemStack(ObjectMapper m, ItemStackInfo s) {
        ObjectNode n = m.createObjectNode();
        n.put("id", s.id());
        n.put("count", s.count());
        if (!s.componentKeys().isEmpty()) {
            ArrayNode keys = n.putArray("componentKeys");
            for (String k : s.componentKeys()) {
                keys.add(k);
            }
        }
        n.put("maxStackSize", s.maxStackSize());
        n.put("maxDurability", s.maxDurability());
        n.put("damage", s.damage());
        return n;
    }

    public static ObjectNode inventory(ObjectMapper m, InventoryInfo inv) {
        ObjectNode n = m.createObjectNode();
        n.put("size", inv.size());
        ArrayNode slots = n.putArray("slots");
        for (ItemStackInfo s : inv.slots()) {
            slots.add(itemStack(m, s));
        }
        return n;
    }

    public static ObjectNode statusEffect(ObjectMapper m, StatusEffectInfo s) {
        ObjectNode n = m.createObjectNode();
        n.put("id", s.id());
        n.put("amplifier", s.amplifier());
        n.put("remainingDurationTicks", s.remainingDurationTicks());
        n.put("ambient", s.ambient());
        n.put("showParticles", s.showParticles());
        n.put("showIcon", s.showIcon());
        return n;
    }

    public static String uuid(UUID u) {
        return u == null ? null : u.toString();
    }

    public static <T> ArrayNode arrayOf(ObjectMapper m, List<T> values, ToJson<T> mapper) {
        ArrayNode arr = m.createArrayNode();
        if (values == null) {
            return arr;
        }
        for (T v : values) {
            arr.add(mapper.toJson(m, v));
        }
        return arr;
    }

    @FunctionalInterface
    public interface ToJson<T> {
        JsonNode toJson(ObjectMapper mapper, T value);
    }
}
