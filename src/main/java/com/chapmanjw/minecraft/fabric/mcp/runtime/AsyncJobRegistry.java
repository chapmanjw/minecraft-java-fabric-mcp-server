package com.chapmanjw.minecraft.fabric.mcp.runtime;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.chapmanjw.minecraft.fabric.mcp.adapter.MinecraftAdapter;

/**
 * Registry and lifecycle owner for long-running async erosion jobs.
 *
 * <p>The erosion math runs off the main thread on a single daemon worker so it never
 * stalls the server tick; the world write-back is drained back onto the main thread in
 * bounded chunks by {@link #tick(MinecraftAdapter)}, called once per
 * {@code END_SERVER_TICK}. This keeps both phases off each other's threads: the worker
 * only ever touches an {@link ErosionJob}'s private float grid, and only the tick (main
 * thread) touches world state.
 *
 * <p>MCP transport is stateless and has no progress/streaming primitive, so jobs are
 * surfaced through ordinary tools: a start tool submits and returns a job id, and
 * status/result tools read this registry directly (no main-thread hop needed — all the
 * fields they read are volatile/atomic).
 */
public final class AsyncJobRegistry {

    private static final Logger LOGGER = LoggerFactory.getLogger("minecraft_fabric_mcp/runtime");

    /** Max columns written back per server tick — bounds the per-tick main-thread cost. */
    private static final int WRITE_CHUNK_COLS = 16_384;

    /** Cap on retained finished jobs before the oldest are evicted, to bound memory. */
    private static final int MAX_RETAINED_JOBS = 64;

    private final Map<String, ErosionJob> jobs = new ConcurrentHashMap<>();
    private final AtomicInteger idCounter = new AtomicInteger(0);
    private final ExecutorService worker;

    public AsyncJobRegistry() {
        this.worker =
                Executors.newSingleThreadExecutor(
                        r -> {
                            Thread t = new Thread(r, "mcp-erosion-worker");
                            t.setDaemon(true);
                            return t;
                        });
    }

    /**
     * Register a job and kick off its erosion on the worker thread. Returns the job id
     * the caller hands back to the MCP client for polling.
     */
    public String submit(ErosionJob job) {
        evictIfNeeded();
        jobs.put(job.jobId(), job);
        worker.execute(job::runErosion);
        return job.jobId();
    }

    /** Allocate the next opaque job id. */
    public String nextJobId() {
        return "erode-" + idCounter.incrementAndGet();
    }

    /** Look up a job by id, or {@code null} if unknown/evicted. */
    public ErosionJob get(String jobId) {
        return jobs.get(jobId);
    }

    /**
     * Drain pending write-back work onto the main thread. Called once per server tick.
     * Each job in {@code WRITING} state gets up to {@link #WRITE_CHUNK_COLS} columns
     * materialised this tick; a job whose cursor reaches the end is marked done.
     *
     * <p>Must run on the main thread — it calls into {@code adapter} which touches the
     * world. Cheap when no jobs are writing (single map scan).
     */
    public void tick(MinecraftAdapter adapter) {
        if (jobs.isEmpty()) {
            return;
        }
        for (ErosionJob job : jobs.values()) {
            if (job.state() != ErosionJob.State.WRITING) {
                continue;
            }
            try {
                drainOne(job, adapter);
            } catch (RuntimeException e) {
                LOGGER.error("Erosion job {} write-back failed", job.jobId(), e);
                job.markFailed(
                        "write-back failed: "
                                + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()));
            }
        }
    }

    private void drainOne(ErosionJob job, MinecraftAdapter adapter) {
        int total = job.total();
        int from = job.written();
        if (from >= total) {
            job.markDone();
            return;
        }
        int to = Math.min(from + WRITE_CHUNK_COLS, total);
        ErosionJob.Params p = job.params();
        MinecraftAdapter.ErodedApply spec =
                new MinecraftAdapter.ErodedApply(
                        p.originX(),
                        p.originZ(),
                        p.width(),
                        p.length(),
                        p.floorY(),
                        p.surfaceBlock(),
                        p.subsurfaceBlock(),
                        p.subsurfaceDepth(),
                        job.originalHeights(),
                        job.newHeights(),
                        from,
                        to);
        MinecraftAdapter.ErodedApplyResult res = adapter.terrainApplyErodedColumns(p.dimensionId(), spec);
        job.addBlocksChanged(res.blocksChanged());
        // Advance only by the columns actually applied. If a mid-slice chunk had unloaded,
        // colsAdvanced is short of the requested span; the unwritten tail is retried next
        // tick (the adapter requested a reload). colsAdvanced == 0 means the cursor's own
        // chunk was unloaded — leave the cursor put and retry without marking done.
        if (res.colsAdvanced() > 0) {
            job.advanceWriteCursor(res.colsAdvanced());
        }
        if (job.written() >= total) {
            job.markDone();
        }
    }

    private void evictIfNeeded() {
        if (jobs.size() < MAX_RETAINED_JOBS) {
            return;
        }
        // Evict finished jobs first; they hold the large height arrays.
        for (Map.Entry<String, ErosionJob> e : jobs.entrySet()) {
            ErosionJob.State s = e.getValue().state();
            if (s == ErosionJob.State.DONE || s == ErosionJob.State.FAILED) {
                jobs.remove(e.getKey());
                if (jobs.size() < MAX_RETAINED_JOBS) {
                    return;
                }
            }
        }
    }

    /** Stop the worker thread; called from server-stopping. */
    public void shutdown() {
        worker.shutdownNow();
    }
}
