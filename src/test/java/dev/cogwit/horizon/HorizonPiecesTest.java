package dev.cogwit.horizon;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HorizonPiecesTest {
    private static final int HELMET = Horizon.Piece.HELMET.bit, CHEST = Horizon.Piece.CHEST.bit,
            LEGS = Horizon.Piece.LEGS.bit, BOOTS = Horizon.Piece.BOOTS.bit;

    @Test
    void eachPartBringsItsOwnFeature() {
        assertTrue(Horizon.has(HELMET, Horizon.Feature.VISOR));
        assertTrue(Horizon.has(CHEST, Horizon.Feature.CORE));
        assertTrue(Horizon.has(LEGS, Horizon.Feature.THRUSTERS));
        assertTrue(Horizon.has(BOOTS, Horizon.Feature.LANDING));
        assertFalse(Horizon.has(CHEST, Horizon.Feature.THRUSTERS));
        assertFalse(Horizon.has(HELMET | CHEST, Horizon.Feature.LANDING));
    }

    @Test
    void flightNeedsLegsAndBootsAndOnlyTheWholeSuitIsSealed() {
        assertFalse(Horizon.has(LEGS, Horizon.Feature.FLIGHT));
        assertFalse(Horizon.has(BOOTS, Horizon.Feature.FLIGHT));
        assertTrue(Horizon.has(LEGS | BOOTS, Horizon.Feature.FLIGHT));
        assertFalse(Horizon.has(HELMET | CHEST | LEGS, Horizon.Feature.SEALED));
        assertTrue(Horizon.has(Horizon.FULL, Horizon.Feature.SEALED));
    }

    @Test
    void protectionIsSplitByArmorPoints() {
        assertEquals(0f, Horizon.share(0), 1e-6);
        assertEquals(9f / 24, Horizon.share(CHEST), 1e-6);
        assertEquals(8f / 24, Horizon.share(HELMET | BOOTS), 1e-6);
        assertEquals(1f, Horizon.share(Horizon.FULL), 1e-6);
    }

    @Test
    void theFullSuitMatchesTheOldSetBonusAndMassNeedsTheCore() {
        assertEquals(Horizon.damageMultiplier(50f, 0.3f, 0.2f),
                Horizon.damageMultiplier(Horizon.FULL, 50f, 0.3f, 0.2f), 1e-6);
        // boots alone: 4/24 of the 30%, and mass doesn't count without the core
        assertEquals(1f - 0.3f * 4 / 24, Horizon.damageMultiplier(BOOTS, 100f, 0.3f, 0.2f), 1e-6);
    }

    @Test
    void landingsGrowWithHeightAndTheCrouchComesBack() {
        assertEquals(0f, Horizon.landingStrength(4f));
        assertTrue(Horizon.landingStrength(8f) < Horizon.landingStrength(20f));
        assertEquals(1f, Horizon.landingStrength(200f));
        assertTrue(Horizon.landingRadius(1f) > Horizon.landingRadius(0.2f));
        assertEquals(0f, Horizon.landingCrouch(0, 1f));
        assertEquals(1f, Horizon.landingCrouch(200, 1f));
        assertEquals(0f, Horizon.landingCrouch(Horizon.landingPoseMillis(1f), 1f), 1e-6);
    }
}
