package dev.cogwit.agent;

/**
 * How the player fights, read from the damage they dealt lately: mostly melee, mostly arrows, mostly spells, or a mix.
 * Old damage fades with a five minute half-life, so the profile follows a change of style within a few fights.
 * Short-term memory, not saved. Pure logic.
 */
public final class StyleProfile {
    public enum Kind { MELEE, RANGED, MAGIC }

    public enum Style {
        MIXED, BRAWLER, ARCHER, CASTER;

        public String key() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    public static final double HALF_LIFE_TICKS = 6000;
    /** Damage dealt before Cogwit dares to name a style. */
    public static final double CONFIDENT = 60;
    /** Share of the damage one kind needs to count as the player's style. */
    public static final double SHARE = 0.6;

    private final double[] dealt = new double[Kind.values().length];
    private long at;

    public void record(Kind kind, double damage, long now) {
        decay(now);
        dealt[kind.ordinal()] += Math.max(0, damage);
    }

    private void decay(long now) {
        if (at != 0 && now > at) {
            double keep = Math.pow(0.5, (now - at) / HALF_LIFE_TICKS);
            for (int i = 0; i < dealt.length; i++) {
                dealt[i] *= keep;
            }
        }
        at = Math.max(at, now);
    }

    public double share(Kind kind, long now) {
        decay(now);
        double total = 0;
        for (double d : dealt) {
            total += d;
        }
        return total <= 0 ? 0 : dealt[kind.ordinal()] / total;
    }

    public Style style(long now) {
        decay(now);
        double total = 0;
        for (double d : dealt) {
            total += d;
        }
        if (total < CONFIDENT) {
            return Style.MIXED;
        }
        if (dealt[Kind.RANGED.ordinal()] / total >= SHARE) {
            return Style.ARCHER;
        }
        if (dealt[Kind.MAGIC.ordinal()] / total >= SHARE) {
            return Style.CASTER;
        }
        return dealt[Kind.MELEE.ordinal()] / total >= SHARE ? Style.BRAWLER : Style.MIXED;
    }
}
