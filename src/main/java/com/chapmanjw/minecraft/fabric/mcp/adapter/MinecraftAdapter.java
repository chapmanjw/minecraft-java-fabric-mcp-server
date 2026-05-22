package com.chapmanjw.minecraft.fabric.mcp.adapter;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

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
 * The single seam between tool implementations and the Minecraft API.
 *
 * <p>Every method here is called on Minecraft's main server thread — the
 * {@link com.chapmanjw.minecraft.fabric.mcp.runtime.MinecraftMainThreadExecutor} schedules into
 * it, and the tools that call this interface have already submitted their work.
 * Implementations MUST NOT submit nested main-thread work; treat the calling thread
 * as the main thread directly.
 *
 * <p>The interface is intentionally flat (not split by domain into sub-interfaces) so
 * tool implementations can read one method dispatch instead of two. Methods are
 * grouped by domain comment for navigation.
 *
 * <p>All methods returning {@code Optional} return empty when the targeted entity,
 * dimension, position, or named resource doesn't exist; callers convert empty to a
 * structured "not found" error at the tool layer.
 */
public interface MinecraftAdapter {

    // =====================================================================
    // Lifecycle
    // =====================================================================

    /**
     * Bind to a running Minecraft server. Called from
     * {@code ServerLifecycleEvents.SERVER_STARTING}. Implementations capture the
     * {@code MinecraftServer} reference and any per-startup state (registries, etc.).
     *
     * <p>Typed as {@link Object} on the interface to keep this dependency-free; the
     * implementation classes cast to the concrete Minecraft type using a Stonecutter
     * version block.
     */
    void bind(Object minecraftServer);

    /** Release the server reference. Called from {@code SERVER_STOPPING}. */
    void unbind();

    boolean isBound();

    // =====================================================================
    // Server
    // =====================================================================

    ServerStatus serverGetStatus();

    String serverGetMotd();

    void serverSetMotd(String motd);

    void serverSaveAllWorlds(boolean flush);

    void serverReloadResources();

    // =====================================================================
    // Level (per-dimension)
    // =====================================================================

    List<String> levelListDimensions();

    Optional<DimensionInfo> levelGetDimensionInfo(String dimensionId);

    Optional<LevelInfo> levelGetInfo(String dimensionId);

    long levelGetTime(String dimensionId);

    void levelSetTime(String dimensionId, long timeOfDay);

    String levelGetWeather(String dimensionId);

    void levelSetWeather(String dimensionId, String weather, int durationTicks);

    String levelGetDifficulty();

    void levelSetDifficulty(String difficulty);

    Vec3i levelGetSpawnPoint(String dimensionId);

    void levelSetSpawnPoint(String dimensionId, Vec3i position);

    void levelPlaySound(String dimensionId, Vec3d position, String soundId, float volume, float pitch);

    void levelSpawnParticle(
            String dimensionId,
            Vec3d position,
            String particleId,
            int count,
            Vec3d offset,
            double speed);

    void levelLightningStrike(String dimensionId, Vec3d position, boolean cosmetic);

    void levelCreateExplosion(
            String dimensionId, Vec3d position, float power, boolean fire, boolean breakBlocks);

    Optional<GameRuleInfo> levelGetGameRule(String name);

    void levelSetGameRule(String name, String value);

    List<GameRuleInfo> levelListGameRules();

    Optional<BiomeInfo> levelGetBiomeAt(String dimensionId, Vec3i position);

    List<BiomeInfo> levelListBiomesInDimension(String dimensionId);

    // =====================================================================
    // Block / BlockState
    // =====================================================================

    Optional<BlockStateInfo> blockGetState(String dimensionId, Vec3i position);

    boolean blockSetState(String dimensionId, Vec3i position, BlockSpec spec, int updateFlags);

    /** Bulk fill; returns the count of blocks actually changed. */
    long blockFillRegion(String dimensionId, BoundingBox box, BlockSpec spec, FillMode mode);

