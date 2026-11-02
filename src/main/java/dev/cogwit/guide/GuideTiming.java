package dev.cogwit.guide;

/** When Cogwit speaks up: at once for a new achievement, otherwise a reminder now and then, never in a flood. */
public final class GuideTiming {
    public static final long REMIND_TICKS = 6000L;
    public static final long MIN_GAP_TICKS = 1200L;

    public enum Decision { NONE, CONGRATULATE, REMIND }

    private GuideTiming() {}

    /**
     * @param completed index of the last finished milestone in the unbroken run, -1 for none
     * @param allDone true when nothing is left to suggest
     */
    public static Decision decide(GuideState state, long now, int completed, boolean allDone) {
        return decide(state, now, completed, allDone, REMIND_TICKS);
    }

    /** Same, with the reminder interval picked by the player's chatter setting ({@code Long.MAX_VALUE} = never). */
    public static Decision decide(GuideState state, long now, int completed, boolean allDone, long remindTicks) {
        if (!state.enabled() || tooSoon(state, now, MIN_GAP_TICKS)) {
            return Decision.NONE;
        }
        if (completed > state.lastCompleted()) {
            return Decision.CONGRATULATE;
        }
        if (!allDone && remindTicks != Long.MAX_VALUE && !tooSoon(state, now, remindTicks)) {
            return Decision.REMIND;
        }
        return Decision.NONE;
    }

    /** A clock that went backwards (new world, edited save) counts as long ago. */
    private static boolean tooSoon(GuideState state, long now, long gap) {
        long last = state.lastHintTick();
        return last >= 0 && last <= now && now - last < gap;
    }
}
