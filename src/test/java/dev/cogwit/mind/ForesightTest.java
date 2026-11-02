package dev.cogwit.mind;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ForesightTest {
    @Test
    void valuableOresWinTheOutlineBudget() {
        record F(Mind.Ore ore, double d) {}
        List<F> all = List.of(new F(Mind.Ore.COAL, 1), new F(Mind.Ore.IRON, 2), new F(Mind.Ore.DIAMOND, 30),
                new F(Mind.Ore.IRON, 1.5));
        List<F> two = Mind.pickFinds(all, F::ore, F::d, 2);
        assertEquals(Mind.Ore.DIAMOND, two.get(0).ore());
        assertEquals(1.5, two.get(1).d());
    }

    @Test
    void theWaveReachesFarBlocksLater() {
        assertEquals(0, Mind.waveArrival(0));
        assertEquals(800, Mind.waveArrival(32));
    }

    @Test
    void nightIsAnnouncedAboutAMinuteAhead() {
        assertTrue(Mind.duskWarning(11_350));
        assertTrue(Mind.duskWarning(24_000 * 5 + 11_400));
        assertFalse(Mind.duskWarning(6_000));
        assertFalse(Mind.duskWarning(13_000));
    }

    @Test
    void aCliffCountsOnlyWhenTheFallWouldHurt() {
        assertTrue(Mind.cliffAhead(14, false, false));
        assertFalse(Mind.cliffAhead(5, false, false));
        assertFalse(Mind.cliffAhead(30, true, false));
        assertFalse(Mind.cliffAhead(30, false, true));
    }

    @Test
    void onlyAnAggressiveMobClosingInIsComing() {
        assertTrue(Threats.coming(true, 0.1));
        assertFalse(Threats.coming(false, 0.1));
        assertFalse(Threats.coming(true, -0.1));
    }
}
