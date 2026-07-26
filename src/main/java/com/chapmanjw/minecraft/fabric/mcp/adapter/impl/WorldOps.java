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
                            natural));
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
            // Which countdown is meaningful depends on what the weather currently IS. While a
            // clear spell is locked in, vanilla's advanceWeatherCycle pins rainTime to 1 or 2 every
            // tick (it only decrements clearWeatherTime), so reporting rainTime made a 12000-tick
            // clear lock read as "weatherRemainingTicks: 1" -- i.e. about to change, when in fact
            // nothing could change for ten minutes. Report the clear countdown when clear, and the
            // rain/thunder countdown otherwise. Not version-specific: both branches had this.
            boolean isClear = "clear".equals(weather);
            int weatherRemaining = 0;
            //? if mc_gte_26 {
            var weatherData = level.getWeatherData();
            weatherRemaining =
                    isClear ? weatherData.getClearWeatherTime() : weatherData.getRainTime();
            //?} else {
            /*LevelData data = level.getLevelData();
            if (data instanceof ServerLevelData sld) {
                weatherRemaining = isClear ? sld.getClearWeatherTime() : sld.getRainTime();
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
        /*// Day time is GLOBAL and owned by the overworld. Every other ServerLevel is backed by
        // DerivedLevelData, whose setDayTime(long) disassembles to a bare return -- so calling it
        // on the requested level silently did nothing for the nether and the end while this method
        // still returned normally and the tool reported "Set time of <dim> to <n>". Verified live
        // on 1.21.11: setting the nether to 1000 left the clock at 25251 and climbing.
        // Set it on the overworld instead, which is exactly what /time set does on the 26+ branch.
        // requireLevel above is still what validates the caller's dimension id.
        ctx.requireServer().overworld().setDayTime(timeOfDay);
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
        // /weather takes a Brigadier TimeArgument, and a BARE number there is a count of
        // TICKS -- only a "300s" style suffix means seconds. This used to divide the requested
        // ticks by 20 and pass the result bare, so every duration came out 20x too short:
        // asking for 6000 ticks of rain produced 300 ticks and the weather cleared in fifteen
        // seconds. Verified live on 26.1.1 -- "weather rain 300" left rainTime at ~215 while
        // "weather rain 6000" left it at 5908. Pass the ticks straight through.
        String suffix = durationTicks > 0 ? " " + durationTicks : "";
        CommandResult r = ctx.commandExecute("weather " + w + suffix);
        if (r.successCount() == 0 && r.error() != null) {
            throw new AdapterException(r.error());
        }
        //?} else {
        /*boolean raining = "rain".equalsIgnoreCase(weather) || "thunder".equalsIgnoreCase(weather);
        boolean thundering = "thunder".equalsIgnoreCase(weather);
        // Weather is GLOBAL and owned by the overworld -- the same DerivedLevelData trap as
        // setDayTime. setRaining/setRainTime/setThundering/setThunderTime are all bare returns on
        // a derived level, so this silently did nothing for the nether and the end while the tool
        // still reported "Weather set to rain for N ticks". Verified live on 1.21.11: asking for
        // rain in the nether left the overworld clear.
        //
        // setWeatherParameters(clearTime, weatherTime, raining, thundering) -- the duration goes
        // in clearTime when clearing and in weatherTime otherwise. Hardcoding clearTime to 0 meant
        // a "clear" request set no clear lock at all, leaving the next weather tick free to roll
        // straight back to rain; vanilla's WeatherCommand.setClear passes the duration there.
        ServerLevel clockOwner = ctx.requireServer().overworld();
        if (raining) {
            clockOwner.setWeatherParameters(0, durationTicks, true, thundering);
        } else {
            clockOwner.setWeatherParameters(durationTicks, 0, false, false);
        }
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
        // Vanilla /summon syntax is `summon <entity> [<pos>] [<nbt>]` -- NBT goes at the
        // end -- and `lightning_bolt` has no `Cosmetic` NBT field. The pre-fix command
        // form (`summon lightning_bolt {Cosmetic:1b} <x> <y> <z>`) parsed as a position
        // expression and produced "Expected double" at the NBT brace.
        //
        // Honour `cosmetic=true` via the direct API: spawning the bolt by hand lets us
        // call setVisualOnly(true) which is the only stable way to suppress damage and
        // fire without leaving a 'cosmetic' command-line knob (which vanilla doesn't
        // expose). When cosmetic=false we summon via /summon for parity with the
        // command surface.
        if (cosmetic) {
            ServerLevel level = ctx.requireLevel(dimensionId);
            // EntitySpawnReason has the same enum surface on 1.21.11 and 26.1.x.
            // Minecraft 26.2 moved the EntityType.<NAME> constants to a new
            // net.minecraft.world.entity.EntityTypes class; EntityType itself now declares
            // none. The entity class and the create(...) signature are unchanged.
            //? if mc_gte_26_2 {
            /*net.minecraft.world.entity.LightningBolt bolt =
                    net.minecraft.world.entity.EntityTypes.LIGHTNING_BOLT.create(
                            level, net.minecraft.world.entity.EntitySpawnReason.COMMAND);
            *///?} else {
            net.minecraft.world.entity.LightningBolt bolt =
                    net.minecraft.world.entity.EntityType.LIGHTNING_BOLT.create(
                            level, net.minecraft.world.entity.EntitySpawnReason.COMMAND);
            //?}
            if (bolt == null) {
                throw new AdapterException("Failed to construct lightning bolt entity");
            }
            // Modern Mojang mappings dropped Entity.moveTo(double, double, double) --
            // use snapTo, which has the same teleport-without-interpolation semantics.
            bolt.snapTo(position.x(), position.y(), position.z());
            bolt.setVisualOnly(true);
            level.addFreshEntity(bolt);
            return;
        }
        CommandResult r =
                ctx.commandExecute(
                        String.format(
                                Locale.ROOT,
                                "execute in %s run summon minecraft:lightning_bolt %f %f %f",
                                dimensionId,
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
        return Optional.of(new GameRuleInfo(name, value, gameRuleCategoryName(rule)));
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
                                                gameRuleCategoryName(rule))));
        out.sort((a, b) -> a.name().compareTo(b.name()));
        return out;
    }

    /**
     * Returns the namespaced id (or path) of a game rule's category as a stable
     * string. The legacy implementation called {@code category().toString()} which
     * leaked the intermediary record class name under Fabric Loader runtime mappings
     * (e.g. {@code class_5198[id=minecraft:updates]}). {@code GameRuleCategory} is a
     * record holding an {@link Identifier}; the identifier {@code toString()} is
     * stable across mapping layers ({@code minecraft:updates}).
     */
    private static String gameRuleCategoryName(GameRule<?> rule) {
        try {
            var category = rule.category();
            if (category == null) {
                return "";
            }
            Identifier id = category.id();
            if (id == null) {
                return "";
            }
            return id.toString();
        } catch (Throwable t) {
            // Belt and braces — if category() ever returns null on a future version
            // we still want a stable string rather than the toString leak.
            return "";
        }
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
            BlockPos at = new BlockPos(position.x(), position.y(), position.z());
            return Optional.of(describeBiome(id, biome, at, level.getSeaLevel()));
        } catch (AdapterException ae) {
            return Optional.empty();
        }
    }

    /**
     * Builds a {@link BiomeInfo}, optionally resolved against a specific block.
     *
     * <p>With a position we can answer the questions that actually depend on where you are
     * standing: what precipitation falls here (temperature drops with altitude, so one biome
     * snows on a peak and rains in the valley) and what colour the grass takes (swamp and dark
     * forest apply a modifier). Without one -- the dimension listing -- those two are left null
     * rather than being invented from an arbitrary reference point.
     *
     * <p>downfall comes from the access-widened climateSettings record. It is NOT a weather
     * value: vanilla reads it only as the second axis into the grass/foliage colour gradient.
     */
    private static BiomeInfo describeBiome(
            Identifier id, net.minecraft.world.level.biome.Biome biome, BlockPos at, int seaLevel) {
        String precipitation = null;
        Integer grassColor = null;
        if (at != null) {
            precipitation = biome.getPrecipitationAt(at, seaLevel).getSerializedName();
            grassColor = biome.getGrassColor(at.getX(), at.getZ());
        }
        var effects = biome.getSpecialEffects();
        return new BiomeInfo(
                id == null ? "unknown" : id.toString(),
                biome.getBaseTemperature(),
                biome.climateSettings.downfall(),
                biome.hasPrecipitation(),
                precipitation,
                grassColor,
                biome.getFoliageColor(),
                biome.getDryFoliageColor(),
                effects.waterColor(),
                effects.grassColorModifier().getSerializedName());
    }

    List<BiomeInfo> levelListBiomesInDimension(String dimensionId) {
        ServerLevel level = ctx.requireLevel(dimensionId);
        var biomeRegistry = level.registryAccess().lookupOrThrow(Registries.BIOME);
        List<BiomeInfo> out = new ArrayList<>();
        biomeRegistry.listElements()
                .forEach(
                        h ->
                                out.add(
                                        describeBiome(
                                                h.key().identifier(), h.value(), null, 0)));
        return out;
    }

    // =====================================================================
    // Structures — minimum viable subset for v0.1.0
    // =====================================================================

    boolean structureSaveFromWorld(String name, String dimensionId, BoundingBox box, boolean includeEntities) {
        // The /structure save command was removed in 1.20.5 -- /place template only LOADS.
        // Capture world contents via StructureTemplate.fillFromWorld and persist via direct
        // NBT IO. StructureTemplateManager.save() returned false for our in-memory templates
        // (it expects a different lifecycle), so write the file ourselves -- matches what
        // structureFileWrite already does.
        try {
            ServerLevel level = ctx.requireLevel(dimensionId);
            StructureTemplateManager mgr = ctx.requireServer().getStructureManager();
            Identifier id = AdapterContext.parseIdentifier(name);
            StructureTemplate template = mgr.getOrCreate(id);
            BlockPos origin = new BlockPos(box.x1(), box.y1(), box.z1());
            net.minecraft.core.Vec3i size =
                    new net.minecraft.core.Vec3i(
                            box.x2() - box.x1() + 1,
                            box.y2() - box.y1() + 1,
                            box.z2() - box.z1() + 1);
            // The 5th param is a List of blocks to ignore -- vanilla streams it
            // unconditionally, so null NPEs. Pass an empty list to capture everything.
            template.fillFromWorld(level, origin, size, includeEntities, java.util.Collections.emptyList());
            template.setAuthor("minecraft-fabric-mcp");
            net.minecraft.nbt.CompoundTag tag =
                    template.save(new net.minecraft.nbt.CompoundTag());
            Path file = structureFilePath(id);
            if (file == null) {
                return false;
            }
            Files.createDirectories(file.getParent());
            try (java.io.OutputStream os = Files.newOutputStream(file)) {
                net.minecraft.nbt.NbtIo.writeCompressed(tag, os);
            }
            return true;
        } catch (AdapterException ae) {
            return false;
        } catch (Exception e) {
            AdapterContext.LOGGER.warn("structureSaveFromWorld failed for " + name, e);
            return false;
        }
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
        // listTemplates() only enumerates the in-memory / resource-manager templates (the
        // minecraft: namespace and loaded datapacks); it misses structures saved on disk under
        // generated/<namespace>/structures/ in custom namespaces (e.g. mcb:*) that have not been
        // pulled into memory yet. Merge the in-memory ids with an on-disk scan (mirroring
        // structureFileList) so a freshly structure_save_from_world'd structure shows up here.
        // De-duplicate via a LinkedHashSet keyed on the identifier string. structureGetInfo()
        // resolves each id through mgr.get(), which lazily loads custom-namespace templates from
        // disk, so size/onDisk/inMemory are populated correctly for both sources.
        java.util.LinkedHashSet<String> ids = new java.util.LinkedHashSet<>();
        try (Stream<Identifier> templates = mgr.listTemplates()) {
            templates.map(Identifier::toString).forEach(ids::add);
        }
        ids.addAll(structureFileList());
        List<StructureInfo> out = new ArrayList<>();
        ids.stream().sorted().forEach(id -> structureGetInfo(id).ifPresent(out::add));
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
        // The underlying StructureTemplateManager.remove returns void and silently no-ops on
        // unknown ids, so callers were getting "deleted" even when no structure existed.
        // Check both the in-memory cache AND the on-disk file before reporting success.
        boolean existedInMemory = mgr.get(id).isPresent();
        Path file = structureFilePath(id);
        boolean existedOnDisk = file != null && Files.isRegularFile(file);
        if (!existedInMemory && !existedOnDisk) {
            return false;
        }
        try {
            mgr.remove(id);
        } catch (Exception e) {
            return false;
        }
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
        // Idempotent: returns true whether the file was deleted now or was already gone.
        // structure_delete also removes the on-disk copy, so a subsequent file_delete
        // would otherwise look like a failure.
        Identifier id = AdapterContext.parseIdentifier(name);
        Path file = structureFilePath(id);
        if (file == null) {
            return false;
        }
        try {
            Files.deleteIfExists(file);
            return true;
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

    /**
     * Formats a double for a Brigadier command argument.
     *
     * <p>Plain string concatenation cannot be used here. {@code Double.toString} switches to
     * scientific notation at 1e7 and below 1e-3, so a size of 59999968 was concatenated as
     * "5.9999968E7" and the command parser rejected the whole command. The tool then reported
     * "failed" with no indication why. That bound is not academic: the DEFAULT world border is
     * 59999968, so every setter here could shrink a border and never restore it. Verified live on
     * 1.21.11 -- 9999999 succeeded and 10000000 failed, exactly the notation threshold.
     *
     * <p>{@code toPlainString} never emits an exponent. stripTrailingZeros first keeps whole
     * numbers looking whole ("1000" rather than "1000.0"); on its own it would produce "1E+3",
     * which is why toPlainString is applied after it and not instead of it.
     */
    private static String cmdNum(double v) {
        return java.math.BigDecimal.valueOf(v).stripTrailingZeros().toPlainString();
    }

    boolean worldborderSetSize(String dimensionId, double size, int timeSeconds) {
        return runInDim(
                dimensionId,
                "worldborder set " + cmdNum(size) + (timeSeconds > 0 ? " " + timeSeconds : ""));
    }

    boolean worldborderAddSize(String dimensionId, double delta, int timeSeconds) {
        return runInDim(
                dimensionId,
                "worldborder add " + cmdNum(delta) + (timeSeconds > 0 ? " " + timeSeconds : ""));
    }

    boolean worldborderSetCenter(String dimensionId, double x, double z) {
        return runInDim(dimensionId, "worldborder center " + cmdNum(x) + " " + cmdNum(z));
    }

    boolean worldborderSetWarningBlocks(String dimensionId, int blocks) {
        return runInDim(dimensionId, "worldborder warning distance " + blocks);
    }

    boolean worldborderSetWarningTime(String dimensionId, int seconds) {
        return runInDim(dimensionId, "worldborder warning time " + seconds);
    }

    boolean worldborderSetDamageAmount(String dimensionId, double amount) {
        return runInDim(dimensionId, "worldborder damage amount " + cmdNum(amount));
    }

    boolean worldborderSetDamageBuffer(String dimensionId, double buffer) {
        return runInDim(dimensionId, "worldborder damage buffer " + cmdNum(buffer));
    }

    private boolean runInDim(String dimensionId, String command) {
        // All worldborder setters and the datapack enable/disable flow are void
        // setters in vanilla -- they return successCount=0 on the happy path when the
        // value is already at the target. Use commandOk so a no-op set isn't reported
        // as a failure.
        return AdapterContext.commandOk(
                ctx.commandExecute("execute in " + dimensionId + " run " + command));
    }

    // ----- points of interest -------------------------------------------------

    /**
     * Queries the level's {@link net.minecraft.world.entity.ai.village.poi.PoiManager} directly.
     *
     * <p>Deliberately NOT built on net.minecraft.util.debug.DebugSubscriptions: that is a broadcast
     * mechanism (broadcastToAll / hasAnySubscriberFor) which pushes packets to subscribed clients
     * and exposes nothing queryable, so it would need a fake subscriber and would not work headless.
     * PoiManager is a plain server-side query API, and getInRange is identical on every supported
     * target, so this needs no version gate.
     */
    java.util.List<com.chapmanjw.minecraft.fabric.mcp.adapter.dto.PoiInfo> poiQuery(
            String dimensionId, int x, int y, int z, int radius, String typeFilter) {
        ServerLevel level = ctx.requireLevel(dimensionId);
        net.minecraft.core.BlockPos centre = new net.minecraft.core.BlockPos(x, y, z);

        java.util.function.Predicate<
                        net.minecraft.core.Holder<
                                net.minecraft.world.entity.ai.village.poi.PoiType>>
                typePredicate;
        if (typeFilter == null || typeFilter.isBlank()) {
            typePredicate = holder -> true;
        } else {
            Identifier wanted = AdapterContext.parseIdentifier(typeFilter);
            typePredicate =
                    holder ->
                            holder.unwrapKey()
                                    .map(k -> k.identifier().equals(wanted))
                                    .orElse(false);
        }

        java.util.List<com.chapmanjw.minecraft.fabric.mcp.adapter.dto.PoiInfo> out =
                new java.util.ArrayList<>();
        level.getPoiManager()
                .getInRange(
                        typePredicate,
                        centre,
                        radius,
                        net.minecraft.world.entity.ai.village.poi.PoiManager.Occupancy.ANY)
                .forEach(
                        record -> {
                            String type =
                                    record.getPoiType()
                                            .unwrapKey()
                                            .map(k -> k.identifier().toString())
                                            .orElse("unknown");
                            net.minecraft.core.BlockPos p = record.getPos();
                            out.add(
                                    new com.chapmanjw.minecraft.fabric.mcp.adapter.dto.PoiInfo(
                                            type,
                                            new com.chapmanjw.minecraft.fabric.mcp.adapter.dto.Vec3i(
                                                    p.getX(), p.getY(), p.getZ()),
                                            record.isOccupied(),
                                            record.getFreeTickets()));
                        });
        // PoiManager.getInRange streams out of its internal per-chunk storage, whose iteration
        // order is not stable: two identical queries against an unchanged world returned the same
        // two POIs in opposite orders (home,cartographer then cartographer,home), observed live on
        // 26.1.1. A caller diffing successive queries would read that as the village changing when
        // nothing had. Sort on position, then type, so the result is reproducible. Same reasoning
        // as the block-family variant ordering.
        out.sort(
                java.util.Comparator
                        .<com.chapmanjw.minecraft.fabric.mcp.adapter.dto.PoiInfo>comparingInt(
                                p -> p.pos().x())
                        .thenComparingInt(p -> p.pos().y())
                        .thenComparingInt(p -> p.pos().z())
                        .thenComparing(
                                com.chapmanjw.minecraft.fabric.mcp.adapter.dto.PoiInfo::type));
        return out;
    }
}
