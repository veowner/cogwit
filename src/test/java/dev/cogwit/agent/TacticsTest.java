package dev.cogwit.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class TacticsTest {
    private static Tactics.Move m(String id) {
        return Tactics.MOVES.get(id);
    }

    @Test
    void weakLoneMobsAreLeftToTheSword() {
        Tactics.Fight zombie = new Tactics.Fight(1, false, 3, 0, 1f, 20);
        assertTrue(Tactics.choose(zombie, List.of(m("warden"), m("scylla"))).isEmpty());
    }

    @Test
    void aBossGetsTheStrongestMoveThatLands() {
        Tactics.Fight boss = new Tactics.Fight(1, true, 10, 5, 1f, 300);
        assertEquals("warden", Tactics.choose(boss, List.of(m("wither"), m("warden"), m("frostmaw")))
                .orElseThrow().id(), "the sonic boom beats skulls; the frost breath does not reach");
    }

    @Test
    void aimedMovesNeedTheTargetInFront() {
        Tactics.Fight behind = new Tactics.Fight(1, true, 10, 120, 1f, 300);
        assertTrue(Tactics.choose(behind, List.of(m("warden"), m("leviathan"))).isEmpty());
        assertEquals("umvuthi", Tactics.choose(behind, List.of(m("warden"), m("umvuthi"))).orElseThrow().id(),
                "a self-aiming move works anyway");
        Tactics.Fight nearlyOnIt = new Tactics.Fight(1, true, 10, 12, 1f, 300);
        assertFalse(Tactics.lands(m("elder_guardian"), nearlyOnIt), "the laser needs the crosshair on it");
        assertTrue(Tactics.lands(m("warden"), nearlyOnIt));
    }

    @Test
    void crowdsFavourAreaMoves() {
        Tactics.Fight crowd = new Tactics.Fight(5, false, 4, 0, 1f, 20);
        assertEquals("sun_spirit", Tactics.choose(crowd, List.of(m("wither"), m("sun_spirit"))).orElseThrow().id());
    }

    @Test
    void dashesStayOutOfPointBlankAndOutOfTrouble() {
        assertFalse(Tactics.lands(m("slider"), new Tactics.Fight(1, true, 2, 0, 1f, 300)), "too close");
        assertTrue(Tactics.lands(m("slider"), new Tactics.Fight(1, true, 6, 0, 1f, 300)));
        assertFalse(Tactics.lands(m("slider"), new Tactics.Fight(1, true, 6, 0, 0.2f, 300)), "not when hurt");
    }

    @Test
    void hurtYouGetProtectedFirst() {
        Tactics.Fight trouble = new Tactics.Fight(3, false, 3, 0, 0.2f, 20);
        assertEquals("netherite_monstrosity", Tactics.choose(trouble,
                List.of(m("scylla"), m("netherite_monstrosity"))).orElseThrow().id());
        assertTrue(Tactics.choose(new Tactics.Fight(1, false, 3, 0, 0.2f, 20), List.of(m("wither"))).isEmpty(),
                "a lone zombie when hurt: no attack move");
    }

    @Test
    void everyTrophyWithAnActiveIsKnown() {
        for (String key : List.of("ender_dragon", "wither", "warden", "elder_guardian", "ignis",
                "netherite_monstrosity", "ender_guardian", "harbinger", "leviathan", "ancient_remnant", "maledictus",
                "scylla", "frostmaw", "umvuthi", "sculptor", "slider", "valkyrie_queen", "sun_spirit", "dead_king",
                "fire_boss", "archevoker", "naga", "lich", "minoshroom", "hydra", "knight_phantom", "ur_ghast",
                "alpha_yeti", "snow_queen", "infernal_dragon", "kraken", "sandworm", "underworld_knight", "yeti")) {
            assertTrue(Tactics.MOVES.containsKey(key), key);
        }
    }
}
