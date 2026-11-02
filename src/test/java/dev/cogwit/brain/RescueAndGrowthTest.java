package dev.cogwit.brain;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RescueAndGrowthTest {
    private static final long T0 = 10_000;

    private static BrainState fed() {
        return BrainState.EMPTY.withTokens(Brain.MAX_TOKENS);
    }

    @Test
    void lethalHitLeavesTwoHealth() {
        assertTrue(Brain.isLethal(5f, 5f));
        assertFalse(Brain.isLethal(5f, 4.5f));
        assertEquals(3f, Brain.survivableDamage(5f, 30f));
        assertEquals(4f, Brain.survivableDamage(5f, 4f), "non-lethal hits pass through untouched");
        assertEquals(0f, Brain.survivableDamage(1f, 30f), "never heals, only holds");
    }

    @Test
    void rescueCostsTenMinutesOfSleepAndShieldsForThreeSeconds() {
        BrainState s = Brain.rescue(fed(), T0).orElseThrow();
        assertTrue(Brain.isAsleep(s, T0 + Brain.RESCUE_SLEEP_TICKS - 1));
        assertFalse(Brain.isAsleep(s, T0 + Brain.RESCUE_SLEEP_TICKS));
        assertTrue(Brain.isShielded(s, T0));
        assertTrue(Brain.isShielded(s, T0 + 59));
        assertFalse(Brain.isShielded(s, T0 + 60));
        assertEquals(Growth.RESCUE_XP, s.xp());
        assertEquals(Brain.MAX_TOKENS, s.tokens(), "rescuing is paid with sleep, not tokens");
    }

    @Test
    void sleepingCogwitCannotRescue() {
        BrainState s = Brain.rescue(fed(), T0).orElseThrow();
        assertTrue(Brain.rescue(s, T0 + 100).isEmpty());
    }

    @Test
    void neverShieldedBeforeAnyRescue() {
        assertFalse(Brain.isShielded(fed(), 0));
        assertFalse(Brain.isShielded(fed(), 30));
    }

    @Test
    void copperAppleWakesOnlyAfterThreeMinutes() {
        BrainState s = Brain.rescue(fed(), T0).orElseThrow();
        assertFalse(Brain.canWake(s, T0 + Brain.WAKE_AFTER_TICKS - 1));
        assertEquals(1, Brain.wakeTicksLeft(s, T0 + Brain.WAKE_AFTER_TICKS - 1));
        assertTrue(Brain.canWake(s, T0 + Brain.WAKE_AFTER_TICKS));
        assertFalse(Brain.canWake(fed(), T0), "an awake Cogwit has nothing to wake from");
    }

    @Test
    void runningOutOfTokensRecordsWhenSleepStarted() {
        BrainState trained = new BrainState(List.of(new Skill("fire", 50, 0, 0, "", 0)), 1, 0, 0, 0, 0, 0);
        DamageResult r = Brain.onDamage(trained, "fire", 10f, T0);
        assertTrue(Brain.isAsleep(r.state(), T0 + 1));
        assertEquals(T0, r.state().sleptAt());
        assertEquals(Brain.WAKE_AFTER_TICKS, Brain.wakeTicksLeft(r.state(), T0));
    }

    @Test
    void levelsOpenPerksInOrder() {
        assertEquals(0, Growth.level(0));
        assertEquals(0, Growth.level(49.9f));
        assertEquals(1, Growth.level(50));
        assertEquals(Growth.MAX_LEVEL, Growth.level(1e6f));
        assertTrue(Growth.has(50, Growth.Perk.EYE));
        assertFalse(Growth.has(50, Growth.Perk.HIGHLIGHT));
        assertTrue(Growth.has(500, Growth.Perk.BACK_SHIELD));
        assertFalse(Growth.has(999, Growth.Perk.BROTHERS));
        assertTrue(Growth.has(2000, Growth.Perk.OVERDRIVE));
        assertEquals(Growth.Perk.HIGHLIGHT, Growth.next(60));
        assertNull(Growth.next(5000));
    }

    @Test
    void progressAndPointsToNextLevel() {
        assertEquals(0.5f, Growth.progress(125), 1e-6);
        assertEquals(75, Growth.toNext(125));
        assertEquals(1, Growth.toNext(199.2f), "rounds up so it never says 0 before the level");
        assertEquals(1f, Growth.progress(2000));
        assertEquals(0, Growth.toNext(2000));
        assertEquals(List.of(1, 2), Growth.levelsGained(10, 250));
        assertEquals(List.of(), Growth.levelsGained(60, 70));
    }

    @Test
    void blockedDamageGrowsCogwitAndAnnouncesLevels() {
        BrainState trained = new BrainState(List.of(new Skill("fire", 50, 0, 0, "", 0)), Brain.MAX_TOKENS,
                0, 0, 0, 0, 0).withXp(45);
        DamageResult r = Brain.onDamage(trained, "fire", 10f, T0);
        assertEquals(51f, r.state().xp(), 1e-4, "60% of 10 = 6 points blocked");
        assertTrue(r.events().contains(new BrainEvent.Grew(1)));
    }

    @Test
    void unprotectedHitsTeachSkillsButDoNotGrowCogwit() {
        DamageResult r = Brain.onDamage(fed(), "fire", 10f, T0);
        assertEquals(0f, r.state().xp());
    }

    @Test
    void backShieldNeedsLevelThreeAndHasCooldown() {
        BrainState young = fed().withXp(499);
        assertTrue(Brain.backShield(young, T0).isEmpty());
        BrainState grown = fed().withXp(500);
        BrainState used = Brain.backShield(grown, T0).orElseThrow();
        assertEquals(Brain.MAX_TOKENS - Brain.BACK_SHIELD_TOKEN_COST, used.tokens());
        assertTrue(Brain.backShield(used, T0 + Brain.BACK_SHIELD_COOLDOWN_TICKS - 1).isEmpty());
        assertTrue(Brain.backShield(used, T0 + Brain.BACK_SHIELD_COOLDOWN_TICKS).isPresent());
        assertTrue(Brain.backShield(grown.withTokens(Brain.BACK_SHIELD_TOKEN_COST - 1), T0).isEmpty());
    }

    @Test
    void behindMeansOppositeTheLookDirection() {
        // looking north (-z)
        assertTrue(Brain.isBehind(0, -1, 0, 1), "straight behind");
        assertTrue(Brain.isBehind(0, -1, 0.5, 1), "behind and a little to the side");
        assertFalse(Brain.isBehind(0, -1, 1, 0), "to the side is visible");
        assertFalse(Brain.isBehind(0, -1, 0, -1), "in front");
        assertFalse(Brain.isBehind(0, -1, 0, 0), "standing inside you");
    }

    @Test
    void hudShowsLevelWakeAndShield() {
        BrainState s = Brain.rescue(fed().withXp(500), T0).orElseThrow();
        HudView v = HudView.of(s, T0 + 100);
        assertEquals(3, v.level());
        assertEquals(Brain.WAKE_AFTER_TICKS - 100, v.wakeTicksLeft());
        assertTrue(v.backShieldUnlocked());
        assertEquals(0, HudView.of(fed(), T0).wakeTicksLeft());
    }
}
