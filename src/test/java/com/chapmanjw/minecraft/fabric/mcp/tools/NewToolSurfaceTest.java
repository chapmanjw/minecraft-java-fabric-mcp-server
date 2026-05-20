package com.chapmanjw.minecraft.fabric.mcp.tools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;

import org.junit.jupiter.api.Test;

import com.chapmanjw.minecraft.fabric.mcp.protocol.Tool;
import com.chapmanjw.minecraft.fabric.mcp.tools.advancement.AdvancementTools;
import com.chapmanjw.minecraft.fabric.mcp.tools.annotations.McpTool;
import com.chapmanjw.minecraft.fabric.mcp.tools.bossbar.BossbarTools;
import com.chapmanjw.minecraft.fabric.mcp.tools.contentregistry.ContentRegistryTools;
import com.chapmanjw.minecraft.fabric.mcp.tools.fluidstorage.FluidStorageTools;
import com.chapmanjw.minecraft.fabric.mcp.tools.function.FunctionTools;
import com.chapmanjw.minecraft.fabric.mcp.tools.itemmodify.ItemModifyTools;
import com.chapmanjw.minecraft.fabric.mcp.tools.playerscreen.PlayerScreenTools;
import com.chapmanjw.minecraft.fabric.mcp.tools.resourcecondition.ResourceConditionTools;
import com.chapmanjw.minecraft.fabric.mcp.tools.schedule.ScheduleTools;
import com.chapmanjw.minecraft.fabric.mcp.tools.worldborder.WorldborderTools;

/**
 * Schema-level smoke coverage for every newly-introduced tool class.
 *
 * <p>Verifies that each class:
 * <ol>
 *   <li>Carries an {@link McpTool} annotation with a non-empty name and description.</li>
 *   <li>Can be instantiated via the no-arg constructor used by the runtime registrar.</li>
 *   <li>Publishes a non-null JSON Schema with {@code type: object} at the root.</li>
 * </ol>
 *
 * <p>End-to-end behavior is integration-tested through the running mod; these tests
 * exist to prevent the wire surface from silently regressing when a tool class is
 * refactored.
 */
class NewToolSurfaceTest {

    /** Every new tool class added by this milestone. Sorted by domain for readability. */
    private static final List<Class<? extends Tool>> TOOL_CLASSES =
            List.of(
                    AdvancementTools.Grant.class,
                    AdvancementTools.Revoke.class,
                    AdvancementTools.ListPlayer.class,
                    AdvancementTools.ListAll.class,
                    AdvancementTools.GetDefinition.class,
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
                    ContentRegistryTools.GetFuel.class,
                    ContentRegistryTools.SetFuel.class,
                    ContentRegistryTools.IsFlammableBlock.class,
                    ContentRegistryTools.SetFlammableBlock.class,
                    ContentRegistryTools.IsCompostable.class,
                    ContentRegistryTools.SetCompostable.class,
                    FluidStorageTools.Get.class,
                    FluidStorageTools.ListAt.class,
                    FunctionTools.Run.class,
                    FunctionTools.ListAll.class,
                    FunctionTools.GetDefinition.class,
                    ItemModifyTools.EntitySlot.class,
                    ItemModifyTools.BlockSlot.class,
                    PlayerScreenTools.OpenMenu.class,
                    PlayerScreenTools.OpenContainer.class,
                    PlayerScreenTools.Close.class,
                    ResourceConditionTools.Evaluate.class,
                    ScheduleTools.FunctionTool.class,
                    ScheduleTools.Clear.class,
                    ScheduleTools.ListAll.class,
                    WorldborderTools.Get.class,
                    WorldborderTools.SetSize.class,
                    WorldborderTools.AddSize.class,
                    WorldborderTools.SetCenter.class,
                    WorldborderTools.SetWarningBlocks.class,
                    WorldborderTools.SetWarningTime.class,
                    WorldborderTools.SetDamageAmount.class,
                    WorldborderTools.SetDamageBuffer.class);

    @Test
    void everyNewToolHasAnnotation() {
        for (Class<? extends Tool> klass : TOOL_CLASSES) {
            McpTool meta = klass.getAnnotation(McpTool.class);
            assertNotNull(meta, klass.getName() + " is missing @McpTool");
            assertTrue(!meta.name().isBlank(), klass.getName() + " has blank name");
            assertTrue(!meta.description().isBlank(), klass.getName() + " has blank description");
        }
    }

    @Test
    void everyNewToolInstantiatesAndPublishesSchema() throws Exception {
        for (Class<? extends Tool> klass : TOOL_CLASSES) {
            Tool tool = klass.getDeclaredConstructor().newInstance();
            JsonNode schema = tool.inputSchema();
            assertNotNull(schema, klass.getName() + " published null schema");
            assertEquals(
                    "object",
                    schema.path("type").asText(),
                    klass.getName() + " schema root is not type=object");
        }
    }

    @Test
    void contentRegistryTools_RequireItemOrBlockId() {
        // Spot-check three representative tools: the schema-DSL should declare required
        // properties matching the documented surface.
        JsonNode getFuelSchema = instantiate(ContentRegistryTools.GetFuel.class).inputSchema();
        assertEquals(1, getFuelSchema.path("required").size());
        assertEquals("item_id", getFuelSchema.path("required").get(0).asText());

        JsonNode isFlammable = instantiate(ContentRegistryTools.IsFlammableBlock.class).inputSchema();
        assertEquals("block_id", isFlammable.path("required").get(0).asText());

        JsonNode setCompost = instantiate(ContentRegistryTools.SetCompostable.class).inputSchema();
        // Required: item_id + chance
        JsonNode req = setCompost.path("required");
        assertEquals(2, req.size());
    }

    @Test
    void bossbarColorEnumLocked() {
        JsonNode schema = instantiate(BossbarTools.SetColor.class).inputSchema();
        JsonNode colors = schema.path("properties").path("color").path("enum");
        assertTrue(colors.isArray());
        // Vanilla locks the wire vocabulary; if this list shrinks, downstream clients
        // break — the test exists so a refactor can't silently drop one.
        assertEquals(7, colors.size());
    }

    @Test
    void worldborderSetSizeHasOptionalTimeSeconds() {
        JsonNode schema = instantiate(WorldborderTools.SetSize.class).inputSchema();
        // required = {dimension, size} — time_seconds is optional
        JsonNode req = schema.path("required");
        assertEquals(2, req.size());
        // Property must still appear under properties even when optional.
        assertNotNull(schema.path("properties").get("time_seconds"));
        assertNull(schema.path("properties").get("nonexistent"));
    }

    @Test
    void advancementGrantModeEnumValuesMatchVanilla() {
        JsonNode schema = instantiate(AdvancementTools.Grant.class).inputSchema();
        JsonNode mode = schema.path("properties").path("mode").path("enum");
        assertTrue(mode.isArray());
        assertEquals(5, mode.size());
    }

    private static Tool instantiate(Class<? extends Tool> klass) {
        try {
            return klass.getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(klass.getName(), e);
        }
    }
}
