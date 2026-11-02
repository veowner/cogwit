package dev.cogwit.horizon;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DamageMultiplierTest {
    @Test
    void emptyMeterTakesTheBaseShareOff() {
        assertEquals(0.70f, Horizon.damageMultiplier(0, 0.30f, 0.20f), 1e-5);
    }

    @Test
    void fullMassAddsTheMassShare() {
        assertEquals(0.50f, Horizon.damageMultiplier(Horizon.MASS_MAX, 0.30f, 0.20f), 1e-5);
        assertEquals(0.60f, Horizon.damageMultiplier(Horizon.MASS_MAX / 2, 0.30f, 0.20f), 1e-5);
    }

    @Test
    void neverBecomesInvulnerable() {
        assertEquals(0.05f, Horizon.damageMultiplier(Horizon.MASS_MAX, 0.9f, 0.9f), 1e-5);
    }
}
