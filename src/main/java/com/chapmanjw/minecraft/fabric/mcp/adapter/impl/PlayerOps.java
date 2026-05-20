package com.chapmanjw.minecraft.fabric.mcp.adapter.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.InventoryCarrier;
import net.minecraft.world.level.block.entity.BlockEntity;

import com.chapmanjw.minecraft.fabric.mcp.adapter.AdapterException;
import com.chapmanjw.minecraft.fabric.mcp.adapter.dto.InventoryInfo;
import com.chapmanjw.minecraft.fabric.mcp.adapter.dto.ItemSpec;
import com.chapmanjw.minecraft.fabric.mcp.adapter.dto.ItemStackInfo;
import com.chapmanjw.minecraft.fabric.mcp.adapter.dto.PlayerInfo;
import com.chapmanjw.minecraft.fabric.mcp.adapter.dto.Vec3d;
import com.chapmanjw.minecraft.fabric.mcp.adapter.dto.Vec3i;

/**
 * Player, inventory/container, and item-stack operations.
 */
final class PlayerOps {

    private final AdapterContext ctx;

    PlayerOps(AdapterContext ctx) {
        this.ctx = ctx;
    }

    // =====================================================================
    // Player
    // =====================================================================

    List<PlayerInfo> playerListOnline() {
        List<PlayerInfo> out = new ArrayList<>();
        for (ServerPlayer p : ctx.requireServer().getPlayerList().getPlayers()) {
            out.add(toPlayerInfo(p));
        }
        return out;
    }

    Optional<PlayerInfo> playerGetInfo(UUID uuid) {
        ServerPlayer p = ctx.requireServer().getPlayerList().getPlayer(uuid);
        return p == null ? Optional.empty() : Optional.of(toPlayerInfo(p));
    }

    private PlayerInfo toPlayerInfo(ServerPlayer p) {
        return new PlayerInfo(
                p.getUUID(),
                p.getName().getString(),
                p.level().dimension().identifier().toString(),
                new Vec3d(p.getX(), p.getY(), p.getZ()),
                p.getYRot(),
                p.getXRot(),
                p.gameMode.getGameModeForPlayer().getName(),
                p.getHealth(),
                p.getMaxHealth(),
                p.getFoodData().getFoodLevel(),
                p.getFoodData().getSaturationLevel(),
                p.experienceLevel,
                p.experienceProgress,
                p.connection == null ? -1 : p.connection.latency());
    }

