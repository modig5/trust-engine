package engine;

import java.util.concurrent.atomic.AtomicBoolean;

/** Limits and cancellation for one search. Create before starting a worker. */
public final class SearchRequest {
    public final int maxDepth;
    /** -1 means depth-only; zero returns a legal fallback without searching. */
    public final long moveTimeMillis;
    private final AtomicBoolean stopped = new AtomicBoolean();
    private final AtomicBoolean started = new AtomicBoolean();

    private SearchRequest(int maxDepth, long moveTimeMillis) {
        if (maxDepth < 1 || maxDepth > 128) throw new IllegalArgumentException("Depth must be 1..128");
        this.maxDepth = maxDepth;
        this.moveTimeMillis = moveTimeMillis;
    }

    public static SearchRequest depth(int depth) {
        return new SearchRequest(depth, -1);
    }

    public static SearchRequest fixedTime(int depth, long millis) {
        if (millis < 0) throw new IllegalArgumentException("Move time must be nonnegative");
        return new SearchRequest(depth, millis);
    }

    /**
     * Use the side-to-move's clock and increment. Zero movesToGo estimates 30 moves.
     * Reserve up to 50 ms (10% of the remaining time) for returning/submitting a move.
     * Future increment is used to size the budget, but is never spent before receipt.
     */
    public static SearchRequest forClock(int depth, long remainingMillis, long incrementMillis, int movesToGo) {
        if (remainingMillis < 0 || incrementMillis < 0 || movesToGo < 0)
            throw new IllegalArgumentException("Clock values must be nonnegative");
        long reserve = Math.min(50, remainingMillis / 10 + (remainingMillis % 10 == 0 ? 0 : 1));
        long usable = remainingMillis - reserve;
        int horizon = movesToGo == 0 ? 30 : movesToGo;
        long base = usable / horizon;
        long incrementShare = incrementMillis / 4 * 3 + incrementMillis % 4 * 3 / 4;
        // Saturating addition without overflow, always capped by the current clock.
        return fixedTime(depth, base + Math.min(usable - base, incrementShare));
    }

    public void stop() {
        stopped.set(true);
    }

    public boolean isStopped() {
        return stopped.get();
    }

    void begin() {
        if (!started.compareAndSet(false, true))
            throw new IllegalStateException("Use a new SearchRequest for each search");
    }

    long budgetNanos() {
        // Keep elapsed-time subtraction valid even for very large supplied budgets.
        return Math.min(moveTimeMillis, Long.MAX_VALUE / 2 / 1_000_000) * 1_000_000;
    }
}
