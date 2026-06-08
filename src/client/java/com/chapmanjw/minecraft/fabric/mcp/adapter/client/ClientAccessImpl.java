package com.chapmanjw.minecraft.fabric.mcp.adapter.client;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import java.util.function.Supplier;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.NativeImage;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import com.chapmanjw.minecraft.fabric.mcp.adapter.ClientAccess;
import com.chapmanjw.minecraft.fabric.mcp.runtime.MinecraftMainThreadExecutor;

/**
 * Production {@link ClientAccess} backed by the running {@link Minecraft} client.
 *
 * <p>This is the <em>only</em> class in the mod that imports {@code net.minecraft.client.*} /
 * {@code com.mojang.blaze3d.*}. It is instantiated solely from the client entrypoint
 * ({@code McpClientMod}); nothing on the dedicated-server code path references it, so a headless
 * server never classloads these client/render types.
 *
 * <p>Every method marshals onto the client thread via a {@link MinecraftMainThreadExecutor}
 * attached to {@code Minecraft.getInstance()::execute} — the client-thread analog of the server
 * mod's main-thread executor. Reading the framebuffer or world state from an HTTP thread is
 * undefined behavior, exactly as on the server.
 *
 * <p><strong>Mappings note.</strong> The mod targets Minecraft with official (Mojang) mappings.
 * The render/capture symbols used here ({@link Screenshot#takeScreenshot}, {@link RenderTarget},
 * {@link NativeImage}) were verified by {@code javap} against the client jars of all three build
 * targets — 1.21.11, 26.1.1, and 26.1.2 — and are identical across them (no per-version split
 * needed). Notably, the only capture entry point is the callback form
 * {@code takeScreenshot(RenderTarget, [int downScale,] Consumer<NativeImage>)}, and
 * {@link NativeImage} has no in-memory byte export on these versions — only {@code writeToFile} —
 * so PNG bytes are obtained via a temp file. All client coupling is localized to this file behind
 * the stable {@link ClientAccess} interface.
 */
public final class ClientAccessImpl implements ClientAccess {

    private final MinecraftMainThreadExecutor clientExecutor;
    private final ObjectMapper mapper;

    public ClientAccessImpl(MinecraftMainThreadExecutor clientExecutor, ObjectMapper mapper) {
        this.clientExecutor = clientExecutor;
        this.mapper = mapper;
    }

    @Override
    public boolean inGame() {
        return onClient(
                () -> {
                    Minecraft mc = Minecraft.getInstance();
                    return mc != null && mc.level != null && mc.player != null;
                });
    }

    /** Milliseconds to let the client render menu-free frames after closing a screen. */
    private static final long SCREEN_SETTLE_MS = 250L;

