package dev.cogwit.agent;

import dev.cogwit.mind.Threats;

import java.util.List;

/**
 * Where to back off to when cornered: eight headings around the player, each scored by how many enemies it leads
 * away from (closer ones count more), skipping headings with no safe ground. Pure logic.
 */
public final class Escape {
    public static final int HEADINGS = 8;

    private Escape() {}

    /** Unit x/z of heading {@code k}, counted counter-clockwise from +x in 45 degree steps. */
    public static double[] heading(int k) {
        double a = k * Math.PI * 2 / HEADINGS;
        return new double[] {Math.cos(a), Math.sin(a)};
    }

    /**
     * The heading that leads furthest from the threats (each given as x/z offset from the player), or -1 when no
     * heading has safe ground.
     */
    public static int safest(List<double[]> threats, boolean[] groundOk) {
        int best = -1;
        double bestScore = Double.NEGATIVE_INFINITY;
        for (int k = 0; k < HEADINGS; k++) {
            if (!groundOk[k]) {
                continue;
            }
            double[] h = heading(k);
            double score = 0;
            for (double[] t : threats) {
                double d = Math.hypot(t[0], t[1]);
                if (d < 1e-6) {
                    continue;
                }
                score -= (h[0] * t[0] + h[1] * t[1]) / d / Math.max(1, d);
            }
            if (score > bestScore) {
                bestScore = score;
                best = k;
            }
        }
        return best;
    }

    /** One of eight words relative to where the player looks: 0 ahead, 1 ahead-right, 2 right ... 7 ahead-left. */
    public static int relative(double lookX, double lookZ, double dirX, double dirZ) {
        double angle = Threats.relativeAngle(lookX, lookZ, dirX, dirZ);
        return Math.floorMod((int) Math.round(angle / 45), 8);
    }
}
