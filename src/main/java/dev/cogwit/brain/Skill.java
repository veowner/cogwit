package dev.cogwit.brain;

/**
 * One slot of Cogwit's context window. A compacted slot holds two categories at half strength
 * and stops learning.
 */
public record Skill(String category, int xp, long lastUsed, long lastXpGain, String merged, int mergedXp) {
    public static Skill learned(String category, long now) {
        return new Skill(category, 1, now, now, "", 0);
    }

    public boolean compacted() {
        return !merged.isEmpty();
    }

    public boolean covers(String c) {
        return category.equals(c) || merged.equals(c);
    }

    public float reductionFor(String c) {
        if (category.equals(c)) {
            return compacted() ? Brain.reductionForXp(xp) / 2f : Brain.reductionForXp(xp);
        }
        if (merged.equals(c)) {
            return Brain.reductionForXp(mergedXp) / 2f;
        }
        return 0f;
    }

    Skill touched(long now) {
        return new Skill(category, xp, now, lastXpGain, merged, mergedXp);
    }

    Skill taught(long now) {
        return new Skill(category, Math.min(xp + 1, Brain.MAX_XP), now, now, merged, mergedXp);
    }
}
