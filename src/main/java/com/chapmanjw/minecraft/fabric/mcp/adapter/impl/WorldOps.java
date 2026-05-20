package com.chapmanjw.minecraft.fabric.mcp.adapter.impl;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Stream;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gamerules.GameRule;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import net.minecraft.world.level.storage.LevelData;
import net.minecraft.world.level.storage.LevelResource;
//? if !mc_gte_26 {
/*import net.minecraft.world.level.storage.ServerLevelData;
*///?}

import com.chapmanjw.minecraft.fabric.mcp.adapter.AdapterException;
import com.chapmanjw.minecraft.fabric.mcp.adapter.dto.BiomeInfo;
import com.chapmanjw.minecraft.fabric.mcp.adapter.dto.BoundingBox;
import com.chapmanjw.minecraft.fabric.mcp.adapter.dto.CommandResult;
import com.chapmanjw.minecraft.fabric.mcp.adapter.dto.DimensionInfo;
import com.chapmanjw.minecraft.fabric.mcp.adapter.dto.GameRuleInfo;
import com.chapmanjw.minecraft.fabric.mcp.adapter.dto.LevelInfo;
import com.chapmanjw.minecraft.fabric.mcp.adapter.dto.StructureInfo;
import com.chapmanjw.minecraft.fabric.mcp.adapter.dto.Vec3d;
import com.chapmanjw.minecraft.fabric.mcp.adapter.dto.Vec3i;
import com.chapmanjw.minecraft.fabric.mcp.adapter.dto.WorldBorderInfo;

/**
 * Level (per-dimension), structures, and world-border operations.
 */
final class WorldOps {

    private final AdapterContext ctx;

    WorldOps(AdapterContext ctx) {
        this.ctx = ctx;
    }

    // =====================================================================
    // Level helpers
    // =====================================================================

    List<String> levelListDimensions() {
        List<String> out = new ArrayList<>();
        for (ServerLevel level : ctx.requireServer().getAllLevels()) {
            out.add(level.dimension().identifier().toString());
        }
        return out;
    }

    Optional<DimensionInfo> levelGetDimensionInfo(String dimensionId) {
        try {
            ServerLevel level = ctx.requireLevel(dimensionId);
            var dimType = level.dimensionType();
            // 1.21.11 and 26.1.x removed the explicit ultraWarm()/piglinSafe()/natural()
            // accessors on DimensionType. Those three flags collapse cleanly onto the
            // vanilla dimension identifier — every datapack-defined dimension that wants
            // distinct values can override via DimensionType data files, but the runtime
            // adapter only exposes the well-known vanilla mapping here. This preserves
            // the DimensionInfo schema across versions without reaching into
            // EnvironmentAttributeMap (whose keys are not part of the stable adapter API).
            String idStr = level.dimension().identifier().toString();
            boolean ultraWarm = "minecraft:the_nether".equals(idStr);
            boolean piglinSafe = "minecraft:the_nether".equals(idStr);
            boolean natural = "minecraft:overworld".equals(idStr);
            var typeKey = level.dimensionTypeRegistration().unwrapKey().orElse(null);
            String typeId = typeKey == null ? idStr : typeKey.identifier().toString();
            //? if mc_gte_26 {
            long timeOfDay = level.getOverworldClockTime();
            //?} else {
            /*long timeOfDay = level.getDayTime();
            *///?}
            return Optional.of(
                    new DimensionInfo(
                            idStr,
                            typeId,
                            dimType.minY(),
                            dimType.minY() + dimType.height() - 1,
                            timeOfDay,
                            dimType.hasCeiling(),
                            ultraWarm,
                            piglinSafe,
                            natural,
                            ""));
        } catch (AdapterException ae) {
            return Optional.empty();
        }
    }

    Optional<LevelInfo> levelGetInfo(String dimensionId) {
        try {
            ServerLevel level = ctx.requireLevel(dimensionId);
            BlockPos spawn = level.getRespawnData().pos();
            String weather =
                    level.isThundering() ? "thunder" : (level.isRaining() ? "rain" : "clear");
            int weatherRemaining = 0;
            //? if mc_gte_26 {
            weatherRemaining = level.getWeatherData().getRainTime();
            //?} else {
            /*LevelData data = level.getLevelData();
            if (data instanceof ServerLevelData sld) {
                weatherRemaining = sld.getRainTime();
            }
            *///?}
            //? if mc_gte_26 {
            long dayTime = level.getOverworldClockTime();
            //?} else {
            /*long dayTime = level.getDayTime();
            *///?}
            return Optional.of(
                    new LevelInfo(
                            level.dimension().identifier().toString(),
                            dayTime,
                            level.getGameTime(),
                            weather,
                            weatherRemaining,
                            level.getDifficulty().getSerializedName(),
                            level.getLevelData().isDifficultyLocked(),
                            ctx.requireServer().getDefaultGameType().getName(),
                            new Vec3i(spawn.getX(), spawn.getY(), spawn.getZ()),
                            level.getLevelData().isHardcore()));
        } catch (AdapterException ae) {
            return Optional.empty();
        }
    }

