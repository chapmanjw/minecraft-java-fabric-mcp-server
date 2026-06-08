package com.chapmanjw.minecraft.fabric.mcp.tools;

import java.util.List;

import com.chapmanjw.minecraft.fabric.mcp.compat.ToolCompatibilityFilter;
import com.chapmanjw.minecraft.fabric.mcp.protocol.Tool;
import com.chapmanjw.minecraft.fabric.mcp.protocol.ToolRegistry;
import com.chapmanjw.minecraft.fabric.mcp.tools.client.ClientTools;

/**
 * The client-only tool universe — the {@code client} category. Kept separate from
 * {@link ToolRegistration#ALL_TOOL_CLASSES} so that the dedicated-server boot path never
 * references these classes (and therefore never classloads anything client-coupled). This class
 * is loaded solely from the client entrypoint ({@code McpClientMod}).
 *
 * <p>The tool classes themselves carry no {@code net.minecraft.client.*} imports — they reach the
 * client through the {@code ClientAccess} seam — but isolating the list here keeps the boundary
 * unambiguous and easy to audit.
 */
public final class ClientToolRegistration {

    private ClientToolRegistration() {}

    /** Every client-only inspection tool. Add new client tools here. */
    public static final List<Class<? extends Tool>> CLIENT_TOOL_CLASSES =
            List.of(
                    ClientTools.ViewCapture.class,
                    ClientTools.ClientStatus.class,
                    ClientTools.SenseCrosshair.class,
                    ClientTools.SenseRaycast.class,
                    ClientTools.SenseEntities.class,
                    ClientTools.SenseScreen.class);

    /** Build the client-only registry, sharing {@link ToolRegistration}'s filter loop. */
    public static ToolRegistry buildRegistry(ToolCompatibilityFilter filter) {
        return ToolRegistration.buildRegistry(CLIENT_TOOL_CLASSES, filter);
    }
}
