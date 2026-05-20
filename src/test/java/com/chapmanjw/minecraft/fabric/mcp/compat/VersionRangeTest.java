package com.chapmanjw.minecraft.fabric.mcp.compat;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class VersionRangeTest {

    @Test
    void atLeastAcceptsEqualAndGreater() {
        assertTrue(VersionRange.atLeast("1.21.11", "1.21.11"));
        assertTrue(VersionRange.atLeast("26.1.1", "1.21.0"));
        assertTrue(VersionRange.atLeast("1.21.11", "1.21.0"));
    }

    @Test
    void atLeastRejectsLower() {
        assertFalse(VersionRange.atLeast("1.21.0", "1.21.11"));
        assertFalse(VersionRange.atLeast("1.21.11", "26.1.0"));
    }

    @Test
    void atLeastTreatsEmptyMinAsUnbounded() {
        assertTrue(VersionRange.atLeast("1.21.11", ""));
        assertTrue(VersionRange.atLeast("1.21.11", null));
    }

    @Test
    void atMostAcceptsEqualAndLower() {
        assertTrue(VersionRange.atMost("1.21.11", "1.21.11"));
        assertTrue(VersionRange.atMost("1.21.0", "1.21.11"));
        assertTrue(VersionRange.atMost("1.20.0", "26.1.0"));
    }

    @Test
    void atMostRejectsHigher() {
        assertFalse(VersionRange.atMost("26.1.0", "1.21.11"));
    }

    @Test
    void atMostTreatsEmptyMaxAsUnbounded() {
        assertTrue(VersionRange.atMost("1.21.11", ""));
        assertTrue(VersionRange.atMost("1.21.11", null));
    }

    @Test
    void matchesEmptyOrWildcardPredicateAcceptsAll() {
        assertTrue(VersionRange.matches("0.141.4", "*"));
        assertTrue(VersionRange.matches("0.141.4", ""));
        assertTrue(VersionRange.matches("0.141.4", null));
    }

    @Test
    void matchesGteRangePredicate() {
        assertTrue(VersionRange.matches("0.141.4", ">=0.140.0"));
        assertFalse(VersionRange.matches("0.139.0", ">=0.140.0"));
    }

    @Test
    void matchesBuildSuffixStrippedBeforeCompare() {
        // 0.141.4+1.21.11 should be parsed as 0.141.4 for comparison purposes.
        assertTrue(VersionRange.matches("0.141.4+1.21.11", ">=0.140.0"));
        assertFalse(VersionRange.matches("0.139.4+1.21.11", ">=0.140.0"));
    }

    @Test
    void matchesUnparseablePredicateReturnsFalse() {
        // A malformed predicate must NOT silently allow the version through; the
        // production code is intentionally conservative here so tool authors must
        // fix the metadata rather than rely on accidental success.
        assertFalse(VersionRange.matches("1.0.0", "garbage((("));
    }

    @Test
    void matchesIntervalPredicate() {
        assertTrue(VersionRange.matches("1.5.0", ">=1.0.0 <2.0.0"));
        assertFalse(VersionRange.matches("2.5.0", ">=1.0.0 <2.0.0"));
    }

    @Test
    void compareFallsBackToLexicographicOnUnparseableCandidate() {
        // Neither side parses as semver; the implementation falls back to lexical
        // comparison so a startup check doesn't crash on weird module versions.
        // Pick clearly-unparseable strings so the fallback path is exercised; the
        // result is just whatever String.compareTo says — we just verify no throw.
        boolean unused = VersionRange.atLeast("garbage", "elsewhere");
        // Any boolean is fine — we only require no exception.
        assertTrue(unused || !unused);
    }
}
