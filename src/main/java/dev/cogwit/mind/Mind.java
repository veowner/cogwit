package dev.cogwit.mind;

import dev.cogwit.brain.Brain;
import dev.cogwit.brain.BrainState;
import dev.cogwit.brain.Growth;

/** Cogwit's "brain": when to speak up, scanning, marks, moods and what he says about your deaths. Pure logic. */
public final class Mind {
    /** The same kind of hint is repeated at most this often. */
    public static final long HINT_COOLDOWN_TICKS = 200;
    /** A creeper this close counts as a threat. */
    public static final double CREEPER_RANGE = 7;
    public static final float LOW_HEALTH = 0.3f;

    public static final int SCAN_RADIUS = 32;
    public static final int SCAN_TOKEN_COST = 30;
    public static final long SCAN_COOLDOWN_TICKS = 600;
    /** How long the outlines stay on screen. */
    public static final long SCAN_SHOW_TICKS = 200;
    /** Never outline more than this many ores (most valuable first) and containers (closest first). */
    public static final int SCAN_LIMIT = 160, SCAN_CONTAINER_LIMIT = 32;
    /** How fast the scan wave runs out from Cogwit, in blocks per second. */
    public static final double SCAN_WAVE_SPEED = 40;

    /** What the scan tells apart, worth first; {@code colour} is the outline's RGB. */
    public enum Ore {
        DEBRIS(10, 0xC0806A), DIAMOND(9, 0x5CE6EE), EMERALD(8, 0x3DE07A), GOLD(6, 0xFFD24A), MODDED(6, 0xE7A6FF),
        LAPIS(5, 0x4A7CFF), REDSTONE(4, 0xFF4A3D), IRON(4, 0xE8BC94), QUARTZ(3, 0xF2EDE4), COPPER(3, 0xE8804E),
        COAL(1, 0x8A8A90);

        public final int value;
        public final int colour;

        Ore(int value, int colour) {
            this.value = value;
            this.colour = colour;
        }

        public String key() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    /** The finds worth outlining: most valuable first, then closest, at most {@code limit}. */
    public static <T> java.util.List<T> pickFinds(java.util.List<T> all, java.util.function.Function<T, Ore> ore,
            java.util.function.ToDoubleFunction<T> distance, int limit) {
        return all.stream()
                .sorted(java.util.Comparator.<T>comparingInt(t -> -ore.apply(t).value).thenComparingDouble(distance))
                .limit(limit)
                .toList();
    }

    /** Ms after the scan until its wave reaches something {@code distance} blocks away. */
    public static long waveArrival(double distance) {
        return Math.round(distance / SCAN_WAVE_SPEED * 1000);
    }

    /** How far the mark key reaches, and how long a mark stays. */
    public static final double MARK_REACH = 96;
    public static final long MARK_SHOW_TICKS = 300;
    /** Players this close to a mark see it. */
    public static final double MARK_SHARE_RANGE = 256;

    public static final long HAPPY_FED_TICKS = 60, HAPPY_KILL_TICKS = 40, HAPPY_LEVEL_TICKS = 100;
    public static final long SCARED_TICKS = 40;

    public enum Hint { CREEPER_BEHIND, SKELETON_AIMING, LOW_HP, LAVA, CLIFF, NIGHT, AIR, TOOL, SURROUNDED }

    /** Enemies this close count towards being surrounded. */
    public static final double SURROUND_RANGE = 6;

    /** Surrounded: many enemies close, or a few when you're already hurt. */
    public static boolean surrounded(int enemiesClose, float healthShare) {
        return enemiesClose >= 6 || enemiesClose >= 4 && healthShare < 0.6f;
    }

    /** A drop at least this deep right ahead of a walking player is worth a word. */
    public static final int CLIFF_DEPTH = 10;
    /** How far ahead of the feet Cogwit looks for a drop, in blocks. */
    public static final double CLIFF_AHEAD = 1.6;
    /** Air share under which Cogwit tells you how far the surface is. */
    public static final float AIR_LOW = 0.35f;
    /** Uses left on a tool at which Cogwit warns, and at which he takes an enchanted one out of your hand. */
    public static final int TOOL_WARN = 10, TOOL_SAVE = 2;
    /** Day time (0 to 24000) when Cogwit says night is coming: about a minute before mobs start to spawn. */
    public static final long DUSK_FROM = 11_300, DUSK_TO = 11_500;

