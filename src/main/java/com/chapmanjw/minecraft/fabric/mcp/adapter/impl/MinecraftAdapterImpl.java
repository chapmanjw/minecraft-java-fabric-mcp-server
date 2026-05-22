package com.chapmanjw.minecraft.fabric.mcp.adapter.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.SharedConstants;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

import com.chapmanjw.minecraft.fabric.mcp.adapter.AdapterException;
import com.chapmanjw.minecraft.fabric.mcp.adapter.MinecraftAdapter;
import com.chapmanjw.minecraft.fabric.mcp.adapter.dto.AdvancementProgressInfo;
import com.chapmanjw.minecraft.fabric.mcp.adapter.dto.BiomeInfo;
import com.chapmanjw.minecraft.fabric.mcp.adapter.dto.BlockSpec;
import com.chapmanjw.minecraft.fabric.mcp.adapter.dto.BlockStateInfo;
import com.chapmanjw.minecraft.fabric.mcp.adapter.dto.BossbarInfo;
import com.chapmanjw.minecraft.fabric.mcp.adapter.dto.BoundingBox;
import com.chapmanjw.minecraft.fabric.mcp.adapter.dto.CommandResult;
import com.chapmanjw.minecraft.fabric.mcp.adapter.dto.CompostableInfo;
import com.chapmanjw.minecraft.fabric.mcp.adapter.dto.DatapackInfo;
import com.chapmanjw.minecraft.fabric.mcp.adapter.dto.DimensionInfo;
import com.chapmanjw.minecraft.fabric.mcp.adapter.dto.EntityInfo;
import com.chapmanjw.minecraft.fabric.mcp.adapter.dto.FlammableBlockInfo;
import com.chapmanjw.minecraft.fabric.mcp.adapter.dto.FluidStackInfo;
import com.chapmanjw.minecraft.fabric.mcp.adapter.dto.GameRuleInfo;
import com.chapmanjw.minecraft.fabric.mcp.adapter.dto.InventoryInfo;
import com.chapmanjw.minecraft.fabric.mcp.adapter.dto.ItemSpec;
import com.chapmanjw.minecraft.fabric.mcp.adapter.dto.ItemStackInfo;
import com.chapmanjw.minecraft.fabric.mcp.adapter.dto.LevelInfo;
import com.chapmanjw.minecraft.fabric.mcp.adapter.dto.LootDropInfo;
import com.chapmanjw.minecraft.fabric.mcp.adapter.dto.PlayerInfo;
import com.chapmanjw.minecraft.fabric.mcp.adapter.dto.RecipeInfo;
import com.chapmanjw.minecraft.fabric.mcp.adapter.dto.ScheduledFunctionInfo;
import com.chapmanjw.minecraft.fabric.mcp.adapter.dto.ScoreboardObjectiveInfo;
import com.chapmanjw.minecraft.fabric.mcp.adapter.dto.ServerStatus;
import com.chapmanjw.minecraft.fabric.mcp.adapter.dto.StatusEffectInfo;
import com.chapmanjw.minecraft.fabric.mcp.adapter.dto.StructureInfo;
import com.chapmanjw.minecraft.fabric.mcp.adapter.dto.TeamInfo;
import com.chapmanjw.minecraft.fabric.mcp.adapter.dto.Vec3d;
import com.chapmanjw.minecraft.fabric.mcp.adapter.dto.Vec3i;
import com.chapmanjw.minecraft.fabric.mcp.adapter.dto.WorldBorderInfo;

/**
 * Production {@link MinecraftAdapter} implementation, organised as a thin facade
 * over domain-specific helper classes ({@link BlockOps}, {@link EntityOps}, etc.)
 * that share an {@link AdapterContext}.
 *
 * <p>The implementation is intentionally pragmatic: for read-heavy operations it
 * calls vanilla Minecraft API directly, and for write-heavy operations it constructs
 * the equivalent vanilla command string and dispatches through Brigadier. The
 * command route is more verbose per call but vastly more stable across mapping and
 * minor-version changes — the cost of one extra string-parse step is negligible
 * compared to the mod's HTTP round trip.
 *
 * <p>Where the API surface differs between 1.21.11 and 26.1.x, Stonecutter
 * {@code //? if mc_gte_26} blocks switch between the two. The 26.1.x form
 * appears uncommented; the 1.21.11 alternate is in a commented-out block below
 * and the Stonecutter preprocessor swaps the comment markers at build time.
 * The {@code mc_gte_26} constant is wired up in mod-build.gradle.kts via the
 * Stonecutter extension's {@code constants} container.
 *
 * <p>Methods that have not yet been mapped to direct API or commands raise
 * {@link AdapterException} with an actionable message so the tool layer can surface
 * a structured "not implemented for this target" error.
 */
public final class MinecraftAdapterImpl implements MinecraftAdapter {

