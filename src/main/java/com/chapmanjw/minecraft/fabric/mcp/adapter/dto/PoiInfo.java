package com.chapmanjw.minecraft.fabric.mcp.adapter.dto;

/**
 * One point of interest as the server's own village AI sees it.
 *
 * <p>Beds, workstations and bells are all POI records, so this is how a build is checked against
 * what the game actually believes rather than what it looks like: a bed that villagers cannot claim
 * simply is not a home POI, however convincing the room around it.
 *
 * @param type POI type registry id, e.g. {@code minecraft:home} or {@code minecraft:armorer}
 * @param pos block position of the POI
 * @param occupied whether every ticket is taken (for a bed, that a villager has claimed it)
 * @param freeTickets remaining claims available; 0 with {@code occupied=false} means the type
 *     defines no tickets at all rather than that it is full
 */
public record PoiInfo(String type, Vec3i pos, boolean occupied, int freeTickets) {}
