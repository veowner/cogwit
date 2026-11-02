package dev.cogwit.speech;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SpeechTimingTest {
    @Test
    void longerLinesStayLongerButNotForever() {
        assertTrue(SpeechTiming.lifetime(100, false) > SpeechTiming.lifetime(10, false));
        assertEquals(SpeechTiming.MAX_MILLIS, SpeechTiming.lifetime(10_000, false));
        assertEquals(SpeechTiming.HINT_MILLIS, SpeechTiming.lifetime(100, true));
    }

    @Test
    void linesTypeOutButHintsAppearAtOnce() {
        assertEquals(1, SpeechTiming.visibleChars(0, 50, false));
        assertTrue(SpeechTiming.visibleChars(300, 50, false) < 50);
        assertEquals(50, SpeechTiming.visibleChars(10_000, 50, false));
        assertEquals(50, SpeechTiming.visibleChars(0, 50, true));
    }

    @Test
    void fadesOutAtTheEnd() {
        long life = 5000;
        assertEquals(1f, SpeechTiming.alpha(0, life));
        assertEquals(1f, SpeechTiming.alpha(life - SpeechTiming.FADE_MILLIS, life));
        assertEquals(0.5f, SpeechTiming.alpha(life - SpeechTiming.FADE_MILLIS / 2, life), 1e-6);
        assertEquals(0f, SpeechTiming.alpha(life, life));
    }
}
