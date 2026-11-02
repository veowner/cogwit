package dev.cogwit.mind;

import dev.cogwit.brain.Brain;
import dev.cogwit.brain.BrainState;
import dev.cogwit.brain.Growth;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MindTest {
    private static final long T0 = 10_000;
    private static final float HIGHLIGHT_XP = Growth.threshold(Growth.Perk.HIGHLIGHT.level());

    private static BrainState fed(float xp) {
        return BrainState.EMPTY.withTokens(Brain.MAX_TOKENS).withXp(xp);
    }

    @Test
    void hintsRepeatAtMostEveryTenSeconds() {
        assertTrue(Mind.ready(0, T0));
        assertFalse(Mind.ready(T0, T0 + Mind.HINT_COOLDOWN_TICKS - 1));
        assertTrue(Mind.ready(T0, T0 + Mind.HINT_COOLDOWN_TICKS));
    }

    @Test
    void creepersMatterWhenCloseAndBehindOrHissing() {
        assertTrue(Mind.creeperThreat(4, true, false));
        assertTrue(Mind.creeperThreat(4, false, true));
        assertFalse(Mind.creeperThreat(4, false, false), "one you can see and that isn't hissing is fine");
        assertFalse(Mind.creeperThreat(12, true, true), "too far");
    }

    @Test
    void scanningNeedsLevelTwoTokensAndCooldown() {
        assertEquals(Mind.Scan.LOCKED, Mind.canScan(fed(HIGHLIGHT_XP - 1), MindState.EMPTY, T0));
        assertEquals(Mind.Scan.OK, Mind.canScan(fed(HIGHLIGHT_XP), MindState.EMPTY, T0));
        assertEquals(Mind.Scan.COOLDOWN, Mind.canScan(fed(HIGHLIGHT_XP),
                MindState.EMPTY.withScanReadyAt(T0 + 1), T0));
        assertEquals(Mind.Scan.NO_TOKENS, Mind.canScan(BrainState.EMPTY.withXp(HIGHLIGHT_XP), MindState.EMPTY, T0));
        assertEquals(Mind.Scan.ASLEEP, Mind.canScan(fed(HIGHLIGHT_XP).asleep(T0, T0 + 10), MindState.EMPTY, T0 + 1));
    }

    @Test
    void fearBeatsJoy() {
        MindState m = Mind.cheer(MindState.EMPTY, T0, Mind.HAPPY_FED_TICKS);
        assertEquals(Mind.Emotion.HAPPY, Mind.emotion(m, T0 + 1));
        assertEquals(Mind.Emotion.SCARED, Mind.emotion(Mind.scare(m, T0), T0 + 1));
        assertEquals(Mind.Emotion.CALM, Mind.emotion(m, T0 + Mind.HAPPY_FED_TICKS));
    }

    @Test
    void cheeringNeverShortensJoy() {
        MindState long_ = Mind.cheer(MindState.EMPTY, T0, Mind.HAPPY_LEVEL_TICKS);
        assertEquals(T0 + Mind.HAPPY_LEVEL_TICKS, Mind.cheer(long_, T0, Mind.HAPPY_KILL_TICKS).happyUntil());
    }

    @Test
    void deathsAreSortedByCause() {
        assertEquals(Mind.DeathKind.FALL, Mind.classify("fall", false, false, false));
        assertEquals(Mind.DeathKind.LAVA, Mind.classify("lava", false, false, false));
        assertEquals(Mind.DeathKind.EXPLOSION, Mind.classify("explosion", false, true, false));
        assertEquals(Mind.DeathKind.BOSS, Mind.classify("mob_attack", true, true, false));
        assertEquals(Mind.DeathKind.MOB, Mind.classify("mob_attack", false, true, false));
        assertEquals(Mind.DeathKind.PLAYER, Mind.classify("player_attack", false, false, true));
        assertEquals(Mind.DeathKind.VOID, Mind.classify("out_of_world", false, false, false));
        assertEquals(Mind.DeathKind.OTHER, Mind.classify("cactus", false, false, false));
    }

    @Test
    void commentsRotate() {
        assertEquals(2, Mind.variant(1));
        assertEquals(1, Mind.variant(3));
        assertNotEquals(Mind.variant(4), Mind.variant(5));
    }
}
