package dev.cogwit.guide;

import dev.cogwit.guide.GuideTiming.Decision;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GuideTimingTest {
    private static final long T0 = 100_000;

    private static GuideState spokeAt(long tick, int completed) {
        return new GuideState(true, tick, completed);
    }

    @Test
    void firstEverMessageIsAReminder() {
        assertEquals(Decision.REMIND, GuideTiming.decide(GuideState.DEFAULT, T0, -1, false));
    }

    @Test
    void newMilestoneCongratulatesAtOnce() {
        assertEquals(Decision.CONGRATULATE,
                GuideTiming.decide(spokeAt(T0 - GuideTiming.MIN_GAP_TICKS, 1), T0, 2, false));
    }

    @Test
    void neverMoreThanOneMessagePerMinute() {
        GuideState state = spokeAt(T0 - GuideTiming.MIN_GAP_TICKS + 1, 1);
        assertEquals(Decision.NONE, GuideTiming.decide(state, T0, 2, false));
    }

    @Test
    void congratulationWaitsOutTheGapInsteadOfBeingLost() {
        GuideState state = spokeAt(T0, 1);
        assertEquals(Decision.NONE, GuideTiming.decide(state, T0 + 600, 2, false));
        assertEquals(Decision.CONGRATULATE, GuideTiming.decide(state, T0 + GuideTiming.MIN_GAP_TICKS, 2, false));
    }

    @Test
    void remindersComeEveryFiveMinutes() {
        GuideState state = spokeAt(T0, 1);
        assertEquals(Decision.NONE, GuideTiming.decide(state, T0 + GuideTiming.REMIND_TICKS - 1, 1, false));
        assertEquals(Decision.REMIND, GuideTiming.decide(state, T0 + GuideTiming.REMIND_TICKS, 1, false));
    }

    @Test
    void disabledGuideStaysSilent() {
        GuideState off = GuideState.DEFAULT.withEnabled(false);
        assertEquals(Decision.NONE, GuideTiming.decide(off, T0, 5, false));
        assertEquals(Decision.NONE, GuideTiming.decide(off, T0 * 10, -1, false));
    }

    @Test
    void nothingToSayOnceEverythingIsDoneUntilSomethingNewHappens() {
        int last = Milestone.values().length - 1;
        assertEquals(Decision.NONE, GuideTiming.decide(spokeAt(0, last), T0, last, true));
        assertEquals(Decision.CONGRATULATE, GuideTiming.decide(spokeAt(0, last - 1), T0, last, true));
    }

    @Test
    void regressionDoesNotRepeatOldCongratulations() {
        assertEquals(Decision.NONE, GuideTiming.decide(spokeAt(0, 5), T0 * 2, 3, true));
    }

    @Test
    void aClockThatWentBackwardsDoesNotMuteTheGuide() {
        assertEquals(Decision.REMIND, GuideTiming.decide(spokeAt(T0 * 5, 1), T0, 1, false));
    }

    @Test
    void stateKeepsTheHighestCompletedIndex() {
        assertEquals(4, spokeAt(0, 4).congratulated(10, 2).lastCompleted());
        assertEquals(6, spokeAt(0, 4).congratulated(10, 6).lastCompleted());
        assertEquals(10, spokeAt(0, 4).congratulated(10, 6).lastHintTick());
    }

    @Test
    void quietNeverRemindsButStillCongratulates() {
        assertEquals(Decision.NONE, GuideTiming.decide(GuideState.DEFAULT, T0, -1, false, Long.MAX_VALUE));
        assertEquals(Decision.CONGRATULATE, GuideTiming.decide(GuideState.DEFAULT, T0, 0, false, Long.MAX_VALUE));
    }

    @Test
    void aLongerGapWaitsLonger() {
        GuideState spoke = GuideState.DEFAULT.spoke(T0);
        long gap = GuideTiming.REMIND_TICKS * 2;
        assertEquals(Decision.NONE, GuideTiming.decide(spoke, T0 + GuideTiming.REMIND_TICKS, -1, false, gap));
        assertEquals(Decision.REMIND, GuideTiming.decide(spoke, T0 + gap, -1, false, gap));
    }
}
