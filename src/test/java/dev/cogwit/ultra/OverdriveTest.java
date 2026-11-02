package dev.cogwit.ultra;

import dev.cogwit.brain.Brain;
import dev.cogwit.brain.BrainState;
import dev.cogwit.brain.Growth;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class OverdriveTest {
    private static final long T0 = 100_000;
    private static final float MAX_XP = Growth.threshold(Growth.Perk.OVERDRIVE.level());

    private static BrainState fed(float xp) {
        return BrainState.EMPTY.withTokens(Brain.MAX_TOKENS).withXp(xp);
    }

    @Test
    void onlyAGrownAwakeFedCogwitRages() {
        assertTrue(Overdrive.canStart(fed(MAX_XP), UltraState.EMPTY, T0));
        assertFalse(Overdrive.canStart(fed(MAX_XP - 1), UltraState.EMPTY, T0), "needs level 5");
        assertFalse(Overdrive.canStart(fed(MAX_XP).asleep(T0, T0 + 10), UltraState.EMPTY, T0 + 1));
        assertFalse(Overdrive.canStart(BrainState.EMPTY.withXp(MAX_XP), UltraState.EMPTY, T0), "needs tokens");
    }

    @Test
    void startsWithABossAndLingersTenSecondsAfterIt() {
        Overdrive.Step start = Overdrive.tick(UltraState.EMPTY, true, true, T0);
        assertEquals(Overdrive.Change.STARTED, start.change());
        Overdrive.Step quiet = Overdrive.tick(start.state(), false, true, T0 + Overdrive.LINGER_TICKS - 1);
        assertEquals(Overdrive.Change.NONE, quiet.change());
        assertTrue(quiet.state().active());
        Overdrive.Step end = Overdrive.tick(quiet.state(), false, true, T0 + Overdrive.LINGER_TICKS);
        assertEquals(Overdrive.Change.ENDED, end.change());
        assertTrue(Overdrive.inHangover(end.state(), T0 + Overdrive.LINGER_TICKS + 1));
    }

    @Test
    void theBossKeepsItGoing() {
        UltraState u = Overdrive.tick(UltraState.EMPTY, true, true, T0).state();
        u = Overdrive.tick(u, true, true, T0 + 1000).state();
        assertEquals(Overdrive.Change.NONE, Overdrive.tick(u, false, true, T0 + 1100).change());
    }

    @Test
    void noRageDuringTheHangover() {
        UltraState after = new UltraState(false, 0, 0, T0 + 100, false, 1f);
        assertFalse(Overdrive.canStart(fed(MAX_XP), after, T0));
        assertEquals(Overdrive.Change.NONE, Overdrive.tick(after, true, false, T0).change());
    }

    @Test
    void rageProtectsMoreAndCostsTriple() {
        UltraState rage = new UltraState(true, T0, T0, 0, false, 1f);
        assertEquals(0.25f, Overdrive.protection(0f, rage, T0));
        assertEquals(0.6f, Overdrive.protection(0.6f, rage, T0));
        assertEquals(10, Overdrive.extraCost(5f, 0.25f, rage), "5 hp x 25% x 4 tokens = 5, twice more");
        assertEquals(0, Overdrive.extraCost(5f, 0.25f, UltraState.EMPTY));
        assertEquals(3, Overdrive.upkeep(1, rage));
        assertEquals(1, Overdrive.upkeep(1, UltraState.EMPTY));
    }

    @Test
    void hangoverHalvesProtection() {
        UltraState tired = new UltraState(false, 0, 0, T0 + 100, false, 1f);
        assertEquals(0.3f, Overdrive.protection(0.6f, tired, T0), 1e-6);
        assertEquals(0.6f, Overdrive.protection(0.6f, tired, T0 + 100), 1e-6);
    }

    @Test
    void collapseEndsRageImmediately() {
        UltraState rage = new UltraState(true, T0, T0, 0, true, 0.5f);
        UltraState down = Overdrive.collapse(rage, T0 + 5);
        assertFalse(down.active());
        assertTrue(Overdrive.inHangover(down, T0 + 6));
        assertSame(UltraState.EMPTY, Overdrive.collapse(UltraState.EMPTY, T0));
    }

    @Test
    void bossPhasesAreQuarters() {
        assertEquals(75, Overdrive.phaseCrossed(0.8f, 0.7f));
        assertEquals(50, Overdrive.phaseCrossed(0.55f, 0.5f));
        assertEquals(0, Overdrive.phaseCrossed(0.6f, 0.55f));
        assertEquals(75, Overdrive.phaseCrossed(1f, 0.3f), "a huge hit reports the first threshold crossed");
    }

    @Test
    void projectilesHeadedAtYouAreNoticed() {
        assertTrue(Overdrive.incoming(0, 0, 10, 0, 0, -1.5, 0, 0, 0), "straight at you");
        assertFalse(Overdrive.incoming(0, 0, 10, 0, 0, 1.5, 0, 0, 0), "flying away");
        assertFalse(Overdrive.incoming(0, 0, 10, 1.5, 0, 0, 0, 0, 0), "passing by");
        assertFalse(Overdrive.incoming(0, 0, 30, 0, 0, -1.5, 0, 0, 0), "too far to matter");
        assertFalse(Overdrive.incoming(0, 0, 10, 0, 0, -0.05, 0, 0, 0), "lying on the ground");
    }
}
