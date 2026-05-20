package com.chapmanjw.minecraft.fabric.mcp.adapter.dto;

import java.util.List;

/**
 * Snapshot of a single player's progress through advancements.
 *
 * <p>{@link #granted} lists advancements where every criterion is satisfied.
 * {@link #inProgress} lists started-but-not-complete advancements with per-criterion
 * progress so callers can show meaningful progress bars.
 */
public record AdvancementProgressInfo(
        List<String> granted,
        List<InProgress> inProgress) {

    public AdvancementProgressInfo {
        granted = granted == null ? List.of() : List.copyOf(granted);
        inProgress = inProgress == null ? List.of() : List.copyOf(inProgress);
    }

    public record InProgress(
            String id,
            List<String> criteriaCompleted,
            List<String> criteriaRemaining) {

        public InProgress {
            criteriaCompleted =
                    criteriaCompleted == null ? List.of() : List.copyOf(criteriaCompleted);
            criteriaRemaining =
                    criteriaRemaining == null ? List.of() : List.copyOf(criteriaRemaining);
        }
    }
}
