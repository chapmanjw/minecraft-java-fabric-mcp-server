package com.chapmanjw.minecraft.fabric.mcp.protocol;

import java.util.Optional;

/**
 * The complete set of Minecraft event types the MCP server can stream.
 *
 * <p>The wire name follows {@code <domain>.<verb>} — for example
 * {@code "player.join"} — matching the tool surface naming convention. The enum
 * exists so handlers and the {@link com.chapmanjw.minecraft.fabric.mcp.protocol.EventBus}
 * can switch on type-safe values rather than raw strings.
 */
public enum EventType {
    SERVER_TICK("server.tick"),
    SERVER_STARTING("server.starting"),
    SERVER_STARTED("server.started"),
    SERVER_STOPPING("server.stopping"),
    SERVER_STOPPED("server.stopped"),

    PLAYER_JOIN("player.join"),
    PLAYER_LEAVE("player.leave"),
    PLAYER_CHAT("player.chat"),
    PLAYER_RESPAWN("player.respawn"),
    PLAYER_DEATH("player.death"),

    ENTITY_SPAWN("entity.spawn"),
    ENTITY_DEATH("entity.death"),
    ENTITY_LOAD("entity.load"),
    ENTITY_UNLOAD("entity.unload"),

    BLOCK_BREAK("block.break"),
    BLOCK_PLACE("block.place"),
    BLOCK_USE("block.use"),

    ITEM_USE("item.use"),
    ITEM_CRAFT("item.craft"),

    CONTAINER_OPEN("container.open"),
    CONTAINER_CLOSE("container.close");

    private final String wireName;

    EventType(String wireName) {
        this.wireName = wireName;
    }

    public String wireName() {
        return wireName;
    }

    public static Optional<EventType> fromWireName(String wire) {
        if (wire == null) {
            return Optional.empty();
        }
        for (EventType t : values()) {
            if (t.wireName.equals(wire)) {
                return Optional.of(t);
            }
        }
        return Optional.empty();
    }
}