    private final AdapterContext ctx;
    private final BlockOps blockOps;
    private final EntityOps entityOps;
    private final PlayerOps playerOps;
    private final WorldOps worldOps;
    private final GameplayOps gameplayOps;
    private final RegistryOps registryOps;
    private final DataOps dataOps;
    private final long startedAtMillis = System.currentTimeMillis();

    public MinecraftAdapterImpl(String modVersion) {
        this.ctx = new AdapterContext(modVersion);
        this.blockOps = new BlockOps(ctx);
        this.entityOps = new EntityOps(ctx);
        this.playerOps = new PlayerOps(ctx);
        this.worldOps = new WorldOps(ctx);
        this.gameplayOps = new GameplayOps(ctx);
        this.registryOps = new RegistryOps(ctx);
        this.dataOps = new DataOps(ctx);
    }

    // =====================================================================
    // Lifecycle
    // =====================================================================

    @Override
    public void bind(Object minecraftServer) {
        if (!(minecraftServer instanceof MinecraftServer ms)) {
            throw new AdapterException(
                    "bind() expects a MinecraftServer, got "
                            + (minecraftServer == null ? "null" : minecraftServer.getClass().getName()));
        }
        ctx.setServer(ms);
    }

    @Override
    public void unbind() {
        ctx.clearServer();
    }

    @Override
    public boolean isBound() {
        return ctx.isBound();
    }

    // =====================================================================
    // Server
    // =====================================================================

    @Override
    public ServerStatus serverGetStatus() {
        MinecraftServer s = ctx.requireServer();
        List<String> dims = new ArrayList<>();
        for (ServerLevel level : s.getAllLevels()) {
            dims.add(level.dimension().identifier().toString());
        }
        double avgTickTimeNanos = computeAverageTickTimeNanos(s);
        double avgMspt = avgTickTimeNanos / 1_000_000.0;
        double avgTps = avgMspt > 0 ? Math.min(20.0, 1000.0 / avgMspt) : 20.0;
        return new ServerStatus(
                SharedConstants.getCurrentVersion().name(),
                FabricLoader.getInstance()
                        .getModContainer("fabricloader")
                        .map(m -> m.getMetadata().getVersion().getFriendlyString())
                        .orElse("unknown"),
                ctx.modVersion(),
                s.getMotd(),
                System.currentTimeMillis() - startedAtMillis,
                avgTps,
                avgMspt,
                s.getPlayerCount(),
                s.getMaxPlayers(),
                dims,
                // registeredToolCount: the adapter has no view of the tool registry; the
                // server_get_status tool overrides this with the real count.
                -1);
    }

    /**
     * Reads the average tick time. Public accessor changed names across versions; this
     * helper uses the stable {@code tickTimesNanos} array when available, falling back
     * to the {@code averageTickTime} field via reflection if direct access is removed.
     */
    private static double computeAverageTickTimeNanos(MinecraftServer s) {
        // 1.21+ exposes long[] getTickTimesNanos() — average across the ring buffer.
        try {
            long[] times = s.getTickTimesNanos();
            if (times == null || times.length == 0) {
                return 0.0;
            }
            long sum = 0L;
            for (long t : times) {
                sum += t;
            }
            return (double) sum / times.length;
        } catch (LinkageError e) {
            // Older accessor returned float averageTickTime (in ms). Fall back via reflection.
            try {
                var field = MinecraftServer.class.getDeclaredField("averageTickTime");
                field.setAccessible(true);
                return ((float) field.get(s)) * 1_000_000.0;
            } catch (ReflectiveOperationException nested) {
                return 0.0;
            }
        }
    }

    @Override
    public String serverGetMotd() {
        return ctx.requireServer().getMotd();
    }

    @Override
    public void serverSetMotd(String motd) {
        ctx.requireServer().setMotd(motd);
    }

    @Override
    public void serverSaveAllWorlds(boolean flush) {
        ctx.requireServer().saveAllChunks(false, flush, true);
    }

    @Override
    public void serverReloadResources() {
        MinecraftServer s = ctx.requireServer();
        // Async reload — we don't block; the command dispatcher would return immediately too.
        try {
            s.reloadResources(s.getPackRepository().getSelectedIds());
        } catch (Exception e) {
            throw new AdapterException("Failed to reload resources: " + e.getMessage(), e);
        }
    }

    // =====================================================================
    // Level (per-dimension) — delegated to WorldOps
    // =====================================================================

    @Override
    public List<String> levelListDimensions() {
        return worldOps.levelListDimensions();
    }

    @Override
    public Optional<DimensionInfo> levelGetDimensionInfo(String dimensionId) {
        return worldOps.levelGetDimensionInfo(dimensionId);
    }

    @Override
    public Optional<LevelInfo> levelGetInfo(String dimensionId) {
        return worldOps.levelGetInfo(dimensionId);
    }

