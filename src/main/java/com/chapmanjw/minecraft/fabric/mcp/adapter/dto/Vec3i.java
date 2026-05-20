package com.chapmanjw.minecraft.fabric.mcp.adapter.dto;

/** Integer 3D position. Block coordinates are always integers. */
public record Vec3i(int x, int y, int z) {

    public Vec3d toVec3d() {
        return new Vec3d(x, y, z);
    }

    /** Returns the bounding box from this point to {@code other}, inclusive on both ends. */
    public BoundingBox boxTo(Vec3i other) {
        return BoundingBox.of(this, other);
    }
}