    long levelGetTime(String dimensionId) {
        ServerLevel level = ctx.requireLevel(dimensionId);
        //? if mc_gte_26 {
        return level.getOverworldClockTime();
        //?} else {
        /*return level.getDayTime();
        *///?}
    }

    void levelSetTime(String dimensionId, long timeOfDay) {
        ServerLevel level = ctx.requireLevel(dimensionId);
        //? if mc_gte_26 {
        // ServerLevel.setDayTime was removed in 26.1.x — go through the time command, which
        // updates the overworld clock and propagates to clients.
        CommandResult r = ctx.commandExecute("time set " + timeOfDay);
        if (r.successCount() == 0 && r.error() != null) {
            throw new AdapterException(r.error());
        }
        //?} else {
        /*level.setDayTime(timeOfDay);
        *///?}
    }

    String levelGetWeather(String dimensionId) {
        ServerLevel level = ctx.requireLevel(dimensionId);
        return level.isThundering() ? "thunder" : (level.isRaining() ? "rain" : "clear");
    }

    void levelSetWeather(String dimensionId, String weather, int durationTicks) {
        ServerLevel level = ctx.requireLevel(dimensionId);
        //? if mc_gte_26 {
        // ServerLevel.setWeatherParameters was removed in 26.1.x. The /weather command
        // dispatches to the same WeatherData internally.
        String w =
                "thunder".equalsIgnoreCase(weather)
                        ? "thunder"
                        : ("rain".equalsIgnoreCase(weather) ? "rain" : "clear");
        final int ticksPerSecond = 20;
        int durationSeconds = Math.max(0, durationTicks / ticksPerSecond);
        String suffix = durationSeconds > 0 ? " " + durationSeconds : "";
        CommandResult r = ctx.commandExecute("weather " + w + suffix);
        if (r.successCount() == 0 && r.error() != null) {
            throw new AdapterException(r.error());
        }
        //?} else {
        /*boolean raining = "rain".equalsIgnoreCase(weather) || "thunder".equalsIgnoreCase(weather);
        boolean thundering = "thunder".equalsIgnoreCase(weather);
        level.setWeatherParameters(0, durationTicks, raining, thundering);
        *///?}
    }

    String levelGetDifficulty() {
        return ctx.requireServer().getWorldData().getDifficulty().getSerializedName();
    }

    void levelSetDifficulty(String difficulty) {
        // Difficulty cannot be set per-dimension; this maps to /difficulty.
        CommandResult r = ctx.commandExecute("difficulty " + difficulty);
        if (r.successCount() == 0 && r.error() != null) {
            throw new AdapterException(r.error());
        }
    }

    Vec3i levelGetSpawnPoint(String dimensionId) {
        ServerLevel level = ctx.requireLevel(dimensionId);
        BlockPos pos = level.getRespawnData().pos();
        return new Vec3i(pos.getX(), pos.getY(), pos.getZ());
    }

    void levelSetSpawnPoint(String dimensionId, Vec3i position) {
        ServerLevel level = ctx.requireLevel(dimensionId);
        BlockPos pos = new BlockPos(position.x(), position.y(), position.z());
        level.setRespawnData(LevelData.RespawnData.of(level.dimension(), pos, 0.0f, 0.0f));
    }

    void levelPlaySound(
            String dimensionId, Vec3d position, String soundId, float volume, float pitch) {
        CommandResult r =
                ctx.commandExecute(
                        String.format(
                                Locale.ROOT,
                                "playsound %s master @a %f %f %f %f %f",
                                soundId,
                                position.x(),
                                position.y(),
                                position.z(),
                                volume,
                                pitch));
        if (r.successCount() == 0 && r.error() != null) {
            throw new AdapterException(r.error());
        }
    }

