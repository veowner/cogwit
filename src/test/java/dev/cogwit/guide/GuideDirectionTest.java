package dev.cogwit.guide;

import dev.cogwit.guide.GuideDirection.Compass;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GuideDirectionTest {
    @Test
    void minecraftAxesMapToCompassPoints() {
        assertEquals(Compass.NORTH, GuideDirection.compass(0, -100));
        assertEquals(Compass.SOUTH, GuideDirection.compass(0, 100));
        assertEquals(Compass.EAST, GuideDirection.compass(100, 0));
        assertEquals(Compass.WEST, GuideDirection.compass(-100, 0));
    }

    @Test
    void diagonalsCoverTheCorners() {
        assertEquals(Compass.NORTH_EAST, GuideDirection.compass(100, -100));
        assertEquals(Compass.SOUTH_EAST, GuideDirection.compass(100, 100));
        assertEquals(Compass.SOUTH_WEST, GuideDirection.compass(-100, 100));
        assertEquals(Compass.NORTH_WEST, GuideDirection.compass(-100, -100));
    }

    @Test
    void mostlyNorthStaysNorth() {
        assertEquals(Compass.NORTH, GuideDirection.compass(30, -100));
        assertEquals(Compass.NORTH_EAST, GuideDirection.compass(60, -100));
    }

    @Test
    void arrowPointsUpWhenTargetIsStraightAhead() {
        assertEquals(0f, GuideDirection.arrowDegrees(0, 100, 0f), 0.01f);
        assertEquals(0f, GuideDirection.arrowDegrees(-100, 0, 90f), 0.01f);
        assertEquals(0f, GuideDirection.arrowDegrees(100, 0, -90f), 0.01f);
    }

    @Test
    void arrowTurnsTowardTheSideOfTheTarget() {
        // facing south, east is on the player's left, west on the right
        assertEquals(-90f, GuideDirection.arrowDegrees(100, 0, 0f), 0.01f);
        assertEquals(90f, GuideDirection.arrowDegrees(-100, 0, 0f), 0.01f);
        assertEquals(180f, Math.abs(GuideDirection.arrowDegrees(0, -100, 0f)), 0.01f);
    }

    @Test
    void arrowAngleStaysInHalfOpenRangeForAnyYaw() {
        for (float yaw = -720f; yaw <= 720f; yaw += 37.5f) {
            float a = GuideDirection.arrowDegrees(12, -34, yaw);
            assertTrue(a >= -180f && a < 180f, "yaw " + yaw + " gave " + a);
        }
    }

    @Test
    void distancesAreRoundedForSpeech() {
        assertEquals(10, GuideDirection.roughDistance(3));
        assertEquals(60, GuideDirection.roughDistance(63));
        assertEquals(100, GuideDirection.roughDistance(118));
        assertEquals(450, GuideDirection.roughDistance(437));
        assertEquals(1200, GuideDirection.roughDistance(1190));
    }
}
