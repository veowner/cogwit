package dev.cogwit.horizon;

/**
 * The forces behind the armor skills, as plain vectors so they can be tested: the well's spiral pull,
 * the collapse's drag-in and blast, and how far screen effects reach. Pure logic.
 */
public final class SkillPhysics {
    /** Inward pull at the edge of the well and at its heart, in blocks per tick added per tick. */
    public static final double WELL_PULL_EDGE = 0.08, WELL_PULL_CORE = 0.32;
    /** Sideways push that turns the pull into an orbit. */
    public static final double WELL_SWIRL = 0.22;
    /** How much of a projectile's direction bends towards the well each tick. */
    public static final double PROJECTILE_BEND = 0.18;
    public static final double COLLAPSE_LIFT = 0.12, COLLAPSE_BLAST = 1.6;
    /** Ticks the collapse charges before it blows. */
    public static final int COLLAPSE_CHARGE_TICKS = 30, COLLAPSE_BLAST_TICKS = 16;

    private SkillPhysics() {}

    /**
     * Velocity to add to something at offset (dx, dy, dz) from the well centre: a pull that grows near the core,
     * a swirl around the vertical axis, and a nudge towards the centre's height. Zero outside the radius.
     */
    public static double[] wellForce(double dx, double dy, double dz, double radius) {
        double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (d >= radius || d < 1e-6) {
            return new double[] {0, 0, 0};
        }
        double closeness = 1 - d / radius;
        double pull = WELL_PULL_EDGE + (WELL_PULL_CORE - WELL_PULL_EDGE) * closeness * closeness;
        double flat = Math.hypot(dx, dz);
        double sx = flat < 1e-6 ? 0 : -dz / flat, sz = flat < 1e-6 ? 0 : dx / flat;
        double swirl = WELL_SWIRL * closeness;
        return new double[] {-dx / d * pull + sx * swirl, -dy / d * pull * 1.2, -dz / d * pull + sz * swirl};
    }

    /** How hard a projectile turns at distance {@code d} from a well of {@code radius}: much harder near the core. */
    public static double bendFor(double d, double radius) {
        double closeness = Math.max(0, 1 - d / radius);
        return PROJECTILE_BEND * 0.8 + 0.45 * closeness * closeness;
    }

    public static double[] bendProjectile(double vx, double vy, double vz, double toX, double toY, double toZ) {
        return bendProjectile(vx, vy, vz, toX, toY, toZ, PROJECTILE_BEND);
    }

    /** A projectile's new velocity: same speed, direction turned part of the way ({@code bend}) towards the well. */
    public static double[] bendProjectile(double vx, double vy, double vz, double toX, double toY, double toZ,
            double bend) {
        double speed = Math.sqrt(vx * vx + vy * vy + vz * vz);
        double t = Math.sqrt(toX * toX + toY * toY + toZ * toZ);
        if (speed < 1e-6 || t < 1e-6) {
            return new double[] {vx, vy, vz};
        }
        double nx = vx / speed * (1 - bend) + toX / t * bend;
        double ny = vy / speed * (1 - bend) + toY / t * bend;
        double nz = vz / speed * (1 - bend) + toZ / t * bend;
        double n = Math.sqrt(nx * nx + ny * ny + nz * nz);
        return new double[] {nx / n * speed, ny / n * speed, nz / n * speed};
    }

    /** During the charge everything is dragged in and lifted a little, harder as the charge builds. */
    public static double[] collapseDrag(double dx, double dz, int chargeTick) {
        double d = Math.hypot(dx, dz);
        double build = Math.min(1, (double) chargeTick / COLLAPSE_CHARGE_TICKS);
        double pull = 0.06 + 0.22 * build;
        if (d < 0.8) {
            return new double[] {0, COLLAPSE_LIFT * build, 0};
        }
        return new double[] {-dx / d * pull, COLLAPSE_LIFT * build, -dz / d * pull};
    }

    /** The blast throws things out and up, weaker with distance. */
    public static double[] collapseBlast(double dx, double dz, double radius) {
        double d = Math.hypot(dx, dz);
        double strength = COLLAPSE_BLAST * Math.max(0.25, 1 - d / radius);
        if (d < 1e-6) {
            return new double[] {0, strength * 0.8, 0};
        }
        return new double[] {dx / d * strength, 0.55 + strength * 0.35, dz / d * strength};
    }

    /** How strongly a screen effect reaches someone {@code distance} away: 1 up close, 0 past {@code range}. */
    public static float screenFalloff(double distance, double range) {
        if (distance >= range) {
            return 0f;
        }
        double t = distance / range;
        return (float) ((1 - t) * (1 - t));
    }

    /** Points of a spiral arm at parameter t in [0,1): from the rim inwards, {@code turns} times around. */
    public static double[] spiralPoint(double radius, double turns, double phase, double t) {
        double r = radius * (1 - t);
        double a = phase + t * turns * 2 * Math.PI;
        return new double[] {Math.cos(a) * r, Math.sin(a) * r};
    }
}
