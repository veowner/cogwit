package dev.cogwit.trophy;

import java.util.Arrays;
import java.util.Comparator;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

/** Which boss Cogwit suggests next: the easiest trophy still missing, preferring one in the player's dimension. */
public final class TrophyHunt {
    private TrophyHunt() {}

    public static Optional<Trophy> next(Set<Trophy> owned, Predicate<Trophy> available, String dimension) {
        return Arrays.stream(Trophy.values())
                .filter(t -> t.implemented && !owned.contains(t) && available.test(t))
                .min(Comparator.<Trophy>comparingInt(t -> t.difficulty)
                        .thenComparing(t -> !dimension.equals(dimensionOf(t))));
    }

    public static long countAvailable(Predicate<Trophy> available) {
        return Arrays.stream(Trophy.values()).filter(t -> t.implemented && available.test(t)).count();
    }

    /** Where the boss lives; trophies without a recorded dimension count as the overworld. */
    public static String dimensionOf(Trophy trophy) {
        return trophy.dimension == null ? "minecraft:overworld" : trophy.dimension;
    }

    /** One to five stars from the 1..20 difficulty scale. */
    public static int stars(Trophy trophy) {
        return Math.max(1, Math.min(5, (trophy.difficulty + 3) / 4));
    }

    /** Trophies whose mod is loaded: what a real game can hand out. */
    public static boolean loaded(Trophy trophy) {
        return net.neoforged.fml.ModList.get() == null || net.neoforged.fml.ModList.get().isLoaded(trophy.mod());
    }
}
