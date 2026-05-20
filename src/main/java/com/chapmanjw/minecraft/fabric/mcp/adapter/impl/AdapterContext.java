package com.chapmanjw.minecraft.fabric.mcp.adapter.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.chapmanjw.minecraft.fabric.mcp.adapter.AdapterException;
import com.chapmanjw.minecraft.fabric.mcp.adapter.dto.CommandResult;
import com.chapmanjw.minecraft.fabric.mcp.adapter.dto.EntityInfo;
import com.chapmanjw.minecraft.fabric.mcp.adapter.dto.ItemStackInfo;
import com.chapmanjw.minecraft.fabric.mcp.adapter.dto.Vec3d;

/**
 * Shared context and utilities for the {@code MinecraftAdapterImpl} facade and its
 * domain-specific helper classes. Holds the bound {@link MinecraftServer} reference
 * (set via {@code bind}, cleared via {@code unbind}) and exposes the common helpers
 * each domain helper needs.
 */
final class AdapterContext {

    static final Logger LOGGER = LoggerFactory.getLogger("minecraft_fabric_mcp/adapter");

    private final AtomicReference<MinecraftServer> server = new AtomicReference<>();
    private final String modVersion;

    AdapterContext(String modVersion) {
        this.modVersion = modVersion;
    }

    String modVersion() {
        return modVersion;
    }

    void setServer(MinecraftServer ms) {
        this.server.set(ms);
    }

    void clearServer() {
        this.server.set(null);
    }

    MinecraftServer server() {
        return server.get();
    }

    boolean isBound() {
        return server.get() != null;
    }

    MinecraftServer requireServer() {
        MinecraftServer s = server.get();
        if (s == null) {
            throw new AdapterException("Minecraft server is not running");
        }
        return s;
    }

    ServerLevel requireLevel(String dimensionId) {
        MinecraftServer s = requireServer();
        Identifier loc = parseIdentifier(dimensionId);
        for (ServerLevel level : s.getAllLevels()) {
            if (level.dimension().identifier().equals(loc)) {
                return level;
            }
        }
        throw new AdapterException("Unknown dimension: " + dimensionId);
    }

    static Identifier parseIdentifier(String id) {
        try {
            Identifier rl = Identifier.tryParse(id);
            if (rl == null) {
                throw new AdapterException("Invalid identifier: " + id);
            }
            return rl;
        } catch (Exception e) {
            throw new AdapterException("Invalid identifier '" + id + "': " + e.getMessage());
        }
    }

    CommandResult commandExecute(String command) {
        MinecraftServer s = requireServer();
        List<String> output = new ArrayList<>();
        CommandSourceStack source =
                s.createCommandSourceStack()
                        .withSuppressedOutput()
                        .withCallback((success, count) -> { })
                        .withSource(
                                new CapturingCommandSource(CommandSource.NULL, output));
        try {
            int result = s.getCommands().getDispatcher().execute(command, source);
            return new CommandResult(result, output, null);
        } catch (CommandSyntaxException e) {
            return new CommandResult(0, output, e.getMessage());
        } catch (Exception e) {
            return new CommandResult(0, output, e.getMessage());
        }
    }

    EntityInfo toEntityInfo(Entity e) {
        Vec3 vel = e.getDeltaMovement();
        Identifier typeId =
                requireServer()
                        .registryAccess()
                        .lookupOrThrow(Registries.ENTITY_TYPE)
                        .getKey(e.getType());
        float health = 0;
        float maxHealth = 0;
        if (e instanceof LivingEntity le) {
            health = le.getHealth();
            maxHealth = le.getMaxHealth();
        }
        //? if mc_gte_26 {
        List<String> tags = new ArrayList<>(e.entityTags());
        //?} else {
        /*List<String> tags = new ArrayList<>(e.getTags());
        *///?}
        return new EntityInfo(
                e.getUUID(),
                typeId == null ? "unknown" : typeId.toString(),
                e.getCustomName() == null ? null : e.getCustomName().getString(),
                e.level().dimension().identifier().toString(),
                new Vec3d(e.getX(), e.getY(), e.getZ()),
                new Vec3d(vel.x, vel.y, vel.z),
                e.getYRot(),
                e.getXRot(),
                health,
                maxHealth,
                e.onGround(),
                e.isAlive(),
                tags);
    }