    void levelSpawnParticle(
            String dimensionId,
            Vec3d position,
            String particleId,
            int count,
            Vec3d offset,
            double speed) {
        // /particle <name> <pos> <delta> <speed> <count>
        CommandResult r =
                ctx.commandExecute(
                        String.format(
                                Locale.ROOT,
                                "execute in %s run particle %s %f %f %f %f %f %f %f %d",
                                dimensionId,
                                particleId,
                                position.x(),
                                position.y(),
                                position.z(),
                                offset.x(),
                                offset.y(),
                                offset.z(),
                                speed,
                                count));
        if (r.successCount() == 0 && r.error() != null) {
            throw new AdapterException(r.error());
        }
    }

    void levelLightningStrike(String dimensionId, Vec3d position, boolean cosmetic) {
        String entityType = cosmetic ? "lightning_bolt {Cosmetic:1b}" : "lightning_bolt";
        CommandResult r =
                ctx.commandExecute(
                        String.format(
                                Locale.ROOT,
                                "execute in %s run summon %s %f %f %f",
                                dimensionId,
                                entityType,
                                position.x(),
                                position.y(),
                                position.z()));
        if (r.successCount() == 0 && r.error() != null) {
            throw new AdapterException(r.error());
        }
    }

    void levelCreateExplosion(
            String dimensionId, Vec3d position, float power, boolean fire, boolean breakBlocks) {
        ServerLevel level = ctx.requireLevel(dimensionId);
        Level.ExplosionInteraction mode =
                breakBlocks
                        ? Level.ExplosionInteraction.TNT
                        : Level.ExplosionInteraction.NONE;
        level.explode(
                null,
                position.x(),
                position.y(),
                position.z(),
                power,
                fire,
                mode);
    }

    Optional<GameRuleInfo> levelGetGameRule(String name) {
        GameRules rules = requireGameRules();
        GameRule<?> rule = findGameRule(rules, name);
        if (rule == null) {
            return Optional.empty();
        }
        String value = rules.getAsString(rule);
        return Optional.of(new GameRuleInfo(name, value, rule.category().toString()));
    }

    void levelSetGameRule(String name, String value) {
        // Dispatch via command to leverage vanilla parsing of the value.
        CommandResult r = ctx.commandExecute("gamerule " + name + " " + value);
        if (r.successCount() == 0 && r.error() != null) {
            throw new AdapterException(r.error());
        }
    }

    List<GameRuleInfo> levelListGameRules() {
        List<GameRuleInfo> out = new ArrayList<>();
        GameRules rules = requireGameRules();
        rules.availableRules()
                .forEach(
                        rule ->
                                out.add(
                                        new GameRuleInfo(
                                                rule.id(),
                                                rules.getAsString(rule),
                                                rule.category().toString())));
        out.sort((a, b) -> a.name().compareTo(b.name()));
        return out;
    }

    /**
     * Returns the server's GameRules. In 1.21.11 the canonical accessor is
     * {@code ((ServerLevelData) level.getLevelData()).getGameRules()}; the overworld's
     * GameRules instance is shared across all dimensions. In 26.1.x the accessor moved
     * to {@code MinecraftServer.getGameRules()} directly.
     */
    private GameRules requireGameRules() {
        MinecraftServer s = ctx.requireServer();
        //? if mc_gte_26 {
        return s.getGameRules();
        //?} else {
        /*ServerLevel overworld = s.overworld();
        if (overworld == null) {
            throw new AdapterException("Overworld is not loaded; cannot access game rules");
        }
        LevelData data = overworld.getLevelData();
        if (!(data instanceof ServerLevelData sld)) {
            throw new AdapterException(
                    "Overworld LevelData is not a ServerLevelData; cannot access game rules");
        }
        return sld.getGameRules();
        *///?}
    }

    private static GameRule<?> findGameRule(GameRules rules, String name) {
        return rules.availableRules()
                .filter(r -> r.id().equals(name))
                .findFirst()
                .orElse(null);
    }

    Optional<BiomeInfo> levelGetBiomeAt(String dimensionId, Vec3i position) {
        try {
            ServerLevel level = ctx.requireLevel(dimensionId);
            var biomeHolder = level.getBiome(new BlockPos(position.x(), position.y(), position.z()));
            Identifier id =
                    biomeHolder.unwrapKey().map(ResourceKey::identifier).orElse(null);
            var biome = biomeHolder.value();
            return Optional.of(
                    new BiomeInfo(
                            id == null ? "unknown" : id.toString(),
                            biome.getBaseTemperature(),
                            0.0f,
                            biome.hasPrecipitation()));
        } catch (AdapterException ae) {
            return Optional.empty();
        }
    }

