package dev.cogwit.crew;

import dev.cogwit.brain.Brain;
import dev.cogwit.brain.BrainState;
import dev.cogwit.brain.Growth;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CrewTest {
    private static final long T0 = 50_000;

    private static BrainState fed(float xp) {
        return BrainState.EMPTY.withTokens(Brain.MAX_TOKENS).withXp(xp);
    }

    @Test
    void deployNeedsAnAwakeFedCogwit() {
        assertEquals(Crew.Deploy.OK, Crew.canDeploy(fed(0), 500, T0));
        assertEquals(Crew.Deploy.NO_TOKENS, Crew.canDeploy(fed(0), Crew.MIN_TOKENS_TO_DEPLOY - 1, T0));
        assertEquals(Crew.Deploy.ASLEEP, Crew.canDeploy(fed(0).asleep(T0, T0 + 100), 500, T0 + 1));
    }

    @Test
    void brothersJoinFromLevelFour() {
        assertEquals(List.of(Crew.Kind.COGWIT), Crew.squad(fed(999), CrewState.EMPTY, T0));
        assertEquals(List.of(Crew.Kind.COGWIT, Crew.Kind.SPARK, Crew.Kind.RIVET, Crew.Kind.ANVIL),
                Crew.squad(fed(Growth.threshold(Growth.Perk.BROTHERS.level())), CrewState.EMPTY, T0));
    }

    @Test
    void aFallenBrotherSitsOutAndCogwitGrieves() {
        CrewState crew = Crew.brotherFell(CrewState.EMPTY, Crew.Kind.RIVET, T0);
        assertEquals(List.of(Crew.Kind.COGWIT, Crew.Kind.SPARK, Crew.Kind.ANVIL), Crew.squad(fed(1000), crew, T0 + 1));
        assertEquals(Crew.GRIEF_FACTOR, Crew.grief(crew, T0 + 1));
        assertEquals(1f, Crew.grief(crew, T0 + Crew.GRIEF_TICKS));
        assertTrue(Crew.squad(fed(1000), crew, T0 + Crew.BROTHER_RETURN_TICKS).contains(Crew.Kind.RIVET));
    }

    @Test
    void upkeepAddsUpAndAnvilCostsDouble() {
        assertEquals(1, Crew.upkeep(List.of(Crew.Kind.COGWIT)));
        assertEquals(5, Crew.upkeep(List.of(Crew.Kind.values())));
    }

    @Test
    void brothersFeelDifferent() {
        assertTrue(Crew.Kind.SPARK.speed > Crew.Kind.RIVET.speed);
        assertTrue(Crew.Kind.ANVIL.health > Crew.Kind.RIVET.health);
        assertTrue(Crew.Kind.SPARK.attackInterval < Crew.Kind.ANVIL.attackInterval);
        assertTrue(Crew.Kind.ANVIL.scale > 1f && Crew.Kind.SPARK.scale < 1f);
    }

    @Test
    void rescueCoverLastsTenSeconds() {
        BrainState s = Brain.rescue(fed(0), T0).orElseThrow();
        assertTrue(Crew.isCovering(s, T0));
        assertTrue(Crew.isCovering(s, T0 + Crew.RESCUE_COVER_TICKS - 1));
        assertFalse(Crew.isCovering(s, T0 + Crew.RESCUE_COVER_TICKS));
        assertFalse(Crew.isCovering(fed(0), T0));
    }

    @Test
    void knockoutPutsCogwitToSleep() {
        BrainState s = Crew.knockout(fed(0), T0);
        assertTrue(Brain.isAsleep(s, T0 + Crew.KNOCKOUT_TICKS - 1));
        assertFalse(Brain.isAsleep(s, T0 + Crew.KNOCKOUT_TICKS));
    }

    @Test
    void lowHealthIsUnderThirtyPercent() {
        assertTrue(Crew.isLowHealth(5, 20));
        assertFalse(Crew.isLowHealth(6, 20));
        assertFalse(Crew.isLowHealth(0, 20), "a dead one is not asking for help any more");
    }
}
