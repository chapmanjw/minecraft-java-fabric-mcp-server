package com.chapmanjw.minecraft.fabric.mcp.adapter;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * The single seam between client-only tools and the running Minecraft <em>client</em>.
 *
 * <p>This is the client-side cousin of {@link MinecraftAdapter}. Where {@code MinecraftAdapter}
 * exposes the dedicated-server world API, {@code ClientAccess} exposes what a real, rendered
 * client can do that a headless server structurally cannot: capture the framebuffer the player
 * actually sees, and read client-side perception (the crosshair target, a view raycast, nearby
 * entities, the open screen). It is the "fill the gaps" surface for in-game inspection.
 *
 * <p><strong>No {@code net.minecraft.client.*} types appear in this interface on purpose.</strong>
 * Every method returns plain JSON / primitives / bytes. That keeps this interface — and therefore
 * {@link com.chapmanjw.minecraft.fabric.mcp.protocol.ToolContext}, which holds a reference to it —
 * loadable on a dedicated server with no client classes on the classpath. The implementation
 * ({@code adapter.client.ClientAccessImpl}) is the only place that imports client/render classes,
 * and it is instantiated solely from the client entrypoint ({@code McpClientMod}).
 *
 * <p>All reads marshal onto the client thread internally (see the implementation), mirroring how
 * server tools marshal onto the server main thread via {@code BaseTool.onMainThread}.
 */
public interface ClientAccess {

    /** True when the client is connected to a world (integrated or remote) with a local player. */
    boolean inGame();

    /**
     * Capture the local player's current first-person frame as PNG bytes — the real client
     * render (textures, lighting, sky, fog, entities), i.e. exactly what the human sees.
     *
     * @param downscale integer downscale factor applied to the captured frame; {@code 1} = native
     *     framebuffer resolution. Values &gt; 1 reduce resolution to keep the inline image small.
     * @param closeScreen when true, dismiss any open client screen (e.g. the pause/Esc menu that
     *     opens on focus loss) and let a clean frame render before capturing, so the result shows
     *     the world rather than a GUI overlay.
     * @return PNG bytes, or {@code null} if the client is not in a world / no frame is available.
     */
    byte[] capturePng(int downscale, boolean closeScreen);

    /**
     * Local player + session status: position, facing (yaw/pitch), dimension, health, hunger,
     * held item, gamemode, and the server address (or "singleplayer"). Read-only.
     */
    JsonNode status();

    /** What the crosshair is currently pointing at: MISS, a block (pos + state), or an entity. */
    JsonNode crosshair();

    /**
     * Raycast from the player's eye along the current facing.
     *
     * @param maxDistance reach in blocks
     * @param includeFluids whether fluid surfaces count as hits
     */
    JsonNode raycast(double maxDistance, boolean includeFluids);

    /**
     * Entities the client currently knows about within {@code radius} of the player, optionally
     * filtered by a (case-insensitive substring of the) entity-type id.
     */
    JsonNode nearbyEntities(double radius, String typeFilter);

    /**
     * The current GUI/screen state: the open {@code Screen} (if any) and, when a container menu
     * other than the player inventory is open, a summary of its slot contents.
     */
    JsonNode screen();
}
