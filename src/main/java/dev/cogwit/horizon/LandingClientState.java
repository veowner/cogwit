package dev.cogwit.horizon;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Who is in the middle of a landing crouch on this client, by entity id. Plain data, safe to load on a server. */
public final class LandingClientState {
    private record Landing(long startMillis, float strength) {}

    private static final Map<Integer, Landing> LANDINGS = new ConcurrentHashMap<>();

    private LandingClientState() {}

    public static void landed(int entityId, float strength) {
        LANDINGS.put(entityId, new Landing(System.currentTimeMillis(), strength));
    }

    /** How deep this entity crouches right now, 0 when it isn't landing. */
    public static float crouch(int entityId) {
        Landing landing = LANDINGS.get(entityId);
        if (landing == null) {
            return 0f;
        }
        long age = System.currentTimeMillis() - landing.startMillis();
        if (age > Horizon.landingPoseMillis(landing.strength())) {
            LANDINGS.remove(entityId);
            return 0f;
        }
        return Horizon.landingCrouch(age, landing.strength());
    }
}
