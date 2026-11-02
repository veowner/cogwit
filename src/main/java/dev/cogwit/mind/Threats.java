package dev.cogwit.mind;

import java.util.Comparator;
import java.util.List;

/** Geometry and priorities for Cogwit's Jarvis-style HUD: lock-on brackets and edge arrows. Pure logic. */
public final class Threats {
    /** How bad something is; also the draw priority, worst first. */
    public enum Level { DANGER, BOSS, WARN, LOOT, INFO }

    /** At most this many brackets at once, so the screen stays readable. */
    public static final int MAX_SHOWN = 6;
    /** Outside this angle from where you look, a threat gets an arrow around the crosshair instead. */
    public static final double ARROW_ANGLE = 50;
    /** Brackets start this much bigger and snap onto the target. */
    public static final float LOCK_START = 1.7f;
    public static final long LOCK_MILLIS = 260;

    private Threats() {}

    /**
     * Horizontal angle from the look direction to a target in degrees: 0 ahead, positive to the right,
     * ±180 behind. Minecraft's x/z: looking along (lookX, lookZ), "right" is (-lookZ, lookX).
     */
    public static double relativeAngle(double lookX, double lookZ, double toX, double toZ) {
        double ahead = lookX * toX + lookZ * toZ;
        double right = -lookZ * toX + lookX * toZ;
        return Math.toDegrees(Math.atan2(right, ahead));
    }

    public static boolean needsArrow(double angle) {
        return Math.abs(angle) > ARROW_ANGLE;
    }

    /** Bracket scale {@code age} ms after the target was first seen: from big to snug. */
    public static float lockOn(long age) {
        if (age >= LOCK_MILLIS) {
            return 1f;
        }
        float t = Math.max(0, age) / (float) LOCK_MILLIS;
        float ease = 1 - (1 - t) * (1 - t) * (1 - t);
        return LOCK_START + (1f - LOCK_START) * ease;
    }

    /** Worst first, then closest; keeps only what fits on screen. */
    public static <T> List<T> pick(List<T> all, java.util.function.Function<T, Level> level,
            java.util.function.ToDoubleFunction<T> distance) {
        return all.stream()
                .sorted(Comparator.comparing(level).thenComparingDouble(distance))
                .limit(MAX_SHOWN)
                .toList();
    }

    /** A mob closing in this fast (blocks per tick) while aggressive is coming for you. */
    public static final double COMING_SPEED = 0.03;

    public static boolean coming(boolean aggressive, double approachSpeed) {
        return aggressive && approachSpeed > COMING_SPEED;
    }

    public static Level creeper(double distance, boolean behind, boolean swelling) {
        if (swelling && distance < Mind.CREEPER_RANGE + 3) {
            return Level.DANGER;
        }
        return Mind.creeperThreat(distance, behind, false) ? Level.WARN : Level.INFO;
    }
}
