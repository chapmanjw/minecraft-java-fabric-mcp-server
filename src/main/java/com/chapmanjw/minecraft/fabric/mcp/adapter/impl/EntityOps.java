package com.chapmanjw.minecraft.fabric.mcp.adapter.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.storage.TagValueOutput;

import com.chapmanjw.minecraft.fabric.mcp.adapter.AdapterException;
import com.chapmanjw.minecraft.fabric.mcp.adapter.dto.CommandResult;
import com.chapmanjw.minecraft.fabric.mcp.adapter.dto.EntityInfo;
import com.chapmanjw.minecraft.fabric.mcp.adapter.dto.StatusEffectInfo;
import com.chapmanjw.minecraft.fabric.mcp.adapter.dto.Vec3d;

/**
 * Non-player entity operations.
 */
final class EntityOps {

    private final AdapterContext ctx;

    EntityOps(AdapterContext ctx) {
        this.ctx = ctx;
    }

    Optional<UUID> entitySummon(
            String dimensionId, String entityType, Vec3d position, String snbt) {
        String nbtPart = snbt == null || snbt.isBlank() ? "" : " " + snbt;
        // The /summon command's success flag doesn't surface the new entity's UUID, so
        // snapshot the dimension's entity UUIDs in a small box around the spawn point
        // before AND after, and return whichever UUID is new. This is more reliable than
        // tickCount-based heuristics, which can pick a long-lived ambient mob that
        // happens to be near the spawn point.
        ServerLevel level = ctx.requireLevel(dimensionId);
        BlockPos pos = new BlockPos((int) position.x(), (int) position.y(), (int) position.z());
        var box =
                new net.minecraft.world.phys.AABB(
                        pos.getX() - 2, pos.getY() - 2, pos.getZ() - 2,
                        pos.getX() + 3, pos.getY() + 3, pos.getZ() + 3);
        java.util.Set<UUID> before = new java.util.HashSet<>();
        for (Entity e : level.getEntities((Entity) null, box, ent -> true)) {
            before.add(e.getUUID());
        }

        CommandResult r =
                ctx.commandExecute(
                        String.format(
                                Locale.ROOT,
                                "execute in %s run summon %s %f %f %f%s",
                                dimensionId,
                                entityType,
                                position.x(),
                                position.y(),
                                position.z(),
                                nbtPart));
        if (r.successCount() == 0) {
            return Optional.empty();
        }

        // Diff the box: return the entity that wasn't there before. If multiple new
        // entities appear (unlikely in a 5-block box during a single tick), prefer the
        // one with the lowest tickCount — that's the most recently spawned.
        UUID newest = null;
        long newestTick = Long.MAX_VALUE;
        for (Entity e : level.getEntities((Entity) null, box, ent -> true)) {
            if (before.contains(e.getUUID())) {
                continue;
            }
            if (e.tickCount < newestTick) {
                newestTick = e.tickCount;
                newest = e.getUUID();
            }
        }
        return Optional.ofNullable(newest);
    }

    Optional<EntityInfo> entityGet(UUID uuid) {
        MinecraftServer s = ctx.requireServer();
        for (ServerLevel level : s.getAllLevels()) {
            Entity e = level.getEntity(uuid);
            if (e != null) {
                return Optional.of(ctx.toEntityInfo(e));
            }
        }
        return Optional.empty();
    }

    Optional<EntityInfo> entityGetByNetworkId(String dimensionId, int networkId) {
        try {
            ServerLevel level = ctx.requireLevel(dimensionId);
            Entity e = level.getEntity(networkId);
            return e == null ? Optional.empty() : Optional.of(ctx.toEntityInfo(e));
        } catch (AdapterException ae) {
            return Optional.empty();
        }
    }

    List<EntityInfo> entityQuery(String dimensionId, String selector, int limit) {
        if (selector == null || selector.isBlank()) {
            return List.of();
        }
        ServerLevel level = ctx.requireLevel(dimensionId);
        List<EntityInfo> out = new ArrayList<>();

        // Parse the selector through Brigadier's EntityArgument so we get vanilla
        // selector semantics (type=, distance=, predicate filters, sort, NBT match…)
        // without re-implementing them. EntityArgument.entities() wraps the parser and
        // its `parse(StringReader, CommandSource)` overload returns an EntitySelector.
        try {
            CommandSourceStack source = ctx.requireServer().createCommandSourceStack().withLevel(level);
            var reader = new com.mojang.brigadier.StringReader(selector);
            var arg = net.minecraft.commands.arguments.EntityArgument.entities();
            var entitySelector = arg.parse(reader, source);
            for (Entity e : entitySelector.findEntities(source)) {
                if (out.size() >= limit) {
                    break;
                }
                out.add(ctx.toEntityInfo(e));
            }
        } catch (Exception parserFailure) {
            // Selector couldn't be parsed; fall through and return whatever we accumulated
            // (likely empty). The caller sees an empty list rather than a stack trace.
            AdapterContext.LOGGER.debug(
                    "entityQuery: selector '{}' failed to parse: {}",
                    selector,
                    parserFailure.getMessage());
        }
        return out;
    }

