package app.luna.cycle;

/** Uses elapsed time including device sleep, never the adjustable wall clock. */
final class ReturnWindow {
    static final long DURATION_MS = 120_000L;
    private long leftAt = -1;
    void leave(long now) { if (leftAt < 0) leftAt = now; }
    boolean expired(long now) { return leftAt >= 0 && (now < leftAt || now - leftAt >= DURATION_MS); }
    long remaining(long now) {
        if (leftAt < 0) return DURATION_MS;
        return expired(now) ? 0 : DURATION_MS - (now - leftAt);
    }
    void clear() { leftAt = -1; }
}
