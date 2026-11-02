package dev.cogwit.ultra;

/** What the owner's screen knows about the Overdrive flash. Plain fields, safe to load on a server. */
public final class UltraClientState {
    private static volatile long flashStartedMillis = -1;
    private static volatile boolean started;

    private UltraClientState() {}

    public static void trigger(boolean start) {
        started = start;
        flashStartedMillis = System.currentTimeMillis();
    }

    /** 0..1 progress of the current flash, or -1 when none is playing. */
    public static float flash(long durationMillis) {
        if (flashStartedMillis < 0) {
            return -1;
        }
        float t = (System.currentTimeMillis() - flashStartedMillis) / (float) durationMillis;
        return t > 1 ? -1 : t;
    }

    public static boolean started() {
        return started;
    }
}
