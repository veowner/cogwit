package dev.cogwit.guide;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class GuideProgressTest {
    private static final GuideFacts WORN_FED = GuideFacts.NOTHING.withCogwit(true, 1000);

    @Test
    void freshPlayerIsToldToGetCogwit() {
        assertEquals(Optional.of(Milestone.GET_COGWIT), GuideProgress.next(GuideFacts.NOTHING));
        assertEquals(-1, GuideProgress.lastCompleted(GuideFacts.NOTHING));
    }

    @Test
    void carryingCogwitIsNotWearingIt() {
        GuideFacts carried = GuideFacts.NOTHING.withCogwit(false, 0);
        assertEquals(Optional.of(Milestone.WEAR_COGWIT), GuideProgress.next(carried));
    }

    @Test
    void wornCogwitNeedsAThousandTokens() {
        assertEquals(Optional.of(Milestone.FEED_COGWIT), GuideProgress.next(GuideFacts.NOTHING.withCogwit(true, 999)));
        assertEquals(Optional.of(Milestone.NETHERITE), GuideProgress.next(WORN_FED));
    }

    @Test
    void heavyCoreLeadsToEchoShards() {
        GuideFacts facts = WORN_FED.withNetherite().withHeavyCore();
        assertEquals(Optional.of(Milestone.ECHO_SHARDS), GuideProgress.next(facts));
    }

    @Test
    void fourShardsFinishEchoShardsButThreeDoNot() {
        GuideFacts base = WORN_FED.withNetherite().withHeavyCore();
        assertEquals(Optional.of(Milestone.ECHO_SHARDS), GuideProgress.next(base.withEchoShards(3)));
        assertEquals(Optional.of(Milestone.SINGULARITY_CORE), GuideProgress.next(base.withEchoShards(4)));
    }

    @Test
    void singularityCoreSatisfiesItsIngredients() {
        GuideFacts facts = WORN_FED.withNetherite().withSingularityCore();
        assertTrue(GuideProgress.isDone(Milestone.HEAVY_CORE, facts));
        assertTrue(GuideProgress.isDone(Milestone.ECHO_SHARDS, facts));
        assertEquals(Optional.of(Milestone.HORIZON_TEMPLATE), GuideProgress.next(facts));
    }

    @Test
    void anyHorizonPieceSatisfiesEveryIngredient() {
        GuideFacts facts = WORN_FED.withNetherite().withHorizonPiece();
        for (Milestone m : Milestone.values()) {
            if (m.ordinal() < Milestone.FULL_SET.ordinal()) {
                assertTrue(GuideProgress.isDone(m, facts), m + " should be done");
            }
        }
        assertEquals(Optional.of(Milestone.FULL_SET), GuideProgress.next(facts));
    }

    @Test
    void fullSetLeadsToTokenCells() {
        GuideFacts facts = WORN_FED.withNetherite().withFullSet();
        assertEquals(Optional.of(Milestone.TOKEN_CELL), GuideProgress.next(facts));
        assertEquals(Optional.of(Milestone.CELLS_IN_ARMOR), GuideProgress.next(facts.withCells(true, 0)));
    }

    @Test
    void cellsInArmorOpenChapterTwo() {
        GuideFacts facts = WORN_FED.withNetherite().withFullSet().withCells(true, 2).withTrophies(0, 8);
        assertEquals(Optional.of(Milestone.COGWIT_LEVEL_2), GuideProgress.next(facts));
        assertEquals(Optional.of(Milestone.FIRST_TROPHY), GuideProgress.next(facts.withLevel(2)));
        assertEquals(Optional.of(Milestone.COGWIT_LEVEL_4), GuideProgress.next(facts.withLevel(3).withTrophies(1, 8)));
    }

    @Test
    void everyTrophyAtMaxLevelFinishesTheRoadmap() {
        GuideFacts facts = WORN_FED.withNetherite().withFullSet().withCells(true, 2).withLevel(5).withTrophies(8, 8);
        assertEquals(Optional.empty(), GuideProgress.next(facts));
        assertEquals(Milestone.values().length - 1, GuideProgress.lastCompleted(facts));
    }

    @Test
    void anInsertedCellCountsAsHavingOne() {
        GuideFacts facts = WORN_FED.withNetherite().withFullSet().withCells(false, 1);
        assertTrue(GuideProgress.isDone(Milestone.TOKEN_CELL, facts));
    }

    @Test
    void nextIsTheFirstGapEvenIfLaterStepsAreDone() {
        GuideFacts facts = GuideFacts.NOTHING.withCogwit(true, 1000).withHeavyCore();
        assertEquals(Optional.of(Milestone.NETHERITE), GuideProgress.next(facts));
        assertTrue(GuideProgress.isDone(Milestone.HEAVY_CORE, facts));
        assertEquals(Milestone.FEED_COGWIT.ordinal(), GuideProgress.lastCompleted(facts));
    }

    @Test
    void maskRoundTripsAndAgreesWithFacts() {
        GuideFacts facts = WORN_FED.withNetherite().withHeavyCore();
        int mask = GuideProgress.doneMask(facts);
        for (Milestone m : Milestone.values()) {
            assertEquals(GuideProgress.isDone(m, facts), GuideProgress.isDone(m, mask), m.name());
        }
        assertEquals(GuideProgress.next(facts), GuideProgress.next(mask));
    }

    @Test
    void everyMilestoneHasAUniqueIdAndStructureTargetsAreNamespaced() {
        var ids = new java.util.HashSet<String>();
        for (Milestone m : Milestone.values()) {
            assertTrue(ids.add(m.id()), m.id());
            m.target().ifPresent(t -> assertTrue(t.structure().startsWith("minecraft:"), t.structure()));
        }
    }

    @Test
    void netheriteHintAsksForTheNetherOnlyOutsideIt() {
        assertEquals("cogwit.guide.hint.netherite.travel", Milestone.NETHERITE.hintKey("minecraft:overworld"));
        assertEquals("cogwit.guide.hint.netherite", Milestone.NETHERITE.hintKey("minecraft:the_nether"));
        assertEquals("cogwit.guide.hint.heavy_core", Milestone.HEAVY_CORE.hintKey("minecraft:the_nether"));
    }

    @Test
    void targetLabelKeysAreShort() {
        assertEquals("cogwit.guide.target.ancient_city", Milestone.ECHO_SHARDS.target().orElseThrow().labelKey());
    }

    @Test
    void aFullHorizonSetCountsTheNetheriteItWasMadeFrom() {
        GuideFacts suited = WORN_FED.withHorizonPiece().withFullSet();
        int mask = GuideProgress.progress(suited, 0);
        assertTrue(GuideProgress.isDone(Milestone.NETHERITE, mask), "the netherite armor went into the set");
        assertTrue(GuideProgress.isDone(Milestone.HORIZON_TEMPLATE, mask));
        assertEquals(Milestone.TOKEN_CELL.ordinal(), GuideProgress.firstPending(mask));
    }

    @Test
    void stepsStayDoneOnceTheirItemsAreGone() {
        GuideFacts fed = WORN_FED.withNetherite();
        int reached = GuideProgress.toKeep(GuideProgress.progress(fed, 0));
        GuideFacts hungryNow = GuideFacts.NOTHING.withCogwit(true, 200);
        int mask = GuideProgress.progress(hungryNow, reached);
        assertTrue(GuideProgress.isDone(Milestone.FEED_COGWIT, mask), "tokens spent since: still fed once");
        assertTrue(GuideProgress.isDone(Milestone.NETHERITE, mask), "armor stored in a chest: still done");
    }

    @Test
    void allTrophiesIsCheckedAfreshWhenNewBossesAppear() {
        int reached = GuideProgress.toKeep(GuideProgress.progress(WORN_FED.withTrophies(5, 5), 0));
        assertFalse(GuideProgress.isDone(Milestone.ALL_TROPHIES,
                GuideProgress.progress(WORN_FED.withTrophies(5, 8), reached)), "three new bosses to hunt");
        assertTrue(GuideProgress.isDone(Milestone.FIRST_TROPHY,
                GuideProgress.progress(WORN_FED.withTrophies(5, 8), reached)));
    }

    @Test
    void levelFiveMeansTheEarlierLevelsToo() {
        int mask = GuideProgress.progress(GuideFacts.NOTHING.withLevel(5), 0);
        assertTrue(GuideProgress.isDone(Milestone.COGWIT_LEVEL_2, mask));
        assertTrue(GuideProgress.isDone(Milestone.GET_COGWIT, mask));
    }

    @Test
    void chapterThreeWaitsForChapterTwoAndSkipsMissingMods() {
        GuideFacts done2 = GuideFacts.NOTHING.withLevel(5).withNetherite().withHorizonPiece().withFullSet()
                .withCells(true, 8).withTrophies(21, 21);
        assertEquals(Milestone.TWILIGHT_PORTAL.ordinal(), GuideProgress.firstPending(
                GuideProgress.progress(done2.withTwilight(false, 0, 8).withWorldBosses(0, 5), 0)));
        int noLands = GuideProgress.progress(done2, 0);
        assertEquals(Milestone.values().length, GuideProgress.firstPending(noLands),
                "without Twilight Forest and Block Factory in the pack there is nothing left");
        int halfway = GuideProgress.progress(done2.withTwilight(true, 3, 8).withWorldBosses(0, 5), 0);
        assertEquals(Milestone.TWILIGHT_BOSSES.ordinal(), GuideProgress.firstPending(halfway));
    }

    @Test
    void eachHuntStepHasItsOwnBosses() {
        assertTrue(Milestone.TWILIGHT_BOSSES.hunts("twilightforest"));
        assertFalse(Milestone.ALL_TROPHIES.hunts("twilightforest"));
        assertTrue(Milestone.ALL_TROPHIES.hunts("cataclysm"));
        assertTrue(Milestone.WORLD_BOSSES.hunts("block_factorys_bosses"));
        assertEquals(3, Milestone.WORLD_BOSSES.chapter());
        assertEquals(2, Milestone.ALL_TROPHIES.chapter());
    }
}
