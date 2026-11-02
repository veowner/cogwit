package dev.cogwit.trophy;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class TrophiesTest {
    private static final UUID A = new UUID(0, 1), B = new UUID(0, 2), C = new UUID(0, 3);

    @Test
    void thirtyFiveDistinctBosses() {
        assertEquals(35, Trophy.values().length);
        assertEquals(35, java.util.Arrays.stream(Trophy.values()).map(t -> t.entity).distinct().count());
        assertEquals(Trophy.WARDEN, Trophy.byEntity("minecraft:warden").orElseThrow());
        assertEquals(Trophy.SCYLLA, Trophy.byKey("scylla").orElseThrow());
        assertEquals("cataclysm", Trophy.IGNIS.mod());
        assertFalse(Trophy.FERROUS_WROUGHTNAUT.hasActive);
        assertEquals("twilightforest", Trophy.LICH.mod());
        assertEquals("block_factorys_bosses", Trophy.KRAKEN.mod());
        assertEquals(Trophy.UNDERWORLD_KNIGHT, Trophy.byEntity("block_factorys_bosses:underworld_knight").orElseThrow());
    }

    @Test
    void theNewBossesKeepTheirOwnLairsAndOrder() {
        for (Trophy t : Trophy.values()) {
            assertNotNull(t.dimension, t.name());
            assertEquals(t.hasActive, t != Trophy.FERROUS_WROUGHTNAUT, t.name() + " has an active unless it is the Wroughtnaut");
            if (t.structure != null) {
                assertTrue(t.structure.startsWith(t.mod() + ":"), t.name() + " lives in a structure of its own mod");
            }
        }
        for (Trophy t : new Trophy[] {Trophy.NAGA, Trophy.LICH, Trophy.MINOSHROOM, Trophy.HYDRA, Trophy.KNIGHT_PHANTOM,
                Trophy.UR_GHAST, Trophy.ALPHA_YETI, Trophy.SNOW_QUEEN}) {
            assertEquals("twilightforest:twilight_forest", t.dimension, t.name());
        }
        assertEquals("minecraft:the_nether", Trophy.UNDERWORLD_KNIGHT.dimension, "the arena is in the Nether");
        // the Twilight Forest is fought in its own order: naga first, snow queen last
        Trophy[] order = {Trophy.NAGA, Trophy.LICH, Trophy.MINOSHROOM, Trophy.HYDRA, Trophy.KNIGHT_PHANTOM, Trophy.UR_GHAST,
                Trophy.ALPHA_YETI, Trophy.SNOW_QUEEN};
        for (int i = 1; i < order.length; i++) {
            assertTrue(order[i - 1].difficulty <= order[i].difficulty, order[i].name() + " is not easier than " + order[i - 1].name());
        }
    }

    @Test
    void everyoneWhoFoughtRecentlyGetsIt() {
        long now = 10_000;
        List<UUID> got = Trophies.credited(Map.of(A, now - 100, B, now - Trophies.CREDIT_TICKS - 1), C, now);
        assertTrue(got.contains(A), "hit it 5 seconds ago");
        assertFalse(got.contains(B), "left the fight long ago");
        assertTrue(got.contains(C), "the killer always counts");
        assertEquals(1, Trophies.credited(Map.of(A, now), A, now).size(), "no double entries");
    }

    @Test
    void completeOnlyWithAllOfThem() {
        assertFalse(Trophies.complete(EnumSet.of(Trophy.WITHER)));
        assertTrue(Trophies.complete(EnumSet.allOf(Trophy.class)));
    }

    @Test
    void friendsNearbyShareTheirPassives() {
        Set<Trophy> p = Trophies.passives(Set.of(Trophy.WITHER), List.of(Set.of(Trophy.WARDEN), Set.of()));
        assertEquals(EnumSet.of(Trophy.WITHER, Trophy.WARDEN), p);
        assertTrue(Trophies.passives(Set.of(), List.of()).isEmpty());
    }

    @Test
    void everyTrophyIsDescribedInBothLanguages() throws IOException {
        for (String file : List.of("ru_ru.json", "en_us.json")) {
            String lang = Files.readString(Path.of("src/main/resources/assets/cogwit/lang", file), StandardCharsets.UTF_8);
            for (Trophy t : Trophy.values()) {
                for (String part : List.of("name", "passive", "active", "hint")) {
                    assertTrue(lang.contains("\"cogwit.trophy." + t.key() + "." + part + "\":"), file + " lacks " + t.key() + "." + part);
                }
                if (t.structure != null) {
                    String path = t.structure.substring(t.structure.indexOf(':') + 1);
                    assertTrue(lang.contains("\"cogwit.guide.target." + path + "\":"), file + " lacks a name for the lair " + path);
                }
            }
        }
    }
}
