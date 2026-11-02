package dev.cogwit.speech;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpeechFilterTest {
    @Test
    void quietKeepsDangerAndAchievements() {
        assertTrue(SpeechFilter.isImportant("cogwit.say.hungry"));
        assertTrue(SpeechFilter.isImportant("cogwit.trophy.gained"));
        assertTrue(SpeechFilter.isImportant("cogwit.guide.done"));
        assertTrue(SpeechFilter.isImportant("cogwit.perk.brothers"));
    }

    @Test
    void quietDropsSmallTalk() {
        assertFalse(SpeechFilter.isImportant("cogwit.say.level_up"));
        assertFalse(SpeechFilter.isImportant("cogwit.guide.tip.3"));
        assertFalse(SpeechFilter.isImportant("cogwit.say.refusal"));
    }
}
