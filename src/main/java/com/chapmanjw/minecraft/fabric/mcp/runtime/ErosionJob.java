package com.chapmanjw.minecraft.fabric.mcp.runtime;

import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * A single hydraulic (droplet) erosion job and its lifecycle state.
 *
 * <p>Concurrency model — no locks, only safe publication:
 *
 * <ul>
 *   <li>The surveyed input grid ({@link #originalHeights}) is captured on the main
 *       thread before the worker starts and is never mutated afterward.
 *   <li>The worker thread runs the pure-math droplet simulation on a private float
 *       grid, then publishes the rounded result into {@link #newHeights} and flips
 *       {@link #state} to {@code WRITING} (or {@code DONE} for a dry run). The volatile
 *       state write happens-after the array fill, so the main thread sees a fully
 *       populated array once it observes {@code WRITING}.
 *   <li>The write-back cursor ({@link #writeCursor}) is touched only by the server tick
 *       (always the main thread), so it needs no synchronization beyond the volatile
 *       reads other threads do for status reporting.
 * </ul>
 *
 * <p>The simulation itself touches no world state; only survey (before) and write-back
 * (after, chunked across ticks) run on the main thread.
 */
public final class ErosionJob {

    /** Lifecycle states, observed by the status/result tools and the tick drainer. */
    public enum State {
        SURVEYING,
        ERODING,
        WRITING,
        DONE,
        FAILED
    }

    /** Immutable region + algorithm parameters for one job. */
    public record Params(
            String dimensionId,
            int originX,
            int originZ,
            int width,
            int length,
            int floorY,
            String surfaceBlock,
            String subsurfaceBlock,
            int subsurfaceDepth,
            int droplets,
            int maxLifetime,
            double inertia,
            double capacity,
            double deposition,
            double erosion,
            double evaporation,
            double gravity,
            double initialSpeed,
            double initialWater,
            int protectX0,
            int protectZ0,
            int protectX1,
            int protectZ1,
            int apron,
            long seed,
            boolean dryRun) {}

    private static final double SMOOTHSTEP_C2 = 3.0;
    private static final double SMOOTHSTEP_C3 = 2.0;
    private static final double MIN_SEDIMENT_CAPACITY = 0.01;
    private static final double DIR_EPSILON = 1.0e-6;

    private final String jobId;
    private final Params params;
    private final int total;

    /** Surveyed top-Y per column (row-major xi*length+zi). Captured once, then immutable. */
    private final int[] originalHeights;

    /** Per-column erosion-strength factor in [0,1] from protect box + apron. Immutable after init. */
    private final double[] factor;

    /** Result heights, published by the worker before state flips to WRITING. */
    private volatile int[] newHeights;

    private volatile State state = State.SURVEYING;
    private volatile String error;

    /** Columns already written back; only mutated on the main thread (tick). */
    private final AtomicInteger writeCursor = new AtomicInteger(0);
    /** Running tally of blocks changed during write-back. */
    private final AtomicLong blocksChanged = new AtomicLong(0);

    /** Stats computed by the worker (valid once state >= WRITING). */
    private volatile int maxDelta;
    private volatile double meanAbsDelta;
    private volatile double movedTotal;

    public ErosionJob(String jobId, Params params, int[] originalHeights) {
        this.jobId = jobId;
        this.params = params;
        this.total = params.width() * params.length();
        if (originalHeights.length != total) {
            throw new IllegalArgumentException(
                    "originalHeights length " + originalHeights.length + " != width*length " + total);
        }
        this.originalHeights = originalHeights;
        this.factor = computeFactors(params);
    }

    public String jobId() {
        return jobId;
    }

    public Params params() {
        return params;
    }

    public State state() {
        return state;
    }

    public String error() {
        return error;
    }

    public int total() {
        return total;
    }

    public int written() {
        return writeCursor.get();
    }

    public long blocksChanged() {
        return blocksChanged.get();
    }

    public int maxDelta() {
        return maxDelta;
    }

    public double meanAbsDelta() {
        return meanAbsDelta;
    }

    public double movedTotal() {
        return movedTotal;
    }

    public int[] originalHeights() {
        return originalHeights;
    }

    public int[] newHeights() {
        return newHeights;
    }

    public double progress() {
        if (state == State.DONE) {
            return 1.0;
        }
        if (state == State.WRITING && total > 0) {
            return (double) writeCursor.get() / total;
        }
        return 0.0;
    }

    /** Smoothstep ramp clamped to [0,1] for the apron taper. */
    private static double smoothstep(double t) {
        double x = t < 0.0 ? 0.0 : (t > 1.0 ? 1.0 : t);
        return x * x * (SMOOTHSTEP_C2 - SMOOTHSTEP_C3 * x);
    }

    private static double[] computeFactors(Params p) {
        int w = p.width();
        int len = p.length();
        double[] f = new double[w * len];
        boolean hasProtect = p.protectX0() != Integer.MIN_VALUE
                && p.protectX1() >= p.protectX0()
                && p.protectZ1() >= p.protectZ0();
        for (int xi = 0; xi < w; xi++) {
            for (int zi = 0; zi < len; zi++) {
                int wx = p.originX() + xi;
                int wz = p.originZ() + zi;
                double fac = 1.0;
                if (hasProtect) {
                    boolean inside = wx >= p.protectX0() && wx <= p.protectX1()
                            && wz >= p.protectZ0() && wz <= p.protectZ1();
                    if (inside) {
                        fac = 0.0;
                    } else if (p.apron() > 0) {
                        int dxOut = Math.max(0, Math.max(p.protectX0() - wx, wx - p.protectX1()));
                        int dzOut = Math.max(0, Math.max(p.protectZ0() - wz, wz - p.protectZ1()));
                        double dist = Math.sqrt((double) dxOut * dxOut + (double) dzOut * dzOut);
                        fac = dist >= p.apron() ? 1.0 : smoothstep(dist / p.apron());
                    }
                }
                f[xi * len + zi] = fac;
            }
        }
        return f;
    }

    /**
     * Run the droplet simulation on a private float grid (worker thread only — no world
     * access), publish the rounded result, and advance the state machine. On a dry run
     * this jumps straight to {@code DONE}; otherwise to {@code WRITING} so the tick
     * drainer can apply the result across subsequent ticks.
     */
    public void runErosion() {
        try {
            state = State.ERODING;
            int w = params.width();
            int len = params.length();
            float[] h = new float[total];
            for (int i = 0; i < total; i++) {
                h[i] = originalHeights[i];
            }
            simulate(h, w, len);

            int[] result = new int[total];
            long sumAbs = 0;
            int mx = 0;
            for (int i = 0; i < total; i++) {
                int nv = Math.round(h[i]);
                if (nv < params.floorY()) {
                    nv = params.floorY();
                }
                result[i] = nv;
                int d = Math.abs(nv - originalHeights[i]);
                sumAbs += d;
                if (d > mx) {
                    mx = d;
                }
            }
            this.maxDelta = mx;
            this.meanAbsDelta = total > 0 ? (double) sumAbs / total : 0.0;
            this.newHeights = result; // safe-publish before the volatile state write below
            this.state = params.dryRun() ? State.DONE : State.WRITING;
        } catch (RuntimeException e) {
            this.error = e.getClass().getSimpleName()
                    + (e.getMessage() == null ? "" : ": " + e.getMessage());
            this.state = State.FAILED;
        }
    }

    private void simulate(float[] h, int w, int len) {
        Random rng = new Random(params.seed());
        double moved = 0.0;
        for (int d = 0; d < params.droplets(); d++) {
            double posX = rng.nextDouble() * (w - 1);
            double posZ = rng.nextDouble() * (len - 1);
            double dirX = 0.0;
            double dirZ = 0.0;
            double speed = params.initialSpeed();
            double water = params.initialWater();
            double sediment = 0.0;

            for (int life = 0; life < params.maxLifetime(); life++) {
                int nodeX = (int) posX;
                int nodeZ = (int) posZ;
                if (nodeX < 0 || nodeX >= w - 1 || nodeZ < 0 || nodeZ >= len - 1) {
                    break;
                }
                double fx = posX - nodeX;
                double fz = posZ - nodeZ;

                double[] hg = heightAndGradient(h, w, len, nodeX, nodeZ, fx, fz);
                double oldHeight = hg[0];
                double gradX = hg[1];
                double gradZ = hg[2];

                dirX = dirX * params.inertia() - gradX * (1.0 - params.inertia());
                dirZ = dirZ * params.inertia() - gradZ * (1.0 - params.inertia());
                double dlen = Math.sqrt(dirX * dirX + dirZ * dirZ);
                if (dlen < DIR_EPSILON) {
                    break;
                }
                dirX /= dlen;
                dirZ /= dlen;
                posX += dirX;
                posZ += dirZ;

                if (posX < 0.0 || posX >= w - 1 || posZ < 0.0 || posZ >= len - 1) {
                    break;
                }
                int nx = (int) posX;
                int nz = (int) posZ;
                double nfx = posX - nx;
                double nfz = posZ - nz;
                double newHeight = heightAndGradient(h, w, len, nx, nz, nfx, nfz)[0];
                double deltaHeight = newHeight - oldHeight;

                double capacityHere = Math.max(
                        -deltaHeight * speed * water * params.capacity(), MIN_SEDIMENT_CAPACITY);

                if (sediment > capacityHere || deltaHeight > 0.0) {
                    double depositAmount = deltaHeight > 0.0
                            ? Math.min(deltaHeight, sediment)
                            : (sediment - capacityHere) * params.deposition();
                    depositAmount = applyDeposit(h, w, len, nodeX, nodeZ, fx, fz, depositAmount);
                    sediment -= depositAmount;
                } else {
                    double erodeAmount =
                            Math.min((capacityHere - sediment) * params.erosion(), -deltaHeight);
                    erodeAmount = applyErode(h, w, len, nodeX, nodeZ, fx, fz, erodeAmount);
                    sediment += erodeAmount;
                    moved += erodeAmount;
                }

                speed = Math.sqrt(Math.max(0.0, speed * speed + deltaHeight * params.gravity()));
                water *= (1.0 - params.evaporation());
                if (water <= 0.0) {
                    break;
                }
            }
        }
        this.movedTotal = moved;
    }

    private static double[] heightAndGradient(
            float[] h, int w, int len, int nodeX, int nodeZ, double fx, double fz) {
        int i = nodeX * len + nodeZ;
        double nw = h[i];
        double ne = h[i + len];
        double sw = h[i + 1];
        double se = h[i + len + 1];
        double gradX = (ne - nw) * (1.0 - fz) + (se - sw) * fz;
        double gradZ = (sw - nw) * (1.0 - fx) + (se - ne) * fx;
        double height = nw * (1.0 - fx) * (1.0 - fz)
                + ne * fx * (1.0 - fz)
                + sw * (1.0 - fx) * fz
                + se * fx * fz;
        return new double[] {height, gradX, gradZ};
    }

    /** Distribute {@code amount} of deposition across the 4 surrounding nodes, factor-scaled. */
    private double applyDeposit(
            float[] h, int w, int len, int nodeX, int nodeZ, double fx, double fz, double amount) {
        int i = nodeX * len + nodeZ;
        double applied = 0.0;
        applied += add(h, i, factor[i] * amount * (1.0 - fx) * (1.0 - fz));
        applied += add(h, i + len, factor[i + len] * amount * fx * (1.0 - fz));
        applied += add(h, i + 1, factor[i + 1] * amount * (1.0 - fx) * fz);
        applied += add(h, i + len + 1, factor[i + len + 1] * amount * fx * fz);
        return applied;
    }

    /** Remove {@code amount} of material across the 4 surrounding nodes, factor-scaled. */
    private double applyErode(
            float[] h, int w, int len, int nodeX, int nodeZ, double fx, double fz, double amount) {
        int i = nodeX * len + nodeZ;
        double applied = 0.0;
        applied += add(h, i, -factor[i] * amount * (1.0 - fx) * (1.0 - fz));
        applied += add(h, i + len, -factor[i + len] * amount * fx * (1.0 - fz));
        applied += add(h, i + 1, -factor[i + 1] * amount * (1.0 - fx) * fz);
        applied += add(h, i + len + 1, -factor[i + len + 1] * amount * fx * fz);
        return Math.abs(applied);
    }

    private static double add(float[] h, int idx, double delta) {
        h[idx] += (float) delta;
        return delta;
    }

    /** Advance the write cursor by {@code n} columns and return the previous value. */
    public int advanceWriteCursor(int n) {
        return writeCursor.getAndAdd(n);
    }

    public void addBlocksChanged(long n) {
        blocksChanged.addAndGet(n);
    }

    public void markDone() {
        this.state = State.DONE;
    }

    public void markFailed(String message) {
        this.error = message;
        this.state = State.FAILED;
    }
}