    Optional<InventoryInfo> playerGetInventory(UUID uuid) {
        ServerPlayer p = ctx.requireServer().getPlayerList().getPlayer(uuid);
        if (p == null) {
            return Optional.empty();
        }
        List<ItemStackInfo> slots = new ArrayList<>();
        for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
            slots.add(ctx.toItemStackInfo(p.getInventory().getItem(i)));
        }
        return Optional.of(new InventoryInfo(p.getInventory().getContainerSize(), slots));
    }

    boolean playerGiveItem(UUID uuid, ItemSpec item) {
        // /give returns a meaningful successCount (1 per stack delivered) so we keep the
        // > 0 check; the UUID needs the @a[uuid=...] wrapper because vanilla rejects a
        // bare UUID for the player-target argument.
        return ctx.commandExecute(
                                "give "
                                        + AdapterContext.playerSelector(uuid)
                                        + " "
                                        + item.id()
                                        + " "
                                        + item.count())
                        .successCount() > 0;
    }

    boolean playerClearInventorySlot(UUID uuid, int slot) {
        // /item replace entity returns a meaningful successCount.
        return ctx.commandExecute(
                        "item replace entity "
                                + AdapterContext.entitySelector(uuid)
                                + " container."
                                + slot
                                + " with minecraft:air")
                        .successCount() > 0;
    }

    boolean playerClearAllInventory(UUID uuid) {
        // /clear returns successCount=N (items cleared). On a player with zero matching
        // items it reports 0, but the command still succeeded -- prefer commandOk so the
        // empty-inventory case isn't reported as failure.
        return AdapterContext.commandOk(
                ctx.commandExecute("clear " + AdapterContext.playerSelector(uuid)));
    }

    boolean playerSetGamemode(UUID uuid, String gameMode) {
        // /gamemode is a void setter (successCount=0 on a no-op such as setting the
        // current gamemode); use commandOk.
        return AdapterContext.commandOk(
                ctx.commandExecute(
                        "gamemode " + gameMode + " " + AdapterContext.playerSelector(uuid)));
    }

    boolean playerKick(UUID uuid, String reason) {
        ServerPlayer p = ctx.requireServer().getPlayerList().getPlayer(uuid);
        if (p == null) {
            return false;
        }
        p.connection.disconnect(Component.literal(reason == null ? "Kicked by MCP" : reason));
        return true;
    }

    boolean playerSendMessage(UUID uuid, String message) {
        ServerPlayer p = ctx.requireServer().getPlayerList().getPlayer(uuid);
        if (p == null) {
            return false;
        }
        p.sendSystemMessage(Component.literal(message));
        return true;
    }

    boolean playerSendActionbar(UUID uuid, String message) {
        // /title ... actionbar is a void setter.
        String cmd =
                "title "
                        + AdapterContext.playerSelector(uuid)
                        + " actionbar "
                        + AdapterContext.asJsonText(message);
        return AdapterContext.commandOk(ctx.commandExecute(cmd));
    }

    boolean playerSendTitle(
            UUID uuid,
            String title,
            String subtitle,
            int fadeInTicks,
            int stayTicks,
            int fadeOutTicks) {
        // Each subcommand here is a void setter; commandOk rather than successCount > 0.
        String sel = AdapterContext.playerSelector(uuid);
        boolean ok = true;
        if (fadeInTicks > 0 || stayTicks > 0 || fadeOutTicks > 0) {
            ok &=
                    AdapterContext.commandOk(
                            ctx.commandExecute(
                                    "title "
                                            + sel
                                            + " times "
                                            + fadeInTicks
                                            + " "
                                            + stayTicks
                                            + " "
                                            + fadeOutTicks));
        }
        if (subtitle != null && !subtitle.isBlank()) {
            ok &=
                    AdapterContext.commandOk(
                            ctx.commandExecute(
                                    "title "
                                            + sel
                                            + " subtitle "
                                            + AdapterContext.asJsonText(subtitle)));
        }
        ok &=
                AdapterContext.commandOk(
                        ctx.commandExecute(
                                "title " + sel + " title " + AdapterContext.asJsonText(title)));
        return ok;
    }

    boolean playerPlaySound(UUID uuid, String soundId, float volume, float pitch) {
        // /playsound is a void setter (successCount=0 if nobody is in range to hear it).
        return AdapterContext.commandOk(
                ctx.commandExecute(
                        "execute as "
                                + AdapterContext.entitySelector(uuid)
                                + " at @s run playsound "
                                + soundId
                                + " master @s ~ ~ ~ "
                                + volume
                                + " "
                                + pitch));
    }

    boolean playerSetSpawnPoint(UUID uuid, String dimensionId, Vec3i position) {
        // /spawnpoint is a void setter.
        return AdapterContext.commandOk(
                ctx.commandExecute(
                        "execute in "
                                + dimensionId
                                + " run spawnpoint "
                                + AdapterContext.playerSelector(uuid)
                                + " "
                                + position.x()
                                + " "
                                + position.y()
                                + " "
                                + position.z()));
    }

    boolean playerGrantXp(UUID uuid, int amount) {
        // /xp add is a void setter.
        return AdapterContext.commandOk(
                ctx.commandExecute(
                        "xp add "
                                + AdapterContext.playerSelector(uuid)
                                + " "
                                + amount
                                + " points"));
    }

    boolean playerSetXpLevel(UUID uuid, int level) {
        // /xp set is a void setter.
        return AdapterContext.commandOk(
                ctx.commandExecute(
                        "xp set "
                                + AdapterContext.playerSelector(uuid)
                                + " "
                                + level
                                + " levels"));
    }

    boolean playerSetCamera(UUID viewer, UUID target) {
        // /spectate is a void setter.
        return AdapterContext.commandOk(
                ctx.commandExecute(
                        "execute as "
                                + AdapterContext.entitySelector(viewer)
                                + " run spectate "
                                + AdapterContext.entitySelector(target)));
    }

    // =====================================================================
    // Inventory / Container — direct API for player/entity/block targets
    // =====================================================================

    /**
     * Resolves a target string to a {@link Container} (or {@code null} if the target is
     * invalid / not a container). Supported target forms:
     * <ul>
     *   <li>{@code "player:<uuid>"} — the player's inventory (a Container).</li>
     *   <li>{@code "entity:<uuid>"} — the entity's carried inventory, if it implements
     *       {@link InventoryCarrier} (mob/villager). Otherwise {@code null}.</li>
     *   <li>{@code "block:<dim>:<x>:<y>:<z>"} — the BlockEntity at the position, if it
     *       is a {@link Container} (chest, hopper, barrel, dispenser, …).</li>
     * </ul>
     */
    private Container resolveContainer(String target) {
        if (target == null || target.isBlank()) {
            return null;
        }
        if (target.startsWith("player:")) {
            try {
                UUID uuid = UUID.fromString(target.substring("player:".length()));
                ServerPlayer p = ctx.requireServer().getPlayerList().getPlayer(uuid);
                return p == null ? null : p.getInventory();
            } catch (IllegalArgumentException e) {
                return null;
            }
        }
        if (target.startsWith("entity:")) {
            try {
                UUID uuid = UUID.fromString(target.substring("entity:".length()));
                for (ServerLevel level : ctx.requireServer().getAllLevels()) {
                    Entity e = level.getEntity(uuid);
                    if (e instanceof InventoryCarrier ic) {
                        return ic.getInventory();
                    }
                    if (e instanceof Container c) {
                        return c;
                    }
                }
                return null;
            } catch (IllegalArgumentException e) {
                return null;
            }
        }
        if (target.startsWith("block:")) {
            String[] parts = target.substring("block:".length()).split(":");
            if (parts.length < 4) {
                return null;
            }
            // dim might itself contain a colon (e.g. "minecraft:overworld"). Recombine
            // the trailing 3 parts as coordinates and everything before them as the dim id.
            int n = parts.length;
            int x;
            int y;
            int z;
            try {
                x = Integer.parseInt(parts[n - 3]);
                y = Integer.parseInt(parts[n - 2]);
                z = Integer.parseInt(parts[n - 1]);
            } catch (NumberFormatException nfe) {
                return null;
            }
            StringBuilder dim = new StringBuilder();
            for (int i = 0; i < n - 3; i++) {
                if (dim.length() > 0) {
                    dim.append(':');
                }
                dim.append(parts[i]);
            }
            try {
                ServerLevel level = ctx.requireLevel(dim.toString());
                BlockEntity be = level.getBlockEntity(new BlockPos(x, y, z));
                if (be instanceof Container c) {
                    return c;
                }
                return null;
            } catch (AdapterException ae) {
                return null;
            }
        }
        return null;
    }

    Optional<InventoryInfo> inventoryGet(String target) {
        Container c = resolveContainer(target);
        if (c == null) {
            return Optional.empty();
        }
        List<ItemStackInfo> slots = new ArrayList<>();
        for (int i = 0; i < c.getContainerSize(); i++) {
            slots.add(ctx.toItemStackInfo(c.getItem(i)));
        }
        return Optional.of(new InventoryInfo(c.getContainerSize(), slots));
    }

    boolean inventorySetSlot(String target, int slot, ItemSpec item) {
        Container c = resolveContainer(target);
        if (c == null || slot < 0 || slot >= c.getContainerSize()) {
            return false;
        }
        var registry = ctx.requireServer().registryAccess().lookupOrThrow(Registries.ITEM);
        var holderOpt = registry.get(AdapterContext.parseIdentifier(item.id()));
        if (holderOpt.isEmpty()) {
            return false;
        }
        var stack = new net.minecraft.world.item.ItemStack(holderOpt.get(), item.count());
        c.setItem(slot, stack);
        c.setChanged();
        return true;
    }

    boolean inventoryClearSlot(String target, int slot) {
        Container c = resolveContainer(target);
        if (c == null || slot < 0 || slot >= c.getContainerSize()) {
            return false;
        }
        c.setItem(slot, net.minecraft.world.item.ItemStack.EMPTY);
        c.setChanged();
        return true;
    }

    boolean inventorySwapSlots(String target, int slotA, int slotB) {
        Container c = resolveContainer(target);
        if (c == null) {
            return false;
        }
        if (slotA < 0 || slotA >= c.getContainerSize() || slotB < 0 || slotB >= c.getContainerSize()) {
            return false;
        }
        var a = c.getItem(slotA);
        var b = c.getItem(slotB);
        c.setItem(slotA, b);
        c.setItem(slotB, a);
        c.setChanged();
        return true;
    }

    int inventoryCountItems(String target, String itemId) {
        Container c = resolveContainer(target);
        if (c == null) {
            return 0;
        }
        Identifier match = AdapterContext.parseIdentifier(itemId);
        var registry = ctx.requireServer().registryAccess().lookupOrThrow(Registries.ITEM);
        int total = 0;
        for (int i = 0; i < c.getContainerSize(); i++) {
            var stack = c.getItem(i);
            if (stack.isEmpty()) {
                continue;
            }
            Identifier id = registry.getKey(stack.getItem());
            if (match.equals(id)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    // =====================================================================
    // ItemStack
    // =====================================================================

    Optional<ItemStackInfo> itemStackDescribe(ItemSpec spec) {
        // Look up the item from the registry directly.
        var registry = ctx.requireServer().registryAccess().lookupOrThrow(Registries.ITEM);
        Identifier id = AdapterContext.parseIdentifier(spec.id());
        var holderOpt = registry.get(id);
        if (holderOpt.isEmpty()) {
            return Optional.empty();
        }
        var stack = new net.minecraft.world.item.ItemStack(holderOpt.get(), spec.count());
        return Optional.of(ctx.toItemStackInfo(stack));
    }

    boolean itemStackDropAt(String dimensionId, Vec3d position, ItemSpec spec) {
        // Use /summon item with Item tag.
        String snbt =
                "{Item:{id:\""
                        + spec.id()
                        + "\",count:"
                        + spec.count()
                        + (spec.components() == null ? "" : "," + spec.components())
                        + "}}";
        return ctx.commandExecute(
                        String.format(
                                Locale.ROOT,
                                "execute in %s run summon item %f %f %f %s",
                                dimensionId,
                                position.x(),
                                position.y(),
                                position.z(),
                                snbt))
                .successCount() > 0;
    }
}
