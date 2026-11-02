package dev.cogwit.guide;

/** Compass words and screen angles for a target at an offset (dx east, dz south) from the player. */
public final class GuideDirection {
    public enum Compass {
        NORTH, NORTH_EAST, EAST, SOUTH_EAST, SOUTH, SOUTH_WEST, WEST, NORTH_WEST;

        /** Translation key such as {@code cogwit.guide.dir.north_east}. */
        public String key() {
            return "cogwit.guide.dir." + name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    private GuideDirection() {}

    public static Compass compass(double dx, double dz) {
        double degrees = Math.toDegrees(Math.atan2(dx, -dz));
        int sector = (int) Math.round(degrees / 45.0);
        return Compass.values()[Math.floorMod(sector, 8)];
    }

    /**
     * Degrees to rotate an upward-pointing arrow clockwise so it points at the target for a player
     * looking along {@code playerYaw} (Minecraft yaw: 0 south, 90 west). Result is in [-180, 180).
     */
    public static float arrowDegrees(double dx, double dz, float playerYaw) {
        double toTarget = Math.toDegrees(Math.atan2(-dx, dz));
        double relative = (toTarget - playerYaw) % 360.0;
        if (relative >= 180.0) relative -= 360.0;
        if (relative < -180.0) relative += 360.0;
        return (float) relative;
    }

    /** Rounded for speech: nearest 10 below 100 blocks, nearest 50 below 1000, else nearest 100. */
    public static int roughDistance(double blocks) {
        double d = Math.max(0, blocks);
        int step = d < 100 ? 10 : d < 1000 ? 50 : 100;
        return Math.max(step, (int) (Math.round(d / step) * step));
    }
}
