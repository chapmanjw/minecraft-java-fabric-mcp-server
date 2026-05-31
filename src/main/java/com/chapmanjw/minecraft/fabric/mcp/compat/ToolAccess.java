package com.chapmanjw.minecraft.fabric.mcp.compat;

import java.util.Locale;
import java.util.Optional;

/**
 * The access level a tool requires, the second axis (alongside {@link ToolCategory})
 * the registration filter uses to trim the surface.
 *
 * <p>Three ranked levels:
 *
 * <ul>
 *   <li>{@link #READ} — inspects game/server state without mutating it (the
 *       {@link ReadOnlyHeuristic} name patterns: {@code _get_/_list_/_describe/_check_/
 *       _query/_find_/_evaluate/…}).
 *   <li>{@link #WRITE} — the normal case: mutates world / entity / item / scoreboard /
 *       … state.
 *   <li>{@link #ADMIN} — world-wide, server-lifecycle, destructive, or command-tree
 *       operations (world border resizing, difficulty / game-rule changes, explosions,
 *       command registration, resource reload, datapack enable/disable, player kick).
 *       Opt-in: the default access cap is {@link #WRITE}.
 * </ul>
 *
 * <p>The operator's {@code max_access} config caps the surface: a tool registers only
 * when its {@link #rank()} is less than or equal to the configured cap's rank.
 */
public enum ToolAccess {

    /** Does not mutate game or server state. */
    READ(0),

    /** Mutates ordinary world / entity / item / scoreboard state. */
    WRITE(1),

    /** World-wide, server-lifecycle, destructive, or command-tree operations. */
    ADMIN(2);

    private final int rank;

    ToolAccess(int rank) {
        this.rank = rank;
    }

    /** Ordered severity: {@code READ(0) < WRITE(1) < ADMIN(2)}. */
    public int rank() {
        return rank;
    }

    /**
     * Lookup an access level by its lower-case wire name (e.g. {@code "read"},
     * {@code "write"}, {@code "admin"}). Used by Config to parse {@code max_access}.
     */
    public static Optional<ToolAccess> fromWireName(String wireName) {
        if (wireName == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(ToolAccess.valueOf(wireName.trim().toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    /** Stable lower-case identifier safe to use in config files and CLI args. */
    public String wireName() {
        return name().toLowerCase(Locale.ROOT);
    }
}