    @Override
    public long levelGetTime(String dimensionId) {
        return worldOps.levelGetTime(dimensionId);
    }

    @Override
    public void levelSetTime(String dimensionId, long timeOfDay) {
        worldOps.levelSetTime(dimensionId, timeOfDay);
    }

    @Override
    public String levelGetWeather(String dimensionId) {
        return worldOps.levelGetWeather(dimensionId);
    }

    @Override
    public void levelSetWeather(String dimensionId, String weather, int durationTicks) {
        worldOps.levelSetWeather(dimensionId, weather, durationTicks);
    }

    @Override
    public String levelGetDifficulty() {
        return worldOps.levelGetDifficulty();
    }

    @Override
    public void levelSetDifficulty(String difficulty) {
        worldOps.levelSetDifficulty(difficulty);
    }

    @Override
    public Vec3i levelGetSpawnPoint(String dimensionId) {
        return worldOps.levelGetSpawnPoint(dimensionId);
    }

    @Override
    public void levelSetSpawnPoint(String dimensionId, Vec3i position) {
        worldOps.levelSetSpawnPoint(dimensionId, position);
    }

    @Override
    public void levelPlaySound(
            String dimensionId, Vec3d position, String soundId, float volume, float pitch) {
        worldOps.levelPlaySound(dimensionId, position, soundId, volume, pitch);
    }

    @Override
    public void levelSpawnParticle(
            String dimensionId,
            Vec3d position,
            String particleId,
            int count,
            Vec3d offset,
            double speed) {
        worldOps.levelSpawnParticle(dimensionId, position, particleId, count, offset, speed);
    }

    @Override
    public void levelLightningStrike(String dimensionId, Vec3d position, boolean cosmetic) {
        worldOps.levelLightningStrike(dimensionId, position, cosmetic);
    }

    @Override
    public void levelCreateExplosion(
            String dimensionId, Vec3d position, float power, boolean fire, boolean breakBlocks) {
        worldOps.levelCreateExplosion(dimensionId, position, power, fire, breakBlocks);
    }

    @Override
    public Optional<GameRuleInfo> levelGetGameRule(String name) {
        return worldOps.levelGetGameRule(name);
    }

    @Override
    public void levelSetGameRule(String name, String value) {
        worldOps.levelSetGameRule(name, value);
    }

    @Override
    public List<GameRuleInfo> levelListGameRules() {
        return worldOps.levelListGameRules();
    }

    @Override
    public Optional<BiomeInfo> levelGetBiomeAt(String dimensionId, Vec3i position) {
        return worldOps.levelGetBiomeAt(dimensionId, position);
    }

    @Override
    public List<BiomeInfo> levelListBiomesInDimension(String dimensionId) {
        return worldOps.levelListBiomesInDimension(dimensionId);
    }

    // =====================================================================
    // Block / BlockState — delegated to BlockOps
    // =====================================================================

    @Override
    public Optional<BlockStateInfo> blockGetState(String dimensionId, Vec3i position) {
        return blockOps.blockGetState(dimensionId, position);
    }

    @Override
    public boolean blockSetState(String dimensionId, Vec3i position, BlockSpec spec, int updateFlags) {
        return blockOps.blockSetState(dimensionId, position, spec, updateFlags);
    }

    @Override
    public long blockFillRegion(String dimensionId, BoundingBox box, BlockSpec spec, FillMode mode) {
        return blockOps.blockFillRegion(dimensionId, box, spec, mode);
    }

    @Override
    public long blockCloneRegion(
            String sourceDimension,
            BoundingBox source,
            String destDimension,
            Vec3i destinationOrigin,
            CloneMode mode) {
        return blockOps.blockCloneRegion(sourceDimension, source, destDimension, destinationOrigin, mode);
    }

    @Override
    public long blockReplaceInRegion(
            String dimensionId, BoundingBox box, String targetBlockId, BlockSpec replacement) {
        return blockOps.blockReplaceInRegion(dimensionId, box, targetBlockId, replacement);
    }

    @Override
    public int blockGetTopY(String dimensionId, int x, int z) {
        return blockOps.blockGetTopY(dimensionId, x, z);
    }

    @Override
    public List<BlockMatch> blockScanRegion(
            String dimensionId, BoundingBox box, String matchBlockId, int limit) {
        return blockOps.blockScanRegion(dimensionId, box, matchBlockId, limit);
    }

    @Override
    public ScanSummary blockScanSummary(String dimensionId, BoundingBox box) {
        return blockOps.blockScanSummary(dimensionId, box);
    }

    @Override
    public Optional<MapColorInfo> blockGetMapColor(String dimensionId, Vec3i position) {
        return blockOps.blockGetMapColor(dimensionId, position);
    }

