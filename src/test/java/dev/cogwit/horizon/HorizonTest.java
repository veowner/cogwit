package dev.cogwit.horizon;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class HorizonTest {
    private static final long T = 10_000;

    @Test
    void takingDamageBuildsMassAndMarksCombat() {
        MassGain g = Horizon.onDamageTaken(HorizonState.EMPTY, 20f, T);
        assertEquals(5f, g.state().mass(), 1e-6);
        assertEquals(T, g.state().lastCombat());
        assertEquals(0f, g.overflow(), 1e-6);
    }

    @Test
    void massCapsAndReportsOverflow() {
        HorizonState s = HorizonState.EMPTY.withMass(98f);
        MassGain g = Horizon.onDamageTaken(s, 20f, T);
        assertEquals(Horizon.MASS_MAX, g.state().mass(), 1e-6);
        assertEquals(3f, g.overflow(), 1e-6);
    }

    @Test
    void meleeHitsGetBonusFromMassAndAlsoBuildMass() {
        HorizonState s = HorizonState.EMPTY.withMass(50f);
        MeleeResult r = Horizon.onMeleeHit(s, 10f, T);
        assertEquals(5f, r.bonus(), 1e-6);
        assertEquals(51f, r.state().mass(), 1e-6);
    }

    @Test
    void massLeaksOnlyAfterThirtySecondsOutOfCombat() {
        HorizonState s = HorizonState.EMPTY.withMass(50f).withLastCombat(T);
        assertEquals(50f, Horizon.decay(s, T + Horizon.MASS_DECAY_DELAY_TICKS - 1).mass(), 1e-6);
        float after = Horizon.decay(s, T + Horizon.MASS_DECAY_DELAY_TICKS).mass();
        assertEquals(50f - Horizon.MASS_DECAY_PER_TICK, after, 1e-6);
        assertEquals(0f, Horizon.decay(HorizonState.EMPTY.withMass(0.01f), T + 100_000).mass(), 1e-6);
    }

    @Test
    void blinkHasShortCooldown() {
        AbilityResult r = Horizon.blink(HorizonState.EMPTY, T);
        assertEquals(AbilityResult.Status.OK, r.status());
        assertEquals(AbilityResult.Status.COOLDOWN, Horizon.blink(r.state(), T + 1).status());
        assertEquals(AbilityResult.Status.OK, Horizon.blink(r.state(), T + Horizon.BLINK_COOLDOWN_TICKS).status());
    }

    @Test
    void secondKeyCastsWellUntilMassIsFullThenCollapse() {
        assertEquals(Horizon.Ability.WELL, Horizon.gravityKey(HorizonState.EMPTY.withMass(99f)));
        assertEquals(Horizon.Ability.COLLAPSE, Horizon.gravityKey(HorizonState.EMPTY.withMass(Horizon.MASS_MAX)));
    }

    @Test
    void wellRunsForItsDurationThenCoolsDown() {
        AbilityResult r = Horizon.well(HorizonState.EMPTY, T);
        assertEquals(AbilityResult.Status.OK, r.status());
        assertTrue(Horizon.isWellActive(r.state(), T + Horizon.WELL_DURATION_TICKS - 1));
        assertFalse(Horizon.isWellActive(r.state(), T + Horizon.WELL_DURATION_TICKS));
        assertEquals(AbilityResult.Status.COOLDOWN, Horizon.well(r.state(), T + 10).status());
    }

    @Test
    void collapseNeedsFullMassAndEmptiesIt() {
        assertEquals(AbilityResult.Status.NOT_READY, Horizon.collapse(HorizonState.EMPTY.withMass(60f)).status());
        AbilityResult r = Horizon.collapse(HorizonState.EMPTY.withMass(Horizon.MASS_MAX));
        assertEquals(AbilityResult.Status.OK, r.status());
        assertEquals(0f, r.state().mass(), 1e-6);
    }

    @Test
    void voidRescueOncePerTenMinutes() {
        AbilityResult r = Horizon.voidRescue(HorizonState.EMPTY, T);
        assertEquals(AbilityResult.Status.OK, r.status());
        assertEquals(AbilityResult.Status.COOLDOWN, Horizon.voidRescue(r.state(), T + 1000).status());
        assertEquals(AbilityResult.Status.OK,
                Horizon.voidRescue(r.state(), T + Horizon.VOID_RESCUE_COOLDOWN_TICKS).status());
    }

    @Test
    void onlyIncomingProjectilesAreReflected() {
        // projectile at x=5 flying towards the player at the origin
        assertTrue(Horizon.isIncoming(5, 0, 0, -1, 0, 0, 0, 0, 0));
        // same projectile flying away
        assertFalse(Horizon.isIncoming(5, 0, 0, 1, 0, 0, 0, 0, 0));
    }

    @Test
    void reflectedProjectileHeadsBackToItsShooter() {
        double[] v = Horizon.aimAt(0, 0, 0, 0, 0, 10, 2.0);
        assertEquals(0, v[0], 1e-9);
        assertEquals(0, v[1], 1e-9);
        assertEquals(2.0, v[2], 1e-9);
    }

    @Test
    void tipsAreShownOnce() {
        HorizonState s = HorizonState.EMPTY;
        assertFalse(s.hasSeenTip("full_set"));
        assertTrue(s.withTip("full_set").hasSeenTip("full_set"));
    }
}