    /** Whether night is a minute away at this day time. */
    public static boolean duskWarning(long dayTime) {
        long t = Math.floorMod(dayTime, 24_000L);
        return t >= DUSK_FROM && t < DUSK_TO;
    }

    /** A cliff worth mentioning: deep, no water to land in, and the player has nothing to break the fall. */
    public static boolean cliffAhead(int depth, boolean waterBelow, boolean fallProof) {
        return depth >= CLIFF_DEPTH && !waterBelow && !fallProof;
    }

    public enum Scan { OK, LOCKED, ASLEEP, COOLDOWN, NO_TOKENS }

    public enum Emotion { CALM, HAPPY, SCARED }

    public enum DeathKind { FALL, LAVA, FIRE, EXPLOSION, DROWN, VOID, STARVE, MAGIC, BOSS, MOB, PLAYER, OTHER }

    /** Variants of each death comment in the language files. */
    public static final int DEATH_VARIANTS = 3;

    private Mind() {}

    public static boolean ready(long lastShown, long now) {
        return lastShown == 0 || now - lastShown >= HINT_COOLDOWN_TICKS;
    }

    /** A creeper is worth shouting about when it is close and either behind you or already hissing. */
    public static boolean creeperThreat(double distance, boolean behind, boolean swelling) {
        return distance < CREEPER_RANGE && (behind || swelling);
    }

    public static Scan canScan(BrainState s, MindState m, long now) {
        if (!Growth.has(s.xp(), Growth.Perk.HIGHLIGHT)) {
            return Scan.LOCKED;
        }
        if (Brain.isAsleep(s, now)) {
            return Scan.ASLEEP;
        }
        if (now < m.scanReadyAt()) {
            return Scan.COOLDOWN;
        }
        return s.tokens() < SCAN_TOKEN_COST ? Scan.NO_TOKENS : Scan.OK;
    }

    public static Emotion emotion(MindState m, long now) {
        if (now < m.scaredUntil()) {
            return Emotion.SCARED;
        }
        return now < m.happyUntil() ? Emotion.HAPPY : Emotion.CALM;
    }

    public static MindState cheer(MindState m, long now, long ticks) {
        return m.withHappyUntil(Math.max(m.happyUntil(), now + ticks));
    }

    public static MindState scare(MindState m, long now) {
        return m.withScaredUntil(now + SCARED_TICKS);
    }

    /**
     * Sorts a death by its damage type path ("fall", "lava", ...) and who did it.
     */
    public static DeathKind classify(String damageType, boolean bossKiller, boolean mobKiller, boolean playerKiller) {
        if (bossKiller) return DeathKind.BOSS;
        if (playerKiller) return DeathKind.PLAYER;
        return switch (damageType) {
            case "fall", "fly_into_wall", "stalagmite", "falling_stalactite", "falling_anvil", "falling_block" ->
                    DeathKind.FALL;
            case "lava" -> DeathKind.LAVA;
            case "in_fire", "on_fire", "hot_floor", "campfire" -> DeathKind.FIRE;
            case "explosion", "player_explosion", "fireworks", "bad_respawn_point" -> DeathKind.EXPLOSION;
            case "drown" -> DeathKind.DROWN;
            case "out_of_world", "outside_border" -> DeathKind.VOID;
            case "starve" -> DeathKind.STARVE;
            case "magic", "indirect_magic", "wither", "dragon_breath", "sonic_boom" -> DeathKind.MAGIC;
            default -> mobKiller ? DeathKind.MOB : DeathKind.OTHER;
        };
    }

    /** Which comment variant to use for the n-th death, so Cogwit doesn't repeat himself back to back. */
    public static int variant(int deaths) {
        return Math.floorMod(deaths, DEATH_VARIANTS) + 1;
    }
}
