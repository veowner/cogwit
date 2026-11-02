package dev.cogwit.brain;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Cogwit's learning rules, kept free of Minecraft classes so they can be unit-tested.
 * Time is measured in game ticks (20 per second).
 */
public final class Brain {
    public static final int CONTEXT_SIZE = 5;
    public static final int MAX_XP = 50;
    public static final int MAX_TOKENS = 2000;
    public static final long XP_COOLDOWN_TICKS = 20;
    public static final float TOKENS_PER_HP = 4f;
    public static final long SLEEP_TICKS = 6000;
    public static final long COMPACT_COOLDOWN_TICKS = 12000;
    public static final long THINK_COOLDOWN_TICKS = 2400;
    public static final long THINK_DURATION_TICKS = 160;
    public static final int THINK_TOKEN_COST = 50;
    public static final long REGEN_DELAY_TICKS = 100;
    public static final long REGEN_INTERVAL_TICKS = 50;
    public static final int REGEN_TOKEN_COST = 2;
    public static final int EFFECT_TOKEN_COST = 5;
    public static final long REFUSAL_MESSAGE_COOLDOWN_TICKS = 200;
    public static final int HUNGRY_BELOW = 200;

    /** Saving the owner from death costs a long nap. */
    public static final long RESCUE_SLEEP_TICKS = 12000;
    /** A Copper Apple can wake Cogwit once he has slept this long. */
    public static final long WAKE_AFTER_TICKS = 3600;
    /** Invulnerability right after a rescue. */
    public static final long RESCUE_SHIELD_TICKS = 60;
    /** Health a rescued owner is left with. */
    public static final float RESCUE_HEALTH = 2f;
    public static final long BACK_SHIELD_COOLDOWN_TICKS = 160;
    public static final int BACK_SHIELD_TOKEN_COST = 25;
    /** How far behind (cosine against the look direction) a hit must come from for the back shield. */
    public static final double BEHIND_DOT = -0.3;

    private static final int[] LEVEL_XP = {3, 10, 25, 50};
    private static final float[] LEVEL_REDUCTION = {0.10f, 0.25f, 0.40f, 0.60f};

    private Brain() {}

    public static int levelForXp(int xp) {
        int level = 0;
        while (level < LEVEL_XP.length && xp >= LEVEL_XP[level]) {
            level++;
        }
        return level;
    }

    /** World balance multiplier on every skill's blocking, set from the server config; 1 in pure tests. */
    private static volatile float blockScale = 1f;

    public static void setBlockScale(float scale) {
        blockScale = Math.max(0f, scale);
    }

    public static float blockScale() {
        return blockScale;
    }

    public static float reductionForXp(int xp) {
        int level = levelForXp(xp);
        return level == 0 ? 0f : Math.min(0.95f, LEVEL_REDUCTION[level - 1] * blockScale);
    }

    /** Hits needed to reach the next level, or 0 when maxed. */
    public static int xpToNextLevel(int xp) {
        int level = levelForXp(xp);
        return level >= LEVEL_XP.length ? 0 : LEVEL_XP[level] - xp;
    }

    /** 0..1 progress from the current level's threshold to the next one; 1 when maxed. */
    public static float levelProgress(int xp) {
        int level = levelForXp(xp);
        if (level >= LEVEL_XP.length) {
            return 1f;
        }
        int from = level == 0 ? 0 : LEVEL_XP[level - 1];
        return (float) (xp - from) / (LEVEL_XP[level] - from);
    }

    /** Game ticks as "m:ss". */
    public static String clock(long ticks) {
        long total = Math.max(0, ticks) / 20;
        return String.format("%d:%02d", total / 60, total % 60);
    }

    public static boolean isAsleep(BrainState s, long now) {
        return now < s.sleepUntil();
    }

    /** Thinking started when the cooldown was set and lasts {@link #THINK_DURATION_TICKS}. */
    public static boolean isThinking(BrainState s, long now) {
        long startedAt = s.thinkReadyAt() - THINK_COOLDOWN_TICKS;
        return s.thinkReadyAt() > 0 && now >= startedAt && now < startedAt + THINK_DURATION_TICKS;
    }

    public static float reductionFor(BrainState s, String category) {
        return s.find(category).map(skill -> skill.reductionFor(category)).orElse(0f);
    }

    /** Strongest protection among a group of nearby Cogwits that are awake and fed. */
    public static float bestReduction(List<BrainState> states, String category, long now) {
        float best = 0f;
        for (BrainState s : states) {
            if (!isAsleep(s, now) && s.tokens() > 0) {
                best = Math.max(best, reductionFor(s, category));
            }
        }
        return best;
    }

