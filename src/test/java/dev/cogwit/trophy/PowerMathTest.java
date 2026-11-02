package dev.cogwit.trophy;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PowerMathTest {
    @Test
    void theWroughtnautReturnsThirtyPercent() {
        assertEquals(3f, PowerMath.reflected(10f), 1e-6);
        assertEquals(0f, PowerMath.reflected(0f), 1e-6);
        assertEquals(0f, PowerMath.reflected(-4f), 1e-6, "healing is not reflected");
    }

    @Test
    void theDeadKingLeavesAQuarterOfTheHealthAndNeverLessThanOne() {
        assertEquals(5f, PowerMath.reviveHealth(20f), 1e-6);
        assertEquals(10f, PowerMath.reviveHealth(40f), 1e-6);
        assertEquals(1f, PowerMath.reviveHealth(2f), 1e-6);
    }

    @Test
    void theSculptorCatchesOnlyLongFallsOverCloseGround() {
        assertFalse(PowerMath.catchesFall(5f, 2), "a short fall needs no help");
        assertFalse(PowerMath.catchesFall(12f, -1), "no ground in sight, nothing to catch for");
        assertFalse(PowerMath.catchesFall(12f, PowerMath.CATCH_GROUND + 1), "ground too far below");
        assertTrue(PowerMath.catchesFall(7f, 1));
        assertTrue(PowerMath.catchesFall(30f, PowerMath.CATCH_GROUND));
    }

    @Test
    void theConeReachesAheadAndWidensForBigCreatures() {
        // looking along +z, 8 blocks, 35 degrees either side
        assertTrue(PowerMath.inCone(0, 0, 5, 0, 0, 1, 8, 35, 0.3), "straight ahead");
        assertTrue(PowerMath.inCone(2, 0, 5, 0, 0, 1, 8, 35, 0.3), "21 degrees off");
        assertFalse(PowerMath.inCone(5, 0, 5, 0, 0, 1, 8, 35, 0.3), "45 degrees off");
        assertFalse(PowerMath.inCone(0, 0, -5, 0, 0, 1, 8, 35, 0.3), "behind");
        assertFalse(PowerMath.inCone(0, 0, 9, 0, 0, 1, 8, 35, 0.3), "past the reach");
        assertTrue(PowerMath.inCone(0, 0, 8.2, 0, 0, 1, 8, 35, 0.5), "its edge is inside the reach");
        assertTrue(PowerMath.inCone(4.2, 0, 5, 0, 0, 1, 8, 35, 1.0), "a wide body touching the edge of the cone");
        assertFalse(PowerMath.inCone(4.2, 0, 5, 0, 0, 1, 8, 35, 0.1), "a thin one does not");
        assertTrue(PowerMath.inCone(0.1, 0, 0.1, 0, 0, 1, 8, 35, 0.4), "inside the caster's own space");
        assertFalse(PowerMath.inCone(0, 0, 5, 0, 0, 0, 8, 35, 0.3), "no direction, no cone");
    }

    /** Where the tip of a unit vertical shard ends up after rotateY(0).rotateX(tiltX).rotateZ(tiltZ). */
    private static double[] tipAfter(double[] tilts) {
        double x = -Math.sin(tilts[1]);
        double y = Math.cos(tilts[1]);
        double z = y * Math.sin(tilts[0]);
        y = y * Math.cos(tilts[0]);
        return new double[] {x, y, z};
    }

    @Test
    void aLeaningShardTipsTowardsTheRightSideByTheRightAngle() {
        double theta = 0.4;
        for (double[] dir : new double[][] {{1, 0}, {0, 1}, {-1, 0}, {0, -1}, {1, 1}, {-3, 2}}) {
            double[] tip = tipAfter(PowerMath.lean(dir[0], dir[1], theta));
            double len = Math.hypot(dir[0], dir[1]);
            assertEquals(Math.cos(theta), tip[1], 1e-9, "leans by theta from the vertical");
            assertEquals(dir[0] / len * Math.sin(theta), tip[0], 1e-9, "x towards the direction");
            assertEquals(dir[1] / len * Math.sin(theta), tip[2], 1e-9, "z towards the direction");
        }
        double[] none = PowerMath.lean(0, 0, 0.4);
        assertEquals(0, none[0], 1e-12);
        assertEquals(0, none[1], 1e-12);
    }

    @Test
    void aSlashReachesCreaturesAlongTheWholeSeam() {
        // a seam from (0,0,0) to (0,4,0)
        assertEquals(1.0, PowerMath.distanceToSegment(1, 2, 0, 0, 0, 0, 0, 4, 0), 1e-9, "beside the middle");
        assertEquals(0.0, PowerMath.distanceToSegment(0, 3, 0, 0, 0, 0, 0, 4, 0), 1e-9, "on it");
        assertEquals(2.0, PowerMath.distanceToSegment(0, 6, 0, 0, 0, 0, 0, 4, 0), 1e-9, "past the end, to the end");
        assertEquals(3.0, PowerMath.distanceToSegment(0, 0, 3, 0, 0, 0, 0, 0, 0), 1e-9, "a seam of no length is a point");
    }
}
