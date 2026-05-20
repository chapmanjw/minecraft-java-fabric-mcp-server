package com.chapmanjw.minecraft.fabric.mcp.adapter.dto;

/**
 * Inclusive integer bounding box. Both corners belong to the box, so a 1×1×1 box has
 * {@code from == to}. Capacity is capped at {@link Integer#MAX_VALUE} blocks; tools
 * that operate on regions use {@link #volume()} for size-aware behavior (e.g. choosing
 * sync vs async fill).
 */
public record BoundingBox(int x1, int y1, int z1, int x2, int y2, int z2) {

    public BoundingBox {
        if (x1 > x2 || y1 > y2 || z1 > z2) {
            throw new IllegalArgumentException(
                    "BoundingBox: corner 1 must be <= corner 2 on every axis");
        }
    }

    public static BoundingBox of(Vec3i a, Vec3i b) {
        return new BoundingBox(
                Math.min(a.x(), b.x()),
                Math.min(a.y(), b.y()),
                Math.min(a.z(), b.z()),
                Math.max(a.x(), b.x()),
                Math.max(a.y(), b.y()),
                Math.max(a.z(), b.z()));
    }

    public Vec3i min() {
        return new Vec3i(x1, y1, z1);
    }

    public Vec3i max() {
        return new Vec3i(x2, y2, z2);
    }

    public int sizeX() {
        return x2 - x1 + 1;
    }

    public int sizeY() {
        return y2 - y1 + 1;
    }

    public int sizeZ() {
        return z2 - z1 + 1;
    }

    public long volume() {
        return (long) sizeX() * sizeY() * sizeZ();
    }

    public boolean contains(Vec3i p) {
        return p.x() >= x1
                && p.x() <= x2
                && p.y() >= y1
                && p.y() <= y2
                && p.z() >= z1
                && p.z() <= z2;
    }
}
