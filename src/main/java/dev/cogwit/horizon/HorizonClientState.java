package dev.cogwit.horizon;

import dev.cogwit.network.HorizonSyncPayload;

/** Last armor state the server sent for the local player; read by the HUD. Safe to load on servers. */
public final class HorizonClientState {
    private static volatile HorizonSyncPayload latest = new HorizonSyncPayload(0f, 0, 0, 0, 0);
    private static volatile long receivedAtMillis;

    private HorizonClientState() {}

    public static void update(HorizonSyncPayload payload) {
        latest = payload;
        receivedAtMillis = System.currentTimeMillis();
    }

    public static HorizonSyncPayload latest() {
        return latest;
    }

    /** Ticks left on a cooldown, counting down locally between syncs. */
    public static int ticksLeft(int sentTicksLeft) {
        long elapsedTicks = (System.currentTimeMillis() - receivedAtMillis) / 50;
        return (int) Math.max(0, sentTicksLeft - elapsedTicks);
    }
}
