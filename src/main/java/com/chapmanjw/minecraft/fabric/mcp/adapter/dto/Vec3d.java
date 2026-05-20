package com.chapmanjw.minecraft.fabric.mcp.adapter.dto;

/** Plain 3D position, used everywhere coordinates cross the adapter boundary. */
public record Vec3d(double x, double y, double z) {

    public Vec3i toBlockPos() {
        return new Vec3i((int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z));
    }
}
