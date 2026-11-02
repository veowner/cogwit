package dev.cogwit.guide;

/**
 * Per-player guide settings and bookkeeping. {@code lastHintTick} is -1 before the first message,
 * {@code lastCompleted} is the highest milestone index already congratulated (-1 for none), {@code reached}
 * one bit per milestone ever done, so a step stays done once its items are used up or stored away.
 */
public record GuideState(boolean enabled, long lastHintTick, int lastCompleted, int reached) {
    public static final GuideState DEFAULT = new GuideState(true, -1L, -1, 0);

    public GuideState(boolean enabled, long lastHintTick, int lastCompleted) {
        this(enabled, lastHintTick, lastCompleted, 0);
    }

    public GuideState withEnabled(boolean value) {
        return new GuideState(value, lastHintTick, lastCompleted, reached);
    }

    public GuideState spoke(long now) {
        return new GuideState(enabled, now, lastCompleted, reached);
    }

    public GuideState congratulated(long now, int completed) {
        return new GuideState(enabled, now, Math.max(lastCompleted, completed), reached);
    }

    public GuideState withReached(int mask) {
        return new GuideState(enabled, lastHintTick, lastCompleted, mask);
    }
}
