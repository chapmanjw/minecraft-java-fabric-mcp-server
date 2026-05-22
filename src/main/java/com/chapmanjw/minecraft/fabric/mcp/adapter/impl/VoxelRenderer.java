package com.chapmanjw.minecraft.fabric.mcp.adapter.impl;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import javax.imageio.ImageIO;

/**
 * Pure, headless voxel-to-PNG renderer: a colour grid in, PNG bytes out. Mirrors
 * the plugin's Python iso/ortho renderer so that a design-time model render and a
 * built-world render read the same way. No client, display, or render pipeline
 * needed — it draws with {@link BufferedImage} + AWT2D, which work on a dedicated
 * server JVM.
 *
 * <p>{@code colors} is a flat grid indexed {@code (x*ny + y)*nz + z}: {@code 0}
 * means air/empty, any other value is a solid voxel coloured {@code 0xRRGGBB}.
 */
final class VoxelRenderer {

    private VoxelRenderer() {}

    private static final int BG = 0xC8D6E4; // light slate background
    private static final int MAX_CHANNEL = 255;
    private static final int BYTE_MASK = 0xFF;
    private static final double SHADE_TOP = 1.0;
    private static final double SHADE_LEFT = 0.66;
    private static final double SHADE_RIGHT = 0.50;
    private static final int PAD = 8;
    private static final int FLAT_Z = 2;
    private static final int FLAT_X = 0;

