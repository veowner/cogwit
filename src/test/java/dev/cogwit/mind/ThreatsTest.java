package dev.cogwit.mind;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ThreatsTest {
    @Test
    void anglesAreMeasuredFromWhereYouLook() {
        // looking north (-z): east (+x) is to the right
        assertEquals(0, Threats.relativeAngle(0, -1, 0, -5), 1e-6);
        assertEquals(90, Threats.relativeAngle(0, -1, 5, 0), 1e-6);
        assertEquals(-90, Threats.relativeAngle(0, -1, -5, 0), 1e-6);
        assertEquals(180, Math.abs(Threats.relativeAngle(0, -1, 0, 5)), 1e-6);
    }

    @Test
    void onlyThingsOffToTheSideGetArrows() {
        assertFalse(Threats.needsArrow(20));
        assertTrue(Threats.needsArrow(-120));
        assertTrue(Threats.needsArrow(180));
    }

    @Test
    void bracketsSnapOntoTheTarget() {
        assertEquals(Threats.LOCK_START, Threats.lockOn(0), 1e-6);
        assertTrue(Threats.lockOn(100) < Threats.LOCK_START && Threats.lockOn(100) > 1f);
        assertEquals(1f, Threats.lockOn(Threats.LOCK_MILLIS));
        assertEquals(1f, Threats.lockOn(10_000));
    }

    @Test
    void worstAndClosestComeFirstAndTheListIsCapped() {
        record T(String name, Threats.Level level, double distance) {}
        List<T> all = List.of(new T("zombie", Threats.Level.INFO, 2), new T("creeper", Threats.Level.DANGER, 9),
                new T("boss", Threats.Level.BOSS, 30), new T("near skeleton", Threats.Level.WARN, 3),
                new T("far skeleton", Threats.Level.WARN, 12), new T("a", Threats.Level.INFO, 5),
                new T("b", Threats.Level.INFO, 6), new T("c", Threats.Level.INFO, 7));
        List<T> shown = Threats.pick(all, T::level, T::distance);
        assertEquals(Threats.MAX_SHOWN, shown.size());
        assertEquals("creeper", shown.get(0).name());
        assertEquals("boss", shown.get(1).name());
        assertEquals("near skeleton", shown.get(2).name());
        assertEquals("zombie", shown.get(4).name());
    }

    @Test
    void creepersEscalate() {
        assertEquals(Threats.Level.DANGER, Threats.creeper(5, false, true));
        assertEquals(Threats.Level.WARN, Threats.creeper(4, true, false));
        assertEquals(Threats.Level.INFO, Threats.creeper(9, false, false));
    }
}
