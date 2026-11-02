package dev.cogwit.horizon.fx;

/** The screen effects playing right now, decaying over time. Plain data, safe to load on a server. */
public final class ScreenFxState {
    private static final long[] START = new long[6];
    private static final long[] LENGTH = new long[6];
    private static final float[] STRENGTH = new float[6];

    private ScreenFxState() {}

    public static synchronized void start(int kind, float strength, int ticks) {
        if (kind < 0 || kind >= START.length) {
            return;
        }
        long now = System.currentTimeMillis();
        // a stronger effect replaces a weaker one; a weaker one never cuts a strong one short
        if (current(kind) > strength) {
            return;
        }
        START[kind] = now;
        LENGTH[kind] = Math.max(1, ticks) * 50L;
        STRENGTH[kind] = strength;
    }

    /** 0..1 progress through an effect, or -1 when it isn't playing. */
    public static synchronized float progress(int kind) {
        long age = System.currentTimeMillis() - START[kind];
        return LENGTH[kind] == 0 || age >= LENGTH[kind] ? -1 : (float) age / LENGTH[kind];
    }

    /** Current strength of an effect, fading linearly to 0 by its end. */
    public static synchronized float current(int kind) {
        float t = progress(kind);
        return t < 0 ? 0 : STRENGTH[kind] * (1 - t);
    }

    public static synchronized float peak(int kind) {
        return progress(kind) < 0 ? 0 : STRENGTH[kind];
    }
}