    long blockCloneRegion(
            String sourceDimension,
            BoundingBox source,
            String destDimension,
            Vec3i destinationOrigin,
            CloneMode mode);

    long blockReplaceInRegion(
            String dimensionId, BoundingBox box, String targetBlockId, BlockSpec replacement);

    /**
     * Highest Y at column {@code (x, z)} for the given heightmap. {@code heightmapType}
     * is a {@code net.minecraft.world.level.levelgen.Heightmap.Types} name —
     * {@code WORLD_SURFACE} (default), {@code OCEAN_FLOOR}, {@code MOTION_BLOCKING},
     * {@code MOTION_BLOCKING_NO_LEAVES}, {@code WORLD_SURFACE_WG}, {@code OCEAN_FLOOR_WG}.
     */
    int blockGetTopY(String dimensionId, int x, int z, String heightmapType);

    /**
     * Materialise a per-column heightmap into blocks in one main-thread pass — the
     * efficient path for generated terrain: send a compact height grid + palette
     * instead of thousands of box fills. Fills each column stone → subsurface →
     * surface (and water up to {@code seaLevel} where the surface is below it).
     * Returns the number of blocks set.
     */
    long blockFillColumns(String dimensionId, ColumnFill spec);

    /**
     * A tile of per-column terrain. {@code height}/{@code surface}/{@code subsurface}
     * are row-major arrays of length {@code width * length} indexed {@code xi*length + zi};
     * {@code surface}/{@code subsurface} hold palette indices into {@code palette}.
     * Columns fill from {@code floorY} (stone) up; a column whose top is below
     * {@code seaLevel} (when {@code waterIndex >= 0}) is flooded with water to sea level.
     */
    record ColumnFill(
            int originX,
            int originZ,
            int width,
            int length,
            int floorY,
            int seaLevel,
            java.util.List<String> palette,
            int subsurfaceDepth,
            int stoneIndex,
            int waterIndex,
            int[] height,
            int[] surface,
            int[] subsurface) {}

    List<BlockMatch> blockScanRegion(
            String dimensionId, BoundingBox box, String matchBlockId, int limit);

    /**
     * One-pass aggregate scan of a box: a material histogram and the non-air
     * bounding box, computed server-side so no per-block data floods the caller.
     * The primitive for archaeology (what's up here?) and pre-clear checks
     * (what would I overwrite?).
     */
    ScanSummary blockScanSummary(String dimensionId, BoundingBox box);

    /** Base map-colour RGB (0xRRGGBB) and palette id of the block at a position. */
    Optional<MapColorInfo> blockGetMapColor(String dimensionId, Vec3i position);

    /**
     * Render a region to a PNG (bytes) from block map colours — the native
     * verify-time "eyes". {@code view} is one of iso/side/front/top; {@code step}
     * downsamples (1 = every block); {@code scale} sets pixels per voxel. Works
     * headless (no client).
     */
    byte[] worldRenderRegion(String dimensionId, BoundingBox box, String view, int step, int scale);

    enum FillMode {
        REPLACE,
        DESTROY,
        HOLLOW,
        OUTLINE,
        KEEP
    }

    enum CloneMode {
        NORMAL,
        MASKED,
        MOVE
    }

    record BlockMatch(Vec3i position, BlockStateInfo state) {}

    /**
     * Aggregate scan result. {@code histogram} maps block id → count (air
     * excluded); {@code nonAirMin}/{@code nonAirMax} bound the non-air blocks
     * (both null when the box is empty).
     */
    record ScanSummary(
            long scannedVolume,
            long nonAirCount,
            Map<String, Long> histogram,
            Vec3i nonAirMin,
            Vec3i nonAirMax) {}

    /** Base map colour: palette {@code id} and packed {@code rgb} (0xRRGGBB). */
    record MapColorInfo(int id, int rgb) {}

    // =====================================================================
    // BlockEntity
    // =====================================================================

    Optional<String> blockEntityGetNbt(String dimensionId, Vec3i position);

