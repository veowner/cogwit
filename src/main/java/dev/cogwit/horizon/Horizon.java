package dev.cogwit.horizon;

/**
 * Rules of the Event Horizon armor, free of Minecraft classes so they can be unit-tested.
 * Time is in game ticks (20 per second).
 */
public final class Horizon {
    /** Full set halves all incoming damage that isn't cancelled outright (fall, fire). */
    public static final float DAMAGE_MULTIPLIER = 0.5f;

    /**
     * Share of damage the full set lets through: {@code base} off with an empty mass meter, rising by
     * {@code perMass} at full mass.
     */
    public static float damageMultiplier(float mass, float base, float perMass) {
        float fill = Math.max(0f, Math.min(1f, mass / MASS_MAX));
        return Math.max(0.05f, 1f - base - perMass * fill);
    }

    public static final float MASS_MAX = 100f;
    public static final float MASS_FROM_TAKEN = 0.25f;
    public static final float MASS_FROM_DEALT = 0.10f;
    public static final float MELEE_BONUS_PER_MASS = 0.10f;
    public static final long MASS_DECAY_DELAY_TICKS = 600;
    public static final float MASS_DECAY_PER_TICK = 0.1f;

    public static final double BLINK_RANGE = 30;
    public static final long BLINK_COOLDOWN_TICKS = 40;

    public static final long WELL_COOLDOWN_TICKS = 400;
    public static final long WELL_DURATION_TICKS = 60;
    public static final double WELL_RADIUS = 8;
    public static final double WELL_DISTANCE = 8;
    public static final float WELL_DAMAGE = 2f;
    public static final long WELL_DAMAGE_INTERVAL_TICKS = 10;

    public static final double COLLAPSE_RADIUS = 12;
    public static final float COLLAPSE_DAMAGE = 30f;

    public static final long VOID_RESCUE_COOLDOWN_TICKS = 12_000;
    public static final double REFLECT_RADIUS = 6;

    public enum Ability { BLINK, WELL, COLLAPSE }

    /** The four parts of the suit, as bits of a mask; {@code armor} is the part's armor points. */
    public enum Piece {
        HELMET(4), CHEST(9), LEGS(7), BOOTS(4);

        public final int armor;
        public final int bit;

        Piece(int armor) {
            this.armor = armor;
            this.bit = 1 << ordinal();
        }
    }

    public static final int FULL = 0b1111;
    private static final int ARMOR_TOTAL = 24;

    /**
     * What the parts do. The helmet's faceplate sees in the dark and keeps water out; the chest holds the core
     * (mass, gravity, the reflecting field, trophies); the leggings carry the side thrusters for the dash; the boots
     * absorb landings; legs and boots together fly; only the whole suit is sealed against fire and the void.
     */
    public enum Feature {
        VISOR(Piece.HELMET.bit),
        CORE(Piece.CHEST.bit),
        THRUSTERS(Piece.LEGS.bit),
        LANDING(Piece.BOOTS.bit),
        FLIGHT(Piece.LEGS.bit | Piece.BOOTS.bit),
        SEALED(FULL);

        public final int needs;

        Feature(int needs) {
            this.needs = needs;
        }
    }

    private Horizon() {}

    public static boolean has(int pieces, Feature feature) {
        return (pieces & feature.needs) == feature.needs;
    }

    /** Share of the suit's damage reduction the worn parts give, weighted by their armor points (4/9/7/4). */
    public static float share(int pieces) {
        int armor = 0;
        for (Piece piece : Piece.values()) {
            if ((pieces & piece.bit) != 0) {
                armor += piece.armor;
            }
        }
        return armor / (float) ARMOR_TOTAL;
    }

    public static int count(int pieces) {
        return Integer.bitCount(pieces & FULL);
    }

    /**
     * Share of damage the worn parts let through: the full suit's reduction scaled by {@link #share}, and the
     * mass bonus only with the core.
     */
    public static float damageMultiplier(int pieces, float mass, float base, float perMass) {
        float share = share(pieces);
        return damageMultiplier(has(pieces, Feature.CORE) ? mass : 0f, base * share, perMass * share);
    }

    /** Below this fall the boots just land; from here up the landing throws a shockwave. */
    public static final float LANDING_FROM = 5f;

    /** How hard a landing hits, 0 to 1, from the fall distance in blocks. */
    public static float landingStrength(float fallDistance) {
        if (fallDistance < LANDING_FROM) {
            return 0f;
        }
        return Math.min(1f, 0.2f + (fallDistance - LANDING_FROM) / 20f);
    }

    public static double landingRadius(float strength) {
        return 2.5 + 3.5 * strength;
    }

