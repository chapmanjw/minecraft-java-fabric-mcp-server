package com.chapmanjw.minecraft.fabric.mcp.compat;

import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.Version;

/**
 * Snapshot of the Fabric environment at startup. Captures the running Minecraft
 * version, Fabric Loader version, and every loaded mod's version. Used by
 * {@link ToolCompatibilityFilter} to evaluate {@code @McpTool} constraints.
 *
 * <p>This is captured once (server-starting hook) and then read-only — the set of
 * loaded mods cannot change after the server starts, so refreshing it would only mask
 * bugs.
 */
public record McEnvironment(
        String minecraftVersion, String fabricLoaderVersion, Map<String, String> loadedMods) {

    public McEnvironment {
        loadedMods = Map.copyOf(loadedMods);
    }

    /** Returns the version string of a loaded mod, or empty if it is not present. */
    public Optional<String> moduleVersion(String moduleId) {
        return Optional.ofNullable(loadedMods.get(moduleId));
    }

    public boolean hasModule(String moduleId) {
        return loadedMods.containsKey(moduleId);
    }

    /**
     * Capture the current environment from Fabric Loader. Call once at SERVER_STARTING.
     */
    public static McEnvironment capture() {
        FabricLoader loader = FabricLoader.getInstance();
        String mcVersion = readMcVersion(loader);
        String loaderVersion =
                loader.getModContainer("fabricloader")
                        .map(ModContainer::getMetadata)
                        .map(m -> m.getVersion().getFriendlyString())
                        .orElse("unknown");

        Map<String, String> mods = new TreeMap<>();
        for (ModContainer container : loader.getAllMods()) {
            Version v = container.getMetadata().getVersion();
            mods.put(container.getMetadata().getId(), v.getFriendlyString());
        }
        return new McEnvironment(mcVersion, loaderVersion, mods);
    }

    private static String readMcVersion(FabricLoader loader) {
        return loader.getModContainer("minecraft")
                .map(ModContainer::getMetadata)
                .map(m -> m.getVersion().getFriendlyString())
                .orElse("unknown");
    }
}