    boolean blockEntitySetNbt(String dimensionId, Vec3i position, String snbt);

    boolean blockEntityClearInventory(String dimensionId, Vec3i position);

    // =====================================================================
    // Entity (non-player)
    // =====================================================================

    Optional<UUID> entitySummon(String dimensionId, String entityType, Vec3d position, String snbt);

    Optional<EntityInfo> entityGet(UUID uuid);

    Optional<EntityInfo> entityGetByNetworkId(String dimensionId, int networkId);

    List<EntityInfo> entityQuery(String dimensionId, String selector, int limit);

    Optional<Map<String, String>> entityGetComponents(UUID uuid);

    Optional<String> entityGetNbt(UUID uuid);

    boolean entitySetNbt(UUID uuid, String snbt);

    boolean entityTeleport(UUID uuid, String dimensionId, Vec3d position, Vec3d facingTarget);

    boolean entityApplyDamage(UUID uuid, float amount, String damageType);

    boolean entitySetVelocity(UUID uuid, Vec3d velocity);

    boolean entityApplyEffect(
            UUID uuid,
            String effect,
            int durationTicks,
            int amplifier,
            boolean ambient,
            boolean showParticles,
            boolean showIcon);

    boolean entityRemoveEffect(UUID uuid, String effect);

    List<StatusEffectInfo> entityGetEffects(UUID uuid);

    boolean entityKill(UUID uuid);

    boolean entityDespawn(UUID uuid);

    boolean entityAddTag(UUID uuid, String tag);

    boolean entityRemoveTag(UUID uuid, String tag);

    List<String> entityListTags(UUID uuid);

    // =====================================================================
    // Player
    // =====================================================================

    List<PlayerInfo> playerListOnline();

    Optional<PlayerInfo> playerGetInfo(UUID uuid);

    Optional<InventoryInfo> playerGetInventory(UUID uuid);

    boolean playerGiveItem(UUID uuid, ItemSpec item);

    boolean playerClearInventorySlot(UUID uuid, int slot);

    boolean playerClearAllInventory(UUID uuid);

    boolean playerSetGamemode(UUID uuid, String gameMode);

    boolean playerKick(UUID uuid, String reason);

    boolean playerSendMessage(UUID uuid, String message);

    boolean playerSendActionbar(UUID uuid, String message);

    boolean playerSendTitle(
            UUID uuid,
            String title,
            String subtitle,
            int fadeInTicks,
            int stayTicks,
            int fadeOutTicks);

    boolean playerPlaySound(UUID uuid, String soundId, float volume, float pitch);

    boolean playerSetSpawnPoint(UUID uuid, String dimensionId, Vec3i position);

    boolean playerGrantXp(UUID uuid, int amount);

    boolean playerSetXpLevel(UUID uuid, int level);

    boolean playerSetCamera(UUID viewer, UUID target);

    // =====================================================================
    // Inventory / Container
    // =====================================================================

    /**
     * Read a container by reference. {@code target} is one of:
     * {@code "player:<uuid>"}, {@code "entity:<uuid>"}, {@code "block:<dim>:<x>:<y>:<z>"}.
     */
    Optional<InventoryInfo> inventoryGet(String target);

    boolean inventorySetSlot(String target, int slot, ItemSpec item);

    boolean inventoryClearSlot(String target, int slot);

    boolean inventorySwapSlots(String target, int slotA, int slotB);

    int inventoryCountItems(String target, String itemId);

    // =====================================================================
    // ItemStack
    // =====================================================================

    Optional<ItemStackInfo> itemStackDescribe(ItemSpec spec);

    boolean itemStackDropAt(String dimensionId, Vec3d position, ItemSpec spec);

    // =====================================================================
    // Command
    // =====================================================================

    CommandResult commandExecute(String command);

    CommandResult commandExecuteAs(String command, UUID actor);

    // =====================================================================
    // Scoreboard
    // =====================================================================

    List<ScoreboardObjectiveInfo> scoreboardListObjectives();

