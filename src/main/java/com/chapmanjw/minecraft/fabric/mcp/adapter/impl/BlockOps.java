package com.chapmanjw.minecraft.fabric.mcp.adapter.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

import com.chapmanjw.minecraft.fabric.mcp.adapter.AdapterException;
import com.chapmanjw.minecraft.fabric.mcp.adapter.MinecraftAdapter;
import com.chapmanjw.minecraft.fabric.mcp.adapter.dto.BlockSpec;
import com.chapmanjw.minecraft.fabric.mcp.adapter.dto.BlockStateInfo;
import com.chapmanjw.minecraft.fabric.mcp.adapter.dto.BoundingBox;
import com.chapmanjw.minecraft.fabric.mcp.adapter.dto.CommandResult;
import com.chapmanjw.minecraft.fabric.mcp.adapter.dto.Vec3i;

/**
 * Block and BlockEntity operations.
 */
final class BlockOps {

    private final AdapterContext ctx;

    BlockOps(AdapterContext ctx) {
        this.ctx = ctx;
    }

    Optional<BlockStateInfo> blockGetState(String dimensionId, Vec3i position) {
        try {
            ServerLevel level = ctx.requireLevel(dimensionId);
            BlockPos pos = new BlockPos(position.x(), position.y(), position.z());
            BlockState state = level.getBlockState(pos);
            Identifier id =
                    level.registryAccess()
                            .lookupOrThrow(Registries.BLOCK)
                            .getKey(state.getBlock());

            Map<String, String> props = new java.util.LinkedHashMap<>();
            //? if mc_gte_26 {
            state.getValues()
                    .forEach(v -> props.put(v.property().getName(), v.value().toString()));
            //?} else {
            /*state.getValues()
                    .forEach((property, value) -> props.put(property.getName(), value.toString()));
            *///?}

            int light = level.getMaxLocalRawBrightness(pos);
            float hardness = state.getDestroySpeed(level, pos);
            boolean hasBE = level.getBlockEntity(pos) != null;
            String nbt = null;
            if (hasBE) {
                try {
                    var be = level.getBlockEntity(pos);
                    nbt = be == null ? null : be.saveWithFullMetadata(level.registryAccess()).toString();
                } catch (Throwable t) {
                    // Block entity NBT capture is best-effort; ignore failures.
                }
            }

            return Optional.of(
                    new BlockStateInfo(
                            id == null ? "minecraft:air" : id.toString(),
                            props,
                            light,
                            hardness,
                            hasBE,
                            nbt));
        } catch (AdapterException ae) {
            return Optional.empty();
        }
    }

    boolean blockSetState(String dimensionId, Vec3i position, BlockSpec spec, int updateFlags) {
        // Dispatch via /setblock to leverage vanilla state-property parsing.
        StringBuilder sb = new StringBuilder("setblock ");
        sb.append(position.x()).append(' ').append(position.y()).append(' ').append(position.z()).append(' ');
        sb.append(spec.id());
        if (!spec.properties().isEmpty()) {
            sb.append('[');
            boolean first = true;
            for (Map.Entry<String, String> e : spec.properties().entrySet()) {
                if (!first) {
                    sb.append(',');
                }
                sb.append(e.getKey()).append('=').append(e.getValue());
                first = false;
            }
            sb.append(']');
        }
        if (spec.nbt() != null && !spec.nbt().isBlank()) {
            sb.append(spec.nbt());
        }
        sb.append(" replace");
        CommandResult r =
                ctx.commandExecute("execute in " + dimensionId + " run " + sb);
        return r.successCount() > 0;
    }

    long blockFillRegion(String dimensionId, BoundingBox box, BlockSpec spec, MinecraftAdapter.FillMode mode) {
        String modeStr = mode == null ? "replace" : mode.name().toLowerCase(Locale.ROOT);
        StringBuilder block = new StringBuilder(spec.id());
        if (!spec.properties().isEmpty()) {
            block.append('[');
            boolean first = true;
            for (Map.Entry<String, String> e : spec.properties().entrySet()) {
                if (!first) {
                    block.append(',');
                }
                block.append(e.getKey()).append('=').append(e.getValue());
                first = false;
            }
            block.append(']');
        }
        CommandResult r =
                ctx.commandExecute(
                        String.format(
                                Locale.ROOT,
                                "execute in %s run fill %d %d %d %d %d %d %s %s",
                                dimensionId,
                                box.x1(),
                                box.y1(),
                                box.z1(),
                                box.x2(),
                                box.y2(),
                                box.z2(),
                                block,
                                modeStr));
        return r.successCount();
    }

