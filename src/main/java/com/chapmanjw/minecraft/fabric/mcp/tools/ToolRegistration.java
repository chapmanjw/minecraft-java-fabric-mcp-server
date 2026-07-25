package com.chapmanjw.minecraft.fabric.mcp.tools;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.chapmanjw.minecraft.fabric.mcp.compat.ToolCompatibilityFilter;
import com.chapmanjw.minecraft.fabric.mcp.compat.ToolDescriptor;
import com.chapmanjw.minecraft.fabric.mcp.protocol.Tool;
import com.chapmanjw.minecraft.fabric.mcp.protocol.ToolRegistry;

import com.chapmanjw.minecraft.fabric.mcp.tools.advancement.AdvancementTools;
import com.chapmanjw.minecraft.fabric.mcp.tools.block.BlockTools;
import com.chapmanjw.minecraft.fabric.mcp.tools.blockentity.BlockEntityTools;
import com.chapmanjw.minecraft.fabric.mcp.tools.bossbar.BossbarTools;
import com.chapmanjw.minecraft.fabric.mcp.tools.command.CommandTools;
import com.chapmanjw.minecraft.fabric.mcp.tools.contentregistry.ContentRegistryTools;
import com.chapmanjw.minecraft.fabric.mcp.tools.data.DataTools;
import com.chapmanjw.minecraft.fabric.mcp.tools.datapack.DatapackTools;
import com.chapmanjw.minecraft.fabric.mcp.tools.entity.EntityTools;
import com.chapmanjw.minecraft.fabric.mcp.tools.events.EventsTools;
import com.chapmanjw.minecraft.fabric.mcp.tools.fluidstorage.FluidStorageTools;
import com.chapmanjw.minecraft.fabric.mcp.tools.function.FunctionTools;
import com.chapmanjw.minecraft.fabric.mcp.tools.inventory.InventoryTools;
import com.chapmanjw.minecraft.fabric.mcp.tools.itemmodify.ItemModifyTools;
import com.chapmanjw.minecraft.fabric.mcp.tools.itemstack.ItemStackTools;
import com.chapmanjw.minecraft.fabric.mcp.tools.level.LevelCreateExplosionTool;
import com.chapmanjw.minecraft.fabric.mcp.tools.level.LevelGetBiomeAtTool;
import com.chapmanjw.minecraft.fabric.mcp.tools.level.LevelGetDifficultyTool;
import com.chapmanjw.minecraft.fabric.mcp.tools.level.LevelGetDimensionInfoTool;
import com.chapmanjw.minecraft.fabric.mcp.tools.level.LevelGetGameRuleTool;
import com.chapmanjw.minecraft.fabric.mcp.tools.level.LevelGetInfoTool;
import com.chapmanjw.minecraft.fabric.mcp.tools.level.LevelGetSpawnPointTool;
import com.chapmanjw.minecraft.fabric.mcp.tools.level.LevelGetTimeTool;
import com.chapmanjw.minecraft.fabric.mcp.tools.level.LevelGetWeatherTool;
import com.chapmanjw.minecraft.fabric.mcp.tools.level.LevelFillBiomeTool;
import com.chapmanjw.minecraft.fabric.mcp.tools.level.LevelLightningStrikeTool;
import com.chapmanjw.minecraft.fabric.mcp.tools.level.LevelListBiomesInDimensionTool;
import com.chapmanjw.minecraft.fabric.mcp.tools.level.LevelListDimensionsTool;
import com.chapmanjw.minecraft.fabric.mcp.tools.level.LevelListGameRulesTool;
import com.chapmanjw.minecraft.fabric.mcp.tools.level.LevelPlaceFeatureTool;
import com.chapmanjw.minecraft.fabric.mcp.tools.level.LevelPlaceFeaturesBatchTool;
import com.chapmanjw.minecraft.fabric.mcp.tools.level.LevelPlaySoundTool;
import com.chapmanjw.minecraft.fabric.mcp.tools.level.LevelSetDifficultyTool;
import com.chapmanjw.minecraft.fabric.mcp.tools.level.LevelSetGameRuleTool;
import com.chapmanjw.minecraft.fabric.mcp.tools.level.LevelSetSpawnPointTool;
import com.chapmanjw.minecraft.fabric.mcp.tools.level.LevelSetTimeTool;
import com.chapmanjw.minecraft.fabric.mcp.tools.level.LevelSetWeatherTool;
import com.chapmanjw.minecraft.fabric.mcp.tools.level.LevelSpawnParticleTool;
import com.chapmanjw.minecraft.fabric.mcp.tools.player.PlayerTools;
import com.chapmanjw.minecraft.fabric.mcp.tools.playerscreen.PlayerScreenTools;
import com.chapmanjw.minecraft.fabric.mcp.tools.registry.RegistryAccessTools;
import com.chapmanjw.minecraft.fabric.mcp.tools.resourcecondition.ResourceConditionTools;
import com.chapmanjw.minecraft.fabric.mcp.tools.schedule.ScheduleTools;
import com.chapmanjw.minecraft.fabric.mcp.tools.scoreboard.ScoreboardTools;
import com.chapmanjw.minecraft.fabric.mcp.tools.server.ServerGetMotdTool;
import com.chapmanjw.minecraft.fabric.mcp.tools.server.ServerGetStatusTool;
import com.chapmanjw.minecraft.fabric.mcp.tools.server.ServerReloadResourcesTool;
import com.chapmanjw.minecraft.fabric.mcp.tools.server.ServerSaveAllWorldsTool;
import com.chapmanjw.minecraft.fabric.mcp.tools.server.ServerSetMotdTool;
import com.chapmanjw.minecraft.fabric.mcp.tools.structure.StructureTools;
import com.chapmanjw.minecraft.fabric.mcp.tools.worldborder.WorldborderTools;

