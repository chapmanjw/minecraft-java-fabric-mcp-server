package com.chapmanjw.minecraft.fabric.mcp.compat;

/**
 * Decides whether a tool is "read-only" — i.e. inspects Minecraft state without
 * mutating it. Operators set {@code MCP_EXCLUDE_WRITE_TOOLS=true} (or
 * {@code excludeWriteTools: true} in config.json) to expose only read-only tools to
 * the MCP client — useful for observer agents and CI inspection tasks.
 *
 * <p>The classification is name-pattern-based: matches read-verb fragments in the
 * tool name. Tool authors who need to override the heuristic can pass
 * {@code readOnly = true} on the {@code @McpTool} annotation; that override is
 * applied by {@link ToolCompatibilityFilter} after this heuristic runs.
 *
 * <p>The pattern set was chosen by sweeping the current tool surface (173 tools)
 * and matching against the conventional read-verb segments. Add new fragments here
 * only — never special-case individual tool names.
 */
public final class ReadOnlyHeuristic {

    private static final String[] READ_VERB_FRAGMENTS = {
            "_get_", "_get",
            "_list_", "_list",
            "_find_", "_find",
            "_check_", "_check",
            "_describe", "_describe_",
            "_scan_",
            "_query",
            "_evaluate",
            "_count_", "_count",
            "_read",
            "_is_",
            "_status",
            "_info",
            "_definition",
            "_namespaces"
    };

    private ReadOnlyHeuristic() {}

    /**
     * True if {@code toolName} looks like an inspection — name contains one of the
     * read-verb fragments.
     */
    public static boolean isReadOnly(String toolName) {
        if (toolName == null || toolName.isEmpty()) {
            return false;
        }
        for (String fragment : READ_VERB_FRAGMENTS) {
            if (toolName.contains(fragment) || toolName.endsWith(fragment)) {
                return true;
            }
        }
        return false;
    }
}