    @Override
    public byte[] worldRenderRegion(String dimensionId, BoundingBox box, String view, int step, int scale) {
        return blockOps.worldRenderRegion(dimensionId, box, view, step, scale);
    }

    // =====================================================================
    // BlockEntity — delegated to BlockOps
    // =====================================================================

    @Override
    public Optional<String> blockEntityGetNbt(String dimensionId, Vec3i position) {
        return blockOps.blockEntityGetNbt(dimensionId, position);
    }

    @Override
    public boolean blockEntitySetNbt(String dimensionId, Vec3i position, String snbt) {
        return blockOps.blockEntitySetNbt(dimensionId, position, snbt);
    }

    @Override
    public boolean blockEntityClearInventory(String dimensionId, Vec3i position) {
        return blockOps.blockEntityClearInventory(dimensionId, position);
    }

    // =====================================================================
    // Entity (non-player) — delegated to EntityOps
    // =====================================================================

    @Override
    public Optional<UUID> entitySummon(String dimensionId, String entityType, Vec3d position, String snbt) {
        return entityOps.entitySummon(dimensionId, entityType, position, snbt);
    }

    @Override
    public Optional<EntityInfo> entityGet(UUID uuid) {
        return entityOps.entityGet(uuid);
    }

    @Override
    public Optional<EntityInfo> entityGetByNetworkId(String dimensionId, int networkId) {
        return entityOps.entityGetByNetworkId(dimensionId, networkId);
    }

    @Override
    public List<EntityInfo> entityQuery(String dimensionId, String selector, int limit) {
        return entityOps.entityQuery(dimensionId, selector, limit);
    }

    @Override
    public Optional<Map<String, String>> entityGetComponents(UUID uuid) {
        return entityOps.entityGetComponents(uuid);
    }

    @Override
    public Optional<String> entityGetNbt(UUID uuid) {
        return entityOps.entityGetNbt(uuid);
    }

    @Override
    public boolean entitySetNbt(UUID uuid, String snbt) {
        return entityOps.entitySetNbt(uuid, snbt);
    }

    @Override
    public boolean entityTeleport(UUID uuid, String dimensionId, Vec3d position, Vec3d facingTarget) {
        return entityOps.entityTeleport(uuid, dimensionId, position, facingTarget);
    }

    @Override
    public boolean entityApplyDamage(UUID uuid, float amount, String damageType) {
        return entityOps.entityApplyDamage(uuid, amount, damageType);
    }

    @Override
    public boolean entitySetVelocity(UUID uuid, Vec3d velocity) {
        return entityOps.entitySetVelocity(uuid, velocity);
    }

    @Override
    public boolean entityApplyEffect(
            UUID uuid,
            String effect,
            int durationTicks,
            int amplifier,
            boolean ambient,
            boolean showParticles,
            boolean showIcon) {
        return entityOps.entityApplyEffect(uuid, effect, durationTicks, amplifier, ambient, showParticles, showIcon);
    }

    @Override
    public boolean entityRemoveEffect(UUID uuid, String effect) {
        return entityOps.entityRemoveEffect(uuid, effect);
    }

    @Override
    public List<StatusEffectInfo> entityGetEffects(UUID uuid) {
        return entityOps.entityGetEffects(uuid);
    }

    @Override
    public boolean entityKill(UUID uuid) {
        return entityOps.entityKill(uuid);
    }

    @Override
    public boolean entityDespawn(UUID uuid) {
        return entityOps.entityDespawn(uuid);
    }

    @Override
    public boolean entityAddTag(UUID uuid, String tag) {
        return entityOps.entityAddTag(uuid, tag);
    }

    @Override
    public boolean entityRemoveTag(UUID uuid, String tag) {
        return entityOps.entityRemoveTag(uuid, tag);
    }

    @Override
    public List<String> entityListTags(UUID uuid) {
        return entityOps.entityListTags(uuid);
    }

    // =====================================================================
    // Player — delegated to PlayerOps
    // =====================================================================

    @Override
    public List<PlayerInfo> playerListOnline() {
        return playerOps.playerListOnline();
    }

    @Override
    public Optional<PlayerInfo> playerGetInfo(UUID uuid) {
        return playerOps.playerGetInfo(uuid);
    }

    @Override
    public Optional<InventoryInfo> playerGetInventory(UUID uuid) {
        return playerOps.playerGetInventory(uuid);
    }

    @Override
    public boolean playerGiveItem(UUID uuid, ItemSpec item) {
        return playerOps.playerGiveItem(uuid, item);
    }

    @Override
    public boolean playerClearInventorySlot(UUID uuid, int slot) {
        return playerOps.playerClearInventorySlot(uuid, slot);
    }

    @Override
    public boolean playerClearAllInventory(UUID uuid) {
        return playerOps.playerClearAllInventory(uuid);
    }

