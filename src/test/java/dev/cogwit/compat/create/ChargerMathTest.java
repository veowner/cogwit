package dev.cogwit.compat.create;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.cogwit.cells.Cells;
import org.junit.jupiter.api.Test;

class ChargerMathTest {
    @Test
    void fullSpeedGivesAHundredTwentyEightTokensPerSecond() {
        int charge = 0;
        float fraction = 0f;
        for (int i = 0; i < 20; i++) {
            ChargerMath.Step s = ChargerMath.step(charge, fraction, 256f);
            charge = s.charge();
            fraction = s.fraction();
        }
        assertEquals(128, charge, 1);
    }

    @Test
    void slowSpinAccumulatesFractions() {
        int charge = 0;
        float fraction = 0f;
        for (int i = 0; i < 200; i++) { // 10 seconds at 16 RPM = 8 tokens/s
            ChargerMath.Step s = ChargerMath.step(charge, fraction, 16f);
            charge = s.charge();
            fraction = s.fraction();
        }
        assertEquals(80, charge, 1);
    }

    @Test
    void reverseRotationChargesToo() {
        assertEquals(ChargerMath.tokensPerTick(64f), ChargerMath.tokensPerTick(-64f));
    }

    @Test
    void stopsAtCapacity() {
        assertEquals(Cells.CAPACITY, ChargerMath.step(Cells.CAPACITY - 1, 0.9f, 256f).charge());
        assertEquals(0f, ChargerMath.step(Cells.CAPACITY - 1, 0.9f, 256f).fraction());
        assertEquals(Cells.CAPACITY, ChargerMath.step(Cells.CAPACITY, 0f, 256f).charge());
    }

    @Test
    void standingStillChangesNothing() {
        ChargerMath.Step s = ChargerMath.step(100, 0.25f, 0f);
        assertEquals(100, s.charge());
        assertEquals(0.25f, s.fraction());
    }

    @Test
    void comparatorSignal() {
        assertEquals(0, ChargerMath.signal(false, 1000));
        assertEquals(0, ChargerMath.signal(true, 0));
        assertEquals(1, ChargerMath.signal(true, 1));
        assertEquals(8, ChargerMath.signal(true, Cells.CAPACITY / 2));
        assertEquals(14, ChargerMath.signal(true, Cells.CAPACITY - 1));
        assertEquals(15, ChargerMath.signal(true, Cells.CAPACITY));
    }
}
