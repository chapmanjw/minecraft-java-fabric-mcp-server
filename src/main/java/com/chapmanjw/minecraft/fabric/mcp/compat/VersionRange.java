package com.chapmanjw.minecraft.fabric.mcp.compat;

import net.fabricmc.loader.api.SemanticVersion;
import net.fabricmc.loader.api.Version;
import net.fabricmc.loader.api.VersionParsingException;
import net.fabricmc.loader.api.metadata.version.VersionPredicate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Thin wrapper around Fabric Loader's {@link VersionPredicate} parser that adds a
 * "no upper / no lower bound when empty" convention and converts string Minecraft
 * versions into {@link SemanticVersion} for comparison.
 *
 * <p>We use Fabric Loader's parser rather than rolling our own because Mojang's
 * Minecraft versions follow non-trivial conventions (snapshots, RCs) that Fabric
 * already understands.
 */
public final class VersionRange {

    private static final Logger LOGGER = LoggerFactory.getLogger("minecraft_fabric_mcp/compat");

    private VersionRange() {}

    /** True if {@code candidate} is >= {@code minimum} when both are non-empty. */
    public static boolean atLeast(String candidate, String minimum) {
        if (minimum == null || minimum.isEmpty()) {
            return true;
        }
        return compareSafely(candidate, minimum) >= 0;
    }

    /** True if {@code candidate} is <= {@code maximum} when both are non-empty. */
    public static boolean atMost(String candidate, String maximum) {
        if (maximum == null || maximum.isEmpty()) {
            return true;
        }
        return compareSafely(candidate, maximum) <= 0;
    }

    /**
     * Apply a {@link VersionPredicate} to {@code candidate}. An empty or {@code "*"}
     * predicate accepts any version.
     */
    public static boolean matches(String candidate, String predicate) {
        if (predicate == null || predicate.isEmpty() || "*".equals(predicate)) {
            return true;
        }
        try {
            VersionPredicate vp = VersionPredicate.parse(predicate);
            Version v = SemanticVersion.parse(stripBuildSuffix(candidate));
            return vp.test(v);
        } catch (VersionParsingException e) {
            LOGGER.warn(
                    "Unparseable version constraint: predicate='{}', candidate='{}': {}",
                    predicate,
                    candidate,
                    e.getMessage());
            // Be conservative: if we can't parse, assume incompatible. This forces tool
            // authors to fix the metadata rather than silently passing.
            return false;
        }
    }

    private static int compareSafely(String a, String b) {
        try {
            Version va = SemanticVersion.parse(stripBuildSuffix(a));
            Version vb = SemanticVersion.parse(stripBuildSuffix(b));
            return va.compareTo(vb);
        } catch (VersionParsingException e) {
            // Fall back to lexicographic — better than crashing on a startup constraint check.
            LOGGER.warn("Could not semver-compare '{}' vs '{}': {}", a, b, e.getMessage());
            return a.compareTo(b);
        }
    }

    /**
     * Many Fabric API module versions include a build suffix like
     * {@code "0.141.4+1.21.11"}. {@link SemanticVersion} accepts both; we keep this
     * helper for defensive use against module IDs that carry tag-style suffixes.
     */
    private static String stripBuildSuffix(String version) {
        int plus = version.indexOf('+');
        return plus < 0 ? version : version.substring(0, plus);
    }
}
