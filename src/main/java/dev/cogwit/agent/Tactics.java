package dev.cogwit.agent;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Which trophy active Cogwit casts in a fight, if any. Each active is described by how it finds its victims, so
 * Cogwit only casts one that will land from where you stand and where you look. Cooldowns are not spent on weak
 * lone mobs: only on bosses, tough mobs and crowds, or when you are in trouble. Pure logic.
 */
public final class Tactics {
    /** How an active finds its victims. */
    public enum Aim {
        /** Hits what is around you, wherever you look. */
        AROUND,
        /** Picks its own targets near you. */
        TARGET,
        /** Goes where you look: the target has to be in front of you. */
        FORWARD,
        /** Needs the target right under the crosshair. */
        CROSSHAIR,
        /** Carries you forward into the target: only from some distance and not when you are hurt. */
        DASH
    }

    /** One active: its reach in blocks, a rough strength to rank by, and whether it also protects you. */
    public record Move(String id, Aim aim, double range, int power, boolean defensive) {}

    /**
     * The fight as Cogwit sees it: enemies within a few blocks, whether the target is a boss, how far it is,
     * the angle between your view and it in degrees, your health share and the target's health.
     */
    public record Fight(int crowd, boolean boss, double distance, double angle, float health, float targetHealth) {}

    /** A lone mob with less health than this is left to your sword. */
    public static final float TOUGH = 40;
    /** This many enemies close by make a crowd worth an area active. */
    public static final int CROWD = 3;
    /** Your health share below which protecting you comes first. */
    public static final float HURT = 0.35f;
    /** View cones in degrees for {@link Aim#FORWARD} and {@link Aim#CROSSHAIR}. */
    public static final double FRONT = 20, CROSSHAIR = 6;
    /** A dash is not worth it at point blank range. */
    public static final double DASH_FROM = 3;
    /** How long after a setup (well, landing, dash) a follow-up counts as a combo. */
    public static final long COMBO_TICKS = 16;

    public static final Map<String, Move> MOVES = Map.ofEntries(
            move("ender_dragon", Aim.FORWARD, 20, 4, false),
            move("wither", Aim.FORWARD, 20, 3, false),
            move("warden", Aim.FORWARD, 15, 5, false),
            move("elder_guardian", Aim.CROSSHAIR, 24, 4, false),
            move("ignis", Aim.DASH, 10, 4, false),
            move("netherite_monstrosity", Aim.AROUND, 6, 5, true),
            move("ender_guardian", Aim.FORWARD, 12, 4, false),
            move("harbinger", Aim.TARGET, 20, 4, false),
            move("leviathan", Aim.FORWARD, 20, 5, false),
            move("ancient_remnant", Aim.AROUND, 5, 3, true),
            move("maledictus", Aim.FORWARD, 16, 4, false),
            move("scylla", Aim.TARGET, 14, 5, false),
            move("frostmaw", Aim.FORWARD, 8, 3, true),
            move("umvuthi", Aim.TARGET, 20, 4, false),
            move("sculptor", Aim.TARGET, 10, 4, true),
            move("slider", Aim.DASH, 8, 3, false),
            move("valkyrie_queen", Aim.TARGET, 24, 3, false),
            move("sun_spirit", Aim.AROUND, 8, 3, false),
            move("dead_king", Aim.AROUND, 16, 3, true),
            move("fire_boss", Aim.TARGET, 10, 4, false),
            move("archevoker", Aim.TARGET, 10, 3, false),
            move("naga", Aim.DASH, 11, 3, false),
            move("lich", Aim.TARGET, 16, 4, false),
            move("minoshroom", Aim.TARGET, 12, 4, true),
            move("hydra", Aim.FORWARD, 9, 4, false),
            move("knight_phantom", Aim.TARGET, 14, 4, false),
            move("ur_ghast", Aim.TARGET, 14, 5, false),
            move("alpha_yeti", Aim.TARGET, 20, 4, false),
            move("snow_queen", Aim.TARGET, 12, 5, true),
            move("infernal_dragon", Aim.DASH, 14, 5, false),
            move("kraken", Aim.TARGET, 12, 5, true),
            move("sandworm", Aim.TARGET, 14, 4, false),
            move("underworld_knight", Aim.TARGET, 14, 4, false),
            move("yeti", Aim.AROUND, 7, 4, true));

    private Tactics() {}

    private static Map.Entry<String, Move> move(String id, Aim aim, double range, int power, boolean defensive) {
        return Map.entry(id, new Move(id, aim, range, power, defensive));
    }

    /** Whether the move would land in this fight. */
    public static boolean lands(Move move, Fight fight) {
        if (fight.distance() > move.range()) {
            return false;
        }
        return switch (move.aim()) {
            case AROUND, TARGET -> true;
            case FORWARD -> fight.angle() <= FRONT;
            case CROSSHAIR -> fight.angle() <= CROSSHAIR;
            case DASH -> fight.angle() <= FRONT && fight.distance() >= DASH_FROM && fight.health() >= HURT;
        };
    }

    /** Whether the fight is worth a cooldown at all. */
    public static boolean worthIt(Fight fight) {
        return fight.boss() || fight.crowd() >= CROWD || fight.targetHealth() >= TOUGH || fight.health() < HURT;
    }

    /**
     * The move to cast among the ready ones, or empty. Strongest first; area moves gain a point per extra enemy
     * close by, and when you are hurt a protecting move goes before any other.
     */
    public static Optional<Move> choose(Fight fight, List<Move> ready) {
        return choose(fight, ready, StyleProfile.Style.MIXED, false);
    }

    /**
     * As above, for a player with a known fighting style, and inside a combo window: right after the well lifted the
     * crowd, a hard landing or a dash slammed into it, any target is worth it and the hardest hitter goes first.
     */
    public static Optional<Move> choose(Fight fight, List<Move> ready, StyleProfile.Style style, boolean combo) {
        if (!combo && !worthIt(fight)) {
            return Optional.empty();
        }
        boolean hurt = fight.health() < HURT;
        return ready.stream()
                .filter(m -> lands(m, fight))
                .filter(m -> !hurt || m.defensive() || fight.boss())
                .max(Comparator.comparingInt(m -> score(m, fight, style, combo)));
    }

    static int score(Move move, Fight fight) {
        return score(move, fight, StyleProfile.Style.MIXED, false);
    }

    /**
     * Archers like moves that keep enemies off them, brawlers like dashing in and hitting all around, casters like
     * picking targets from afar. In a combo raw power counts double.
     */
    static int score(Move move, Fight fight, StyleProfile.Style style, boolean combo) {
        int score = combo ? move.power() * 2 : move.power();
        score += switch (style) {
            case ARCHER -> (move.defensive() ? 2 : 0) + (move.aim() == Aim.DASH ? -2 : 0);
            case BRAWLER -> move.aim() == Aim.DASH ? 2 : move.aim() == Aim.AROUND ? 1 : 0;
            case CASTER -> move.aim() == Aim.TARGET ? 1 : move.defensive() ? 1 : 0;
            case MIXED -> 0;
        };
        if (move.aim() == Aim.AROUND || move.aim() == Aim.TARGET) {
            score += Math.max(0, Math.min(fight.crowd(), 6) - 1);
        }
        if (fight.health() < HURT && move.defensive()) {
            score += 10;
        }
        return score;
    }
}
