package dev.cogwit.crew;

import dev.cogwit.brain.Brain;
import dev.cogwit.brain.BrainState;
import dev.cogwit.brain.Growth;

import java.util.ArrayList;
import java.util.List;

/**
 * Cogwit off the shoulder: who fights, what it costs and when they come back. Pure logic, like {@link Brain}.
 */
public final class Crew {
    /** Tokens Cogwit needs in the tank to jump down at all. */
    public static final int MIN_TOKENS_TO_DEPLOY = 20;
    /** A knocked-out Cogwit sleeps this long back on the shoulder. */
    public static final long KNOCKOUT_TICKS = 2400;
    /** A fallen brother comes back after this long. */
    public static final long BROTHER_RETURN_TICKS = 6000;
    /** While grieving a brother, Cogwit protects 30% worse. */
    public static final long GRIEF_TICKS = 1200;
    public static final float GRIEF_FACTOR = 0.7f;
    /** After a rescue Cogwit covers his owner on foot for this long before going to sleep. */
    public static final long RESCUE_COVER_TICKS = 200;
    /** Below this share of health a crew member glows and Cogwit asks for help. */
    public static final float LOW_HEALTH = 0.3f;
    /** Teleport back to the owner when farther than this. */
    public static final double LEASH = 24;

    /** The four of them. Stats are base values before any gear. */
    public enum Kind {
        COGWIT(20, 3, 0.30, 1.00f, 2, 0.0, 1, 20),
        SPARK(12, 2, 0.40, 0.75f, 0, 0.0, 1, 10),
        RIVET(24, 4, 0.30, 1.00f, 2, 0.2, 1, 20),
        ANVIL(40, 5, 0.24, 1.35f, 6, 0.6, 2, 26);

        public final double health, attack, speed, armor, knockbackResistance;
        public final float scale;
        public final int tokensPerSecond, attackInterval;

        Kind(double health, double attack, double speed, float scale, double armor, double knockbackResistance,
                int tokensPerSecond, int attackInterval) {
            this.health = health;
            this.attack = attack;
            this.speed = speed;
            this.scale = scale;
            this.armor = armor;
            this.knockbackResistance = knockbackResistance;
            this.tokensPerSecond = tokensPerSecond;
            this.attackInterval = attackInterval;
        }

        public boolean brother() {
            return this != COGWIT;
        }

        public String key() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    public enum Deploy { OK, ASLEEP, NO_TOKENS }

    private Crew() {}

    public static Deploy canDeploy(BrainState s, int tokens, long now) {
        if (Brain.isAsleep(s, now)) {
            return Deploy.ASLEEP;
        }
        return tokens < MIN_TOKENS_TO_DEPLOY ? Deploy.NO_TOKENS : Deploy.OK;
    }

    /** Who jumps down: Cogwit, plus every brother that is unlocked and not still recovering. */
    public static List<Kind> squad(BrainState s, CrewState crew, long now) {
        List<Kind> squad = new ArrayList<>(List.of(Kind.COGWIT));
        if (Growth.has(s.xp(), Growth.Perk.BROTHERS)) {
            for (Kind k : Kind.values()) {
                if (k.brother() && !crew.isDown(k, now)) {
                    squad.add(k);
                }
            }
        }
        return squad;
    }

    public static int upkeep(List<Kind> fighting) {
        return fighting.stream().mapToInt(k -> k.tokensPerSecond).sum();
    }

    /** Protection multiplier for the owner: lower while a brother has just fallen. */
    public static float grief(CrewState crew, long now) {
        return now < crew.griefUntil() ? GRIEF_FACTOR : 1f;
    }

    /** True while a freshly rescued owner is covered by Cogwit on foot, even though he is technically asleep. */
    public static boolean isCovering(BrainState s, long now) {
        return s.rescuedAt() > 0 && now >= s.rescuedAt() && now < s.rescuedAt() + RESCUE_COVER_TICKS;
    }

    public static boolean isLowHealth(float health, float max) {
        return health > 0 && health < max * LOW_HEALTH;
    }

    /** Cogwit is down: back to the shoulder for a nap. */
    public static BrainState knockout(BrainState s, long now) {
        return s.asleep(now, now + KNOCKOUT_TICKS);
    }

    /** A brother fell: he is out for a while and Cogwit grieves. */
    public static CrewState brotherFell(CrewState crew, Kind kind, long now) {
        return crew.withDownUntil(kind, now + BROTHER_RETURN_TICKS).withGriefUntil(now + GRIEF_TICKS);
    }
}
