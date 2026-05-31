package com.chapmanjw.minecraft.fabric.mcp.adapter.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.impl.attachment.AttachmentRegistryImpl;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.commands.arguments.NbtPathArgument;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.storage.CommandStorage;

import com.chapmanjw.minecraft.fabric.mcp.adapter.AdapterException;
import com.chapmanjw.minecraft.fabric.mcp.adapter.dto.DatapackInfo;

/**
 * Data storage, data attachments, and datapack operations.
 */
final class DataOps {

    private final AdapterContext ctx;

    DataOps(AdapterContext ctx) {
        this.ctx = ctx;
    }

    // =====================================================================
    // Data — v0.2.0
    // =====================================================================

    /**
     * Vanilla command storage is keyed by a single namespaced id (e.g. {@code mcp:default});
     * the {@code path} argument the adapter exposes is the NBT path WITHIN that storage tree.
     * The legacy form {@code <ns>:<ns>} for the storage id meant set and get could disagree
     * about which storage the path resolved against, so we canonicalize on {@code <ns>:default}.
     */
    private static String storageId(String namespace) {
        return namespace + ":default";
    }

    Optional<String> dataStorageGet(String namespace, String path) {
        // `/data get` reports the value via a feedback message, but commandExecute uses
        // withSuppressedOutput() so output is empty. Read CommandStorage directly.
        CommandStorage storage = ctx.requireServer().getCommandStorage();
        Identifier id = Identifier.tryParse(storageId(namespace));
        if (id == null) {
            return Optional.empty();
        }
        CompoundTag root = storage.get(id);
        if (root == null) {
            return Optional.empty();
        }
        String safePath = path == null ? "" : path.trim();
        if (safePath.isEmpty()) {
            return root.isEmpty() ? Optional.empty() : Optional.of(root.toString());
        }
        try {
            NbtPathArgument.NbtPath nbtPath =
                    new NbtPathArgument().parse(new com.mojang.brigadier.StringReader(safePath));
            List<Tag> tags = nbtPath.get(root);
            if (tags.isEmpty()) {
                return Optional.empty();
            }
            if (tags.size() == 1) {
                return Optional.of(tags.get(0).toString());
            }
            StringBuilder sb = new StringBuilder("[");
            for (int i = 0; i < tags.size(); i++) {
                if (i > 0) {
                    sb.append(',');
                }
                sb.append(tags.get(i));
            }
            sb.append(']');
            return Optional.of(sb.toString());
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    boolean dataStorageSet(String namespace, String path, String snbt, boolean merge) {
        // `/data merge storage <id> <snbt>` merges into the root of the storage tree, while
        // `/data modify storage <id> <path> set value <snbt>` writes a single path. The
        // merge form ignores `path` entirely, so callers asking for `merge=true` with a
        // path must use modify-set-value to land at the expected location.
        String cmd;
        if (merge && (path == null || path.isBlank())) {
            cmd = "data merge storage " + storageId(namespace) + " " + snbt;
        } else {
            cmd = "data modify storage " + storageId(namespace) + " " + path + " set value " + snbt;
        }
        return AdapterContext.commandOk(ctx.commandExecute(cmd));
    }

    boolean dataStorageRemove(String namespace, String path) {
        return AdapterContext.commandOk(
                ctx.commandExecute("data remove storage " + storageId(namespace) + " " + path));
    }

    List<String> dataStorageListNamespaces() {
        CommandStorage storage = ctx.requireServer().getCommandStorage();
        Set<String> namespaces = new java.util.LinkedHashSet<>();
        try (Stream<Identifier> keys = storage.keys()) {
            keys.forEach(id -> namespaces.add(id.getNamespace()));
        }
        List<String> out = new ArrayList<>(namespaces);
        out.sort(String::compareTo);
        return out;
    }

    /**
     * Resolves a target string to an {@link AttachmentTarget}. Accepts the same forms
     * as the inventory resolver: player/entity/block.
     */
    private AttachmentTarget resolveAttachmentTarget(String target) {
        if (target == null || target.isBlank()) {
            return null;
        }
        if (target.startsWith("player:")) {
            try {
                UUID uuid = UUID.fromString(target.substring("player:".length()));
                ServerPlayer p = ctx.requireServer().getPlayerList().getPlayer(uuid);
                return p == null ? null : (AttachmentTarget) p;
            } catch (IllegalArgumentException e) {
                return null;
            }
        }
        if (target.startsWith("entity:")) {
            try {
                UUID uuid = UUID.fromString(target.substring("entity:".length()));
                for (ServerLevel level : ctx.requireServer().getAllLevels()) {
                    Entity e = level.getEntity(uuid);
                    if (e != null) {
                        return (AttachmentTarget) e;
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
                return be == null ? null : (AttachmentTarget) be;
            } catch (AdapterException ae) {
                return null;
            }
        }
        return null;
    }

    private static AttachmentType<?> lookupAttachment(String namespace, String key) {
        Identifier id = Identifier.fromNamespaceAndPath(namespace, key);
        return AttachmentRegistryImpl.get(id);
    }

    Optional<String> dataAttachmentGet(String target, String namespace, String key) {
        AttachmentTarget t = resolveAttachmentTarget(target);
        if (t == null) {
            return Optional.empty();
        }
        AttachmentType<?> type = lookupAttachment(namespace, key);
        if (type == null) {
            return Optional.empty();
        }
        Object value = t.getAttached(type);
        return value == null ? Optional.empty() : Optional.of(String.valueOf(value));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    boolean dataAttachmentSet(String target, String namespace, String key, String snbt) {
        AttachmentTarget t = resolveAttachmentTarget(target);
        if (t == null) {
            return false;
        }
        AttachmentType type = lookupAttachment(namespace, key);
        if (type == null) {
            return false;
        }
        // We can't generically deserialize arbitrary SNBT into the attachment's value
        // type without its Codec context. Pass the raw SNBT string through; persistent
        // attachments typed as String will accept it directly. Non-string-typed
        // attachments must use their own deserialization path (e.g. data_storage_set).
        try {
            t.setAttached(type, snbt);
            return true;
        } catch (ClassCastException e) {
            return false;
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    boolean dataAttachmentRemove(String target, String namespace, String key) {
        AttachmentTarget t = resolveAttachmentTarget(target);
        if (t == null) {
            return false;
        }
        AttachmentType type = lookupAttachment(namespace, key);
        if (type == null) {
            return false;
        }
        return t.removeAttached(type) != null;
    }

    List<String> dataAttachmentListKeys(String target, String namespace) {
        // The fabric AttachmentRegistry does not expose a global "list all types" API —
        // attachment types are registered statically by each mod, but the registry impl
        // only exposes get(id) and getSyncableAttachments(). The latter only includes
        // syncable types, which is incomplete. We return the syncable subset filtered to
        // the requested namespace; callers needing all attachment keys must consult
        // their own registry.
        AttachmentTarget t = resolveAttachmentTarget(target);
        if (t == null) {
            return List.of();
        }
        Set<Identifier> syncable = AttachmentRegistryImpl.getSyncableAttachments();
        List<String> out = new ArrayList<>();
        for (Identifier id : syncable) {
            if (id.getNamespace().equals(namespace)) {
                AttachmentType<?> type = AttachmentRegistryImpl.get(id);
                if (type != null && t.hasAttached(type)) {
                    out.add(id.getPath());
                }
            }
        }
        out.sort(String::compareTo);
        return out;
    }

    // =====================================================================
    // Datapack
    // =====================================================================

    List<DatapackInfo> datapackListAvailable() {
        MinecraftServer s = ctx.requireServer();
        List<DatapackInfo> out = new ArrayList<>();
        s.getPackRepository()
                .getAvailablePacks()
                .forEach(
                        pack ->
                                out.add(
                                        new DatapackInfo(
                                                pack.getId(),
                                                pack.getTitle().getString(),
                                                s.getPackRepository().getSelectedIds().contains(pack.getId()),
                                                pack.isFixedPosition())));
        return out;
    }

    List<DatapackInfo> datapackListEnabled() {
        return datapackListAvailable().stream().filter(DatapackInfo::enabled).toList();
    }

    boolean datapackEnable(String id) {
        // The previous implementation ran "/datapack enable <id>". DataPackCommand.enablePack
        // throws ERROR_PACK_FEATURES_NOT_ENABLED for any pack that requests feature flags the
        // world has not enabled (the experimental packs minecart_improvements, trade_rebalance,
        // redstone_experiments). commandExecute swallows that as a generic failure, so the tool
        // reported a bare "failed" with no explanation.
        //
        // Fix: drive the PackRepository directly. For an ordinary disabled pack this selects it
        // and reloads resources (the same effect as the command). For a feature-flag pack we
        // detect the unmet requirement and throw a clear, accurate error: those flags are baked
        // into the world's WorldDataConfiguration at creation time and cannot be turned on at
        // runtime, so the pack genuinely cannot be enabled on an existing world.
        MinecraftServer s = ctx.requireServer();
        net.minecraft.server.packs.repository.PackRepository repo = s.getPackRepository();
        net.minecraft.server.packs.repository.Pack pack = repo.getPack(id);
        if (pack == null) {
            throw new AdapterException("Datapack not found: " + id);
        }
        java.util.Collection<String> selected = repo.getSelectedIds();
        if (selected.contains(id)) {
            // Already enabled — vanilla treats this as an error, but for the adapter it's a no-op
            // success (the requested end state already holds).
            return true;
        }
        net.minecraft.world.flag.FeatureFlagSet requested = pack.getRequestedFeatures();
        net.minecraft.world.flag.FeatureFlagSet worldEnabled = s.getWorldData().enabledFeatures();
        if (!requested.isEmpty() && !requested.isSubsetOf(worldEnabled)) {
            // printMissingFlags(registry, A, B) renders the readable names in A that are absent
            // from B — i.e. the required-but-unmet flags. Using the registry avoids leaking the
            // FeatureFlagSet's (mapping-dependent) toString into the error message.
            String missing =
                    net.minecraft.world.flag.FeatureFlags.printMissingFlags(
                            net.minecraft.world.flag.FeatureFlags.REGISTRY, requested, worldEnabled);
            throw new AdapterException(
                    "Datapack '" + id + "' requires experimental feature flags ("
                            + missing
                            + ") that are not enabled in this world. Feature flags are fixed when"
                            + " the world is created and cannot be enabled at runtime, so this pack"
                            + " can only be activated by re-creating the world with those features"
                            + " selected.");
        }
        // Preserve existing selection order and append the newly-enabled pack.
        java.util.List<String> next = new java.util.ArrayList<>(selected);
        next.add(id);
        repo.setSelected(next);
        // Apply the new selection. reloadResources is async (CompletableFuture<Void>); mirror
        // serverReloadResources and fire it without blocking the tool call.
        try {
            s.reloadResources(repo.getSelectedIds());
        } catch (Exception e) {
            // Roll back the selection so a failed reload doesn't leave the repository in a
            // half-applied state, then surface the cause.
            repo.setSelected(selected);
            throw new AdapterException("Failed to enable datapack '" + id + "': " + e.getMessage(), e);
        }
        return true;
    }

    boolean datapackDisable(String id) {
        // /datapack disable is a void setter; reports 0 on the happy path. Left on the command
        // path because vanilla's disable carries guards we want to honour (e.g. it refuses to
        // disable a pack force-required by an enabled feature flag).
        return AdapterContext.commandOk(ctx.commandExecute("datapack disable " + id));
    }
}