    @Override
    public boolean playerSetGamemode(UUID uuid, String gameMode) {
        return playerOps.playerSetGamemode(uuid, gameMode);
    }

    @Override
    public boolean playerKick(UUID uuid, String reason) {
        return playerOps.playerKick(uuid, reason);
    }

    @Override
    public boolean playerSendMessage(UUID uuid, String message) {
        return playerOps.playerSendMessage(uuid, message);
    }

    @Override
    public boolean playerSendActionbar(UUID uuid, String message) {
        return playerOps.playerSendActionbar(uuid, message);
    }

    @Override
    public boolean playerSendTitle(
            UUID uuid,
            String title,
            String subtitle,
            int fadeInTicks,
            int stayTicks,
            int fadeOutTicks) {
        return playerOps.playerSendTitle(uuid, title, subtitle, fadeInTicks, stayTicks, fadeOutTicks);
    }

    @Override
    public boolean playerPlaySound(UUID uuid, String soundId, float volume, float pitch) {
        return playerOps.playerPlaySound(uuid, soundId, volume, pitch);
    }

    @Override
    public boolean playerSetSpawnPoint(UUID uuid, String dimensionId, Vec3i position) {
        return playerOps.playerSetSpawnPoint(uuid, dimensionId, position);
    }

    @Override
    public boolean playerGrantXp(UUID uuid, int amount) {
        return playerOps.playerGrantXp(uuid, amount);
    }

    @Override
    public boolean playerSetXpLevel(UUID uuid, int level) {
        return playerOps.playerSetXpLevel(uuid, level);
    }

    @Override
    public boolean playerSetCamera(UUID viewer, UUID target) {
        return playerOps.playerSetCamera(viewer, target);
    }

    // =====================================================================
    // Inventory / Container — delegated to PlayerOps
    // =====================================================================

    @Override
    public Optional<InventoryInfo> inventoryGet(String target) {
        return playerOps.inventoryGet(target);
    }

    @Override
    public boolean inventorySetSlot(String target, int slot, ItemSpec item) {
        return playerOps.inventorySetSlot(target, slot, item);
    }

    @Override
    public boolean inventoryClearSlot(String target, int slot) {
        return playerOps.inventoryClearSlot(target, slot);
    }

    @Override
    public boolean inventorySwapSlots(String target, int slotA, int slotB) {
        return playerOps.inventorySwapSlots(target, slotA, slotB);
    }

    @Override
    public int inventoryCountItems(String target, String itemId) {
        return playerOps.inventoryCountItems(target, itemId);
    }

    // =====================================================================
    // ItemStack — delegated to PlayerOps
    // =====================================================================

    @Override
    public Optional<ItemStackInfo> itemStackDescribe(ItemSpec spec) {
        return playerOps.itemStackDescribe(spec);
    }

    @Override
    public boolean itemStackDropAt(String dimensionId, Vec3d position, ItemSpec spec) {
        return playerOps.itemStackDropAt(dimensionId, position, spec);
    }

    // =====================================================================
    // Command — delegated to GameplayOps
    // =====================================================================

    @Override
    public CommandResult commandExecute(String command) {
        return gameplayOps.commandExecute(command);
    }

    @Override
    public CommandResult commandExecuteAs(String command, UUID actor) {
        return gameplayOps.commandExecuteAs(command, actor);
    }

    // =====================================================================
    // Scoreboard — delegated to GameplayOps
    // =====================================================================

    @Override
    public List<ScoreboardObjectiveInfo> scoreboardListObjectives() {
        return gameplayOps.scoreboardListObjectives();
    }

    @Override
    public Optional<ScoreboardObjectiveInfo> scoreboardGetObjective(String name) {
        return gameplayOps.scoreboardGetObjective(name);
    }

    @Override
    public boolean scoreboardAddObjective(String name, String criterion, String displayName) {
        return gameplayOps.scoreboardAddObjective(name, criterion, displayName);
    }

    @Override
    public boolean scoreboardRemoveObjective(String name) {
        return gameplayOps.scoreboardRemoveObjective(name);
    }

    @Override
    public boolean scoreboardSetDisplaySlot(String slot, String objectiveName) {
        return gameplayOps.scoreboardSetDisplaySlot(slot, objectiveName);
    }

    @Override
    public int scoreboardGetScore(String participant, String objectiveName) {
        return gameplayOps.scoreboardGetScore(participant, objectiveName);
    }

    @Override
    public boolean scoreboardSetScore(String participant, String objectiveName, int score) {
        return gameplayOps.scoreboardSetScore(participant, objectiveName, score);
    }

    @Override
    public boolean scoreboardAddScore(String participant, String objectiveName, int delta) {
        return gameplayOps.scoreboardAddScore(participant, objectiveName, delta);
    }