    ItemStackInfo toItemStackInfo(net.minecraft.world.item.ItemStack s) {
        Identifier id =
                requireServer()
                        .registryAccess()
                        .lookupOrThrow(Registries.ITEM)
                        .getKey(s.getItem());
        // Component map → list of component identifier strings. We intentionally drop
        // the values: at runtime, individual Component.toString() implementations leak
        // intermediary class names (e.g. `class_10711[...]`) and balloon the payload to
        // tens of kilobytes per stack. Callers that need component values should fetch
        // SNBT via entity_get_nbt or block_entity_get_nbt.
        List<String> componentKeys = new ArrayList<>();
        var componentRegistry =
                requireServer()
                        .registryAccess()
                        .lookupOrThrow(net.minecraft.core.registries.Registries.DATA_COMPONENT_TYPE);
        for (var ct : s.getComponents().keySet()) {
            Identifier ctId = componentRegistry.getKey(ct);
            if (ctId != null) {
                componentKeys.add(ctId.toString());
            }
        }
        componentKeys.sort(String::compareTo);
        return new ItemStackInfo(
                id == null ? "minecraft:air" : id.toString(),
                s.getCount(),
                componentKeys,
                s.getMaxStackSize(),
                s.getMaxDamage(),
                s.getDamageValue());
    }

    /**
     * Component literal as JSON text. Vanilla expects either a quoted string or a JSON object.
     */
    static String asJsonText(String s) {
        return "{\"text\":\""
                + (s == null ? "" : s.replace("\\", "\\\\").replace("\"", "\\\""))
                + "\"}";
    }

    /**
     * Wrap a UUID as a vanilla {@code @a[uuid=...]} (for players) selector that
     * brigadier will accept. Vanilla commands like /give, /gamemode, /effect,
     * /title, /xp, /spawnpoint, /clear, /advancement, /spectate, /bossbar players
     * reject a bare UUID string -- they need a selector wrapper.
     */
    static String playerSelector(java.util.UUID uuid) {
        return "@a[uuid=" + uuid.toString() + "]";
    }

    /**
     * Wrap a UUID as a vanilla {@code @e[uuid=...]} selector for any entity.
     * Brigadier rejects bare UUID strings for entity-target arguments on most
     * vanilla commands.
     */
    static String entitySelector(java.util.UUID uuid) {
        return "@e[uuid=" + uuid.toString() + ",limit=1]";
    }

    /**
     * Whether a vanilla void/mutation command (one that returns {@code successCount=0}
     * even on success -- e.g. {@code /bossbar set ... players}, {@code /gamemode},
     * {@code /effect clear}, {@code /worldborder set}) actually completed. True iff
     * there was no parse error. For commands that return a meaningful
     * {@code successCount} (e.g. {@code /give}, {@code /summon}, {@code /clone},
     * {@code /fill}), callers should still check {@code r.successCount() > 0}.
     */
    static boolean commandOk(CommandResult r) {
        return r.error() == null;
    }

    Entity findEntityAcrossLevels(java.util.UUID uuid) {
        for (ServerLevel level : requireServer().getAllLevels()) {
            Entity e = level.getEntity(uuid);
            if (e != null) {
                return e;
            }
        }
        return null;
    }

    /**
     * Wraps the server's command source to capture feedback messages emitted via
     * {@link CommandSource#sendSystemMessage}.
     */
    private static final class CapturingCommandSource implements CommandSource {
        private final CommandSource delegate;
        private final List<String> capture;

        CapturingCommandSource(CommandSource delegate, List<String> capture) {
            this.delegate = delegate;
            this.capture = capture;
        }

        @Override
        public void sendSystemMessage(Component message) {
            if (message != null) {
                capture.add(message.getString());
            }
            delegate.sendSystemMessage(message);
        }

        @Override
        public boolean acceptsSuccess() {
            return true;
        }

        @Override
        public boolean acceptsFailure() {
            return true;
        }

        @Override
        public boolean shouldInformAdmins() {
            return false;
        }
    }
}
