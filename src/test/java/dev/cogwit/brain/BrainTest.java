package dev.cogwit.brain;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BrainTest {
    private static final long T0 = 1_000;

    private static BrainState fresh() {
        return BrainState.EMPTY.withTokens(Brain.MAX_TOKENS);
    }

    /** Hits the same category {@code times} times, spaced so every hit counts as a lesson. */
    private static BrainState train(BrainState state, String category, int times, long start) {
        long now = start;
        for (int i = 0; i < times; i++) {
            state = Brain.onDamage(state, category, 1f, now).state();
            now += Brain.XP_COOLDOWN_TICKS;
        }
        return state;
    }

    @Test
    void firstHitTeachesButDoesNotProtect() {
        DamageResult r = Brain.onDamage(fresh(), "fire", 10f, T0);
        assertEquals(0f, r.reduction());
        assertEquals(1, r.state().skills().size());
        assertTrue(r.events().contains(new BrainEvent.Learned("fire")));
    }

    @Test
    void protectionGrowsInStepsAndCapsBelowImmortality() {
        assertEquals(0f, Brain.reductionForXp(2));
        assertEquals(0.10f, Brain.reductionForXp(3));
        assertEquals(0.25f, Brain.reductionForXp(10));
        assertEquals(0.40f, Brain.reductionForXp(25));
        assertEquals(0.60f, Brain.reductionForXp(50));
        assertEquals(0.60f, Brain.reductionForXp(500));
    }

    @Test
    void trainedSkillReducesDamage() {
        BrainState s = train(fresh(), "fire", 10, T0);
        DamageResult r = Brain.onDamage(s, "fire", 10f, T0 + 100_000);
        assertEquals(0.25f, r.reduction(), 1e-6);
    }

    @Test
    void levelUpEventFiresOnThreshold() {
        BrainState s = train(fresh(), "fall", 2, T0);
        DamageResult r = Brain.onDamage(s, "fall", 1f, T0 + 10_000);
        assertTrue(r.events().contains(new BrainEvent.LevelUp("fall", 1)));
    }

    @Test
    void rapidHitsTeachOnlyOnce() {
        BrainState s = fresh();
        s = Brain.onDamage(s, "lava", 1f, T0).state();
        s = Brain.onDamage(s, "lava", 1f, T0 + 1).state();
        s = Brain.onDamage(s, "lava", 1f, T0 + 2).state();
        assertEquals(1, s.skills().get(0).xp());
    }

    @Test
    void contextWindowEvictsLeastRecentlyUsed() {
        BrainState s = fresh();
        long now = T0;
        for (String c : List.of("fire", "fall", "explosion", "projectile", "magic")) {
            s = Brain.onDamage(s, c, 1f, now).state();
            now += 100;
        }
        // touch "fire" so "fall" becomes the stalest
        s = Brain.onDamage(s, "fire", 1f, now).state();
        now += 100;
        DamageResult r = Brain.onDamage(s, "freezing", 1f, now);
        assertEquals(Brain.CONTEXT_SIZE, r.state().skills().size());
        assertTrue(r.events().contains(new BrainEvent.Forgot("fall")));
        assertTrue(r.state().find("fire").isPresent());
        assertFalse(r.state().find("fall").isPresent());
    }

    @Test
    void compactMergesTwoWeakestAtHalfStrength() {
        BrainState s = fresh();
        s = train(s, "fire", 25, T0);           // 40%
        s = train(s, "fall", 10, T0 + 1_000);   // 25%
        s = train(s, "magic", 3, T0 + 2_000);   // 10%
        CompactResult r = Brain.compact(s, T0 + 50_000);
        assertEquals(CompactResult.Status.OK, r.status());
        assertEquals(2, r.state().skills().size());
        assertEquals(0.125f, Brain.reductionFor(r.state(), "fall"), 1e-6);
        assertEquals(0.05f, Brain.reductionFor(r.state(), "magic"), 1e-6);
        assertEquals(0.40f, Brain.reductionFor(r.state(), "fire"), 1e-6);
        assertEquals(CompactResult.Status.COOLDOWN, Brain.compact(r.state(), T0 + 50_001).status());
    }

    @Test
    void compactNeedsTwoUncompressedSkills() {
        BrainState s = Brain.onDamage(fresh(), "fire", 1f, T0).state();
        assertEquals(CompactResult.Status.NOT_ENOUGH, Brain.compact(s, T0).status());
    }

    @Test
    void protectionCostsTokensAndEmptyTankPutsCogwitToSleep() {
        BrainState s = train(fresh(), "fire", 50, T0).withTokens(10);
        long now = T0 + 100_000;
        DamageResult r = Brain.onDamage(s, "fire", 20f, now);
        // wants to prevent 12 HP (60% of 20) = 48 tokens, only 10 available → partial
        assertEquals(0, r.state().tokens());
        assertTrue(r.reduction() > 0f && r.reduction() < 0.60f);
        assertTrue(r.events().contains(new BrainEvent.FellAsleep()));
        assertTrue(Brain.isAsleep(r.state(), now + 1));
        assertFalse(Brain.isAsleep(r.state(), now + Brain.SLEEP_TICKS));
    }

    @Test
    void sleepingCogwitNeitherProtectsNorLearns() {
        BrainState s = train(fresh(), "fire", 50, T0).withSleepUntil(T0 + 500_000);
        DamageResult r = Brain.onDamage(s, "fire", 10f, T0 + 100_000);
        assertEquals(0f, r.reduction());
        assertEquals(s.skills(), r.state().skills());
    }

    @Test
    void itemsToFillStopsOnceFull() {
        assertEquals(0, Brain.itemsToFill(Brain.MAX_TOKENS, 30, 64));
        assertEquals(1, Brain.itemsToFill(Brain.MAX_TOKENS - 10, 30, 64));
        assertEquals(4, Brain.itemsToFill(1950, 15, 64));
        assertEquals(64, Brain.itemsToFill(0, 15, 64));
        assertEquals(3, Brain.itemsToFill(0, 30, 3));
    }

    @Test
    void feedingCanAlwaysReachFull() {
        BrainState s = fresh().withTokens(1985);
        s = Brain.feed(s, 30);
        assertEquals(Brain.MAX_TOKENS, s.tokens());
        int raw = Brain.itemsToFill(0, 15, Integer.MAX_VALUE);
        assertEquals(Brain.MAX_TOKENS, Brain.feed(BrainState.EMPTY, raw * 15).tokens());
    }

    @Test
    void hungerWarningFiresOnlyWhenCrossingTheLine() {
        assertTrue(Brain.becameHungry(Brain.HUNGRY_BELOW, Brain.HUNGRY_BELOW - 1));
        assertFalse(Brain.becameHungry(Brain.HUNGRY_BELOW - 5, Brain.HUNGRY_BELOW - 10));
        assertFalse(Brain.becameHungry(1000, 900));
    }

    @Test
    void feedingCapsAtMax() {
        BrainState s = Brain.feed(fresh().withTokens(Brain.MAX_TOKENS - 50), 100);
        assertEquals(Brain.MAX_TOKENS, s.tokens());
    }

    @Test
    void regenOnlyOutOfCombat() {
        BrainState s = fresh().withLastHurt(T0);
        assertEquals(0f, Brain.regen(s, T0 + 10, 10f, 20f).heal());
        RegenResult r = Brain.regen(s, T0 + Brain.REGEN_DELAY_TICKS, 10f, 20f);
        assertEquals(1f, r.heal());
        assertEquals(fresh().tokens() - Brain.REGEN_TOKEN_COST, r.state().tokens());
        assertEquals(0f, Brain.regen(s, T0 + Brain.REGEN_DELAY_TICKS, 20f, 20f).heal());
    }

    @Test
    void bestReductionTakesStrongestTeammate() {
        BrainState weak = train(fresh(), "explosion", 3, T0);
        BrainState strong = train(fresh(), "explosion", 25, T0);
        assertEquals(0.40f, Brain.bestReduction(List.of(weak, strong), "explosion", T0 + 1), 1e-6);
    }

    @Test
    void refusalIsSpokenRightAwayThenRateLimited() {
        BrainState s = fresh();
        assertTrue(Brain.shouldSayRefusal(s, 50));
        BrainState spoke = s.withLastRefusal(50);
        assertFalse(Brain.shouldSayRefusal(spoke, 60));
        assertTrue(Brain.shouldSayRefusal(spoke, 50 + Brain.REFUSAL_MESSAGE_COOLDOWN_TICKS));
    }

    @Test
    void thinkingHasCooldownAndCost() {
        ThinkResult r = Brain.think(fresh(), T0);
        assertEquals(ThinkResult.Status.OK, r.status());
        assertEquals(Brain.MAX_TOKENS - Brain.THINK_TOKEN_COST, r.state().tokens());
        assertEquals(ThinkResult.Status.COOLDOWN, Brain.think(r.state(), T0 + 1).status());
        assertEquals(ThinkResult.Status.NO_TOKENS, Brain.think(BrainState.EMPTY, T0).status());
    }
}
