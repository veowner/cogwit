package dev.cogwit.client;

/**
 * Turns a right-stick reading into a pointer direction for radial menus. Pure maths, so it is unit
 * tested; {@link GamepadWheel} reads the stick and moves the cursor.
 */
public final class GamepadAim {
    /** Below this tilt the stick is treated as resting and the pointer stays where it is. */
    static final double DEADZONE = 0.35;

    private GamepadAim() {}

    /** {dx, dy, reach} with a unit direction and reach 0..1 past the deadzone, or null while resting. */
    public static double[] aim(float x, float y) {
        double m = Math.hypot(x, y);
        if (m < DEADZONE) {
            return null;
        }
        double reach = Math.min(1, (m - DEADZONE) / (1 - DEADZONE));
        return new double[]{x / m, y / m, reach};
    }
}
