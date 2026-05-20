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
import net.minecraft.world.level.storage.CommandStorage;

import com.chapmanjw.minecraft.fabric.mcp.adapter.AdapterException;
import com.chapmanjw.minecraft.fabric.mcp.adapter.dto.CommandResult;
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
        String safePath = path == null ? "" : path.trim();
        // `/data get storage <id> <path>` requires a path; if none was passed, fall back
        // to dumping the whole storage tree.
        String cmd = safePath.isEmpty()
                ? "data get storage " + storageId(namespace)
                : "data get storage " + storageId(namespace) + " " + safePath;
        CommandResult r = ctx.commandExecute(cmd);
        if (r.error() != null) {
            return Optional.empty();
        }
        // `/data get` reports the value via the command source as a feedback message
        // even when successCount==1; collect the first output line.
        if (!r.output().isEmpty()) {
            return Optional.of(r.output().get(0));
        }
        return r.successCount() > 0 ? Optional.of("") : Optional.empty();
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
        // /datapack enable is a void setter; reports 0 on the happy path.
        return AdapterContext.commandOk(ctx.commandExecute("datapack enable " + id));
    }

    boolean datapackDisable(String id) {
        // /datapack disable is a void setter; reports 0 on the happy path.
        return AdapterContext.commandOk(ctx.commandExecute("datapack disable " + id));
    }
}
