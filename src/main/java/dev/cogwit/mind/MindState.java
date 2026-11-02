package dev.cogwit.mind;

/** Cogwit's moods and the scan cooldown, kept on the talisman so the co-op friend sees the same face. */
public record MindState(long happyUntil, long scaredUntil, long scanReadyAt) {
    public static final MindState EMPTY = new MindState(0, 0, 0);

    public MindState withHappyUntil(long v) {
        return new MindState(v, scaredUntil, scanReadyAt);
    }

    public MindState withScaredUntil(long v) {
        return new MindState(happyUntil, v, scanReadyAt);
    }

    public MindState withScanReadyAt(long v) {
        return new MindState(happyUntil, scaredUntil, v);
    }
}
