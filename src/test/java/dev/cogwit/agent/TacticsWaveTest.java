package dev.cogwit.agent;

import dev.cogwit.mind.Mind;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TacticsWaveTest {
    @Test
    void armorSoaksLessOfAHeavyBlow() {
        assertEquals(7.0, Agent.throughArmor(7, 0, 0), 1e-9);
        double light = Agent.throughArmor(4, 20, 8) / 4, heavy = Agent.throughArmor(14, 20, 8) / 14;
        assertTrue(heavy > light, "a heavy blow keeps a bigger share: " + heavy + " vs " + light);
    }

    @Test
    void theStyleShowsOnceThereIsEnoughToGoOn() {
        StyleProfile p = new StyleProfile();
        p.record(StyleProfile.Kind.RANGED, 30, 100);
        assertEquals(StyleProfile.Style.MIXED, p.style(100), "too little to judge");
        p.record(StyleProfile.Kind.RANGED, 50, 200);
        p.record(StyleProfile.Kind.MELEE, 10, 200);
        assertEquals(StyleProfile.Style.ARCHER, p.style(200));
        p.record(StyleProfile.Kind.MELEE, 400, 200 + (long) StyleProfile.HALF_LIFE_TICKS * 3);
        assertEquals(StyleProfile.Style.BRAWLER, p.style(200 + (long) StyleProfile.HALF_LIFE_TICKS * 3),
                "old arrows fade, new swings count");
    }

    @Test
    void theWayOutLeadsAwayFromTheCrowdOverSafeGround() {
        boolean[] all = new boolean[Escape.HEADINGS];
        java.util.Arrays.fill(all, true);
        List<double[]> east = List.of(new double[] {3, 0}, new double[] {4, 1});
        assertEquals(4, Escape.safest(east, all), "enemies to the east: go west");
        boolean[] noWest = all.clone();
        noWest[4] = false;
        int k = Escape.safest(east, noWest);
        assertTrue(k == 3 || k == 5, "west blocked: the next best is north- or south-west, got " + k);
        assertEquals(-1, Escape.safest(east, new boolean[Escape.HEADINGS]));
    }

    @Test
    void directionsAreToldRelativeToTheLook() {
        // looking south (+z): +x is to the left in Minecraft, -z is behind
        assertEquals(0, Escape.relative(0, 1, 0, 1));
        assertEquals(4, Escape.relative(0, 1, 0, -1));
        assertEquals(2, Escape.relative(0, 1, -1, 0));
        assertEquals(6, Escape.relative(0, 1, 1, 0));
    }

    @Test
    void aComboFollowsUpEvenOnAWeakMobAndArchersKeepTheirDistance() {
        Tactics.Fight weakLoner = new Tactics.Fight(1, false, 5, 5, 1f, 10f);
        List<Tactics.Move> ready = List.of(Tactics.MOVES.get("lich"), Tactics.MOVES.get("frostmaw"));
        assertTrue(Tactics.choose(weakLoner, ready).isEmpty(), "a lone weak mob isn't worth a cooldown");
        assertTrue(Tactics.choose(weakLoner, ready, StyleProfile.Style.MIXED, true).isPresent(), "in a combo it is");
        Tactics.Move guard = new Tactics.Move("guard", Tactics.Aim.AROUND, 6, 4, true);
        Tactics.Move charge = new Tactics.Move("charge", Tactics.Aim.DASH, 10, 4, false);
        // one tough mob: no crowd bonus, so only the style tips the choice
        Tactics.Fight crowd = new Tactics.Fight(1, false, 5, 5, 1f, 50f);
        Optional<Tactics.Move> archer = Tactics.choose(crowd, List.of(charge, guard), StyleProfile.Style.ARCHER, false);
        Optional<Tactics.Move> brawler = Tactics.choose(crowd, List.of(guard, charge), StyleProfile.Style.BRAWLER, false);
        assertEquals("guard", archer.orElseThrow().id());
        assertEquals("charge", brawler.orElseThrow().id());
    }

    @Test
    void surroundedMeansManyOrAFewWhenHurt() {
        assertTrue(Mind.surrounded(6, 1f));
        assertTrue(Mind.surrounded(4, 0.5f));
        assertFalse(Mind.surrounded(4, 0.9f));
        assertFalse(Mind.surrounded(2, 0.1f));
    }
}