    Optional<ScoreboardObjectiveInfo> scoreboardGetObjective(String name);

    boolean scoreboardAddObjective(String name, String criterion, String displayName);

    boolean scoreboardRemoveObjective(String name);

    boolean scoreboardSetDisplaySlot(String slot, String objectiveName);

    int scoreboardGetScore(String participant, String objectiveName);

    boolean scoreboardSetScore(String participant, String objectiveName, int score);

    boolean scoreboardAddScore(String participant, String objectiveName, int delta);

    boolean scoreboardResetParticipant(String participant, String objectiveName);

    List<TeamInfo> scoreboardListTeams();

    boolean scoreboardAddTeam(String name, String displayName);

    boolean scoreboardRemoveTeam(String name);

    boolean scoreboardTeamAddMember(String teamName, String participant);

    /**
     * Removes {@code participant} from a team. Note: vanilla Minecraft does not support
     * scoped removal — this delegates to {@code /team leave <participant>}, which
     * removes them from whichever team they're currently on, regardless of
     * {@code teamName}. The {@code teamName} parameter is preserved for API symmetry
     * with {@link #scoreboardTeamAddMember} and may be used by callers for audit logs.
     * Callers that need scoped semantics should verify current team membership first
     * via {@link #scoreboardListTeams()} before invoking this method.
     */
    boolean scoreboardTeamRemoveMember(String teamName, String participant);

    // =====================================================================
    // Data storage / attachments
    // =====================================================================

    Optional<String> dataStorageGet(String namespace, String path);

    boolean dataStorageSet(String namespace, String path, String snbt, boolean merge);

    boolean dataStorageRemove(String namespace, String path);

    List<String> dataStorageListNamespaces();

    Optional<String> dataAttachmentGet(String target, String namespace, String key);

    boolean dataAttachmentSet(String target, String namespace, String key, String snbt);

    boolean dataAttachmentRemove(String target, String namespace, String key);

    List<String> dataAttachmentListKeys(String target, String namespace);

    // =====================================================================
    // Structures
    // =====================================================================

    boolean structureSaveFromWorld(String name, String dimensionId, BoundingBox box, boolean includeEntities);

    boolean structureLoadToWorld(
            String name,
            String dimensionId,
            Vec3i origin,
            String rotation,
            String mirror,
            boolean includeEntities,
            float integrity);

    List<StructureInfo> structureList();

    Optional<StructureInfo> structureGetInfo(String name);

    boolean structureDelete(String name);

    List<String> structureFileList();

    byte[] structureFileRead(String name);

    boolean structureFileWrite(String name, byte[] payload);

    boolean structureFileDelete(String name);

    // =====================================================================
    // Datapack
    // =====================================================================

    List<DatapackInfo> datapackListAvailable();

    List<DatapackInfo> datapackListEnabled();

    boolean datapackEnable(String id);

    boolean datapackDisable(String id);

    // =====================================================================
    // Loot / Recipe / Tag / Resource
    // =====================================================================

    List<String> lootTableList();

    Optional<String> lootTableGetDefinition(String id);

    LootDropInfo lootTableGenerate(String id, Vec3d position, UUID killer, UUID lootingEntity);

    List<RecipeInfo> recipeList(String type);

    Optional<RecipeInfo> recipeGetDefinition(String id);

    List<RecipeInfo> recipeFindByResult(String itemId);

    List<RecipeInfo> recipeFindByIngredient(String itemId);

    List<String> tagListInRegistry(String registry);

    List<String> tagGetMembers(String registry, String tag);

    boolean tagCheckMembership(String registry, String tag, String member);

    List<String> resourceLoaderListNamespaces();

    Optional<byte[]> resourceLoaderGetResource(String namespace, String path);

    // =====================================================================
    // Content Registry (Fabric)
    // =====================================================================

    int contentRegistryGetFuel(String itemId);

    boolean contentRegistrySetFuel(String itemId, int burnTimeTicks);

    FlammableBlockInfo contentRegistryGetFlammableBlock(String blockId);