    public static DamageResult onDamage(BrainState s, String category, float amount, long now) {
        return onDamage(s, category, amount, reductionFor(s, category), now);
    }

    /**
     * @param wanted protection to apply (may come from a teammate's Cogwit), paid with this Cogwit's tokens
     */
    public static DamageResult onDamage(BrainState s, String category, float amount, float wanted, long now) {
        if (isAsleep(s, now)) {
            return new DamageResult(s.withLastHurt(now), 0f, List.of());
        }
        List<BrainEvent> events = new ArrayList<>();

        float reduction = wanted;
        int tokens = s.tokens();
        int cost = (int) Math.ceil(amount * wanted * TOKENS_PER_HP);
        if (cost > tokens) {
            reduction = wanted * tokens / cost;
            tokens = 0;
        } else {
            tokens -= cost;
        }
        boolean fellAsleep = wanted > 0f && tokens == 0;
        if (fellAsleep) {
            events.add(new BrainEvent.FellAsleep());
        }

        List<Skill> skills = new ArrayList<>(s.skills());
        int index = indexOf(skills, category);
        if (index >= 0) {
            Skill skill = skills.get(index);
            if (!skill.compacted() && now - skill.lastXpGain() >= XP_COOLDOWN_TICKS) {
                Skill taught = skill.taught(now);
                int level = levelForXp(taught.xp());
                if (level > levelForXp(skill.xp())) {
                    events.add(new BrainEvent.LevelUp(category, level));
                }
                skills.set(index, taught);
            } else {
                skills.set(index, skill.touched(now));
            }
        } else {
            if (skills.size() >= CONTEXT_SIZE) {
                Skill stalest = skills.stream().min(Comparator.comparingLong(Skill::lastUsed)).orElseThrow();
                skills.remove(stalest);
                events.add(new BrainEvent.Forgot(stalest.category()));
                if (stalest.compacted()) {
                    events.add(new BrainEvent.Forgot(stalest.merged()));
                }
            }
            skills.add(Skill.learned(category, now));
            events.add(new BrainEvent.Learned(category));
        }

        BrainState next = s.withSkills(skills).withTokens(tokens).withLastHurt(now);
        if (fellAsleep) {
            next = next.asleep(now, now + SLEEP_TICKS);
        }
        float grown = next.xp() + amount * reduction * Growth.XP_PER_BLOCKED_HP;
        for (int level : Growth.levelsGained(next.xp(), grown)) {
            events.add(new BrainEvent.Grew(level));
        }
        next = next.withXp(grown);
        return new DamageResult(next, reduction, List.copyOf(events));
    }

    /** Squeezes the two weakest uncompressed skills into one slot at half strength each. */
    public static CompactResult compact(BrainState s, long now) {
        if (now < s.compactReadyAt()) {
            return new CompactResult(CompactResult.Status.COOLDOWN, s, "", "");
        }
        List<Skill> candidates = s.skills().stream()
                .filter(skill -> !skill.compacted())
                .sorted(Comparator.comparingInt(Skill::xp))
                .toList();
        if (candidates.size() < 2) {
            return new CompactResult(CompactResult.Status.NOT_ENOUGH, s, "", "");
        }
        Skill weakest = candidates.get(0);
        Skill other = candidates.get(1);
        Skill merged = new Skill(other.category(), other.xp(), Math.max(other.lastUsed(), weakest.lastUsed()),
                other.lastXpGain(), weakest.category(), weakest.xp());

        List<Skill> skills = new ArrayList<>(s.skills());
        skills.set(skills.indexOf(other), merged);
        skills.remove(weakest);
        BrainState next = s.withSkills(skills).withCompactReadyAt(now + COMPACT_COOLDOWN_TICKS);
        return new CompactResult(CompactResult.Status.OK, next, other.category(), weakest.category());
    }

    public static BrainState feed(BrainState s, int tokens) {
        return s.withTokens(Math.min(MAX_TOKENS, s.tokens() + tokens));
    }

    /** How many pieces worth {@code value} tokens to eat to get full; the last piece may overflow. */
    public static int itemsToFill(int tokens, int value, int available) {
        return itemsToFill(tokens, value, available, MAX_TOKENS);
    }

    /** Same, for a tank of the given capacity (Cogwit plus any token cells). */
    public static int itemsToFill(int tokens, int value, int available, int capacity) {
        if (tokens >= capacity || value <= 0) {
            return 0;
        }
        int needed = (capacity - tokens + value - 1) / value;
        return Math.min(needed, available);
    }

    /** True the moment tokens drop below the "feed me" line, so Cogwit asks once instead of nagging. */
    public static boolean becameHungry(int before, int after) {
        return before >= HUNGRY_BELOW && after < HUNGRY_BELOW;
    }