    Optional<Map<String, String>> entityGetComponents(UUID uuid) {
        // Components API exists in modern Minecraft for ItemStack but is more limited
        // for entities. Returning empty until we wire the component view from the
        // data-attachment registry in v0.2.0.
        return entityGet(uuid).map(e -> Map.of());
    }

    Optional<String> entityGetNbt(UUID uuid) {
        MinecraftServer s = ctx.requireServer();
        for (ServerLevel level : s.getAllLevels()) {
            Entity e = level.getEntity(uuid);
            if (e == null) {
                continue;
            }
            TagValueOutput output =
                    TagValueOutput.createWithContext(ProblemReporter.DISCARDING, level.registryAccess());
            e.saveWithoutId(output);
            CompoundTag tag = output.buildResult();
            return Optional.of(tag.toString());
        }
        return Optional.empty();
    }

    boolean entitySetNbt(UUID uuid, String snbt) {
        CommandResult r = ctx.commandExecute("data merge entity " + uuid + " " + snbt);
        return r.successCount() > 0;
    }

    boolean entityTeleport(UUID uuid, String dimensionId, Vec3d position, Vec3d facingTarget) {
        String facing =
                facingTarget == null
                        ? ""
                        : String.format(
                                Locale.ROOT,
                                " facing %f %f %f",
                                facingTarget.x(),
                                facingTarget.y(),
                                facingTarget.z());
        String cmd =
                String.format(
                        Locale.ROOT,
                        "execute in %s run tp %s %f %f %f%s",
                        dimensionId,
                        uuid,
                        position.x(),
                        position.y(),
                        position.z(),
                        facing);
        return ctx.commandExecute(cmd).successCount() > 0;
    }

    boolean entityApplyDamage(UUID uuid, float amount, String damageType) {
        String type = damageType == null || damageType.isBlank() ? "minecraft:generic" : damageType;
        return ctx.commandExecute("damage " + uuid + " " + amount + " " + type).successCount() > 0;
    }

    boolean entitySetVelocity(UUID uuid, Vec3d velocity) {
        return ctx.commandExecute(
                        String.format(
                                Locale.ROOT,
                                "data merge entity %s {Motion:[%fd,%fd,%fd]}",
                                uuid,
                                velocity.x(),
                                velocity.y(),
                                velocity.z()))
                .successCount() > 0;
    }

    boolean entityApplyEffect(
            UUID uuid,
            String effect,
            int durationTicks,
            int amplifier,
            boolean ambient,
            boolean showParticles,
            boolean showIcon) {
        // Vanilla `/effect give` takes whole seconds, but the API contract is ticks —
        // round UP so a caller asking for "5 ticks" gets at least 1 second (= 20 ticks)
        // instead of zero. Floor-division here would silently drop short effects.
        final int ticksPerSecond = 20;
        int durationSeconds =
                durationTicks <= 0
                        ? 1
                        : (durationTicks + (ticksPerSecond - 1)) / ticksPerSecond;
        boolean hideParticles = !showParticles;
        return ctx.commandExecute(
                        "effect give "
                                + uuid
                                + " "
                                + effect
                                + " "
                                + durationSeconds
                                + " "
                                + amplifier
                                + " "
                                + hideParticles)
                .successCount() > 0;
    }

    boolean entityRemoveEffect(UUID uuid, String effect) {
        return ctx.commandExecute("effect clear " + uuid + " " + effect).successCount() > 0;
    }

    List<StatusEffectInfo> entityGetEffects(UUID uuid) {
        Optional<EntityInfo> info = entityGet(uuid);
        if (info.isEmpty()) {
            return List.of();
        }
        MinecraftServer s = ctx.requireServer();
        for (ServerLevel level : s.getAllLevels()) {
            Entity e = level.getEntity(uuid);
            if (e instanceof LivingEntity le) {
                List<StatusEffectInfo> out = new ArrayList<>();
                for (var instance : le.getActiveEffects()) {
                    Identifier id =
                            s.registryAccess()
                                    .lookupOrThrow(Registries.MOB_EFFECT)
                                    .getKey(instance.getEffect().value());
                    out.add(
                            new StatusEffectInfo(
                                    id == null ? "unknown" : id.toString(),
                                    instance.getAmplifier(),
                                    instance.getDuration(),
                                    instance.isAmbient(),
                                    instance.isVisible(),
                                    instance.showIcon()));
                }
                return out;
            }
        }
        return List.of();
    }

    boolean entityKill(UUID uuid) {
        return ctx.commandExecute("kill " + uuid).successCount() > 0;
    }

    boolean entityDespawn(UUID uuid) {
        MinecraftServer s = ctx.requireServer();
        for (ServerLevel level : s.getAllLevels()) {
            Entity e = level.getEntity(uuid);
            if (e != null && !(e instanceof ServerPlayer)) {
                e.discard();
                return true;
            }
        }
        return false;
    }

    boolean entityAddTag(UUID uuid, String tag) {
        return ctx.commandExecute("tag " + uuid + " add " + tag).successCount() > 0;
    }

    boolean entityRemoveTag(UUID uuid, String tag) {
        return ctx.commandExecute("tag " + uuid + " remove " + tag).successCount() > 0;
    }

    List<String> entityListTags(UUID uuid) {
        return entityGet(uuid).map(EntityInfo::tags).orElseGet(List::of);
    }
}
