package dev.cogwit.compat.create;

import dev.cogwit.cells.Cells;

/** Cell charger rules, free of Minecraft and Create classes so they can be unit tested. */
public final class ChargerMath {
    /** Tokens per second added per RPM: 256 RPM gives 128 tokens per second, a full cell in about 20 seconds. */
    public static final float TOKENS_PER_SECOND_PER_RPM = 1f / 2f;
    public static final int TICKS_PER_SECOND = 20;
    /** Stress impact (SU) at 1 RPM; 256 RPM costs 1024 SU. */
    public static final double STRESS_PER_RPM = 4.0;

    private ChargerMath() {}

    public static float tokensPerTick(float rpm) {
        return Math.abs(rpm) * TOKENS_PER_SECOND_PER_RPM / TICKS_PER_SECOND;
    }

    /** Result of one tick of charging: the new whole charge and the fractional token carried over. */
    public record Step(int charge, float fraction) {}

    public static Step step(int charge, float fraction, float rpm) {
        if (charge >= Cells.CAPACITY) {
            return new Step(Cells.CAPACITY, 0f);
        }
        float total = fraction + tokensPerTick(rpm);
        int whole = (int) total;
        int next = Math.min(Cells.CAPACITY, charge + whole);
        return next >= Cells.CAPACITY ? new Step(Cells.CAPACITY, 0f) : new Step(next, total - whole);
    }

    /** Comparator strength: 0 for an empty slot or a flat cell, 1..15 otherwise, 15 only when full. */
    public static int signal(boolean hasCell, int charge) {
        if (!hasCell || charge <= 0) {
            return 0;
        }
        return Math.min(15, 1 + (int) (14L * Math.min(charge, Cells.CAPACITY) / Cells.CAPACITY));
    }
}