    @Override
    public boolean scoreboardResetParticipant(String participant, String objectiveName) {
        return gameplayOps.scoreboardResetParticipant(participant, objectiveName);
    }

    @Override
    public List<TeamInfo> scoreboardListTeams() {
        return gameplayOps.scoreboardListTeams();
    }

    @Override
    public boolean scoreboardAddTeam(String name, String displayName) {
        return gameplayOps.scoreboardAddTeam(name, displayName);
    }

    @Override
    public boolean scoreboardRemoveTeam(String name) {
        return gameplayOps.scoreboardRemoveTeam(name);
    }

    @Override
    public boolean scoreboardTeamAddMember(String teamName, String participant) {
        return gameplayOps.scoreboardTeamAddMember(teamName, participant);
    }

    @Override
    public boolean scoreboardTeamRemoveMember(String teamName, String participant) {
        return gameplayOps.scoreboardTeamRemoveMember(teamName, participant);
    }

    // =====================================================================
    // Data storage / attachments — delegated to DataOps
    // =====================================================================

    @Override
    public Optional<String> dataStorageGet(String namespace, String path) {
        return dataOps.dataStorageGet(namespace, path);
    }

    @Override
    public boolean dataStorageSet(String namespace, String path, String snbt, boolean merge) {
        return dataOps.dataStorageSet(namespace, path, snbt, merge);
    }

    @Override
    public boolean dataStorageRemove(String namespace, String path) {
        return dataOps.dataStorageRemove(namespace, path);
    }

    @Override
    public List<String> dataStorageListNamespaces() {
        return dataOps.dataStorageListNamespaces();
    }

    @Override
    public Optional<String> dataAttachmentGet(String target, String namespace, String key) {
        return dataOps.dataAttachmentGet(target, namespace, key);
    }

    @Override
    public boolean dataAttachmentSet(String target, String namespace, String key, String snbt) {
        return dataOps.dataAttachmentSet(target, namespace, key, snbt);
    }

    @Override
    public boolean dataAttachmentRemove(String target, String namespace, String key) {
        return dataOps.dataAttachmentRemove(target, namespace, key);
    }

    @Override
    public List<String> dataAttachmentListKeys(String target, String namespace) {
        return dataOps.dataAttachmentListKeys(target, namespace);
    }

    // =====================================================================
    // Structures — delegated to WorldOps
    // =====================================================================

    @Override
    public boolean structureSaveFromWorld(String name, String dimensionId, BoundingBox box, boolean includeEntities) {
        return worldOps.structureSaveFromWorld(name, dimensionId, box, includeEntities);
    }

    @Override
    public boolean structureLoadToWorld(
            String name,
            String dimensionId,
            Vec3i origin,
            String rotation,
            String mirror,
            boolean includeEntities,
            float integrity) {
        return worldOps.structureLoadToWorld(name, dimensionId, origin, rotation, mirror, includeEntities, integrity);
    }

    @Override
    public List<StructureInfo> structureList() {
        return worldOps.structureList();
    }

    @Override
    public Optional<StructureInfo> structureGetInfo(String name) {
        return worldOps.structureGetInfo(name);
    }

    @Override
    public boolean structureDelete(String name) {
        return worldOps.structureDelete(name);
    }

    @Override
    public List<String> structureFileList() {
        return worldOps.structureFileList();
    }

    @Override
    public byte[] structureFileRead(String name) {
        return worldOps.structureFileRead(name);
    }

    @Override
    public boolean structureFileWrite(String name, byte[] payload) {
        return worldOps.structureFileWrite(name, payload);
    }

    @Override
    public boolean structureFileDelete(String name) {
        return worldOps.structureFileDelete(name);
    }

    // =====================================================================
    // Datapack — delegated to DataOps
    // =====================================================================

    @Override
    public List<DatapackInfo> datapackListAvailable() {
        return dataOps.datapackListAvailable();
    }

    @Override
    public List<DatapackInfo> datapackListEnabled() {
        return dataOps.datapackListEnabled();
    }

    @Override
    public boolean datapackEnable(String id) {
        return dataOps.datapackEnable(id);
    }

    @Override
    public boolean datapackDisable(String id) {
        return dataOps.datapackDisable(id);
    }

    // =====================================================================
    // Loot / Recipe / Tag / Resource — delegated to RegistryOps
    // =====================================================================

    @Override
    public List<String> lootTableList() {
        return registryOps.lootTableList();
    }

    @Override
    public Optional<String> lootTableGetDefinition(String id) {
        return registryOps.lootTableGetDefinition(id);
    }

    @Override
    public LootDropInfo lootTableGenerate(String id, Vec3d position, UUID killer, UUID lootingEntity) {
        return registryOps.lootTableGenerate(id, position, killer, lootingEntity);
    }

    @Override
    public List<RecipeInfo> recipeList(String type) {
        return registryOps.recipeList(type);
    }

