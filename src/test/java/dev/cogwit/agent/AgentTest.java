package dev.cogwit.agent;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class AgentTest {
    @Test
    void modesSetDefaultsAndOverridesWin() {
        assertEquals(Agent.Permission.ASK, Agent.permission(Agent.Mode.CAREFUL, Map.of(), Agent.Action.WEAPON));
        assertEquals(Agent.Permission.ALWAYS, Agent.permission(Agent.Mode.COMBAT, Map.of(), Agent.Action.WEAPON));
        assertEquals(Agent.Permission.NEVER, Agent.permission(Agent.Mode.MINER, Map.of(), Agent.Action.WEAPON));
        assertEquals(Agent.Permission.ALWAYS, Agent.permission(Agent.Mode.MINER, Map.of(), Agent.Action.MAGNET));
        assertEquals(Agent.Permission.NEVER, Agent.permission(Agent.Mode.COMBAT,
                Map.of(Agent.Action.EAT, Agent.Permission.NEVER), Agent.Action.EAT));
    }

    @Test
    void noQuestionsInAFightAboutTheFight() {
        assertEquals(Agent.Decision.ASK, Agent.decide(Agent.Permission.ASK, Agent.Action.WEAPON, false));
        assertEquals(Agent.Decision.ACT, Agent.decide(Agent.Permission.ASK, Agent.Action.WEAPON, true));
        assertEquals(Agent.Decision.ASK, Agent.decide(Agent.Permission.ASK, Agent.Action.MAGNET, true),
                "the magnet isn't a fight matter, so it still asks");
        assertEquals(Agent.Decision.SKIP, Agent.decide(Agent.Permission.NEVER, Agent.Action.PEARL, true));
        assertEquals(Agent.Decision.ACT, Agent.decide(Agent.Permission.ALWAYS, Agent.Action.MAGNET, false));
    }

    @Test
    void combatLastsFiveSeconds() {
        assertTrue(Agent.inCombat(1000, 1000 + Agent.COMBAT_TICKS - 1));
        assertFalse(Agent.inCombat(1000, 1000 + Agent.COMBAT_TICKS));
        assertFalse(Agent.inCombat(0, 50));
    }

    @Test
    void foodFitsTheGapAndSparesGoldenApples() {
        List<Agent.Food> foods = List.of(new Agent.Food(0, 8, 12.8f, false, false),
                new Agent.Food(1, 4, 9.6f, false, true),
                new Agent.Food(2, 4, 0.8f, true, false),
                new Agent.Food(3, 5, 6f, false, false));
        assertEquals(3, Agent.pickFood(foods, 15).orElseThrow().slot(), "5 missing: the 5-point food");
        assertEquals(0, Agent.pickFood(foods, 10).orElseThrow().slot(), "10 missing: the steak");
        assertTrue(Agent.pickFood(List.of(foods.get(1)), 5).isEmpty(), "never a golden apple");
        assertEquals(2, Agent.pickFood(List.of(foods.get(2)), 4).orElseThrow().slot(), "rotten flesh when starving");
        assertTrue(Agent.pickFood(List.of(foods.get(2)), 12).isEmpty(), "but not otherwise");
        assertTrue(Agent.wantsFood(13, false));
        assertFalse(Agent.wantsFood(10, true));
    }

    @Test
    void weaponsByDistance() {
        List<Agent.Weapon> hotbar = List.of(new Agent.Weapon(0, 7, false, false), new Agent.Weapon(1, 9, false, false),
                new Agent.Weapon(2, 6, true, true));
        assertEquals(1, Agent.pickWeapon(hotbar, 2, true).orElseThrow().slot(), "close: the hardest hitter");
        assertEquals(2, Agent.pickWeapon(hotbar, 12, true).orElseThrow().slot(), "far: the bow");
        List<Agent.Weapon> noArrows = List.of(new Agent.Weapon(0, 7, false, false), new Agent.Weapon(2, 6, true, false));
        assertEquals(0, Agent.pickWeapon(noArrows, 12, true).orElseThrow().slot(), "a bow without arrows is no use");
        assertEquals(1, Agent.pickWeapon(hotbar, 12, false).orElseThrow().slot(), "no bow over a weapon you hold");
    }

    @Test
    void yourChoiceInHandStays() {
        assertTrue(Agent.shouldArm(Agent.Held.EMPTY, 0, 5, 10), "bare hands: arm");
        assertFalse(Agent.shouldArm(Agent.Held.OTHER, 0, 50, 2), "a staff, shield or blocks stay");
        assertTrue(Agent.shouldArm(Agent.Held.TOOL, 0, 10, 3), "a pickaxe gives way with the mob at arm's length");
        assertFalse(Agent.shouldArm(Agent.Held.TOOL, 0, 10, 9), "but not for a skeleton far off");
        assertFalse(Agent.shouldArm(Agent.Held.WEAPON, 9.6, 11.2, 2), "iron sword to diamond: not worth it");
        assertTrue(Agent.shouldArm(Agent.Held.WEAPON, 6.4, 12.8, 2), "wooden sword to netherite: yes");
    }

    @Test
    void fallsWorthSaving() {
        assertEquals(7f, Agent.fallDamage(8, 2));
        assertTrue(Agent.fallNeedsSaving(8, 2, 7));
        assertFalse(Agent.fallNeedsSaving(8, 10, 15), "too early, the ground is far");
        assertFalse(Agent.fallNeedsSaving(2, 1, 0), "a hop");
    }

    @Test
    void wornOutArmorAndPearlDirection() {
        assertTrue(Agent.armorWornOut(400, 395));
        assertFalse(Agent.armorWornOut(400, 300));
        assertFalse(Agent.armorWornOut(0, 0), "unbreakable armor is never taken off");
        double[] d = Agent.escapeDirection(0, 0, 3, 0);
        assertTrue(d[0] < 0 && d[1] > 0, "away from the threat and upwards");
        assertEquals(1, d[0] * d[0] + d[1] * d[1] + d[2] * d[2], 1e-9);
    }

    @Test
    void magnetIgnoreList() {
        List<String> ignore = List.of("minecraft:rotten_flesh", "minecraft:cobblestone", "sometrash:*");
        assertTrue(Agent.ignoredByMagnet("minecraft:rotten_flesh", ignore));
        assertTrue(Agent.ignoredByMagnet("sometrash:junk", ignore));
        assertFalse(Agent.ignoredByMagnet("minecraft:diamond", ignore));
    }
}
