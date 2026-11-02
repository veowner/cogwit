package dev.cogwit.trophy;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Trophies of the players this client can see, and the local player's cooldowns. Plain data, server-safe. */
public final class TrophyClientState {
    private static final Map<Integer, Set<Trophy>> OWNED = new HashMap<>();
    private static final Map<Trophy, Long> READY_AT = new EnumMap<>(Trophy.class);

    private TrophyClientState() {}

    public static synchronized void update(int entityId, List<String> keys, List<Long> readyAt, boolean self) {
        Set<Trophy> set = EnumSet.noneOf(Trophy.class);
        for (int i = 0; i < keys.size(); i++) {
            int index = i;
            Trophy.byKey(keys.get(i)).ifPresent(t -> {
                set.add(t);
                if (self && index < readyAt.size()) {
                    READY_AT.put(t, readyAt.get(index));
                }
            });
        }
        OWNED.put(entityId, set);
    }

    public static synchronized Set<Trophy> owned(int entityId) {
        Set<Trophy> set = OWNED.get(entityId);
        return set == null || set.isEmpty() ? Set.of() : EnumSet.copyOf(set);
    }

    /** Game tick at which a trophy's active is ready again for the local player (0 when ready). */
    public static synchronized long readyAt(Trophy trophy) {
        return READY_AT.getOrDefault(trophy, 0L);
    }

    public static synchronized void clear() {
        OWNED.clear();
        READY_AT.clear();
    }
}
