package com.chapmanjw.minecraft.fabric.mcp.protocol;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.chapmanjw.minecraft.fabric.mcp.compat.ToolDescriptor;

/**
 * Holds the registered tools and exposes them by name.
 *
 * <p>Populated once at server-starting after the compatibility filter has narrowed
 * the set. Thread-safe for concurrent reads; writes happen only during startup so
 * they're not concurrent in practice.
 */
public final class ToolRegistry {

    private final Map<String, Entry> tools = new LinkedHashMap<>();

    public synchronized void register(ToolDescriptor descriptor, Tool tool) {
        if (tools.containsKey(descriptor.name())) {
            throw new IllegalStateException(
                    "Duplicate tool name '" + descriptor.name() + "' — names must be unique");
        }
        tools.put(descriptor.name(), new Entry(descriptor, tool));
    }

    public Optional<Entry> lookup(String name) {
        return Optional.ofNullable(tools.get(name));
    }

    public List<Entry> list() {
        return List.copyOf(tools.values());
    }

    public int size() {
        return tools.size();
    }

    public record Entry(ToolDescriptor descriptor, Tool tool) {}
}
