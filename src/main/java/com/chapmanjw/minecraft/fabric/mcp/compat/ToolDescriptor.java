package com.chapmanjw.minecraft.fabric.mcp.compat;

import java.util.List;

/**
 * Snapshot of an {@link com.chapmanjw.minecraft.fabric.mcp.tools.annotations.McpTool}'s constraints,
 * extracted at registry-build time so the filter doesn't re-read the annotation per call.
 */
public record ToolDescriptor(
        String name,
        String description,
        String minMinecraftVersion,
        String maxMinecraftVersion,
        List<RequiredModule> requiredModules,
        String requiredFabricLoaderVersion,
        ToolCategory category,
        boolean readOnly,
        Class<?> toolClass) {

    public ToolDescriptor {
        requiredModules = List.copyOf(requiredModules);
    }

    /** One required Fabric API module along with its version predicate ({@code "*"} = any). */
    public record RequiredModule(String moduleId, String versionPredicate) {}
}
