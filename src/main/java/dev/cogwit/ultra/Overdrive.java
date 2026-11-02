package dev.cogwit.ultra;

import dev.cogwit.brain.Brain;
import dev.cogwit.brain.BrainState;
import dev.cogwit.brain.Growth;

/** Rage mode against bosses: when it starts and ends, what it gives and what it costs. Pure logic. */
public final class Overdrive {
    /** A boss this close that fights the owner wakes Overdrive. */
    public static final double BOSS_RANGE = 32;
    /** Overdrive ends once no boss has fought the owner for this long. */
    public static final long LINGER_TICKS = 200;
    /** Cogwit is drained for two minutes after the fight. */
    public static final long HANGOVER_TICKS = 2400;
    /** Everything costs three times the tokens. */
    public static final int TOKEN_FACTOR = 3;
    /** In rage Cogwit blocks at least this much of any hit, learned or not. */
    public static final float MIN_REDUCTION = 0.25f;
    /** During the hangover protection is halved. */
    public static final float HANGOVER_FACTOR = 0.5f;
    /** Boss health shares that count as a new phase. */
    private static final float[] PHASES = {0.75f, 0.5f, 0.25f};

    public enum Change { NONE, STARTED, ENDED }

    public record Step(UltraState state, Change change) {}

    private Overdrive() {}

    public static boolean inHangover(UltraState u, long now) {
        return now < u.hangoverUntil();
    }

    public static boolean canStart(BrainState s, UltraState u, long now) {
        return Growth.has(s.xp(), Growth.Perk.OVERDRIVE) && !Brain.isAsleep(s, now) && s.tokens() > 0
                && !inHangover(u, now);
    }

    /** Called every second with whether a boss is fighting the owner. */
    public static Step tick(UltraState u, boolean bossEngaged, boolean canStart, long now) {
        if (u.active()) {
            if (bossEngaged) {
                return new Step(new UltraState(true, u.startedAt(), now, u.hangoverUntil(), u.crewByUltra(),
                        u.bossHealth()), Change.NONE);
            }
            if (now - u.lastBossSeen() >= LINGER_TICKS) {
                return new Step(new UltraState(false, u.startedAt(), u.lastBossSeen(), now + HANGOVER_TICKS,
                        u.crewByUltra(), 1f), Change.ENDED);
            }
            return new Step(u, Change.NONE);
        }
        if (bossEngaged && canStart) {
            return new Step(new UltraState(true, now, now, u.hangoverUntil(), false, 1f), Change.STARTED);
        }
        return new Step(u, Change.NONE);
    }

    /** Cogwit ran out of steam mid-fight (asleep or empty): rage stops at once, hangover follows. */
    public static UltraState collapse(UltraState u, long now) {
        return u.active() ? new UltraState(false, u.startedAt(), u.lastBossSeen(), now + HANGOVER_TICKS,
                u.crewByUltra(), 1f) : u;
    }

    /** The protection Cogwit gives for a hit, adjusted for rage or hangover. */
    public static float protection(float reduction, UltraState u, long now) {
        if (u.active()) {
            return Math.max(reduction, MIN_REDUCTION);
        }
        return inHangover(u, now) ? reduction * HANGOVER_FACTOR : reduction;
    }

    /** Extra tokens a protected hit costs in rage, on top of the normal price. */
    public static int extraCost(float amount, float reduction, UltraState u) {
        if (!u.active() || reduction <= 0) {
            return 0;
        }
        return (int) Math.ceil(amount * reduction * Brain.TOKENS_PER_HP) * (TOKEN_FACTOR - 1);
    }

    public static int upkeep(int base, UltraState u) {
        return u.active() ? base * TOKEN_FACTOR : base;
    }

    /** The phase threshold (as a percentage) the boss just dropped below, or 0. */
    public static int phaseCrossed(float before, float after) {
        for (float p : PHASES) {
            if (before > p && after <= p) {
                return Math.round(p * 100);
            }
        }
        return 0;
    }

    /** True when something at {@code p} flying with velocity {@code v} is headed at {@code t} and close. */
    public static boolean incoming(double px, double py, double pz, double vx, double vy, double vz,
            double tx, double ty, double tz) {
        double dx = tx - px, dy = ty - py, dz = tz - pz;
        double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
        double speed = Math.sqrt(vx * vx + vy * vy + vz * vz);
        if (dist < 1e-6 || dist > 16 || speed < 0.2) {
            return false;
        }
        return (vx * dx + vy * dy + vz * dz) / (speed * dist) > 0.9;
    }
}