    List<BiomeInfo> levelListBiomesInDimension(String dimensionId) {
        ServerLevel level = ctx.requireLevel(dimensionId);
        var biomeRegistry = level.registryAccess().lookupOrThrow(Registries.BIOME);
        List<BiomeInfo> out = new ArrayList<>();
        biomeRegistry.listElements()
                .forEach(
                        h ->
                                out.add(
                                        new BiomeInfo(
                                                h.key().identifier().toString(),
                                                h.value().getBaseTemperature(),
                                                0.0f,
                                                h.value().hasPrecipitation())));
        return out;
    }

    // =====================================================================
    // Structures — minimum viable subset for v0.1.0
    // =====================================================================

    boolean structureSaveFromWorld(String name, String dimensionId, BoundingBox box, boolean includeEntities) {
        String entFlag = includeEntities ? "true" : "false";
        return ctx.commandExecute(
                        String.format(
                                Locale.ROOT,
                                "execute in %s run place template %s %d %d %d none none 1.0 0 %s",
                                dimensionId, name, box.x1(), box.y1(), box.z1(), entFlag))
                .successCount() > 0;
    }

    boolean structureLoadToWorld(
            String name,
            String dimensionId,
            Vec3i origin,
            String rotation,
            String mirror,
            boolean includeEntities,
            float integrity) {
        String rotStr = rotation == null ? "none" : rotation;
        String mirStr = mirror == null ? "none" : mirror;
        return ctx.commandExecute(
                        String.format(
                                Locale.ROOT,
                                "execute in %s run place template %s %d %d %d %s %s %f 0",
                                dimensionId, name, origin.x(), origin.y(), origin.z(), rotStr, mirStr, integrity))
                .successCount() > 0;
    }

    List<StructureInfo> structureList() {
        StructureTemplateManager mgr = ctx.requireServer().getStructureManager();
        List<StructureInfo> out = new ArrayList<>();
        try (Stream<Identifier> ids = mgr.listTemplates()) {
            ids.sorted().forEach(id -> structureGetInfo(id.toString()).ifPresent(out::add));
        }
        return out;
    }

    Optional<StructureInfo> structureGetInfo(String name) {
        StructureTemplateManager mgr = ctx.requireServer().getStructureManager();
        Identifier id = AdapterContext.parseIdentifier(name);
        Optional<StructureTemplate> tpl = mgr.get(id);
        if (tpl.isEmpty()) {
            return Optional.empty();
        }
        var size = tpl.get().getSize();
        long fileSize = -1L;
        boolean onDisk = false;
        Path file = structureFilePath(id);
        if (file != null && Files.isRegularFile(file)) {
            onDisk = true;
            try {
                fileSize = Files.size(file);
            } catch (java.io.IOException e) {
                fileSize = -1L;
            }
        }
        return Optional.of(
                new StructureInfo(
                        id.toString(), size.getX(), size.getY(), size.getZ(), fileSize, onDisk, true));
    }

    boolean structureDelete(String name) {
        StructureTemplateManager mgr = ctx.requireServer().getStructureManager();
        Identifier id = AdapterContext.parseIdentifier(name);
        try {
            mgr.remove(id);
        } catch (Exception e) {
            return false;
        }
        Path file = structureFilePath(id);
        if (file != null) {
            try {
                Files.deleteIfExists(file);
            } catch (java.io.IOException e) {
                // best-effort
            }
        }
        return true;
    }

    /**
     * Returns the on-disk path for a generated structure under
     * {@code <world>/generated/<namespace>/structures/<path>.nbt}, or {@code null} when
     * the path cannot be derived.
     */
    private Path structureFilePath(Identifier id) {
        try {
            MinecraftServer s = ctx.requireServer();
            Path generated = s.getWorldPath(LevelResource.GENERATED_DIR);
            return generated.resolve(id.getNamespace()).resolve("structures").resolve(id.getPath() + ".nbt");
        } catch (Exception e) {
            return null;
        }
    }

    private Path structuresRoot() {
        return ctx.requireServer().getWorldPath(LevelResource.GENERATED_DIR);
    }

