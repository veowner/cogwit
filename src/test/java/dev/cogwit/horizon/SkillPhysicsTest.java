package dev.cogwit.horizon;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SkillPhysicsTest {
    private static double dot(double[] a, double x, double y, double z) {
        return a[0] * x + a[1] * y + a[2] * z;
    }

    @Test
    void theWellPullsInwardAndSwirls() {
        double[] f = SkillPhysics.wellForce(4, 0, 0, 8);
        assertTrue(f[0] < 0, "towards the centre");
        assertTrue(Math.abs(f[2]) > 0.01, "and sideways, so things orbit");
        double[] near = SkillPhysics.wellForce(1, 0, 0, 8), far = SkillPhysics.wellForce(7, 0, 0, 8);
        assertTrue(-near[0] > -far[0], "stronger near the core");
        assertArrayEquals(new double[] {0, 0, 0}, SkillPhysics.wellForce(9, 0, 0, 8), 1e-9, "nothing outside");
    }

    @Test
    void projectilesBendButKeepTheirSpeed() {
        double[] v = SkillPhysics.bendProjectile(2, 0, 0, 0, 0, 5);
        assertEquals(2, Math.sqrt(dot(v, v[0], v[1], v[2])), 1e-9);
        assertTrue(v[2] > 0, "turned towards the well");
        assertTrue(v[0] > v[2], "but only part of the way each tick");
    }

    @Test
    void projectilesTurnHarderNearTheCore() {
        assertTrue(SkillPhysics.bendFor(1, 8) > 2 * SkillPhysics.bendFor(7, 8));
        assertTrue(SkillPhysics.bendFor(0, 8) < 1, "never more than a full turn");
    }

    @Test
    void theCollapseDragsInThenBlastsOut() {
        double[] early = SkillPhysics.collapseDrag(5, 0, 1), late = SkillPhysics.collapseDrag(5, 0, 29);
        assertTrue(early[0] < 0 && late[0] < early[0], "dragged in, harder over time");
        assertTrue(late[1] > early[1], "and lifted more");
        double[] blast = SkillPhysics.collapseBlast(3, 4, 12);
        assertTrue(blast[0] > 0 && blast[2] > 0 && blast[1] > 0, "out and up");
        double[] farBlast = SkillPhysics.collapseBlast(9, 0, 12), nearBlast = SkillPhysics.collapseBlast(1, 0, 12);
        assertTrue(nearBlast[0] > farBlast[0], "weaker further away");
    }

    @Test
    void screenEffectsFadeWithDistance() {
        assertEquals(1f, SkillPhysics.screenFalloff(0, 20));
        assertTrue(SkillPhysics.screenFalloff(10, 20) < 0.5f);
        assertEquals(0f, SkillPhysics.screenFalloff(25, 20));
    }

    @Test
    void spiralsEndAtTheCentre() {
        double[] rim = SkillPhysics.spiralPoint(4, 1.5, 0, 0);
        assertEquals(4, Math.hypot(rim[0], rim[1]), 1e-9);
        double[] mid = SkillPhysics.spiralPoint(4, 1.5, 0, 0.5);
        assertEquals(2, Math.hypot(mid[0], mid[1]), 1e-9);
    }
}