    @Override
    public byte[] capturePng(int downscale, boolean closeScreen) {
        // Optionally dismiss any open screen (the pause/Esc menu opens on focus loss, so an
        // alt-tabbed client otherwise captures the Game Menu over the world). Closing it on the
        // client thread, then giving the render loop a moment, yields a clean world frame. In
        // multiplayer the world keeps ticking/rendering while unfocused, so settling works even
        // when the window isn't focused (it must not be minimized).
        if (closeScreen) {
            onClient(
                    () -> {
                        Minecraft mc = Minecraft.getInstance();
                        if (mc != null && mc.screen != null) {
                            mc.setScreen(null);
                        }
                        return null;
                    });
            try {
                Thread.sleep(SCREEN_SETTLE_MS);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            }
        }
        // 26.1's screenshot path is ASYNC (verified against the client jars): takeScreenshot
        // enqueues a GPU texture->buffer copy via CommandEncoder.copyTextureToBuffer(..., Runnable)
        // and invokes the Consumer<NativeImage> only when the readback completes — on a LATER frame
        // on the render thread, not synchronously inside the call. So we schedule the capture on the
        // client thread, let the consumer complete a future, and wait for it from the (HTTP) caller
        // thread. NativeImage has no in-memory byte export on these versions (only writeToFile), so
        // the consumer round-trips a temp PNG to bytes. We capture at NATIVE resolution and do our
        // own nearest-neighbor downscale: the built-in takeScreenshot(fb, int, ...) overload requires
        // the framebuffer dims be divisible by the factor and throws "Image size is not divisible by
        // downscale factor" otherwise, which fails for odd window sizes.
        CompletableFuture<byte[]> result = new CompletableFuture<>();
        clientExecutor
                .submit(
                        () -> {
                            Minecraft mc = Minecraft.getInstance();
                            if (mc == null || mc.level == null || mc.player == null) {
                                result.complete(null);
                                return null;
                            }
                            RenderTarget fb = mc.getMainRenderTarget();
                            if (fb == null) {
                                result.complete(null);
                                return null;
                            }
                            int ds = Math.max(1, downscale);
                            Consumer<NativeImage> onImage =
                                    img -> {
                                        if (img == null) {
                                            result.complete(null);
                                            return;
                                        }
                                        NativeImage scaled = null;
                                        try {
                                            NativeImage toWrite = img;
                                            if (ds > 1) {
                                                int w = Math.max(1, img.getWidth() / ds);
                                                int h = Math.max(1, img.getHeight() / ds);
                                                scaled = new NativeImage(w, h, false);
                                                for (int yy = 0; yy < h; yy++) {
                                                    for (int xx = 0; xx < w; xx++) {
                                                        scaled.setPixel(xx, yy, img.getPixel(xx * ds, yy * ds));
                                                    }
                                                }
                                                toWrite = scaled;
                                            }
                                            Path tmp = Files.createTempFile("mcp-view-", ".png");
                                            try {
                                                toWrite.writeToFile(tmp);
                                                result.complete(Files.readAllBytes(tmp));
                                            } finally {
                                                Files.deleteIfExists(tmp);
                                            }
                                        } catch (IOException e) {
                                            result.completeExceptionally(e);
                                        } finally {
                                            if (scaled != null) {
                                                scaled.close();
                                            }
                                            img.close();
                                        }
                                    };
                            Screenshot.takeScreenshot(fb, onImage);
                            return null;
                        })
                // If merely scheduling failed (e.g. client thread not attached), fail the result so
                // the caller doesn't block until timeout. A normal completion here is expected long
                // before the async readback fires, so don't complete `result` on success.
                .whenComplete(
                        (ignored, ex) -> {
                            if (ex != null) {
                                result.completeExceptionally(ex);
                            }
                        });
        try {
            return result.get(10, java.util.concurrent.TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            throw new IllegalStateException("Frame capture timed out (no GPU readback within 10s)", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for frame capture", e);
        } catch (java.util.concurrent.ExecutionException e) {
            Throwable c = e.getCause() != null ? e.getCause() : e;
            throw new IllegalStateException("Frame capture failed: " + c.getMessage(), c);
        }
    }

    @Override
    public JsonNode status() {
        return onClient(
                () -> {
                    Minecraft mc = Minecraft.getInstance();
                    ObjectNode o = mapper.createObjectNode();
                    boolean inGame = mc != null && mc.level != null && mc.player != null;
                    o.put("in_game", inGame);
                    o.put("singleplayer", mc != null && mc.isLocalServer());
                    ServerData sd = mc == null ? null : mc.getCurrentServer();
                    o.put(
                            "server",
                            sd != null ? sd.ip : (mc != null && mc.isLocalServer() ? "singleplayer" : "unknown"));
                    if (!inGame) {
                        return o;
                    }
                    o.put("dimension", mc.level.dimension().identifier().toString());
                    o.put("player_name", mc.player.getGameProfile().name());
                    Vec3 p = mc.player.position();
                    putPos(o.putObject("pos"), p.x, p.y, p.z);
                    o.put("yaw", round(mc.player.getYRot()));
                    o.put("pitch", round(mc.player.getXRot()));
                    o.put("health", round(mc.player.getHealth()));
                    o.put("food", mc.player.getFoodData().getFoodLevel());
                    ItemStack held = mc.player.getMainHandItem();
                    o.put("held_item", itemId(held));
                    o.put("held_count", held.getCount());
                    return o;
                });
    }

    @Override
    public JsonNode crosshair() {
        return onClient(
                () -> {
                    Minecraft mc = Minecraft.getInstance();
                    ObjectNode o = mapper.createObjectNode();
                    if (mc == null || mc.level == null) {
                        o.put("type", "NONE");
                        return o;
                    }
                    return formatHit(mc.hitResult);
                });
    }

    @Override
    public JsonNode raycast(double maxDistance, boolean includeFluids) {
        return onClient(
                () -> {
                    Minecraft mc = Minecraft.getInstance();
                    if (mc == null || mc.player == null || mc.level == null) {
                        ObjectNode o = mapper.createObjectNode();
                        o.put("type", "NONE");
                        return o;
                    }
                    // Verified (javap, all 3 targets): Entity.pick(double reach, float partialTick,
                    // boolean fluids) -> HitResult.
                    HitResult hr = mc.player.pick(maxDistance, 1.0F, includeFluids);
                    return formatHit(hr);
                });
    }

    @Override
    public JsonNode nearbyEntities(double radius, String typeFilter) {
        return onClient(
                () -> {
                    Minecraft mc = Minecraft.getInstance();
                    ObjectNode o = mapper.createObjectNode();
                    ArrayNode arr = o.putArray("entities");
                    if (mc == null || mc.level == null || mc.player == null) {
                        o.put("count", 0);
                        return o;
                    }
                    Vec3 me = mc.player.position();
                    String filter = (typeFilter == null || typeFilter.isBlank()) ? null
                            : typeFilter.toLowerCase(java.util.Locale.ROOT);
                    for (Entity e : mc.level.entitiesForRendering()) {
                        if (e == mc.player) {
                            continue;
                        }
                        double dist = Math.sqrt(e.position().distanceToSqr(me));
                        if (dist > radius) {
                            continue;
                        }
                        String id = BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).toString();
                        if (filter != null && !id.toLowerCase(java.util.Locale.ROOT).contains(filter)) {
                            continue;
                        }
                        ObjectNode en = arr.addObject();
                        en.put("type", id);
                        en.put("name", e.getName().getString());
                        Vec3 ep = e.position();
                        putPos(en.putObject("pos"), ep.x, ep.y, ep.z);
                        en.put("distance", round(dist));
                    }
                    o.put("count", arr.size());
                    return o;
                });
    }

    @Override
    public JsonNode screen() {
        return onClient(
                () -> {
                    Minecraft mc = Minecraft.getInstance();
                    ObjectNode o = mapper.createObjectNode();
                    Screen s = mc == null ? null : mc.screen;
                    o.put("screen_open", s != null);
                    if (s != null) {
                        o.put("screen_class", s.getClass().getSimpleName());
                        o.put("title", s.getTitle() == null ? "" : s.getTitle().getString());
                    }
                    AbstractContainerMenu menu = (mc != null && mc.player != null) ? mc.player.containerMenu : null;
                    boolean containerOpen =
                            menu != null && mc.player != null && menu != mc.player.inventoryMenu;
                    o.put("container_open", containerOpen);
                    if (containerOpen) {
                        ArrayNode items = o.putArray("container_items");
                        for (Slot slot : menu.slots) {
                            ItemStack st = slot.getItem();
                            if (st.isEmpty()) {
                                continue;
                            }
                            ObjectNode it = items.addObject();
                            it.put("slot", slot.index);
                            it.put("item", itemId(st));
                            it.put("count", st.getCount());
                        }
                    }
                    return o;
                });
    }

    // --- helpers -------------------------------------------------------------

    private ObjectNode formatHit(HitResult hr) {
        ObjectNode o = mapper.createObjectNode();
        if (hr == null) {
            o.put("type", "NONE");
            return o;
        }
        HitResult.Type type = hr.getType();
        o.put("type", type.name());
        Minecraft mc = Minecraft.getInstance();
        if (type == HitResult.Type.BLOCK && hr instanceof BlockHitResult bhr) {
            BlockPos bp = bhr.getBlockPos();
            ObjectNode pos = o.putObject("block_pos");
            pos.put("x", bp.getX());
            pos.put("y", bp.getY());
            pos.put("z", bp.getZ());
            o.put("face", bhr.getDirection().name());
            if (mc != null && mc.level != null) {
                BlockState st = mc.level.getBlockState(bp);
                o.put("block", BuiltInRegistries.BLOCK.getKey(st.getBlock()).toString());
            }
        } else if (type == HitResult.Type.ENTITY && hr instanceof EntityHitResult ehr) {
            Entity e = ehr.getEntity();
            o.put("entity_type", BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).toString());
            o.put("entity_name", e.getName().getString());
        }
        return o;
    }

    private static void putPos(ObjectNode pos, double x, double y, double z) {
        pos.put("x", round(x));
        pos.put("y", round(y));
        pos.put("z", round(z));
    }

    private static String itemId(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return "minecraft:air";
        }
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
    }

    private static double round(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    private <T> T onClient(Supplier<T> work) {
        try {
            return clientExecutor.submitBlocking(work);
        } catch (TimeoutException e) {
            throw new IllegalStateException("Client thread timed out", e);
        } catch (MinecraftMainThreadExecutor.MainThreadWorkException e) {
            Throwable cause = e.getCause();
            throw new IllegalStateException(
                    cause != null && cause.getMessage() != null ? cause.getMessage() : e.getMessage(), cause);
        }
    }
}