    List<String> structureFileList() {
        Path root = structuresRoot();
        if (!Files.isDirectory(root)) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        try (Stream<Path> nsDirs = Files.list(root)) {
            nsDirs.filter(Files::isDirectory).forEach(nsDir -> {
                Path structDir = nsDir.resolve("structures");
                if (!Files.isDirectory(structDir)) {
                    return;
                }
                try (Stream<Path> files = Files.walk(structDir)) {
                    files.filter(p -> p.toString().endsWith(".nbt") && Files.isRegularFile(p))
                            .forEach(p -> {
                                Path rel = structDir.relativize(p);
                                String pathPart = rel.toString().replace('\\', '/');
                                pathPart = pathPart.substring(0, pathPart.length() - 4); // strip .nbt
                                out.add(nsDir.getFileName().toString() + ":" + pathPart);
                            });
                } catch (java.io.IOException e) {
                    // ignore this namespace
                }
            });
        } catch (java.io.IOException e) {
            return out;
        }
        out.sort(String::compareTo);
        return out;
    }

    byte[] structureFileRead(String name) {
        Identifier id = AdapterContext.parseIdentifier(name);
        Path file = structureFilePath(id);
        if (file == null || !Files.isRegularFile(file)) {
            throw new AdapterException("Structure file not found: " + name);
        }
        try {
            return Files.readAllBytes(file);
        } catch (java.io.IOException e) {
            throw new AdapterException("Failed to read structure file " + name + ": " + e.getMessage(), e);
        }
    }

    boolean structureFileWrite(String name, byte[] payload) {
        Identifier id = AdapterContext.parseIdentifier(name);
        Path file = structureFilePath(id);
        if (file == null) {
            return false;
        }
        try {
            Files.createDirectories(file.getParent());
            Files.write(file, payload);
            return true;
        } catch (java.io.IOException e) {
            throw new AdapterException("Failed to write structure file " + name + ": " + e.getMessage(), e);
        }
    }

    boolean structureFileDelete(String name) {
        Identifier id = AdapterContext.parseIdentifier(name);
        Path file = structureFilePath(id);
        if (file == null) {
            return false;
        }
        try {
            return Files.deleteIfExists(file);
        } catch (java.io.IOException e) {
            return false;
        }
    }

    // =====================================================================
    // World border (vanilla)
    // =====================================================================

    /** Milliseconds per Minecraft game tick (20 TPS → 50 ms/tick). */
    private static final long MS_PER_TICK = 50L;

    WorldBorderInfo worldborderGet(String dimensionId) {
        ServerLevel level = ctx.requireLevel(dimensionId);
        var wb = level.getWorldBorder();
        long lerpTime = wb.getLerpTime();
        double lerpTarget = lerpTime > 0 ? wb.getLerpTarget() : -1.0;
        long lerpRemaining = lerpTime > 0
                ? Math.max(0L, lerpTime - System.currentTimeMillis()) / MS_PER_TICK
                : -1L;
        return new WorldBorderInfo(
                wb.getCenterX(),
                wb.getCenterZ(),
                wb.getSize(),
                wb.getWarningBlocks(),
                wb.getWarningTime(),
                wb.getDamagePerBlock(),
                wb.getSafeZone(),
                lerpTarget,
                lerpRemaining);
    }

    boolean worldborderSetSize(String dimensionId, double size, int timeSeconds) {
        return runInDim(
                dimensionId,
                "worldborder set " + size + (timeSeconds > 0 ? " " + timeSeconds : ""));
    }

    boolean worldborderAddSize(String dimensionId, double delta, int timeSeconds) {
        return runInDim(
                dimensionId,
                "worldborder add " + delta + (timeSeconds > 0 ? " " + timeSeconds : ""));
    }

    boolean worldborderSetCenter(String dimensionId, double x, double z) {
        return runInDim(dimensionId, "worldborder center " + x + " " + z);
    }

    boolean worldborderSetWarningBlocks(String dimensionId, int blocks) {
        return runInDim(dimensionId, "worldborder warning distance " + blocks);
    }

    boolean worldborderSetWarningTime(String dimensionId, int seconds) {
        return runInDim(dimensionId, "worldborder warning time " + seconds);
    }

    boolean worldborderSetDamageAmount(String dimensionId, double amount) {
        return runInDim(dimensionId, "worldborder damage amount " + amount);
    }

    boolean worldborderSetDamageBuffer(String dimensionId, double buffer) {
        return runInDim(dimensionId, "worldborder damage buffer " + buffer);
    }

    private boolean runInDim(String dimensionId, String command) {
        return ctx.commandExecute("execute in " + dimensionId + " run " + command).successCount() > 0;
    }
}