    long blockCloneRegion(
            String sourceDimension,
            BoundingBox source,
            String destDimension,
            Vec3i destinationOrigin,
            MinecraftAdapter.CloneMode mode) {
        // Vanilla /clone syntax in 1.21+:
        //   clone [from <srcDim>] <begin> <end> [to <destDim>] <destination>
        //         [filtered|masked|replace] [force|move|normal]
        // The pre-fix form unconditionally injected a `to <x> <y> <z>` between the
        // source box and destination -- vanilla then parsed `to` as a dimension id
        // and the destination coordinates as garbage, so the clone never ran. Build
        // the command compositionally so cross-dimension and same-dimension forms
        // each match the parser.
        String modeStr = mode == null ? "normal" : mode.name().toLowerCase(Locale.ROOT);
        StringBuilder cmd = new StringBuilder("execute in ").append(destDimension).append(" run clone ");
        if (sourceDimension != null && !sourceDimension.equals(destDimension)) {
            cmd.append("from ").append(sourceDimension).append(' ');
        }
        cmd.append(source.x1()).append(' ').append(source.y1()).append(' ').append(source.z1()).append(' ');
        cmd.append(source.x2()).append(' ').append(source.y2()).append(' ').append(source.z2()).append(' ');
        cmd.append(destinationOrigin.x()).append(' ')
                .append(destinationOrigin.y()).append(' ')
                .append(destinationOrigin.z());
        // `replace` is the default filter; explicit so the mode token below is unambiguous.
        cmd.append(" replace ").append(modeStr);
        CommandResult r = ctx.commandExecute(cmd.toString());
        return r.successCount();
    }

    long blockReplaceInRegion(
            String dimensionId, BoundingBox box, String targetBlockId, BlockSpec replacement) {
        CommandResult r =
                ctx.commandExecute(
                        String.format(
                                Locale.ROOT,
                                "execute in %s run fill %d %d %d %d %d %d %s replace %s",
                                dimensionId,
                                box.x1(),
                                box.y1(),
                                box.z1(),
                                box.x2(),
                                box.y2(),
                                box.z2(),
                                replacement.id(),
                                targetBlockId));
        return r.successCount();
    }

    int blockGetTopY(String dimensionId, int x, int z) {
        ServerLevel level = ctx.requireLevel(dimensionId);
        return level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE, x, z);
    }

    /**
     * Maximum total cells (x×y×z) a single block scan may inspect.
     * Same cap applies in the {@code block_scan_region} tool schema.
     */
    private static final long MAX_SCAN_VOLUME = 65_536L;

    List<MinecraftAdapter.BlockMatch> blockScanRegion(
            String dimensionId, BoundingBox box, String matchBlockId, int limit) {
        if (box.volume() > MAX_SCAN_VOLUME) {
            throw new AdapterException(
                    "blockScanRegion: bounding box volume " + box.volume()
                            + " exceeds the " + MAX_SCAN_VOLUME + "-block cap");
        }
        ServerLevel level = ctx.requireLevel(dimensionId);
        Identifier matchId = matchBlockId == null ? null : AdapterContext.parseIdentifier(matchBlockId);
        List<MinecraftAdapter.BlockMatch> matches = new ArrayList<>();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int y = box.y1(); y <= box.y2() && matches.size() < limit; y++) {
            for (int z = box.z1(); z <= box.z2() && matches.size() < limit; z++) {
                for (int x = box.x1(); x <= box.x2() && matches.size() < limit; x++) {
                    cursor.set(x, y, z);
                    BlockState state = level.getBlockState(cursor);
                    Identifier id =
                            level.registryAccess()
                                    .lookupOrThrow(Registries.BLOCK)
                                    .getKey(state.getBlock());
                    if (matchId == null || matchId.equals(id)) {
                        Map<String, String> props = new java.util.LinkedHashMap<>();
                        //? if mc_gte_26 {
                        state.getValues()
                                .forEach(
                                        v ->
                                                props.put(
                                                        v.property().getName(),
                                                        v.value().toString()));
                        //?} else {
                        /*state.getValues()
                                .forEach(
                                        (property, value) ->
                                                props.put(property.getName(), value.toString()));
                        *///?}
                        matches.add(
                                new MinecraftAdapter.BlockMatch(
                                        new Vec3i(x, y, z),
                                        new BlockStateInfo(
                                                id == null ? "minecraft:air" : id.toString(),
                                                props,
                                                0,
                                                0,
                                                false,
                                                null)));
                    }
                }
            }
        }
        return matches;
    }

    // =====================================================================
    // BlockEntity
    // =====================================================================

    Optional<String> blockEntityGetNbt(String dimensionId, Vec3i position) {
        ServerLevel level = ctx.requireLevel(dimensionId);
        var be = level.getBlockEntity(new BlockPos(position.x(), position.y(), position.z()));
        if (be == null) {
            return Optional.empty();
        }
        return Optional.of(be.saveWithFullMetadata(level.registryAccess()).toString());
    }

    boolean blockEntitySetNbt(String dimensionId, Vec3i position, String snbt) {
        CommandResult r =
                ctx.commandExecute(
                        String.format(
                                Locale.ROOT,
                                "execute in %s run data merge block %d %d %d %s",
                                dimensionId,
                                position.x(),
                                position.y(),
                                position.z(),
                                snbt));
        return r.successCount() > 0;
    }

    boolean blockEntityClearInventory(String dimensionId, Vec3i position) {
        // No vanilla command for "clear block inventory" directly; use loot replace block with nothing.
        CommandResult r =
                ctx.commandExecute(
                        String.format(
                                Locale.ROOT,
                                "execute in %s run loot replace block %d %d %d slot.container 0 26 from value []",
                                dimensionId,
                                position.x(),
                                position.y(),
                                position.z()));
        return r.successCount() > 0;
    }
}