    @Override
    public Optional<RecipeInfo> recipeGetDefinition(String id) {
        return registryOps.recipeGetDefinition(id);
    }

    @Override
    public List<RecipeInfo> recipeFindByResult(String itemId) {
        return registryOps.recipeFindByResult(itemId);
    }

    @Override
    public List<RecipeInfo> recipeFindByIngredient(String itemId) {
        return registryOps.recipeFindByIngredient(itemId);
    }

    @Override
    public List<String> tagListInRegistry(String registry) {
        return registryOps.tagListInRegistry(registry);
    }

    @Override
    public List<String> tagGetMembers(String registry, String tag) {
        return registryOps.tagGetMembers(registry, tag);
    }

    @Override
    public boolean tagCheckMembership(String registry, String tag, String member) {
        return registryOps.tagCheckMembership(registry, tag, member);
    }

    @Override
    public List<String> resourceLoaderListNamespaces() {
        return registryOps.resourceLoaderListNamespaces();
    }

    @Override
    public Optional<byte[]> resourceLoaderGetResource(String namespace, String path) {
        return registryOps.resourceLoaderGetResource(namespace, path);
    }

    // =====================================================================
    // Content Registry (Fabric) — delegated to RegistryOps
    // =====================================================================

    @Override
    public int contentRegistryGetFuel(String itemId) {
        return registryOps.contentRegistryGetFuel(itemId);
    }

    @Override
    public boolean contentRegistrySetFuel(String itemId, int burnTimeTicks) {
        return registryOps.contentRegistrySetFuel(itemId, burnTimeTicks);
    }

    @Override
    public FlammableBlockInfo contentRegistryGetFlammableBlock(String blockId) {
        return registryOps.contentRegistryGetFlammableBlock(blockId);
    }

    @Override
    public boolean contentRegistrySetFlammableBlock(String blockId, int burnChance, int spreadChance) {
        return registryOps.contentRegistrySetFlammableBlock(blockId, burnChance, spreadChance);
    }

    @Override
    public CompostableInfo contentRegistryGetCompostable(String itemId) {
        return registryOps.contentRegistryGetCompostable(itemId);
    }

    @Override
    public boolean contentRegistrySetCompostable(String itemId, float chance) {
        return registryOps.contentRegistrySetCompostable(itemId, chance);
    }

    // =====================================================================
    // Resource Conditions (Fabric) — delegated to RegistryOps
    // =====================================================================

    @Override
    public ResourceConditionResult resourceConditionEvaluate(String conditionJson) {
        return registryOps.resourceConditionEvaluate(conditionJson);
    }

    // =====================================================================
    // Fluid Storage (Fabric Transfer API) — delegated to RegistryOps
    // =====================================================================

    @Override
    public Optional<FluidStackInfo> fluidStorageGet(String dimensionId, Vec3i position, String direction) {
        return registryOps.fluidStorageGet(dimensionId, position, direction);
    }

    @Override
    public List<FluidStackInfo> fluidStorageListAt(String dimensionId, Vec3i position) {
        return registryOps.fluidStorageListAt(dimensionId, position);
    }

    // =====================================================================
    // Player Screen Handlers (Fabric) — delegated to RegistryOps
    // =====================================================================

    @Override
    public boolean playerScreenOpenMenu(UUID uuid, String menuType, String title) {
        return registryOps.playerScreenOpenMenu(uuid, menuType, title);
    }

    @Override
    public boolean playerScreenOpenContainer(UUID uuid, String dimensionId, Vec3i position) {
        return registryOps.playerScreenOpenContainer(uuid, dimensionId, position);
    }

    @Override
    public boolean playerScreenClose(UUID uuid) {
        return registryOps.playerScreenClose(uuid);
    }

    // =====================================================================
    // Bossbar (vanilla) — delegated to GameplayOps
    // =====================================================================

    @Override
    public List<BossbarInfo> bossbarList() {
        return gameplayOps.bossbarList();
    }

    @Override
    public Optional<BossbarInfo> bossbarGet(String id) {
        return gameplayOps.bossbarGet(id);
    }

    @Override
    public boolean bossbarAdd(String id, String name) {
        return gameplayOps.bossbarAdd(id, name);
    }

    @Override
    public boolean bossbarRemove(String id) {
        return gameplayOps.bossbarRemove(id);
    }

    @Override
    public boolean bossbarSetValue(String id, int value) {
        return gameplayOps.bossbarSetValue(id, value);
    }

    @Override
    public boolean bossbarSetMax(String id, int max) {
        return gameplayOps.bossbarSetMax(id, max);
    }

    @Override
    public boolean bossbarSetName(String id, String name) {
        return gameplayOps.bossbarSetName(id, name);
    }

    @Override
    public boolean bossbarSetColor(String id, String color) {
        return gameplayOps.bossbarSetColor(id, color);
    }