    boolean contentRegistrySetFlammableBlock(String blockId, int burnChance, int spreadChance);

    CompostableInfo contentRegistryGetCompostable(String itemId);

    boolean contentRegistrySetCompostable(String itemId, float chance);

    // =====================================================================
    // Resource Conditions (Fabric)
    // =====================================================================

    /**
     * Evaluate a serialized {@code ResourceCondition} JSON object against the running
     * server's registry context. Returns a 2-tuple {@code (matches, conditionId)}.
     */
    record ResourceConditionResult(boolean matches, String conditionId) {}

    ResourceConditionResult resourceConditionEvaluate(String conditionJson);

    // =====================================================================
    // Fluid Storage (Fabric Transfer API)
    // =====================================================================

    Optional<FluidStackInfo> fluidStorageGet(String dimensionId, Vec3i position, String direction);

    List<FluidStackInfo> fluidStorageListAt(String dimensionId, Vec3i position);

    // =====================================================================
    // Player Screen Handlers (Fabric)
    // =====================================================================

    boolean playerScreenOpenMenu(UUID uuid, String menuType, String title);

    boolean playerScreenOpenContainer(UUID uuid, String dimensionId, Vec3i position);

    boolean playerScreenClose(UUID uuid);

    // =====================================================================
    // Bossbar (vanilla)
    // =====================================================================

    List<BossbarInfo> bossbarList();

    Optional<BossbarInfo> bossbarGet(String id);

    boolean bossbarAdd(String id, String name);

    boolean bossbarRemove(String id);

    boolean bossbarSetValue(String id, int value);

    boolean bossbarSetMax(String id, int max);

    boolean bossbarSetName(String id, String name);

    boolean bossbarSetColor(String id, String color);

    boolean bossbarSetStyle(String id, String style);

    boolean bossbarSetVisible(String id, boolean visible);

    boolean bossbarSetPlayers(String id, List<UUID> playerUuids);

    // =====================================================================
    // Advancement (vanilla)
    // =====================================================================

    boolean advancementGrant(UUID playerUuid, String advancementId, String mode, String criterion);

    boolean advancementRevoke(UUID playerUuid, String advancementId, String mode, String criterion);

    AdvancementProgressInfo advancementListPlayer(UUID playerUuid);

    List<String> advancementListAll();

    Optional<String> advancementGetDefinition(String advancementId);

    // =====================================================================
    // Function (vanilla)
    // =====================================================================

    boolean functionRun(String functionId, UUID asEntity);

    List<String> functionList(String namespaceFilter);

    Optional<String> functionGetDefinition(String functionId);

    // =====================================================================
    // World border (vanilla)
    // =====================================================================

    WorldBorderInfo worldborderGet(String dimensionId);

    boolean worldborderSetSize(String dimensionId, double size, int timeSeconds);

    boolean worldborderAddSize(String dimensionId, double delta, int timeSeconds);

    boolean worldborderSetCenter(String dimensionId, double x, double z);

    boolean worldborderSetWarningBlocks(String dimensionId, int blocks);

    boolean worldborderSetWarningTime(String dimensionId, int seconds);

    boolean worldborderSetDamageAmount(String dimensionId, double amount);

    boolean worldborderSetDamageBuffer(String dimensionId, double buffer);

    // =====================================================================
    // Schedule (vanilla)
    // =====================================================================

    boolean scheduleFunction(String functionId, int ticks, String mode);

    boolean scheduleClear(String functionId);

    List<ScheduledFunctionInfo> scheduleList();

    // =====================================================================
    // Item modify (vanilla)
    // =====================================================================

    boolean itemModifyEntitySlot(UUID entityUuid, String slot, String modifierId);

    boolean itemModifyBlockSlot(String dimensionId, Vec3i position, String slot, String modifierId);

    // =====================================================================
    // Module / Fabric API availability — used by tool registration filter at runtime.
    // =====================================================================

    boolean hasFabricModule(String moduleId);

    String runningMinecraftVersion();
}
