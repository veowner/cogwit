package dev.cogwit.guide;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Which milestones are done and which comes next. Later finds can satisfy earlier ingredient steps. */
public final class GuideProgress {
    /**
     * What each step could not have been done without. A done step counts all of these as done too: a Horizon
     * piece was smithed from netherite armor, so the netherite step is done even once that armor is gone.
     */
    private static final Map<Milestone, List<Milestone>> NEEDS = new EnumMap<>(Map.ofEntries(
            Map.entry(Milestone.WEAR_COGWIT, List.of(Milestone.GET_COGWIT)),
            Map.entry(Milestone.FEED_COGWIT, List.of(Milestone.WEAR_COGWIT)),
            Map.entry(Milestone.SINGULARITY_CORE, List.of(Milestone.HEAVY_CORE, Milestone.ECHO_SHARDS)),
            Map.entry(Milestone.FIRST_PIECE, List.of(Milestone.NETHERITE, Milestone.SINGULARITY_CORE,
                    Milestone.HORIZON_TEMPLATE)),
            Map.entry(Milestone.FULL_SET, List.of(Milestone.FIRST_PIECE)),
            Map.entry(Milestone.CELLS_IN_ARMOR, List.of(Milestone.TOKEN_CELL, Milestone.FIRST_PIECE)),
            Map.entry(Milestone.COGWIT_LEVEL_2, List.of(Milestone.FEED_COGWIT)),
            Map.entry(Milestone.COGWIT_LEVEL_4, List.of(Milestone.COGWIT_LEVEL_2)),
            Map.entry(Milestone.COGWIT_LEVEL_5, List.of(Milestone.COGWIT_LEVEL_4)),
            Map.entry(Milestone.ALL_TROPHIES, List.of(Milestone.FIRST_TROPHY)),
            Map.entry(Milestone.TWILIGHT_BOSSES, List.of(Milestone.TWILIGHT_PORTAL))));
    /**
     * Steps that are not kept once done: more bosses can become huntable later (a new mod in the pack), and the
     * portal step is read from an advancement, which keeps itself.
     */
    private static final int NOT_KEPT = 1 << Milestone.ALL_TROPHIES.ordinal() | 1 << Milestone.TWILIGHT_PORTAL.ordinal()
            | 1 << Milestone.TWILIGHT_BOSSES.ordinal() | 1 << Milestone.WORLD_BOSSES.ordinal();
    public static final int FEED_TOKENS = 1000;
    public static final int ECHO_SHARDS_NEEDED = 4;

    private GuideProgress() {}

    public static boolean isDone(Milestone m, GuideFacts f) {
        return switch (m) {
            case GET_COGWIT -> f.hasCogwit() || f.wornCogwit();
            case WEAR_COGWIT -> f.wornCogwit();
            case FEED_COGWIT -> f.wornCogwit() && f.wornTokens() >= FEED_TOKENS;
            case NETHERITE -> f.netheriteArmor();
            case HEAVY_CORE -> f.heavyCore() || f.singularityCore() || f.horizonPiece();
            case ECHO_SHARDS -> f.echoShards() >= ECHO_SHARDS_NEEDED || f.singularityCore() || f.horizonPiece();
            case SINGULARITY_CORE -> f.singularityCore() || f.horizonPiece();
            case HORIZON_TEMPLATE -> f.horizonTemplate() || f.horizonPiece();
            case FIRST_PIECE -> f.horizonPiece();
            case FULL_SET -> f.fullSet();
            case TOKEN_CELL -> f.tokenCell() || f.cellsInserted() > 0;
            case CELLS_IN_ARMOR -> f.cellsInserted() > 0;
            case COGWIT_LEVEL_2 -> f.cogwitLevel() >= 2;
            case FIRST_TROPHY -> f.trophies() >= 1;
            case COGWIT_LEVEL_4 -> f.cogwitLevel() >= 4;
            case COGWIT_LEVEL_5 -> f.cogwitLevel() >= 5;
            case ALL_TROPHIES -> f.trophies() >= f.trophiesAvailable();
            // a land whose mod is not in the pack has nothing to do: its steps count as done
            case TWILIGHT_PORTAL -> f.twilightVisited() || f.twilightTrophies() > 0 || f.twilightAvailable() == 0;
            case TWILIGHT_BOSSES -> f.twilightTrophies() >= f.twilightAvailable();
            case WORLD_BOSSES -> f.worldTrophies() >= f.worldAvailable();
        };
    }

    /** One bit per milestone ordinal, set when that milestone is done. */
    public static int doneMask(GuideFacts f) {
        int mask = 0;
        for (Milestone m : Milestone.values()) {
            if (isDone(m, f)) {
                mask |= 1 << m.ordinal();
            }
        }
        return mask;
    }

    /** The mask with every step a done step needed also marked done. */
    public static int withNeeds(int mask) {
        int before;
        do {
            before = mask;
            for (Map.Entry<Milestone, List<Milestone>> e : NEEDS.entrySet()) {
                if (isDone(e.getKey(), mask)) {
                    for (Milestone need : e.getValue()) {
                        mask |= 1 << need.ordinal();
                    }
                }
            }
        } while (mask != before);
        return mask;
    }

    /** Progress from what the player has now plus every step done before, which stays done. */
    public static int progress(GuideFacts f, int reached) {
        return withNeeds(doneMask(f) | reached & ~NOT_KEPT);
    }

    /** The steps to remember as reached from a progress mask. */
    public static int toKeep(int mask) {
        return mask & ~NOT_KEPT;
    }

    public static boolean isDone(Milestone m, int mask) {
        return (mask & (1 << m.ordinal())) != 0;
    }

    /** Index of the first milestone that is not done; {@code Milestone.values().length} when everything is. */
    public static int firstPending(int mask) {
        for (Milestone m : Milestone.values()) {
            if (!isDone(m, mask)) {
                return m.ordinal();
            }
        }
        return Milestone.values().length;
    }

    public static int firstPending(GuideFacts f) {
        return firstPending(doneMask(f));
    }

    public static Optional<Milestone> next(int mask) {
        int index = firstPending(mask);
        return index < Milestone.values().length ? Optional.of(Milestone.values()[index]) : Optional.empty();
    }

    public static Optional<Milestone> next(GuideFacts f) {
        return next(doneMask(f));
    }

    /** Index of the last milestone in the unbroken run of finished steps from the start; -1 if none. */
    public static int lastCompleted(GuideFacts f) {
        return firstPending(f) - 1;
    }
}