    public static float landingDamage(float strength) {
        return 3f + 9f * strength;
    }

    private static final long CROUCH_DOWN_MILLIS = 70, CROUCH_UP_MILLIS = 420;

    private static long crouchHold(float strength) {
        return 120 + (long) (280 * strength);
    }

    /** How long the landing crouch lasts in ms. */
    public static long landingPoseMillis(float strength) {
        return CROUCH_DOWN_MILLIS + crouchHold(strength) + CROUCH_UP_MILLIS;
    }

    /** Depth of the landing crouch, 0 to 1, {@code age} ms after touchdown: drops at once, holds, then rises. */
    public static float landingCrouch(long age, float strength) {
        if (age < 0) {
            return 0f;
        }
        if (age < CROUCH_DOWN_MILLIS) {
            float t = age / (float) CROUCH_DOWN_MILLIS;
            return 1 - (1 - t) * (1 - t);
        }
        long up = age - CROUCH_DOWN_MILLIS - crouchHold(strength);
        if (up <= 0) {
            return 1f;
        }
        float t = Math.min(1f, up / (float) CROUCH_UP_MILLIS);
        return 1 - t * t * (3 - 2 * t);
    }

    public static MassGain addMass(HorizonState s, float amount, long now) {
        float total = s.mass() + amount;
        float overflow = Math.max(0f, total - MASS_MAX);
        return new MassGain(s.withMass(Math.min(MASS_MAX, total)).withLastCombat(now), overflow);
    }

    public static MassGain onDamageTaken(HorizonState s, float damage, long now) {
        return addMass(s, damage * MASS_FROM_TAKEN, now);
    }

    public static MeleeResult onMeleeHit(HorizonState s, float damage, long now) {
        float bonus = s.mass() * MELEE_BONUS_PER_MASS;
        return new MeleeResult(addMass(s, damage * MASS_FROM_DEALT, now).state(), bonus);
    }

    /** One tick of leaking mass once the wearer has been out of combat long enough. */
    public static HorizonState decay(HorizonState s, long now) {
        if (s.mass() <= 0f || now - s.lastCombat() < MASS_DECAY_DELAY_TICKS) {
            return s;
        }
        return s.withMass(Math.max(0f, s.mass() - MASS_DECAY_PER_TICK));
    }

    public static AbilityResult blink(HorizonState s, long now) {
        if (now < s.blinkReadyAt()) {
            return new AbilityResult(AbilityResult.Status.COOLDOWN, s);
        }
        return new AbilityResult(AbilityResult.Status.OK, s.withBlinkReadyAt(now + BLINK_COOLDOWN_TICKS));
    }

    /** The gravity key casts the well, or Collapse once mass is full. */
    public static Ability gravityKey(HorizonState s) {
        return s.mass() >= MASS_MAX ? Ability.COLLAPSE : Ability.WELL;
    }

    public static AbilityResult well(HorizonState s, long now) {
        if (now < s.wellReadyAt()) {
            return new AbilityResult(AbilityResult.Status.COOLDOWN, s);
        }
        return new AbilityResult(AbilityResult.Status.OK,
                s.withWell(now + WELL_COOLDOWN_TICKS, now + WELL_DURATION_TICKS));
    }

    public static boolean isWellActive(HorizonState s, long now) {
        return now < s.wellUntil();
    }

    public static AbilityResult collapse(HorizonState s) {
        if (s.mass() < MASS_MAX) {
            return new AbilityResult(AbilityResult.Status.NOT_READY, s);
        }
        return new AbilityResult(AbilityResult.Status.OK, s.withMass(0f));
    }

    public static AbilityResult voidRescue(HorizonState s, long now) {
        if (now < s.voidRescueReadyAt()) {
            return new AbilityResult(AbilityResult.Status.COOLDOWN, s);
        }
        return new AbilityResult(AbilityResult.Status.OK, s.withVoidRescueReadyAt(now + VOID_RESCUE_COOLDOWN_TICKS));
    }

    /** Whether a projectile at p moving with velocity v is heading towards the target t. */
    public static boolean isIncoming(double px, double py, double pz, double vx, double vy, double vz,
                                     double tx, double ty, double tz) {
        return (tx - px) * vx + (ty - py) * vy + (tz - pz) * vz > 0;
    }

    /** Velocity of the given speed pointing from p to t. */
    public static double[] aimAt(double px, double py, double pz, double tx, double ty, double tz, double speed) {
        double dx = tx - px, dy = ty - py, dz = tz - pz;
        double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len < 1e-9) {
            return new double[] {0, 0, 0};
        }
        return new double[] {dx / len * speed, dy / len * speed, dz / len * speed};
    }
}