/**
 * Central enumeration of every tool class. Each entry is filtered through
 * {@link ToolCompatibilityFilter}; surviving entries are instantiated and registered
 * into the {@link ToolRegistry}.
 *
 * <p>Add a new tool: create the class with {@code @McpTool}, then add it to
 * {@link #ALL_TOOL_CLASSES}. There is no classpath scan — explicit listing keeps the
 * full surface visible from a single file.
 */
public final class ToolRegistration {

    private static final Logger LOGGER = LoggerFactory.getLogger("minecraft_fabric_mcp/tools");

    private ToolRegistration() {}

    /** The full universe of tool classes. Ordered for stable registration logs. */
    public static final List<Class<? extends Tool>> ALL_TOOL_CLASSES =
            List.of(
                    // server
                    ServerGetStatusTool.class,
                    ServerGetMotdTool.class,
                    ServerSetMotdTool.class,
                    ServerSaveAllWorldsTool.class,
                    ServerReloadResourcesTool.class,
                    // level
                    LevelListDimensionsTool.class,
                    LevelGetDimensionInfoTool.class,
                    LevelGetInfoTool.class,
                    LevelGetTimeTool.class,
                    LevelSetTimeTool.class,
                    LevelGetWeatherTool.class,
                    LevelSetWeatherTool.class,
                    LevelGetDifficultyTool.class,
                    LevelSetDifficultyTool.class,
                    LevelGetSpawnPointTool.class,
                    LevelSetSpawnPointTool.class,
                    LevelPlaySoundTool.class,
                    LevelSpawnParticleTool.class,
                    LevelLightningStrikeTool.class,
                    LevelCreateExplosionTool.class,
                    LevelGetGameRuleTool.class,
                    LevelSetGameRuleTool.class,
                    LevelListGameRulesTool.class,
                    LevelGetBiomeAtTool.class,
                    LevelListBiomesInDimensionTool.class,
                    LevelPlaceFeatureTool.class,
                    LevelPlaceFeaturesBatchTool.class,
                    LevelFillBiomeTool.class,
                    // block
                    BlockTools.GetState.class,
                    BlockTools.SetState.class,
                    BlockTools.FillRegion.class,
                    BlockTools.CloneRegion.class,
                    BlockTools.ReplaceInRegion.class,
                    BlockTools.GetTopY.class,
                    BlockTools.ScanRegion.class,
                    BlockTools.FillBatch.class,
                    BlockTools.FillColumns.class,
                    BlockTools.FillColumnsStrata.class,
                    BlockTools.ErodeRegion.class,
                    BlockTools.HydraulicErodeStart.class,
                    BlockTools.HydraulicErodeStatus.class,
                    BlockTools.HydraulicErodeResult.class,
                    BlockTools.ScanSummary.class,
                    BlockTools.GetMapColor.class,
                    BlockTools.RenderRegion.class,
                    // block entity
                    BlockEntityTools.GetNbt.class,
                    BlockEntityTools.SetNbt.class,
                    BlockEntityTools.ClearInventory.class,
                    // entity
                    EntityTools.Summon.class,
                    EntityTools.Get.class,
                    EntityTools.Query.class,
                    EntityTools.GetComponents.class,
                    EntityTools.GetNbt.class,
                    EntityTools.SetNbt.class,
                    EntityTools.Teleport.class,
                    EntityTools.ApplyDamage.class,
                    EntityTools.SetVelocity.class,
                    EntityTools.ApplyEffect.class,
                    EntityTools.RemoveEffect.class,
                    EntityTools.GetEffects.class,
                    EntityTools.Kill.class,
                    EntityTools.Despawn.class,
                    EntityTools.AddTag.class,
                    EntityTools.RemoveTag.class,
                    EntityTools.ListTags.class,
                    // player
                    PlayerTools.ListOnline.class,
                    PlayerTools.GetInfo.class,
                    PlayerTools.GetInventory.class,
                    PlayerTools.GiveItem.class,
                    PlayerTools.ClearSlot.class,
                    PlayerTools.ClearAll.class,
                    PlayerTools.SetGamemode.class,
                    PlayerTools.Kick.class,
                    PlayerTools.SendMessage.class,
                    PlayerTools.SendActionbar.class,
                    PlayerTools.SendTitle.class,
                    PlayerTools.PlaySound.class,
                    PlayerTools.SetSpawn.class,
                    PlayerTools.GrantXp.class,
                    PlayerTools.SetXpLevel.class,
                    PlayerTools.SetCamera.class,
                    // inventory
                    InventoryTools.Get.class,
                    InventoryTools.SetSlot.class,
                    InventoryTools.ClearSlot.class,
                    InventoryTools.SwapSlots.class,
                    InventoryTools.CountItems.class,
                    // itemstack
                    ItemStackTools.Describe.class,
                    ItemStackTools.DropAt.class,
                    // command
                    CommandTools.Execute.class,
                    CommandTools.ExecuteAs.class,
                    CommandTools.Register.class,
                    // scoreboard
                    ScoreboardTools.ListObjectives.class,
                    ScoreboardTools.GetObjective.class,
                    ScoreboardTools.AddObjective.class,
                    ScoreboardTools.RemoveObjective.class,
                    ScoreboardTools.SetDisplaySlot.class,
                    ScoreboardTools.GetScore.class,
                    ScoreboardTools.SetScore.class,
                    ScoreboardTools.AddScore.class,
                    ScoreboardTools.ResetParticipant.class,
                    ScoreboardTools.ListTeams.class,
                    ScoreboardTools.AddTeam.class,
                    ScoreboardTools.RemoveTeam.class,
                    ScoreboardTools.TeamAddMember.class,
                    ScoreboardTools.TeamRemoveMember.class,
                    // data
                    DataTools.StorageGet.class,
                    DataTools.StorageSet.class,
                    DataTools.StorageRemove.class,
                    DataTools.StorageListNamespaces.class,
                    DataTools.AttachmentGet.class,
                    DataTools.AttachmentSet.class,
                    DataTools.AttachmentRemove.class,
                    DataTools.AttachmentListKeys.class,
                    // structure
                    StructureTools.SaveFromWorld.class,
                    StructureTools.LoadToWorld.class,
                    StructureTools.ListAll.class,
                    StructureTools.GetInfo.class,
                    StructureTools.Delete.class,
                    StructureTools.FileRead.class,
                    StructureTools.FileWrite.class,
                    StructureTools.FileList.class,
                    StructureTools.FileDelete.class,
                    // datapack
                    DatapackTools.ListAvailable.class,
                    DatapackTools.ListEnabled.class,
                    DatapackTools.Enable.class,
                    DatapackTools.Disable.class,
                    // loot/recipe/tag/resource
                    RegistryAccessTools.LootList.class,
                    RegistryAccessTools.LootGetDef.class,
                    RegistryAccessTools.LootGenerate.class,
                    RegistryAccessTools.RecipeList.class,
                    RegistryAccessTools.RecipeGetDef.class,
                    RegistryAccessTools.RecipeFindResult.class,
                    RegistryAccessTools.RecipeFindIngredient.class,
                    RegistryAccessTools.TagListInRegistry.class,
                    RegistryAccessTools.TagGetMembers.class,
                    RegistryAccessTools.TagCheckMembership.class,
                    RegistryAccessTools.BlockFamilyVariants.class,
                    RegistryAccessTools.ResourceListNs.class,
                    RegistryAccessTools.ResourceGet.class,
                    // events
                    EventsTools.Subscribe.class,
                    EventsTools.Poll.class,
                    EventsTools.ListSubscriptions.class,
                    EventsTools.Unsubscribe.class,
                    // advancement
                    AdvancementTools.Grant.class,
                    AdvancementTools.Revoke.class,
                    AdvancementTools.ListPlayer.class,
                    AdvancementTools.ListAll.class,
                    AdvancementTools.GetDefinition.class,
                    // bossbar
                    BossbarTools.ListAll.class,
                    BossbarTools.Add.class,
                    BossbarTools.Remove.class,
                    BossbarTools.Get.class,
                    BossbarTools.SetValue.class,
                    BossbarTools.SetMax.class,
                    BossbarTools.SetName.class,
                    BossbarTools.SetColor.class,
                    BossbarTools.SetStyle.class,
                    BossbarTools.SetVisible.class,
                    BossbarTools.SetPlayers.class,
                    // content registry
                    ContentRegistryTools.GetFuel.class,
                    ContentRegistryTools.SetFuel.class,
                    ContentRegistryTools.IsFlammableBlock.class,
                    ContentRegistryTools.SetFlammableBlock.class,
                    ContentRegistryTools.IsCompostable.class,
                    ContentRegistryTools.SetCompostable.class,
                    // fluid storage
                    FluidStorageTools.Get.class,
                    FluidStorageTools.ListAt.class,
                    // function
                    FunctionTools.Run.class,
                    FunctionTools.ListAll.class,
                    FunctionTools.GetDefinition.class,
                    // item modify
                    ItemModifyTools.EntitySlot.class,
                    ItemModifyTools.BlockSlot.class,
                    // player screen
                    PlayerScreenTools.OpenMenu.class,
                    PlayerScreenTools.OpenContainer.class,
                    PlayerScreenTools.Close.class,
                    // resource condition
                    ResourceConditionTools.Evaluate.class,
                    // schedule
                    ScheduleTools.FunctionTool.class,
                    ScheduleTools.Clear.class,
                    ScheduleTools.ListAll.class,
                    // worldborder
                    WorldborderTools.Get.class,
                    WorldborderTools.SetSize.class,
                    WorldborderTools.AddSize.class,
                    WorldborderTools.SetCenter.class,
                    WorldborderTools.SetWarningBlocks.class,
                    WorldborderTools.SetWarningTime.class,
                    WorldborderTools.SetDamageAmount.class,
                    WorldborderTools.SetDamageBuffer.class);

