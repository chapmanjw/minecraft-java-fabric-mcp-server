package com.chapmanjw.minecraft.fabric.mcp.tools.resourcecondition;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import com.chapmanjw.minecraft.fabric.mcp.adapter.MinecraftAdapter;
import com.chapmanjw.minecraft.fabric.mcp.protocol.Schemas;
import com.chapmanjw.minecraft.fabric.mcp.protocol.ToolContext;
import com.chapmanjw.minecraft.fabric.mcp.protocol.ToolResult;
import com.chapmanjw.minecraft.fabric.mcp.tools.BaseTool;
import com.chapmanjw.minecraft.fabric.mcp.tools.annotations.McpTool;

/** Evaluates a Fabric {@code ResourceCondition} JSON object against the live registry. */
public final class ResourceConditionTools {

    private ResourceConditionTools() {}

    @McpTool(
            name = "resource_condition_evaluate",
            description =
                    "Parses a Fabric ResourceCondition JSON object and evaluates it against the"
                            + " server's registry context. Returns matches + decoded condition id.",
            requiredFabricModules = {"fabric-resource-conditions-api-v1"})
    public static final class Evaluate extends BaseTool {
        private static final JsonNode SCHEMA =
                Schemas.object()
                        .required(
                                "condition_json",
                                Schemas.string("Serialized ResourceCondition JSON object"))
                        .build();

        public Evaluate() {
            super("resource_condition_evaluate");
        }

        @Override
        public JsonNode inputSchema() {
            return SCHEMA;
        }

        @Override
        public ToolResult execute(JsonNode arguments, ToolContext context) {
            String json = reader(arguments).requireString("condition_json");
            return onMainThread(
                    context,
                    ignored -> {
                        MinecraftAdapter.ResourceConditionResult res =
                                context.adapter().resourceConditionEvaluate(json);
                        ObjectNode n = context.mapper().createObjectNode();
                        n.put("matches", res.matches());
                        n.put("condition_id", res.conditionId());
                        return ToolResult.ofToon(n);
                    });
        }
    }
}
