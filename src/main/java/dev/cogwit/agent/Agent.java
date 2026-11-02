package dev.cogwit.agent;

import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Cogwit as an agent: what he may do with your inventory, ask / always / never permissions, and the small decisions
 * behind each action (which food, which weapon, when to save you). Pure logic.
 */
public final class Agent {
    /** Things Cogwit can do on his own. {@code combat} ones are done without asking in a fight. */
    public enum Action {
        EAT(true, 2), FALL_SAVE(true, 5), PEARL(true, 10), ARMOR_SAVE(false, 2), WEAPON(true, 1), GAPPLE(true, 5),
        REFILL(false, 1), MAGNET(false, 1), REPLANT(false, 1), ARMOR_SKILLS(true, 5), TROPHIES(true, 5);

        public final boolean combat;
        public final int tokenCost;

        Action(boolean combat, int tokenCost) {
            this.combat = combat;
            this.tokenCost = tokenCost;
        }

        public String key() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    public enum Permission { ASK, ALWAYS, NEVER }

    /** Behaviour presets: each sets a default permission for every action. */
    public enum Mode {
        CAREFUL, COMBAT, MINER;

        public String key() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    public enum Decision { ACT, ASK, SKIP }

    /** Hunger points at which Cogwit feeds you: out of a fight, and when it matters during one. */
    public static final int EAT_BELOW = 14, EAT_BELOW_IN_COMBAT = 8;
    /** Health share counted as critical (pearl out, golden apple in a boss fight). */
    public static final float CRITICAL = 0.25f, GAPPLE_BELOW = 0.5f;
    /** Armor is taken off with this little durability left. */
    public static final int ARMOR_SAVE_DURABILITY = 10;
    /** A ranged weapon is preferred from this distance. */
    public static final double RANGED_FROM = 7;
    /** A tool (pickaxe, shovel) gives way to a weapon only with the threat this close. */
    public static final double MELEE_REACH = 4;
    /** A weapon in hand gives way to another only when that one hits this many times harder per second. */
    public static final double UPGRADE = 1.5;
    /** Seconds since the last hit given or taken that still count as "in a fight". */
    public static final long COMBAT_TICKS = 100;
    /** How long a question waits for Y before Cogwit takes it as a no. */
    public static final long ASK_TIMEOUT_TICKS = 140;
    public static final long PEARL_COOLDOWN_TICKS = 200, GAPPLE_COOLDOWN_TICKS = 600, ASK_COOLDOWN_TICKS = 600;
    public static final double MAGNET_RANGE = 8;
    public static final int JOURNAL_SIZE = 16;

    private static final Map<Mode, Map<Action, Permission>> DEFAULTS = new EnumMap<>(Mode.class);

    static {
        for (Mode mode : Mode.values()) {
            Map<Action, Permission> map = new EnumMap<>(Action.class);
            for (Action a : Action.values()) {
                map.put(a, Permission.ALWAYS);
            }
            DEFAULTS.put(mode, map);
        }
        Map<Action, Permission> careful = DEFAULTS.get(Mode.CAREFUL);
        careful.put(Action.WEAPON, Permission.ASK);
        careful.put(Action.MAGNET, Permission.ASK);
        // skills and trophies are fight actions: "ask" turns into "act" in a fight, and nothing happens outside one
        careful.put(Action.ARMOR_SKILLS, Permission.ASK);
        careful.put(Action.TROPHIES, Permission.ASK);
        Map<Action, Permission> combat = DEFAULTS.get(Mode.COMBAT);
        combat.put(Action.MAGNET, Permission.ASK);
        combat.put(Action.REPLANT, Permission.ASK);
        Map<Action, Permission> miner = DEFAULTS.get(Mode.MINER);
        miner.put(Action.WEAPON, Permission.NEVER);
        miner.put(Action.ARMOR_SKILLS, Permission.NEVER);
        miner.put(Action.TROPHIES, Permission.NEVER);
    }

    private Agent() {}

    public static Permission permission(Mode mode, Map<Action, Permission> overrides, Action action) {
        return overrides.getOrDefault(action, DEFAULTS.get(mode).get(action));
    }

    /** Ask, act or skip. In a fight Cogwit doesn't stop to ask about fight-related actions. */
    public static Decision decide(Permission permission, Action action, boolean inCombat) {
        return switch (permission) {
            case NEVER -> Decision.SKIP;
            case ALWAYS -> Decision.ACT;
            case ASK -> inCombat && action.combat ? Decision.ACT : Decision.ASK;
        };
    }

    public static boolean inCombat(long lastFightTick, long now) {
        return lastFightTick > 0 && now - lastFightTick < COMBAT_TICKS;
    }

    public static boolean wantsFood(int foodLevel, boolean inCombat) {
        return foodLevel < (inCombat ? EAT_BELOW_IN_COMBAT : EAT_BELOW);
    }

