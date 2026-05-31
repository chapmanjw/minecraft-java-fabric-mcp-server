package com.chapmanjw.minecraft.fabric.mcp.compat;

import java.util.List;

/**
 * Snapshot of an {@link com.chapmanjw.minecraft.fabric.mcp.tools.annotations.McpTool}'s constraints,
 * extracted at registry-build time so the filter doesn't re-read the annotation per call.
 *
 * <p>{@code access} is the effective {@link ToolAccess} level computed by the filter
 * from {@code admin()}, {@code readOnly()}, and the {@link ReadOnlyHeuristic}. Use
 * {@link #readOnly()} as a convenience for "is this access {@code READ}?".
 */
public record ToolDescriptor(
        String name,
        String description,
        String minMinecraftVersion,
        String maxMinecraftVersion,
        List<RequiredModule> requiredModules,
        String requiredFabricLoaderVersion,
        ToolCategory category,
        ToolAccess access,
        Class<?> toolClass) {

    public ToolDescriptor {
        requiredModules = List.copyOf(requiredModules);
    }

    /** True when the tool's effective access is {@link ToolAccess#READ}. */
    public boolean readOnly() {
        return access == ToolAccess.READ;
    }

    /** One required Fabric API module along with its version predicate ({@code "*"} = any). */
    public record RequiredModule(String moduleId, String versionPredicate) {}
}
