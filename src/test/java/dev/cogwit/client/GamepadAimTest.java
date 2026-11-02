package dev.cogwit.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GamepadAimTest {
    @Test
    void restingStickDoesNotMoveThePointer() {
        assertNull(GamepadAim.aim(0.1f, -0.2f));
    }

    @Test
    void tiltGivesDirectionAndReach() {
        double[] up = GamepadAim.aim(0f, -1f);
        assertEquals(0, up[0], 1e-9);
        assertEquals(-1, up[1], 1e-9);
        assertEquals(1, up[2], 1e-9);
        double[] half = GamepadAim.aim(0.6f, 0f);
        assertEquals(1, half[0], 1e-9);
        assertTrue(half[2] > 0 && half[2] < 1, "partial tilt reaches part way");
    }

    @Test
    void diagonalsBeyondOneAreClamped() {
        assertEquals(1, GamepadAim.aim(1f, 1f)[2], 1e-9);
    }
}
