package dev.cogwit.trophy;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Who earns a trophy and when the set is complete. Pure logic. */
public final class Trophies {
    /** Everyone who hit the boss this recently when it dies gets the trophy: co-op kills count for both. */
    public static final long CREDIT_TICKS = 600;
    /** Full-set players this close share their trophies' passives. */
    public static final double SHARE_RANGE = 24;
    public static final int TOTAL = Trophy.values().length;

    private Trophies() {}

    /** Players whose last hit on the boss is recent enough, plus whoever landed the killing blow. */
    public static List<UUID> credited(Map<UUID, Long> lastHits, UUID killer, long now) {
        List<UUID> out = new ArrayList<>();
        lastHits.forEach((player, tick) -> {
            if (now - tick <= CREDIT_TICKS) {
                out.add(player);
            }
        });
        if (killer != null && !out.contains(killer)) {
            out.add(killer);
        }
        return out;
    }

    public static boolean complete(Collection<Trophy> owned) {
        return owned.size() >= TOTAL && EnumSet.copyOf(owned).size() == TOTAL;
    }

    /** Passives in effect: your own trophies plus those of full-set players standing near you. */
    public static Set<Trophy> passives(Collection<Trophy> own, Collection<? extends Collection<Trophy>> nearby) {
        Set<Trophy> all = own.isEmpty() ? EnumSet.noneOf(Trophy.class) : EnumSet.copyOf(own);
        for (Collection<Trophy> other : nearby) {
            all.addAll(other);
        }
        return all;
    }
}