    static byte[] render(int[] colors, int nx, int ny, int nz, String view, int scale) {
        String v = view == null ? "iso" : view.toLowerCase(Locale.ROOT);
        BufferedImage img;
        switch (v) {
            case "side":
                img = ortho(colors, nx, ny, nz, FLAT_Z, Math.max(1, scale));
                break;
            case "front":
                img = ortho(colors, nx, ny, nz, FLAT_X, Math.max(1, scale));
                break;
            case "top":
                img = top(colors, nx, ny, nz, Math.max(1, scale));
                break;
            default:
                img = iso(colors, nx, ny, nz, Math.max(2, scale));
                break;
        }
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(img, "png", out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static int idx(int x, int y, int z, int ny, int nz) {
        return (x * ny + y) * nz + z;
    }

    private static boolean surface(int[] c, int x, int y, int z, int nx, int ny, int nz) {
        if (c[idx(x, y, z, ny, nz)] == 0) {
            return false;
        }
        int[][] d = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
        for (int[] dd : d) {
            int xx = x + dd[0];
            int yy = y + dd[1];
            int zz = z + dd[2];
            if (xx < 0 || yy < 0 || zz < 0 || xx >= nx || yy >= ny || zz >= nz) {
                return true; // exposed at the grid border
            }
            if (c[idx(xx, yy, zz, ny, nz)] == 0) {
                return true;
            }
        }
        return false;
    }

    private static int shade(int rgb, double f) {
        int r = (int) Math.min(MAX_CHANNEL, ((rgb >> 16) & BYTE_MASK) * f);
        int g = (int) Math.min(MAX_CHANNEL, ((rgb >> 8) & BYTE_MASK) * f);
        int b = (int) Math.min(MAX_CHANNEL, (rgb & BYTE_MASK) * f);
        return (r << 16) | (g << 8) | b;
    }

    /** Isometric 3/4 view: painter's algorithm, top/left/right face shading. */
    private static BufferedImage iso(int[] c, int nx, int ny, int nz, int a) {
        int th = Math.max(1, a / 2);
        int vh = a;
        List<int[]> pts = new ArrayList<>();
        int minx = Integer.MAX_VALUE;
        int maxx = Integer.MIN_VALUE;
        int miny = Integer.MAX_VALUE;
        int maxy = Integer.MIN_VALUE;
        for (int x = 0; x < nx; x++) {
            for (int y = 0; y < ny; y++) {
                for (int z = 0; z < nz; z++) {
                    if (!surface(c, x, y, z, nx, ny, nz)) {
                        continue;
                    }
                    int zz = nz - 1 - z; // flipz
                    int sx = (x - zz) * a;
                    int sy = (x + zz) * th - y * vh;
                    minx = Math.min(minx, sx - a);
                    maxx = Math.max(maxx, sx + a);
                    miny = Math.min(miny, sy - vh);
                    maxy = Math.max(maxy, sy + th);
                    pts.add(new int[] {x, y, z});
                }
            }
        }
        if (pts.isEmpty()) {
            return blank();
        }
        int w = maxx - minx + PAD;
        int h = maxy - miny + PAD;
        int ox = -minx + PAD / 2;
        int oy = -miny + PAD / 2;
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(new Color(BG));
        g.fillRect(0, 0, w, h);
        // far first: depth = (x + zz)*2 - y
        pts.sort((p, q) -> {
            int dp = (p[0] + (nz - 1 - p[2])) * 2 - p[1];
            int dq = (q[0] + (nz - 1 - q[2])) * 2 - q[1];
            return Integer.compare(dp, dq);
        });
        for (int[] p : pts) {
            int x = p[0];
            int y = p[1];
            int z = p[2];
            int rgb = c[idx(x, y, z, ny, nz)];
            int zz = nz - 1 - z;
            int sx = (x - zz) * a + ox;
            int sy = (x + zz) * th - y * vh + oy;
            int[] tx = {sx, sx + a, sx, sx - a};
            int[] ty = {sy - vh, sy - vh + th, sy - vh + 2 * th, sy - vh + th};
            int[] lx = {sx - a, sx, sx, sx - a};
            int[] ly = {sy - vh + th, sy - vh + 2 * th, sy + 2 * th, sy + th};
            int[] rx = {sx, sx + a, sx + a, sx};
            int[] ry = {sy - vh + 2 * th, sy - vh + th, sy + th, sy + 2 * th};
            g.setColor(new Color(shade(rgb, SHADE_LEFT)));
            g.fillPolygon(lx, ly, 4);
            g.setColor(new Color(shade(rgb, SHADE_RIGHT)));
            g.fillPolygon(rx, ry, 4);
            g.setColor(new Color(shade(rgb, SHADE_TOP)));
            g.fillPolygon(tx, ty, 4);
        }
        g.dispose();
        return img;
    }

    /** Flat orthographic: flatten axis 2 (z → side, x×y) or 0 (x → front, z×y). */
    private static BufferedImage ortho(int[] c, int nx, int ny, int nz, int flat, int px) {
        int cols = (flat == FLAT_Z) ? nx : nz;
        int rows = ny;
        BufferedImage img = new BufferedImage(cols * px, rows * px, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(new Color(BG));
        g.fillRect(0, 0, cols * px, rows * px);
        for (int col = 0; col < cols; col++) {
            for (int row = 0; row < rows; row++) {
                int rgb = 0;
                if (flat == FLAT_Z) { // side: outermost along z at (x=col, y=row)
                    for (int z = nz - 1; z >= 0; z--) {
                        int val = c[idx(col, row, z, ny, nz)];
                        if (val != 0) {
                            rgb = val;
                            break;
                        }
                    }
                } else { // front: outermost along x at (z=col, y=row)
                    for (int x = nx - 1; x >= 0; x--) {
                        int val = c[idx(x, row, col, ny, nz)];
                        if (val != 0) {
                            rgb = val;
                            break;
                        }
                    }
                }
                if (rgb == 0) {
                    continue;
                }
                int rr = rows - 1 - row; // +y reads upward
                g.setColor(new Color(rgb));
                g.fillRect(col * px, rr * px, px, px);
            }
        }
        g.dispose();
        return img;
    }

    /** Top plan view: flatten y, topmost solid per (x, z). */
    private static BufferedImage top(int[] c, int nx, int ny, int nz, int px) {
        BufferedImage img = new BufferedImage(nx * px, nz * px, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(new Color(BG));
        g.fillRect(0, 0, nx * px, nz * px);
        for (int x = 0; x < nx; x++) {
            for (int z = 0; z < nz; z++) {
                int rgb = 0;
                for (int y = ny - 1; y >= 0; y--) {
                    int val = c[idx(x, y, z, ny, nz)];
                    if (val != 0) {
                        rgb = val;
                        break;
                    }
                }
                if (rgb == 0) {
                    continue;
                }
                g.setColor(new Color(rgb));
                g.fillRect(x * px, z * px, px, px);
            }
        }
        g.dispose();
        return img;
    }

    private static BufferedImage blank() {
        BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(new Color(BG));
        g.fillRect(0, 0, 16, 16);
        g.dispose();
        return img;
    }
}