    /** A food in the inventory, as Cogwit sees it. */
    public record Food(int slot, int nutrition, float saturation, boolean harmful, boolean precious) {}

    /**
     * The food to eat: never precious ones (golden apples are for bosses), harmful ones only when starving,
     * and the one that fills the gap best without wasting much.
     */
    public static Optional<Food> pickFood(List<Food> foods, int foodLevel) {
        int missing = 20 - foodLevel;
        boolean starving = foodLevel <= 6;
        return foods.stream()
                .filter(f -> !f.precious() && (!f.harmful() || starving))
                .min(Comparator.<Food>comparingInt(f -> f.harmful() ? 1 : 0)
                        .thenComparingInt(f -> Math.abs(missing - f.nutrition()))
                        .thenComparing(f -> -f.saturation()));
    }

    /**
     * Damage that gets through armor, by the vanilla formula: armor soaks a share that shrinks against heavy blows,
     * so against an armored target a slow, hard-hitting weapon can beat a fast one.
     */
    public static double throughArmor(double damage, double armor, double toughness) {
        double soaked = Math.max(armor * 0.2, Math.min(20, armor - damage / (2 + toughness / 4)));
        return damage * (1 - soaked / 25);
    }

    /** Armor at which Cogwit mentions it when he picks a weapon. */
    public static final double ARMORED = 8;
    /** Archers get the bow from this close. */
    public static final double ARCHER_RANGED_FROM = 4;

    /** A weapon in the hotbar, as Cogwit sees it: {@code dps} is damage per hit times hits per second. */
    public record Weapon(int slot, double dps, boolean ranged, boolean hasAmmo) {}

    /** What is in your hand, as far as swapping it goes. */
    public enum Held { EMPTY, TOOL, WEAPON, OTHER }

    /**
     * Best hotbar weapon for a fight at {@code distance}: ranged from afar when there is ammo and a bow is allowed,
     * else the most damage per second.
     */
    public static Optional<Weapon> pickWeapon(List<Weapon> weapons, double distance, boolean allowRanged) {
        return pickWeapon(weapons, distance, allowRanged, RANGED_FROM);
    }

    /** As above, with the distance from which a bow is preferred (closer for players who mostly shoot). */
    public static Optional<Weapon> pickWeapon(List<Weapon> weapons, double distance, boolean allowRanged,
            double rangedFrom) {
        if (allowRanged && distance >= rangedFrom) {
            Optional<Weapon> ranged = weapons.stream().filter(w -> w.ranged() && w.hasAmmo())
                    .max(Comparator.comparingDouble(Weapon::dps));
            if (ranged.isPresent()) {
                return ranged;
            }
        }
        return weapons.stream().filter(w -> !w.ranged()).max(Comparator.comparingDouble(Weapon::dps));
    }

    /**
     * Whether Cogwit should put a weapon in your hand. Something you chose stays (a staff, a shield, blocks, food,
     * a bow); a pickaxe gives way only with the threat at arm's length; a weapon only to a much better one.
     */
    public static boolean shouldArm(Held held, double heldDps, double bestDps, double distance) {
        return switch (held) {
            case EMPTY -> true;
            case TOOL -> distance <= MELEE_REACH;
            case WEAPON -> bestDps >= heldDps * UPGRADE;
            case OTHER -> false;
        };
    }

    /** A fall worth saving: hurts, and the ground is close enough to act on now. */
    public static boolean fallNeedsSaving(float fallDistance, double groundBelow, float expectedDamage) {
        return fallDistance > 3 && groundBelow <= 3.5 && expectedDamage >= 3;
    }

    /** Vanilla fall damage before armor and enchantments: one point per block after the first three. */
    public static float fallDamage(float fallDistance, double groundBelow) {
        return Math.max(0, (float) Math.ceil(fallDistance + groundBelow - 3));
    }

    public static boolean armorWornOut(int maxDamage, int damage) {
        return maxDamage > 0 && maxDamage - damage <= ARMOR_SAVE_DURABILITY;
    }

    /** Unit direction to throw a pearl: away from the threat, upwards, so it lands some distance off. */
    public static double[] escapeDirection(double fromX, double fromZ, double threatX, double threatZ) {
        double dx = fromX - threatX, dz = fromZ - threatZ;
        double len = Math.hypot(dx, dz);
        if (len < 1e-6) {
            dx = 1;
            dz = 0;
            len = 1;
        }
        double up = 0.55;
        double flat = Math.sqrt(1 - up * up);
        return new double[] {dx / len * flat, up, dz / len * flat};
    }

    /** True when an item id is on the magnet's ignore list (exact id, or a "namespace:*" wildcard). */
    public static boolean ignoredByMagnet(String id, List<String> ignore) {
        for (String rule : ignore) {
            if (rule.equals(id) || rule.endsWith(":*") && id.startsWith(rule.substring(0, rule.length() - 1))) {
                return true;
            }
        }
        return false;
    }
}
