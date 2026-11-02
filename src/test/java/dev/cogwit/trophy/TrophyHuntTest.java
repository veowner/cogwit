package dev.cogwit.trophy;

import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TrophyHuntTest {
    @Test
    void picksTheEasiestImplementedTrophyStillMissing() {
        assertEquals(Optional.of(Trophy.FERROUS_WROUGHTNAUT), TrophyHunt.next(Set.of(), t -> true, "minecraft:overworld"));
        assertEquals(Optional.of(Trophy.SLIDER),
                TrophyHunt.next(EnumSet.of(Trophy.FERROUS_WROUGHTNAUT), t -> true, "minecraft:overworld"));
        assertEquals(Optional.of(Trophy.ELDER_GUARDIAN),
                TrophyHunt.next(EnumSet.of(Trophy.FERROUS_WROUGHTNAUT, Trophy.SLIDER), t -> true, "minecraft:overworld"));
    }

    @Test
    void skipsTrophiesFromModsNotInThePack() {
        assertEquals(Optional.of(Trophy.ELDER_GUARDIAN),
                TrophyHunt.next(Set.of(), t -> t.mod().equals("minecraft"), "minecraft:overworld"));
        assertEquals(Optional.of(Trophy.WITHER), TrophyHunt.next(EnumSet.of(Trophy.ELDER_GUARDIAN),
                t -> t.mod().equals("minecraft"), "minecraft:overworld"));
    }

    @Test
    void everyTrophyCountsNow() {
        assertEquals(Trophy.values().length, TrophyHunt.countAvailable(t -> true));
    }

    @Test
    void nothingLeftMeansNoTarget() {
        Set<Trophy> all = EnumSet.allOf(Trophy.class);
        assertEquals(Optional.empty(), TrophyHunt.next(all, t -> true, "minecraft:overworld"));
    }

    @Test
    void starsCoverTheWholeScale() {
        for (Trophy t : Trophy.values()) {
            int stars = TrophyHunt.stars(t);
            assertTrue(stars >= 1 && stars <= 5, t.name());
        }
        assertEquals(1, TrophyHunt.stars(Trophy.FERROUS_WROUGHTNAUT));
        assertEquals(5, TrophyHunt.stars(Trophy.FIRE_BOSS));
    }
}
