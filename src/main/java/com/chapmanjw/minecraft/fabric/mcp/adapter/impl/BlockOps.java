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

    /**
     * Vanilla {@code /fill} silently no-ops above this volume (it returns success
     * with zero blocks changed). We tile every fill to stay under it so callers
     * never hit the "empty region despite success" trap.
     */
    private static final long MAX_FILL_VOLUME = 32_768L;

    long blockFillRegion(String dimensionId, BoundingBox box, BlockSpec spec, MinecraftAdapter.FillMode mode) {
        String blockArg = blockArg(spec);
        MinecraftAdapter.FillMode m = mode == null ? MinecraftAdapter.FillMode.REPLACE : mode;
        // hollow/outline are shape-aware: naive tiling would stamp interior walls
        // at every tile seam. Tile only the per-block-independent modes; decompose
        // an oversized hollow/outline into its six faces (+ interior air) instead.
        switch (m) {
            case HOLLOW:
            case OUTLINE:
                if (box.volume() <= MAX_FILL_VOLUME) {
                    return fillOnce(dimensionId, box, blockArg, m.name().toLowerCase(Locale.ROOT));
                }
                return fillShell(dimensionId, box, blockArg, m == MinecraftAdapter.FillMode.HOLLOW);
            default: // REPLACE, DESTROY, KEEP — per-block independent, safe to tile
                return fillTiled(dimensionId, box, blockArg, m.name().toLowerCase(Locale.ROOT));
        }
    }

    /** Build the {@code /fill} block argument: id plus {@code [prop=val,...]} state. */
    private static String blockArg(BlockSpec spec) {
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
        return block.toString();
    }

    /** One {@code /fill} over a box already known to be within the vanilla cap. */
    private long fillOnce(String dimensionId, BoundingBox box, String blockArg, String modeStr) {
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
                                blockArg,
                                modeStr));
        return r.successCount();
    }

    /**
     * Recursively split a box along its longest axis until each piece is within
     * {@link #MAX_FILL_VOLUME}, filling each. Valid only for per-block-independent
     * modes (replace / destroy / keep), where tiling cannot change the result.
     */
    private long fillTiled(String dimensionId, BoundingBox box, String blockArg, String modeStr) {
        if (box.volume() <= MAX_FILL_VOLUME) {
            return fillOnce(dimensionId, box, blockArg, modeStr);
        }
        int dx = box.sizeX();
        int dy = box.sizeY();
        int dz = box.sizeZ();
        long sum = 0;
        if (dx >= dy && dx >= dz) {
            int mid = (box.x1() + box.x2()) / 2;
            BoundingBox lo = new BoundingBox(box.x1(), box.y1(), box.z1(), mid, box.y2(), box.z2());
            BoundingBox hi = new BoundingBox(mid + 1, box.y1(), box.z1(), box.x2(), box.y2(), box.z2());
            sum += fillTiled(dimensionId, lo, blockArg, modeStr);
            sum += fillTiled(dimensionId, hi, blockArg, modeStr);
        } else if (dy >= dz) {
            int mid = (box.y1() + box.y2()) / 2;
            BoundingBox lo = new BoundingBox(box.x1(), box.y1(), box.z1(), box.x2(), mid, box.z2());
            BoundingBox hi = new BoundingBox(box.x1(), mid + 1, box.z1(), box.x2(), box.y2(), box.z2());
            sum += fillTiled(dimensionId, lo, blockArg, modeStr);
            sum += fillTiled(dimensionId, hi, blockArg, modeStr);
        } else {
            int mid = (box.z1() + box.z2()) / 2;
            BoundingBox lo = new BoundingBox(box.x1(), box.y1(), box.z1(), box.x2(), box.y2(), mid);
            BoundingBox hi = new BoundingBox(box.x1(), box.y1(), mid + 1, box.x2(), box.y2(), box.z2());
            sum += fillTiled(dimensionId, lo, blockArg, modeStr);
            sum += fillTiled(dimensionId, hi, blockArg, modeStr);
        }
        return sum;
    }

    /**
     * Outline/hollow over a box too large for one {@code /fill}: place the six
     * faces as a non-overlapping partition (each face tiled) and, for hollow,
     * clear the interior to air. Reproduces vanilla outline/hollow without the
     * interior-wall artifacts naive tiling would create.
     */
    private long fillShell(String dimensionId, BoundingBox box, String blockArg, boolean hollow) {
        int x1 = box.x1();
        int y1 = box.y1();
        int z1 = box.z1();
        int x2 = box.x2();
        int y2 = box.y2();
        int z2 = box.z2();
        String mode = "replace";
        long sum = 0;
        // bottom + top: full x,z slabs
        sum += fillTiled(dimensionId, new BoundingBox(x1, y1, z1, x2, y1, z2), blockArg, mode);
        if (y2 != y1) {
            sum += fillTiled(dimensionId, new BoundingBox(x1, y2, z1, x2, y2, z2), blockArg, mode);
        }
        int iy1 = y1 + 1;
        int iy2 = y2 - 1;
        if (iy1 <= iy2) {
            // north + south: full x, inner y
            sum += fillTiled(dimensionId, new BoundingBox(x1, iy1, z1, x2, iy2, z1), blockArg, mode);
            if (z2 != z1) {
                sum += fillTiled(dimensionId, new BoundingBox(x1, iy1, z2, x2, iy2, z2), blockArg, mode);
            }
            int iz1 = z1 + 1;
            int iz2 = z2 - 1;
            if (iz1 <= iz2) {
                // east + west: inner y, inner z
                sum += fillTiled(dimensionId, new BoundingBox(x1, iy1, iz1, x1, iy2, iz2), blockArg, mode);
                if (x2 != x1) {
                    sum += fillTiled(dimensionId, new BoundingBox(x2, iy1, iz1, x2, iy2, iz2), blockArg, mode);
                }
            }
        }
        if (hollow) {
            int ix1 = x1 + 1;
            int ix2 = x2 - 1;
            int jy1 = y1 + 1;
            int jy2 = y2 - 1;
            int kz1 = z1 + 1;
            int kz2 = z2 - 1;
            if (ix1 <= ix2 && jy1 <= jy2 && kz1 <= kz2) {
                BoundingBox interior = new BoundingBox(ix1, jy1, kz1, ix2, jy2, kz2);
                sum += fillTiled(dimensionId, interior, "minecraft:air", mode);
            }
        }
        return sum;
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

    int blockGetTopY(String dimensionId, int x, int z, String heightmapType) {
        ServerLevel level = ctx.requireLevel(dimensionId);
        net.minecraft.world.level.levelgen.Heightmap.Types type;
        try {
            type =
                    (heightmapType == null || heightmapType.isBlank())
                            ? net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE
                            : net.minecraft.world.level.levelgen.Heightmap.Types.valueOf(
                                    heightmapType.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new AdapterException("blockGetTopY: unknown heightmap type '" + heightmapType + "'");
        }
        return level.getHeight(type, x, z);
    }

    /** Update flag for terrain fills: notify clients, skip neighbour-update cascades. */
    private static final int TERRAIN_UPDATE_FLAGS = net.minecraft.world.level.block.Block.UPDATE_CLIENTS;

    /** Half-scale for the strata band-boundary jitter: maps sin+cos ([-2,2]) to +/- amplitude. */
    private static final double STRATA_JITTER_HALF_SCALE = 0.5;

    /** Thermal erosion: fraction of a pair's excess slope moved per sweep step. */
    private static final double THERMAL_MOVE_SHARE = 0.5;
    /** Smoothstep cubic coefficient (3x^2 term). */
    private static final double SMOOTHSTEP_C2 = 3.0;
    /** Smoothstep cubic coefficient (2x^3 term). */
    private static final double SMOOTHSTEP_C3 = 2.0;
    /** Clamp lower bound for the smoothstep ramp. */
    private static final double ERODE_ZERO = 0.0;
    /** Clamp upper bound for the smoothstep ramp. */
    private static final double ERODE_ONE = 1.0;

    /** Cap on columns per call, mirroring the scan cap — keeps the pass under the timeout. */
    private static final long MAX_COLUMNS = 65_536L;

    /**
     * The true ground-surface top-Y for a column, picking among three heightmaps so we
     * never erode a forest canopy or a water surface as if it were the ground:
     *
     * <ul>
     *   <li>{@code WORLD_SURFACE} — highest non-air block (includes leaves and water-top
     *       above the seabed).
     *   <li>{@code OCEAN_FLOOR} — highest motion-blocking block excluding fluids (the
     *       seabed under water).
     *   <li>{@code MOTION_BLOCKING_NO_LEAVES} — like motion-blocking but excludes leaves
     *       (the ground under a forest canopy).
     * </ul>
     *
     * <p>Each heightmap returns the Y of the lowest air cell above the surface, so the top
     * solid block is {@code height - 1}. We take the LOWEST of the three solid tops as the
     * true ground: under water the ocean floor is below the world surface, and under canopy
     * the no-leaves surface is below the world surface (which sat on the leaves). This is
     * the read used for both the survey grid and the write-back reference so they agree.
     */
    private static int trueSurfaceTop(ServerLevel level, int wx, int wz) {
        int worldSurface =
                level.getHeight(
                        net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE, wx, wz);
        int oceanFloor =
                level.getHeight(
                        net.minecraft.world.level.levelgen.Heightmap.Types.OCEAN_FLOOR, wx, wz);
        int noLeaves =
                level.getHeight(
                        net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                        wx,
                        wz);
        int ground = Math.min(worldSurface, Math.min(oceanFloor, noLeaves));
        return ground - 1;
    }

    /**
     * Fail fast if the region's four corners are not loaded. {@code level.getHeight}
     * returns the world's bottom Y (the {@code getMinY} sentinel, e.g. -64) for an
     * ungenerated/unloaded column, which would otherwise be eroded as a phantom flat
     * field. Sampling the corners (cheap) catches the common "forgot to forceload" case.
     */
    private static void requireRegionLoaded(
            ServerLevel level, int originX, int originZ, int width, int length, String op) {
        int sentinel = level.getMinY();
        int x0 = originX;
        int z0 = originZ;
        int x1 = originX + width - 1;
        int z1 = originZ + length - 1;
        int[][] corners = {{x0, z0}, {x1, z0}, {x0, z1}, {x1, z1}};
        for (int[] c : corners) {
            int cx = c[0] >> 4;
            int cz = c[1] >> 4;
            boolean loaded =
                    level.hasChunk(cx, cz)
                            && level.getHeight(
                                            net.minecraft.world.level.levelgen.Heightmap.Types
                                                    .WORLD_SURFACE,
                                            c[0],
                                            c[1])
                                    > sentinel;
            if (!loaded) {
                throw new AdapterException(
                        op
                                + ": region corner (" + c[0] + ", " + c[1] + ") is not loaded"
                                + " (getHeight == minY " + sentinel + " or chunk absent) — forceload"
                                + " the region before eroding");
            }
        }
    }

    long blockFillColumns(String dimensionId, MinecraftAdapter.ColumnFill spec) {
        int w = spec.width();
        int len = spec.length();
        long columns = (long) w * len;
        if (w <= 0 || len <= 0) {
            throw new AdapterException("blockFillColumns: width and length must be positive");
        }
        if (columns > MAX_COLUMNS) {
            throw new AdapterException(
                    "blockFillColumns: " + columns + " columns exceed the " + MAX_COLUMNS
                            + "-column cap; tile the heightmap");
        }
        int expected = (int) columns;
        if (spec.height().length != expected
                || spec.surface().length != expected
                || spec.subsurface().length != expected) {
            throw new AdapterException(
                    "blockFillColumns: height/surface/subsurface arrays must each have width*length ("
                            + expected + ") entries");
        }

        ServerLevel level = ctx.requireLevel(dimensionId);
        var registry = level.registryAccess().lookupOrThrow(Registries.BLOCK);
        BlockState[] palette = new BlockState[spec.palette().size()];
        for (int i = 0; i < palette.length; i++) {
            Identifier id = AdapterContext.parseIdentifier(spec.palette().get(i));
            var block = registry.getValue(id);
            if (block == null) {
                throw new AdapterException("blockFillColumns: unknown block id '" + spec.palette().get(i) + "'");
            }
            palette[i] = block.defaultBlockState();
        }
        if (spec.stoneIndex() < 0 || spec.stoneIndex() >= palette.length) {
            throw new AdapterException("blockFillColumns: stoneIndex out of palette range");
        }
        BlockState stone = palette[spec.stoneIndex()];
        BlockState water =
                (spec.waterIndex() >= 0 && spec.waterIndex() < palette.length)
                        ? palette[spec.waterIndex()]
                        : null;
        int subDepth = Math.max(0, spec.subsurfaceDepth());
        int floorY = spec.floorY();
        int seaLevel = spec.seaLevel();

        long set = 0;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int xi = 0; xi < w; xi++) {
            int wx = spec.originX() + xi;
            for (int zi = 0; zi < len; zi++) {
                int wz = spec.originZ() + zi;
                int i = xi * len + zi;
                int top = spec.height()[i];
                int surfIdx = spec.surface()[i];
                int subIdx = spec.subsurface()[i];
                if (surfIdx < 0 || surfIdx >= palette.length || subIdx < 0 || subIdx >= palette.length) {
                    throw new AdapterException("blockFillColumns: surface/subsurface index out of palette range");
                }
                BlockState surfState = palette[surfIdx];
                BlockState subState = palette[subIdx];
                int subBase = top - subDepth; // first subsurface Y
                for (int y = floorY; y < top; y++) {
                    cursor.set(wx, y, wz);
                    level.setBlock(cursor, y >= subBase ? subState : stone, TERRAIN_UPDATE_FLAGS);
                    set++;
                }
                cursor.set(wx, top, wz);
                level.setBlock(cursor, surfState, TERRAIN_UPDATE_FLAGS);
                set++;
                if (water != null && top < seaLevel) {
                    for (int y = top + 1; y <= seaLevel; y++) {
                        cursor.set(wx, y, wz);
                        level.setBlock(cursor, water, TERRAIN_UPDATE_FLAGS);
                        set++;
                    }
                }
            }
        }
        return set;
    }

    long blockFillColumnsStrata(String dimensionId, MinecraftAdapter.ColumnStrataFill spec) {
        int w = spec.width();
        int len = spec.length();
        long columns = (long) w * len;
        if (w <= 0 || len <= 0) {
            throw new AdapterException("blockFillColumnsStrata: width and length must be positive");
        }
        if (columns > MAX_COLUMNS) {
            throw new AdapterException(
                    "blockFillColumnsStrata: " + columns + " columns exceed the " + MAX_COLUMNS
                            + "-column cap; tile the heightmap");
        }
        int expected = (int) columns;
        if (spec.height().length != expected
                || spec.surface().length != expected
                || spec.subsurface().length != expected) {
            throw new AdapterException(
                    "blockFillColumnsStrata: height/surface/subsurface arrays must each have"
                            + " width*length (" + expected + ") entries");
        }
        if (spec.strataBlocks().size() != spec.strataThickness().length) {
            throw new AdapterException(
                    "blockFillColumnsStrata: strataBlocks and strataThickness must be the same length");
        }

        ServerLevel level = ctx.requireLevel(dimensionId);
        var registry = level.registryAccess().lookupOrThrow(Registries.BLOCK);
        BlockState[] palette = new BlockState[spec.palette().size()];
        for (int i = 0; i < palette.length; i++) {
            Identifier id = AdapterContext.parseIdentifier(spec.palette().get(i));
            var block = registry.getValue(id);
            if (block == null) {
                throw new AdapterException(
                        "blockFillColumnsStrata: unknown block id '" + spec.palette().get(i) + "'");
            }
            palette[i] = block.defaultBlockState();
        }
        BlockState[] strata = new BlockState[spec.strataBlocks().size()];
        for (int i = 0; i < strata.length; i++) {
            Identifier id = AdapterContext.parseIdentifier(spec.strataBlocks().get(i));
            var block = registry.getValue(id);
            if (block == null) {
                throw new AdapterException(
                        "blockFillColumnsStrata: unknown strata block id '"
                                + spec.strataBlocks().get(i) + "'");
            }
            strata[i] = block.defaultBlockState();
        }
        int[] strataThk = spec.strataThickness();
        Identifier baseId = AdapterContext.parseIdentifier(spec.baseStone());
        var baseBlock = registry.getValue(baseId);
        if (baseBlock == null) {
            throw new AdapterException(
                    "blockFillColumnsStrata: unknown base stone id '" + spec.baseStone() + "'");
        }
        BlockState baseStone = baseBlock.defaultBlockState();
        BlockState water =
                (spec.waterIndex() >= 0 && spec.waterIndex() < palette.length)
                        ? palette[spec.waterIndex()]
                        : null;
        int subDepth = Math.max(0, spec.subsurfaceDepth());
        int floorY = spec.floorY();
        int seaLevel = spec.seaLevel();
        int jitterAmp = spec.jitterAmplitude();
        double jitterFreq = spec.jitterFreq();

        long set = 0;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int xi = 0; xi < w; xi++) {
            int wx = spec.originX() + xi;
            for (int zi = 0; zi < len; zi++) {
                int wz = spec.originZ() + zi;
                int i = xi * len + zi;
                int top = spec.height()[i];
                int surfIdx = spec.surface()[i];
                int subIdx = spec.subsurface()[i];
                if (surfIdx < 0 || surfIdx >= palette.length || subIdx < 0 || subIdx >= palette.length) {
                    throw new AdapterException(
                            "blockFillColumnsStrata: surface/subsurface index out of palette range");
                }
                BlockState surfState = palette[surfIdx];
                BlockState subState = palette[subIdx];
                int subBase = top - subDepth; // first subsurface Y (inclusive)

                // Smooth, low-frequency per-column wobble for band boundaries so the
                // strata are not dead-flat. Deterministic (no per-column randomness).
                // sin+cos ranges [-2, 2]; the half-scale maps it back to +/- amplitude.
                int jit = 0;
                if (jitterAmp != 0) {
                    double wobble = Math.sin(wx * jitterFreq) + Math.cos(wz * jitterFreq);
                    jit = (int) Math.round(jitterAmp * STRATA_JITTER_HALF_SCALE * wobble);
                }

                for (int y = floorY; y < top; y++) {
                    BlockState place;
                    if (y >= subBase) {
                        place = subState;
                    } else {
                        // depth below the subsurface band, with the smooth jitter applied
                        int depth = (subBase - 1 - y) + jit;
                        place = strataAt(strata, strataThk, baseStone, depth);
                    }
                    cursor.set(wx, y, wz);
                    level.setBlock(cursor, place, TERRAIN_UPDATE_FLAGS);
                    set++;
                }
                cursor.set(wx, top, wz);
                level.setBlock(cursor, surfState, TERRAIN_UPDATE_FLAGS);
                set++;
                if (water != null && top < seaLevel) {
                    for (int y = top + 1; y <= seaLevel; y++) {
                        cursor.set(wx, y, wz);
                        level.setBlock(cursor, water, TERRAIN_UPDATE_FLAGS);
                        set++;
                    }
                }
            }
        }
        return set;
    }

    /** Pick the strata band block at a given depth-below-subsurface, or base stone. */
    private static BlockState strataAt(
            BlockState[] strata, int[] thickness, BlockState baseStone, int depth) {
        if (depth < 0) {
            return baseStone; // jitter pushed this cell above the first band
        }
        int acc = 0;
        for (int b = 0; b < strata.length; b++) {
            acc += Math.max(0, thickness[b]);
            if (depth < acc) {
                return strata[b];
            }
        }
        return baseStone;
    }

    /**
     * Smoothstep ramp clamped to [0, 1] — tapers erosion strength across the apron
     * around a protected box so the boundary is not a hard step.
     *
     * @param t the unclamped ramp position
     * @return the eased value in [0, 1]
     */
    private static double erodeSmoothstep(double t) {
        double x = t < ERODE_ZERO ? ERODE_ZERO : (t > ERODE_ONE ? ERODE_ONE : t);
        return x * x * (SMOOTHSTEP_C2 - SMOOTHSTEP_C3 * x);
    }

    MinecraftAdapter.ErodeResult terrainErodeRegion(
            String dimensionId, MinecraftAdapter.ErodeSpec spec) {
        int w = spec.width();
        int len = spec.length();
        if (w <= 0 || len <= 0) {
            throw new AdapterException("terrainErodeRegion: width and length must be positive");
        }
        long columns = (long) w * len;
        if (columns > MAX_COLUMNS) {
            throw new AdapterException(
                    "terrainErodeRegion: " + columns + " columns exceed the " + MAX_COLUMNS
                            + "-column cap; tile the region");
        }
        ServerLevel level = ctx.requireLevel(dimensionId);
        requireRegionLoaded(level, spec.originX(), spec.originZ(), w, len, "terrainErodeRegion");
        var registry = level.registryAccess().lookupOrThrow(Registries.BLOCK);

        Identifier surfId = AdapterContext.parseIdentifier(spec.surface());
        var surfBlock = registry.getValue(surfId);
        if (surfBlock == null) {
            throw new AdapterException(
                    "terrainErodeRegion: unknown surface block id '" + spec.surface() + "'");
        }
        BlockState surfaceState = surfBlock.defaultBlockState();
        Identifier subId = AdapterContext.parseIdentifier(spec.subsurface());
        var subBlock = registry.getValue(subId);
        if (subBlock == null) {
            throw new AdapterException(
                    "terrainErodeRegion: unknown subsurface block id '" + spec.subsurface() + "'");
        }
        BlockState subState = subBlock.defaultBlockState();
        BlockState air = net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();

        int total = (int) columns;
        int[] orig = new int[total];
        double[] h = new double[total];
        double[] factor = new double[total];
        boolean hasProtect = spec.protectX0() != Integer.MIN_VALUE
                && spec.protectX1() >= spec.protectX0()
                && spec.protectZ1() >= spec.protectZ0();

        for (int xi = 0; xi < w; xi++) {
            int wx = spec.originX() + xi;
            for (int zi = 0; zi < len; zi++) {
                int wz = spec.originZ() + zi;
                int idx = xi * len + zi;
                int topSolid = trueSurfaceTop(level, wx, wz);
                orig[idx] = topSolid;
                h[idx] = topSolid;
                double fac = ERODE_ONE;
                if (hasProtect) {
                    boolean inside =
                            wx >= spec.protectX0()
                                    && wx <= spec.protectX1()
                                    && wz >= spec.protectZ0()
                                    && wz <= spec.protectZ1();
                    if (inside) {
                        fac = ERODE_ZERO;
                    } else if (spec.apron() > 0) {
                        int dxOut = Math.max(0, Math.max(spec.protectX0() - wx, wx - spec.protectX1()));
                        int dzOut = Math.max(0, Math.max(spec.protectZ0() - wz, wz - spec.protectZ1()));
                        double dist = Math.sqrt((double) dxOut * dxOut + (double) dzOut * dzOut);
                        fac = dist >= spec.apron() ? ERODE_ONE : erodeSmoothstep(dist / spec.apron());
                    }
                }
                factor[idx] = fac;
            }
        }

        int[] dirX = {1, -1, 0, 0};
        int[] dirZ = {0, 0, 1, -1};
        double[] delta = new double[total];
        double moved = ERODE_ZERO;
        for (int it = 0; it < spec.iterations(); it++) {
            java.util.Arrays.fill(delta, ERODE_ZERO);
            for (int xi = 0; xi < w; xi++) {
                for (int zi = 0; zi < len; zi++) {
                    int c = xi * len + zi;
                    for (int d = 0; d < dirX.length; d++) {
                        int nxi = xi + dirX[d];
                        int nzi = zi + dirZ[d];
                        if (nxi < 0 || nxi >= w || nzi < 0 || nzi >= len) {
                            continue;
                        }
                        int ni = nxi * len + nzi;
                        double diff = h[c] - h[ni];
                        if (diff > spec.talus()) {
                            double fac = Math.min(factor[c], factor[ni]);
                            if (fac <= ERODE_ZERO) {
                                continue;
                            }
                            double move =
                                    (diff - spec.talus()) * spec.strength() * THERMAL_MOVE_SHARE * fac;
                            delta[c] -= move;
                            delta[ni] += move;
                        }
                    }
                }
            }
            for (int i = 0; i < total; i++) {
                h[i] += delta[i];
                if (delta[i] > ERODE_ZERO) {
                    moved += delta[i];
                }
            }
        }

        long changed = 0;
        int maxDelta = 0;
        long sumAbs = 0;
        int subDepth = Math.max(0, spec.subsurfaceDepth());
        // Dry run returns the rounded eroded grid so the offline client can render-verify
        // the proposal before applying; on an apply we skip it to keep the response small.
        int[] dryHeights = spec.dryRun() ? new int[total] : null;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int xi = 0; xi < w; xi++) {
            int wx = spec.originX() + xi;
            for (int zi = 0; zi < len; zi++) {
                int wz = spec.originZ() + zi;
                int idx = xi * len + zi;
                int oldTop = orig[idx];
                int newTop = (int) Math.round(h[idx]);
                if (newTop < spec.floorY()) {
                    newTop = spec.floorY();
                }
                if (dryHeights != null) {
                    dryHeights[idx] = newTop;
                }
                int dAbs = Math.abs(newTop - oldTop);
                if (dAbs > maxDelta) {
                    maxDelta = dAbs;
                }
                sumAbs += dAbs;
                if (!spec.dryRun() && newTop != oldTop) {
                    if (newTop < oldTop) {
                        for (int y = oldTop; y > newTop; y--) {
                            cursor.set(wx, y, wz);
                            level.setBlock(cursor, air, TERRAIN_UPDATE_FLAGS);
                            changed++;
                        }
                    } else {
                        for (int y = oldTop + 1; y <= newTop; y++) {
                            cursor.set(wx, y, wz);
                            level.setBlock(cursor, subState, TERRAIN_UPDATE_FLAGS);
                            changed++;
                        }
                    }
                    cursor.set(wx, newTop, wz);
                    level.setBlock(cursor, surfaceState, TERRAIN_UPDATE_FLAGS);
                    changed++;
                    for (int depth = 1; depth <= subDepth; depth++) {
                        int y = newTop - depth;
                        if (y <= spec.floorY()) {
                            break;
                        }
                        cursor.set(wx, y, wz);
                        level.setBlock(cursor, subState, TERRAIN_UPDATE_FLAGS);
                        changed++;
                    }
                }
            }
        }
        double meanAbs = total > 0 ? (double) sumAbs / total : ERODE_ZERO;
        return new MinecraftAdapter.ErodeResult(
                total, changed, maxDelta, meanAbs, moved, spec.iterations(), dryHeights);
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

    /**
     * Maximum cells a single summary scan may inspect. Larger than the raw-scan
     * cap because the output is tiny (a histogram, not per-block rows); bounded
     * only to keep the one main-thread pass well under the tool timeout.
     */
    private static final long MAX_SUMMARY_VOLUME = 1_048_576L;

    MinecraftAdapter.ScanSummary blockScanSummary(String dimensionId, BoundingBox box) {
        if (box.volume() > MAX_SUMMARY_VOLUME) {
            throw new AdapterException(
                    "blockScanSummary: bounding box volume " + box.volume()
                            + " exceeds the " + MAX_SUMMARY_VOLUME + "-block cap");
        }
        ServerLevel level = ctx.requireLevel(dimensionId);
        var blocks = level.registryAccess().lookupOrThrow(Registries.BLOCK);
        java.util.Map<String, Long> histogram = new java.util.LinkedHashMap<>();
        long nonAir = 0;
        boolean any = false;
        int minX = 0;
        int minY = 0;
        int minZ = 0;
        int maxX = 0;
        int maxY = 0;
        int maxZ = 0;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int y = box.y1(); y <= box.y2(); y++) {
            for (int z = box.z1(); z <= box.z2(); z++) {
                for (int x = box.x1(); x <= box.x2(); x++) {
                    cursor.set(x, y, z);
                    BlockState state = level.getBlockState(cursor);
                    if (state.isAir()) {
                        continue;
                    }
                    nonAir++;
                    Identifier id = blocks.getKey(state.getBlock());
                    histogram.merge(id == null ? "minecraft:air" : id.toString(), 1L, Long::sum);
                    if (!any) {
                        minX = x;
                        minY = y;
                        minZ = z;
                        maxX = x;
                        maxY = y;
                        maxZ = z;
                        any = true;
                    } else {
                        minX = Math.min(minX, x);
                        minY = Math.min(minY, y);
                        minZ = Math.min(minZ, z);
                        maxX = Math.max(maxX, x);
                        maxY = Math.max(maxY, y);
                        maxZ = Math.max(maxZ, z);
                    }
                }
            }
        }
        Vec3i mn = any ? new Vec3i(minX, minY, minZ) : null;
        Vec3i mx = any ? new Vec3i(maxX, maxY, maxZ) : null;
        return new MinecraftAdapter.ScanSummary(box.volume(), nonAir, histogram, mn, mx);
    }

    Optional<MinecraftAdapter.MapColorInfo> blockGetMapColor(String dimensionId, Vec3i position) {
        try {
            ServerLevel level = ctx.requireLevel(dimensionId);
            BlockPos pos = new BlockPos(position.x(), position.y(), position.z());
            BlockState state = level.getBlockState(pos);
            net.minecraft.world.level.material.MapColor color = state.getMapColor(level, pos);
            if (color == null) {
                return Optional.empty();
            }
            return Optional.of(new MinecraftAdapter.MapColorInfo(color.id, color.col));
        } catch (AdapterException ae) {
            return Optional.empty();
        }
    }

    /** Maximum sampled cells (post-downsample) a single render may rasterize. */
    private static final long MAX_RENDER_CELLS = 4_194_304L;

    /** Fallback colour for a block with no map colour. */
    private static final int UNKNOWN_RGB = 0x888888;
    /** Low 24 bits — drop any alpha from a packed map colour. */
    private static final int RGB_MASK = 0xFFFFFF;
    /** Stand-in for a true-black solid so it isn't confused with empty (0). */
    private static final int NEAR_BLACK_RGB = 0x010101;

    byte[] worldRenderRegion(String dimensionId, BoundingBox box, String view, int step, int scale) {
        int s = Math.max(1, step);
        int nx = (box.sizeX() + s - 1) / s;
        int ny = (box.sizeY() + s - 1) / s;
        int nz = (box.sizeZ() + s - 1) / s;
        long cells = (long) nx * ny * nz;
        if (cells > MAX_RENDER_CELLS) {
            throw new AdapterException(
                    "worldRenderRegion: " + cells + " sampled cells exceed the "
                            + MAX_RENDER_CELLS + "-cell cap; increase step or shrink the box");
        }
        ServerLevel level = ctx.requireLevel(dimensionId);
        int[] colors = new int[(int) cells];
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        int gi = 0;
        for (int xi = 0; xi < nx; xi++) {
            int wx = box.x1() + xi * s;
            for (int yi = 0; yi < ny; yi++) {
                int wy = box.y1() + yi * s;
                for (int zi = 0; zi < nz; zi++) {
                    int wz = box.z1() + zi * s;
                    cursor.set(wx, wy, wz);
                    BlockState state = level.getBlockState(cursor);
                    int rgb = 0; // 0 == air/empty
                    if (!state.isAir()) {
                        net.minecraft.world.level.material.MapColor mc =
                                state.getMapColor(level, cursor);
                        rgb = (mc == null) ? UNKNOWN_RGB : (mc.col & RGB_MASK);
                        if (rgb == 0) {
                            rgb = NEAR_BLACK_RGB; // keep true-black blocks distinct from air
                        }
                    }
                    colors[gi++] = rgb;
                }
            }
        }
        return VoxelRenderer.render(colors, nx, ny, nz, view, scale);
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
        // /loot replace block ... has no "value []" source; iterate slots directly.
        ServerLevel level = ctx.requireLevel(dimensionId);
        var be = level.getBlockEntity(new BlockPos(position.x(), position.y(), position.z()));
        if (!(be instanceof net.minecraft.world.Container c)) {
            return false;
        }
        for (int i = 0; i < c.getContainerSize(); i++) {
            c.setItem(i, net.minecraft.world.item.ItemStack.EMPTY);
        }
        c.setChanged();
        return true;
    }


    int[] terrainSurveyHeights(
            String dimensionId, int originX, int originZ, int width, int length) {
        if (width <= 0 || length <= 0) {
            throw new AdapterException("terrainSurveyHeights: width and length must be positive");
        }
        ServerLevel level = ctx.requireLevel(dimensionId);
        requireRegionLoaded(level, originX, originZ, width, length, "terrainSurveyHeights");
        int[] h = new int[width * length];
        for (int xi = 0; xi < width; xi++) {
            int wx = originX + xi;
            for (int zi = 0; zi < length; zi++) {
                int wz = originZ + zi;
                h[xi * length + zi] = trueSurfaceTop(level, wx, wz);
            }
        }
        return h;
    }

    MinecraftAdapter.ErodedApplyResult terrainApplyErodedColumns(
            String dimensionId, MinecraftAdapter.ErodedApply spec) {
        ServerLevel level = ctx.requireLevel(dimensionId);
        var registry = level.registryAccess().lookupOrThrow(Registries.BLOCK);
        BlockState surfaceState = erodedBlockState(registry, spec.surface(), "surface");
        BlockState subState = erodedBlockState(registry, spec.subsurface(), "subsurface");
        BlockState air = net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();
        // TODO(R5-followup): deep re-materialize is deferred — this writes a single fixed
        // surface/subsurface palette. The follow-up adds: per-column palette inference from
        // a region survey (Research/12 §3.1), water re-flood/drain for columns crossing sea
        // level (§3.5), slope-driven rock exposure + beach/sediment deposition on the NEW
        // height grid (§3.3/§3.4), and a gravity/hazard consolidation pass (§6). All of that
        // belongs here, replacing the flat surfaceState/subState selection below.

        int len = spec.length();
        int subDepth = Math.max(0, spec.subsurfaceDepth());
        int[] oldH = spec.oldHeights();
        int[] newH = spec.newHeights();
        long changed = 0;
        int from = spec.fromCol();
        int colsAdvanced = 0;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int col = from; col < spec.toCol(); col++) {
            int xi = col / len;
            int zi = col % len;
            int wx = spec.originX() + xi;
            int wz = spec.originZ() + zi;
            // Re-check load state every column: chunks can unload between ticks when no
            // player is near. If this column's chunk is gone, stop WITHOUT advancing past
            // it (so its slice is retried next tick) and ask the chunk source to reload it.
            if (!level.isLoaded(cursor.set(wx, level.getMinY(), wz))) {
                level.getChunk(
                        wx >> 4,
                        wz >> 4,
                        net.minecraft.world.level.chunk.status.ChunkStatus.FULL,
                        true);
                break;
            }
            colsAdvanced = col - from + 1;
            int oldTop = oldH[col];
            int newTop = newH[col];
            if (newTop < spec.floorY()) {
                newTop = spec.floorY();
            }
            if (newTop == oldTop) {
                continue;
            }
            if (newTop < oldTop) {
                for (int y = oldTop; y > newTop; y--) {
                    cursor.set(wx, y, wz);
                    level.setBlock(cursor, air, TERRAIN_UPDATE_FLAGS);
                    changed++;
                }
            } else {
                for (int y = oldTop + 1; y <= newTop; y++) {
                    cursor.set(wx, y, wz);
                    level.setBlock(cursor, subState, TERRAIN_UPDATE_FLAGS);
                    changed++;
                }
            }
            cursor.set(wx, newTop, wz);
            level.setBlock(cursor, surfaceState, TERRAIN_UPDATE_FLAGS);
            changed++;
            for (int depth = 1; depth <= subDepth; depth++) {
                int y = newTop - depth;
                if (y <= spec.floorY()) {
                    break;
                }
                cursor.set(wx, y, wz);
                level.setBlock(cursor, subState, TERRAIN_UPDATE_FLAGS);
                changed++;
            }
        }
        return new MinecraftAdapter.ErodedApplyResult(changed, colsAdvanced);
    }

    private static BlockState erodedBlockState(
            net.minecraft.core.Registry<net.minecraft.world.level.block.Block> registry,
            String id,
            String role) {
        var block = registry.getValue(AdapterContext.parseIdentifier(id));
        if (block == null) {
            throw new AdapterException("terrainApplyErodedColumns: unknown " + role + " block id '" + id + "'");
        }
        return block.defaultBlockState();
    }
}