    public static RegenResult regen(BrainState s, long now, float health, float maxHealth) {
        boolean canHeal = !isAsleep(s, now)
                && health < maxHealth
                && now - s.lastHurt() >= REGEN_DELAY_TICKS
                && s.tokens() >= REGEN_TOKEN_COST;
        if (!canHeal) {
            return new RegenResult(s, 0f);
        }
        return new RegenResult(s.withTokens(s.tokens() - REGEN_TOKEN_COST), 1f);
    }

    /** Pays for shrugging off a harmful effect; empty when Cogwit can't help right now. */
    public static Optional<BrainState> blockEffect(BrainState s, long now) {
        if (isAsleep(s, now) || s.tokens() < EFFECT_TOKEN_COST) {
            return Optional.empty();
        }
        return Optional.of(s.withTokens(s.tokens() - EFFECT_TOKEN_COST));
    }

    /** Cogwit says "I can't help with that" at most once per cooldown, but always the first time. */
    public static boolean shouldSayRefusal(BrainState s, long now) {
        return s.lastRefusal() == 0 || now - s.lastRefusal() >= REFUSAL_MESSAGE_COOLDOWN_TICKS;
    }

    public static ThinkResult think(BrainState s, long now) {
        if (isAsleep(s, now)) {
            return new ThinkResult(ThinkResult.Status.ASLEEP, s);
        }
        if (now < s.thinkReadyAt()) {
            return new ThinkResult(ThinkResult.Status.COOLDOWN, s);
        }
        if (s.tokens() < THINK_TOKEN_COST) {
            return new ThinkResult(ThinkResult.Status.NO_TOKENS, s);
        }
        BrainState next = s.withTokens(s.tokens() - THINK_TOKEN_COST).withThinkReadyAt(now + THINK_COOLDOWN_TICKS);
        return new ThinkResult(ThinkResult.Status.OK, next);
    }

    /** Damage that would kill: health would drop to zero or below. */
    public static boolean isLethal(float health, float damage) {
        return health - damage <= 0f;
    }

    /** How much of a lethal hit to let through so the owner is left at {@link #RESCUE_HEALTH}. */
    public static float survivableDamage(float health, float damage) {
        return isLethal(health, damage) ? Math.max(0f, health - RESCUE_HEALTH) : damage;
    }

    /** Cogwit catches a lethal hit: empty when he is asleep and can't. */
    public static Optional<BrainState> rescue(BrainState s, long now) {
        if (isAsleep(s, now)) {
            return Optional.empty();
        }
        return Optional.of(Growth.gain(s, Growth.RESCUE_XP).asleep(now, now + RESCUE_SLEEP_TICKS).withRescuedAt(now));
    }

    /** True during the few seconds after a rescue when nothing can hurt the owner. */
    public static boolean isShielded(BrainState s, long now) {
        return s.rescuedAt() > 0 && now >= s.rescuedAt() && now < s.rescuedAt() + RESCUE_SHIELD_TICKS;
    }

    public static boolean canWake(BrainState s, long now) {
        return isAsleep(s, now) && wakeTicksLeft(s, now) == 0;
    }

    /** Ticks until a Copper Apple can wake a sleeping Cogwit. */
    public static long wakeTicksLeft(BrainState s, long now) {
        return Math.max(0, s.sleptAt() + WAKE_AFTER_TICKS - now);
    }

    /** True when a hit comes from behind: {@code to} points from the owner to the attacker, both horizontal. */
    public static boolean isBehind(double lookX, double lookZ, double toX, double toZ) {
        double look = Math.hypot(lookX, lookZ), to = Math.hypot(toX, toZ);
        if (look < 1e-6 || to < 1e-6) {
            return false;
        }
        return (lookX * toX + lookZ * toZ) / (look * to) < BEHIND_DOT;
    }

    /** Pays for blocking a hit from behind; empty when the perk is locked, on cooldown or unaffordable. */
    public static Optional<BrainState> backShield(BrainState s, long now) {
        if (!Growth.has(s.xp(), Growth.Perk.BACK_SHIELD) || isAsleep(s, now) || now < s.backShieldReadyAt()
                || s.tokens() < BACK_SHIELD_TOKEN_COST) {
            return Optional.empty();
        }
        return Optional.of(s.withTokens(s.tokens() - BACK_SHIELD_TOKEN_COST)
                .withBackShieldReadyAt(now + BACK_SHIELD_COOLDOWN_TICKS));
    }

    private static int indexOf(List<Skill> skills, String category) {
        for (int i = 0; i < skills.size(); i++) {
            if (skills.get(i).covers(category)) {
                return i;
            }
        }
        return -1;
    }
}
