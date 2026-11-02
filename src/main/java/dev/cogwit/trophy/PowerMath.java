package dev.cogwit.trophy;

/** Plain-number rules of the newer trophy powers, kept free of Minecraft classes so they can be unit tested. */
public final class PowerMath {
    /** The Ferrous Wroughtnaut sends this share of the melee damage it takes back at the attacker. */
    public static final float REFLECT_SHARE = 0.3f;
    /** The Dead King leaves you on this share of your maximum health. */
    public static final float REVIVE_SHARE = 0.25f;
    /** A fall must be longer than this (blocks) before the Sculptor's platform catches it. */
    public static final float CATCH_FALL = 6f;
    /** The platform appears only when solid ground is at most this many blocks under the feet. */
    public static final int CATCH_GROUND = 6;

    private PowerMath() {}

    public static float reflected(float taken) {
        return taken <= 0 ? 0 : taken * REFLECT_SHARE;
    }

    public static float reviveHealth(float maxHealth) {
        return Math.max(1f, maxHealth * REVIVE_SHARE);
    }

    /** Whether a fall is long enough and the ground close enough for the Sculptor to step in. */
    public static boolean catchesFall(float fallDistance, int groundBelow) {
        return fallDistance > CATCH_FALL && groundBelow >= 0 && groundBelow <= CATCH_GROUND;
    }

    /**
     * Whether a creature at offset (dx, dy, dz) from the cone's tip stands inside a cone that opens along
     * (fx, fy, fz) with the given half angle and reach. {@code radius} is the creature's own half width, which widens
     * the cone so a big mob is hit when its edge is inside, not only its centre.
     */
    public static boolean inCone(double dx, double dy, double dz, double fx, double fy, double fz, double range,
            double halfAngleDegrees, double radius) {
        double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
        double flen = Math.sqrt(fx * fx + fy * fy + fz * fz);
        if (dist > range + radius || flen < 1e-9) {
            return false;
        }
        if (dist <= radius) {
            return true;
        }
        double cos = (dx * fx + dy * fy + dz * fz) / (dist * flen);
        double angle = Math.toDegrees(Math.acos(Math.max(-1, Math.min(1, cos))));
        double widen = Math.toDegrees(Math.asin(Math.min(1, radius / dist)));
        return angle <= halfAngleDegrees + widen;
    }

    /**
     * The tilts {tiltX, tiltZ} in radians that stand a shard, built as rotateY(0).rotateX(tiltX).rotateZ(tiltZ), so that
     * its top leans by {@code theta} from the vertical towards the horizontal direction ({@code dx}, {@code dz}).
     */
    public static double[] lean(double dx, double dz, double theta) {
        double len = Math.hypot(dx, dz);
        if (len < 1e-9) {
            return new double[] {0, 0};
        }
        double ux = dx / len, uz = dz / len;
        return new double[] {Math.atan(uz * Math.tan(theta)), -Math.asin(ux * Math.sin(theta))};
    }

    /** How far the point p is from the nearest point of the segment from a to b. */
    public static double distanceToSegment(double px, double py, double pz, double ax, double ay, double az, double bx,
            double by, double bz) {
        double abx = bx - ax, aby = by - ay, abz = bz - az;
        double len2 = abx * abx + aby * aby + abz * abz;
        double t = len2 < 1e-12 ? 0 : Math.max(0, Math.min(1, ((px - ax) * abx + (py - ay) * aby + (pz - az) * abz) / len2));
        double dx = px - (ax + abx * t), dy = py - (ay + aby * t), dz = pz - (az + abz * t);
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }
}