    /**
     * Build and populate a {@link ToolRegistry} for the current Minecraft target.
     * Reports the registration outcome (how many tools registered, how many filtered
     * out by version/module constraints) at INFO so the boot log is informative.
     */
    public static ToolRegistry buildRegistry(ToolCompatibilityFilter filter) {
        return buildRegistry(ALL_TOOL_CLASSES, filter);
    }

    /**
     * Build a {@link ToolRegistry} from an explicit list of tool classes. Used by the server
     * entrypoint with {@link #ALL_TOOL_CLASSES} and by the client entrypoint
     * ({@code ClientToolRegistration}) with the client-only tool list, so both surfaces share the
     * same compatibility/category filtering loop.
     */
    public static ToolRegistry buildRegistry(
            List<Class<? extends Tool>> toolClasses, ToolCompatibilityFilter filter) {
        ToolRegistry registry = new ToolRegistry();
        List<String> skipped = new ArrayList<>();
        for (Class<? extends Tool> klass : toolClasses) {
            Optional<ToolDescriptor> descOpt = filter.evaluate(klass);
            if (descOpt.isEmpty()) {
                skipped.add(klass.getSimpleName());
                continue;
            }
            try {
                Tool tool = klass.getDeclaredConstructor().newInstance();
                registry.register(descOpt.get(), tool);
            } catch (ReflectiveOperationException e) {
                LOGGER.warn("Failed to instantiate tool '{}': {}", klass.getName(), e.getMessage(), e);
            }
        }
        LOGGER.info(
                "Registered {} MCP tools ({} skipped due to version/module/category constraints)",
                registry.size(),
                skipped.size());
        return registry;
    }
}
