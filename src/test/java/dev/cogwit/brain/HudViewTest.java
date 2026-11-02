package dev.cogwit.brain;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class HudViewTest {
    private static final long NOW = 100_000;

    @Test
    void awakeCogwitShowsTokensContextAndSkills() {
        BrainState s = new BrainState(List.of(
                new Skill("fire", 25, NOW - 10, NOW - 10, "", 0),
                new Skill("fall", 3, NOW - 9_000, NOW - 9_000, "", 0)), 1234, 0, 0, 0, NOW - 9_000, 0);
        HudView v = HudView.of(s, NOW);
        assertEquals(HudView.Mood.AWAKE, v.mood());
        assertEquals(1234, v.tokens());
        assertEquals(2, v.rows().size());
        HudView.Row fire = v.rows().get(0);
        assertEquals("fire", fire.category());
        assertEquals(40, fire.percent());
        assertTrue(fire.recentlyUsed());
        assertFalse(v.rows().get(1).recentlyUsed());
    }

    @Test
    void compactedRowCarriesBothHalves() {
        BrainState s = BrainState.EMPTY.withSkills(List.of(new Skill("fall", 10, 0, 0, "magic", 3)))
                .withTokens(100);
        HudView.Row row = HudView.of(s, NOW).rows().get(0);
        assertTrue(row.compacted());
        assertEquals("magic", row.merged());
        assertEquals(13, row.percent());
        assertEquals(5, row.mergedPercent());
    }

    @Test
    void sleepingShowsTimeLeftAndDisablesEverything() {
        BrainState s = BrainState.EMPTY.withSleepUntil(NOW + 1_200);
        HudView v = HudView.of(s, NOW);
        assertEquals(HudView.Mood.ASLEEP, v.mood());
        assertEquals(1_200, v.moodTicksLeft());
        assertFalse(v.regenActive());
        assertFalse(v.shieldReady());
    }

    @Test
    void thinkingShowsRemainingThinkingTime() {
        ThinkResult r = Brain.think(BrainState.EMPTY.withTokens(Brain.MAX_TOKENS), NOW);
        HudView v = HudView.of(r.state(), NOW + 60);
        assertEquals(HudView.Mood.THINKING, v.mood());
        assertEquals(Brain.THINK_DURATION_TICKS - 60, v.moodTicksLeft());
        assertEquals(Brain.THINK_COOLDOWN_TICKS - 60, v.thinkTicksLeft());
    }

    @Test
    void cooldownsAreZeroWhenReady() {
        HudView v = HudView.of(BrainState.EMPTY.withTokens(500), NOW);
        assertEquals(0, v.thinkTicksLeft());
        assertEquals(0, v.compactTicksLeft());
    }

    @Test
    void regenLightsUpOnlyOutOfCombatWithTokens() {
        BrainState s = BrainState.EMPTY.withTokens(500).withLastHurt(NOW - 20);
        assertFalse(HudView.of(s, NOW).regenActive());
        assertTrue(HudView.of(s, NOW + Brain.REGEN_DELAY_TICKS).regenActive());
        assertFalse(HudView.of(s.withTokens(0), NOW + Brain.REGEN_DELAY_TICKS).regenActive());
    }

    @Test
    void levelProgressFillsBetweenThresholds() {
        assertEquals(0f, Brain.levelProgress(0), 1e-6);
        assertEquals(1f / 3f, Brain.levelProgress(1), 1e-6);
        assertEquals(0f, Brain.levelProgress(3), 1e-6);
        assertEquals(1f / 7f, Brain.levelProgress(4), 1e-6);
        assertEquals(1f, Brain.levelProgress(50), 1e-6);
    }

    @Test
    void clockFormatsMinutesAndSeconds() {
        assertEquals("0:00", Brain.clock(0));
        assertEquals("0:05", Brain.clock(100));
        assertEquals("2:00", Brain.clock(2400));
        assertEquals("0:00", Brain.clock(-50));
    }
}