    @Override
    public boolean bossbarSetStyle(String id, String style) {
        return gameplayOps.bossbarSetStyle(id, style);
    }

    @Override
    public boolean bossbarSetVisible(String id, boolean visible) {
        return gameplayOps.bossbarSetVisible(id, visible);
    }

    @Override
    public boolean bossbarSetPlayers(String id, List<UUID> playerUuids) {
        return gameplayOps.bossbarSetPlayers(id, playerUuids);
    }

    // =====================================================================
    // Advancement (vanilla) — delegated to GameplayOps
    // =====================================================================

    @Override
    public boolean advancementGrant(UUID playerUuid, String advancementId, String mode, String criterion) {
        return gameplayOps.advancementGrant(playerUuid, advancementId, mode, criterion);
    }

    @Override
    public boolean advancementRevoke(UUID playerUuid, String advancementId, String mode, String criterion) {
        return gameplayOps.advancementRevoke(playerUuid, advancementId, mode, criterion);
    }

    @Override
    public AdvancementProgressInfo advancementListPlayer(UUID playerUuid) {
        return gameplayOps.advancementListPlayer(playerUuid);
    }

    @Override
    public List<String> advancementListAll() {
        return gameplayOps.advancementListAll();
    }

    @Override
    public Optional<String> advancementGetDefinition(String advancementId) {
        return gameplayOps.advancementGetDefinition(advancementId);
    }

    // =====================================================================
    // Function (vanilla) — delegated to GameplayOps
    // =====================================================================

    @Override
    public boolean functionRun(String functionId, UUID asEntity) {
        return gameplayOps.functionRun(functionId, asEntity);
    }

    @Override
    public List<String> functionList(String namespaceFilter) {
        return gameplayOps.functionList(namespaceFilter);
    }

    @Override
    public Optional<String> functionGetDefinition(String functionId) {
        return gameplayOps.functionGetDefinition(functionId);
    }

    // =====================================================================
    // World border (vanilla) — delegated to WorldOps
    // =====================================================================

    @Override
    public WorldBorderInfo worldborderGet(String dimensionId) {
        return worldOps.worldborderGet(dimensionId);
    }

    @Override
    public boolean worldborderSetSize(String dimensionId, double size, int timeSeconds) {
        return worldOps.worldborderSetSize(dimensionId, size, timeSeconds);
    }

    @Override
    public boolean worldborderAddSize(String dimensionId, double delta, int timeSeconds) {
        return worldOps.worldborderAddSize(dimensionId, delta, timeSeconds);
    }

    @Override
    public boolean worldborderSetCenter(String dimensionId, double x, double z) {
        return worldOps.worldborderSetCenter(dimensionId, x, z);
    }

    @Override
    public boolean worldborderSetWarningBlocks(String dimensionId, int blocks) {
        return worldOps.worldborderSetWarningBlocks(dimensionId, blocks);
    }

    @Override
    public boolean worldborderSetWarningTime(String dimensionId, int seconds) {
        return worldOps.worldborderSetWarningTime(dimensionId, seconds);
    }

    @Override
    public boolean worldborderSetDamageAmount(String dimensionId, double amount) {
        return worldOps.worldborderSetDamageAmount(dimensionId, amount);
    }

    @Override
    public boolean worldborderSetDamageBuffer(String dimensionId, double buffer) {
        return worldOps.worldborderSetDamageBuffer(dimensionId, buffer);
    }

    // =====================================================================
    // Schedule (vanilla) — delegated to GameplayOps
    // =====================================================================

    @Override
    public boolean scheduleFunction(String functionId, int ticks, String mode) {
        return gameplayOps.scheduleFunction(functionId, ticks, mode);
    }

    @Override
    public boolean scheduleClear(String functionId) {
        return gameplayOps.scheduleClear(functionId);
    }

    @Override
    public List<ScheduledFunctionInfo> scheduleList() {
        return gameplayOps.scheduleList();
    }

    // =====================================================================
    // Item modify (vanilla) — delegated to GameplayOps
    // =====================================================================

    @Override
    public boolean itemModifyEntitySlot(UUID entityUuid, String slot, String modifierId) {
        return gameplayOps.itemModifyEntitySlot(entityUuid, slot, modifierId);
    }

    @Override
    public boolean itemModifyBlockSlot(String dimensionId, Vec3i position, String slot, String modifierId) {
        return gameplayOps.itemModifyBlockSlot(dimensionId, position, slot, modifierId);
    }

    // =====================================================================
    // Module / fabric availability
    // =====================================================================

    @Override
    public boolean hasFabricModule(String moduleId) {
        return FabricLoader.getInstance().isModLoaded(moduleId);
    }

    @Override
    public String runningMinecraftVersion() {
        return SharedConstants.getCurrentVersion().name();
    }
}
