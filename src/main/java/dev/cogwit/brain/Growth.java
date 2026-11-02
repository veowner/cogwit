package dev.cogwit.brain;

import java.util.ArrayList;
import java.util.List;

/**
 * Cogwit's own levels. He grows from damage he blocks, from saving his owner and from bosses, and every level
 * opens one perk, in order. Pure logic, like {@link Brain}.
 */
public final class Growth {
    /** Experience needed for levels 1 to 5. */
    private static final float[] LEVEL_XP = {50, 200, 500, 1000, 2000};
    public static final int MAX_LEVEL = LEVEL_XP.length;

    /** One experience per point of damage Cogwit took off a hit. */
    public static final float XP_PER_BLOCKED_HP = 1f;
    public static final float EFFECT_XP = 1f;
    public static final float RESCUE_XP = 25f;
    public static final float BOSS_XP = 200f;

    /** Perks in the order they open; perk {@code n} (0-based) opens at level {@code n + 1}. */
    public enum Perk {
        EYE, HIGHLIGHT, BACK_SHIELD, BROTHERS, OVERDRIVE;

        public int level() {
            return ordinal() + 1;
        }

        public String key() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    private Growth() {}

    public static int level(float xp) {
        int level = 0;
        while (level < LEVEL_XP.length && xp >= LEVEL_XP[level]) {
            level++;
        }
        return level;
    }

    public static boolean has(float xp, Perk perk) {
        return level(xp) >= perk.level();
    }

    /** Experience a level starts at; 0 for level 0. */
    public static float threshold(int level) {
        return level <= 0 ? 0f : LEVEL_XP[Math.min(level, LEVEL_XP.length) - 1];
    }

    /** 0..1 from the current level to the next; 1 when maxed. */
    public static float progress(float xp) {
        int level = level(xp);
        if (level >= MAX_LEVEL) {
            return 1f;
        }
        float from = threshold(level);
        return (xp - from) / (LEVEL_XP[level] - from);
    }

    /** Whole points still needed for the next level, or 0 when maxed. */
    public static int toNext(float xp) {
        int level = level(xp);
        return level >= MAX_LEVEL ? 0 : (int) Math.ceil(LEVEL_XP[level] - xp);
    }

    /** The perk the next level opens, or null when maxed. */
    public static Perk next(float xp) {
        int level = level(xp);
        return level >= MAX_LEVEL ? null : Perk.values()[level];
    }

    /** Levels reached by going from {@code before} to {@code after} experience, lowest first. */
    public static List<Integer> levelsGained(float before, float after) {
        List<Integer> gained = new ArrayList<>();
        for (int l = level(before) + 1; l <= level(after); l++) {
            gained.add(l);
        }
        return gained;
    }

    public static BrainState gain(BrainState s, float xp) {
        return xp <= 0f ? s : s.withXp(s.xp() + xp);
    }
}
