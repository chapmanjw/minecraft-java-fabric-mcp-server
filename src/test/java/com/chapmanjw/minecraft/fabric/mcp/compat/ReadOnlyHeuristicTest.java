package com.chapmanjw.minecraft.fabric.mcp.compat;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ReadOnlyHeuristicTest {

    @Test
    void detectsGetVerb() {
        assertTrue(ReadOnlyHeuristic.isReadOnly("level_get_biome"));
        assertTrue(ReadOnlyHeuristic.isReadOnly("block_get"));
    }

    @Test
    void detectsListVerb() {
        assertTrue(ReadOnlyHeuristic.isReadOnly("entity_list_in_box"));
        assertTrue(ReadOnlyHeuristic.isReadOnly("server_list"));
    }

    @Test
    void detectsFindVerb() {
        assertTrue(ReadOnlyHeuristic.isReadOnly("structure_find_nearest"));
    }

    @Test
    void detectsCheckVerb() {
        assertTrue(ReadOnlyHeuristic.isReadOnly("resource_condition_check_pack"));
    }

    @Test
    void detectsDescribeVerb() {
        assertTrue(ReadOnlyHeuristic.isReadOnly("content_registry_describe"));
    }

    @Test
    void detectsScanVerb() {
        assertTrue(ReadOnlyHeuristic.isReadOnly("level_scan_chunks"));
    }

    @Test
    void detectsQueryVerb() {
        assertTrue(ReadOnlyHeuristic.isReadOnly("recipe_query"));
    }

    @Test
    void detectsEvaluateVerb() {
        assertTrue(ReadOnlyHeuristic.isReadOnly("resource_condition_evaluate"));
    }

    @Test
    void detectsCountVerb() {
        assertTrue(ReadOnlyHeuristic.isReadOnly("entity_count_in_box"));
    }

    @Test
    void detectsReadVerb() {
        assertTrue(ReadOnlyHeuristic.isReadOnly("data_storage_read"));
    }

    @Test
    void detectsIsVerb() {
        assertTrue(ReadOnlyHeuristic.isReadOnly("level_is_day"));
    }

    @Test
    void detectsStatusInfoDefinitionNamespaces() {
        assertTrue(ReadOnlyHeuristic.isReadOnly("server_status"));
        assertTrue(ReadOnlyHeuristic.isReadOnly("server_info"));
        assertTrue(ReadOnlyHeuristic.isReadOnly("recipe_definition"));
        assertTrue(ReadOnlyHeuristic.isReadOnly("registry_namespaces"));
    }

    @Test
    void rejectsMutatingVerbs() {
        assertFalse(ReadOnlyHeuristic.isReadOnly("block_set"));
        assertFalse(ReadOnlyHeuristic.isReadOnly("level_set_time"));
        assertFalse(ReadOnlyHeuristic.isReadOnly("entity_spawn"));
        assertFalse(ReadOnlyHeuristic.isReadOnly("player_kick"));
        assertFalse(ReadOnlyHeuristic.isReadOnly("structure_place"));
        assertFalse(ReadOnlyHeuristic.isReadOnly("server_save"));
    }

    @Test
    void blankNameIsNotReadOnly() {
        assertFalse(ReadOnlyHeuristic.isReadOnly(""));
        assertFalse(ReadOnlyHeuristic.isReadOnly(null));
    }
}
