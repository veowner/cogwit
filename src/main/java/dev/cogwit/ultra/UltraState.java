package dev.cogwit.ultra;

/**
 * Overdrive memory on the talisman.
 *
 * @param active        Cogwit is in rage mode right now
 * @param startedAt     when this Overdrive began
 * @param lastBossSeen  last time a boss was fighting the owner
 * @param hangoverUntil Cogwit is drained after the fight until then
 * @param crewByUltra   Overdrive sent the crew down, so it also calls them back
 * @param bossHealth    the boss's last seen health share (0..1), to announce phases
 */
public record UltraState(boolean active, long startedAt, long lastBossSeen, long hangoverUntil, boolean crewByUltra,
                         float bossHealth) {
    public static final UltraState EMPTY = new UltraState(false, 0, 0, 0, false, 1f);

    public UltraState withCrewByUltra(boolean v) {
        return new UltraState(active, startedAt, lastBossSeen, hangoverUntil, v, bossHealth);
    }

    public UltraState withBossHealth(float v) {
        return new UltraState(active, startedAt, lastBossSeen, hangoverUntil, crewByUltra, v);
    }
}
