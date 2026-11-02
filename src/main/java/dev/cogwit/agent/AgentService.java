package dev.cogwit.agent;

import dev.cogwit.CogwitComponents;
import dev.cogwit.Speech;
import dev.cogwit.brain.Brain;
import dev.cogwit.brain.BrainState;
import dev.cogwit.cells.CellBank;
import dev.cogwit.growth.GrowthContent;
import dev.cogwit.horizon.HorizonActions;
import dev.cogwit.horizon.Horizon;
import dev.cogwit.horizon.HorizonContent;
import dev.cogwit.horizon.HorizonState;
import dev.cogwit.item.CogwitTalismanItem;
import dev.cogwit.network.AgentSettingsPayload;
import dev.cogwit.network.AskPayload;
import dev.cogwit.trophy.Trophy;
import dev.cogwit.trophy.TrophyContent;
import dev.cogwit.trophy.TrophyService;
import dev.cogwit.ultra.UltraService;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetCarriedItemPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.level.Level;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ThrownEnderpearl;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.DiggerItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.MaceItem;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.network.PacketDistributor;

import top.theillusivec4.curios.api.CuriosApi;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/** Server side of the agent: decides, asks, acts, and writes it all into the journal. */
public final class AgentService {
    /** One concrete thing to do right now, described for the question or the journal. */
    public record Step(Component description, Runnable run) {}

    private record Pending(Agent.Action action, long expires) {}

    /** Everything the agent remembers about a player between ticks. Not saved: it is only short-term memory. */
    private static final class Memory {
        long lastFight;
        final Map<Agent.Action, Long> cooldownUntil = new EnumMap<>(Agent.Action.class);
        final Map<Agent.Action, Long> allowedUntil = new EnumMap<>(Agent.Action.class);
        final Map<Agent.Action, Long> askedAt = new EnumMap<>(Agent.Action.class);
        final Map<Integer, Pending> pending = new HashMap<>();
        final ItemStack[] hotbar = new ItemStack[9];
        final int[] hotbarTotal = new int[9];
        int armedSlot = -1;
        boolean weaponOverruled;
        boolean fallHandled;
        BlockPos placedForFall;
        Item placedItem;
        long removeFallBlockAt;
        final StyleProfile style = new StyleProfile();
        StyleProfile.Style announcedStyle = StyleProfile.Style.MIXED;
        long comboUntil;
        Component comboReason;
    }

    private static final Map<UUID, Memory> MEMORY = new ConcurrentHashMap<>();
    private static final AtomicInteger NEXT_ASK = new AtomicInteger(1);
    /** An approved question lets Cogwit do the same without asking for two minutes. */
    private static final long APPROVAL_TICKS = 2400;
    /** Items the magnet leaves on the ground. */
    public static final List<String> MAGNET_IGNORE = List.of("minecraft:rotten_flesh", "minecraft:poisonous_potato",
            "minecraft:cobblestone", "minecraft:cobbled_deepslate", "minecraft:netherrack", "minecraft:dirt",
            "minecraft:gravel", "minecraft:granite", "minecraft:diorite", "minecraft:andesite", "minecraft:tuff",
            "minecraft:sand", "minecraft:wheat_seeds");

    private AgentService() {}

    private static Memory memory(Player player) {
        return MEMORY.computeIfAbsent(player.getUUID(), id -> new Memory());
    }

    /** The id of the newest open question for this player, or -1 (for tests and the screen). */
    public static int lastAsk(Player player) {
        return memory(player).pending.keySet().stream().mapToInt(Integer::intValue).max().orElse(-1);
    }

    public static void forget(Player player) {
        MEMORY.remove(player.getUUID());
    }

    public static AgentState state(ItemStack talisman) {
        return talisman.getOrDefault(CogwitComponents.AGENT.get(), AgentState.DEFAULT);
    }

    public static void setState(ItemStack talisman, AgentState state) {
        talisman.set(CogwitComponents.AGENT.get(), state);
    }

    public static boolean inCombat(Player player) {
        return Agent.inCombat(memory(player).lastFight, player.level().getGameTime());
    }

    /** Any hit given or taken by the player keeps the "in a fight" clock running. */
    public static void noteFight(Player player) {
        memory(player).lastFight = player.level().getGameTime();
    }

    // ---------------------------------------------------------------- decide, ask, act

    /**
     * Runs the step if Cogwit may, asks if he must, or does nothing. Returns true when the step ran now.
     */
    public static boolean attempt(Player player, Agent.Action action, Optional<Step> plan) {
        if (plan.isEmpty()) {
            return false;
        }
        Optional<ItemStack> worn = CogwitTalismanItem.worn(player);
        if (worn.isEmpty()) {
            return false;
        }
        ItemStack talisman = worn.get();
        long now = player.level().getGameTime();
        Memory m = memory(player);
        if (now < m.cooldownUntil.getOrDefault(action, 0L)) {
            return false;
        }
        BrainState brain = CogwitTalismanItem.brain(talisman);
        if (Brain.isAsleep(brain, now)) {
            return false;
        }
        Agent.Decision decision = Agent.decide(state(talisman).permission(action), action, inCombat(player));
        if (decision == Agent.Decision.ASK && now < m.allowedUntil.getOrDefault(action, 0L)) {
            decision = Agent.Decision.ACT;
        }
        switch (decision) {
            case SKIP -> {
                return false;
            }
            case ASK -> {
                ask(player, m, action, plan.get(), now);
                return false;
            }
            default -> {
                return act(player, talisman, action, plan.get());
            }
        }
    }

    private static boolean act(Player player, ItemStack talisman, Agent.Action action, Step step) {
        CellBank.topUp(player, talisman);
        BrainState brain = CogwitTalismanItem.brain(talisman);
        if (brain.tokens() < action.tokenCost) {
            return false;
        }
        CogwitTalismanItem.setBrain(talisman, brain.withTokens(brain.tokens() - action.tokenCost));
        step.run().run();
        Speech.action(player, step.description());
        return true;
    }

    private static void ask(Player player, Memory m, Agent.Action action, Step step, long now) {
        if (now - m.askedAt.getOrDefault(action, -Agent.ASK_COOLDOWN_TICKS) < Agent.ASK_COOLDOWN_TICKS
                || m.pending.values().stream().anyMatch(p -> p.action() == action && p.expires() > now)) {
            return;
        }
        m.askedAt.put(action, now);
        int id = NEXT_ASK.getAndIncrement();
        m.pending.put(id, new Pending(action, now + Agent.ASK_TIMEOUT_TICKS));
        Component question = Component.translatable("cogwit.agent.ask", step.description());
        if (player instanceof ServerPlayer serverPlayer) {
            PacketDistributor.sendToPlayer(serverPlayer, new AskPayload(id, question));
        }
        dev.cogwit.speech.CogwitVoice.beep(player, dev.cogwit.speech.CogwitVoice.Beep.HELLO);
    }

    /** Y pressed: do what was asked (re-planned now, since things may have changed), maybe for good. */
    public static void answer(Player player, int id, boolean always) {
        Memory m = memory(player);
        Pending pending = m.pending.remove(id);
        long now = player.level().getGameTime();
        if (pending == null || pending.expires() < now) {
            return;
        }
        Optional<ItemStack> worn = CogwitTalismanItem.worn(player);
        if (worn.isEmpty()) {
            return;
        }
        if (always) {
            setState(worn.get(), state(worn.get()).with(pending.action(), Agent.Permission.ALWAYS));
        } else {
            m.allowedUntil.put(pending.action(), now + APPROVAL_TICKS);
        }
        plan(player, pending.action()).ifPresent(step -> act(player, worn.get(), pending.action(), step));
    }

    public static void applySettings(Player player, AgentSettingsPayload payload) {
        Optional<ItemStack> worn = CogwitTalismanItem.worn(player);
        if (worn.isEmpty()) {
            return;
        }
        if (payload.openVault()) {
            openVault(player, worn.get());
            return;
        }
        AgentState state = state(worn.get());
        if (!payload.mode().isEmpty()) {
            try {
                state = state.withMode(Agent.Mode.valueOf(payload.mode().toUpperCase(java.util.Locale.ROOT)));
            } catch (IllegalArgumentException ignored) {
                return;
            }
        }
        Map<Agent.Action, Agent.Permission> overrides = new EnumMap<>(Agent.Action.class);
        for (String pair : payload.overrides()) {
            String[] kv = pair.split("=", 2);
            try {
                overrides.put(Agent.Action.valueOf(kv[0].toUpperCase(java.util.Locale.ROOT)),
                        Agent.Permission.valueOf(kv[1].toUpperCase(java.util.Locale.ROOT)));
            } catch (RuntimeException ignored) {
                // a malformed pair from the screen: skip it
            }
        }
        setState(worn.get(), new AgentState(state.mode(), overrides));
    }

    /** The current plan for an action, used again when an answer comes back. */
    public static Optional<Step> plan(Player player, Agent.Action action) {
        return switch (action) {
            case EAT -> planEat(player);
            case PEARL -> planPearl(player);
            case ARMOR_SAVE -> planArmorSave(player);
            case WEAPON -> planWeapon(player);
            case GAPPLE -> planGapple(player);
            case MAGNET -> planMagnet(player);
            case ARMOR_SKILLS -> planArmorSkills(player);
            case TROPHIES -> planTrophies(player);
            case FALL_SAVE, REFILL, REPLANT -> Optional.empty(); // only make sense the moment they happen
        };
    }

    // ---------------------------------------------------------------- the tick

    /** Called every tick for players wearing Cogwit. Each action checks on its own rhythm. */
    public static void tick(Player player) {
        long now = player.level().getGameTime();
        Memory m = memory(player);
        m.pending.values().removeIf(p -> p.expires() < now);
        tickFall(player, m, now);
        tickRefill(player, m);
        tickWeaponChoice(player, m);
        attempt(player, Agent.Action.ARMOR_SAVE, planToolSave(player));
        if (now < m.comboUntil && now % 2 == 0) {
            Optional<Step> finisher = planTrophies(player, true);
            if (attempt(player, Agent.Action.TROPHIES, finisher)) {
                m.cooldownUntil.put(Agent.Action.TROPHIES, now + 40);
                Speech.think(player, Component.translatable("cogwit.think.combo", m.comboReason,
                        finisher.get().description()));
                m.comboUntil = 0;
            }
        }
        if (now % 20 == 0) {
            attempt(player, Agent.Action.EAT, planEat(player));
            attempt(player, Agent.Action.ARMOR_SAVE, planArmorSave(player));
        }
        if (now % 5 == 0 && inCombat(player)) {
            if (attempt(player, Agent.Action.PEARL, planPearl(player))) {
                m.cooldownUntil.put(Agent.Action.PEARL, now + Agent.PEARL_COOLDOWN_TICKS);
            }
            if (attempt(player, Agent.Action.GAPPLE, planGapple(player))) {
                m.cooldownUntil.put(Agent.Action.GAPPLE, now + Agent.GAPPLE_COOLDOWN_TICKS);
            }
        }
        if (now % 10 == 0 && inCombat(player)) {
            if (attempt(player, Agent.Action.WEAPON, planWeapon(player))) {
                m.cooldownUntil.put(Agent.Action.WEAPON, now + 20);
            }
            if (attempt(player, Agent.Action.ARMOR_SKILLS, planArmorSkills(player))) {
                m.cooldownUntil.put(Agent.Action.ARMOR_SKILLS, now + 40);
            } else if (attempt(player, Agent.Action.TROPHIES, planTrophies(player))) {
                m.cooldownUntil.put(Agent.Action.TROPHIES, now + 40);
            }
        }
        if (now % 40 == 0) {
            // the magnet is paid once every two seconds while it is pulling, and pulls between payments
            if (planMagnet(player).isPresent() && attempt(player, Agent.Action.MAGNET, planMagnet(player))) {
                m.allowedUntil.put(Agent.Action.MAGNET, Math.max(m.allowedUntil.getOrDefault(Agent.Action.MAGNET, 0L),
                        now + 40));
            }
        } else if (now % 2 == 0 && now < m.allowedUntil.getOrDefault(Agent.Action.MAGNET, 0L)
                && CogwitTalismanItem.worn(player).map(t -> state(t).permission(Agent.Action.MAGNET)
                != Agent.Permission.NEVER).orElse(false)) {
            pull(player);
        }
    }

    // ---------------------------------------------------------------- eating

    static Optional<Step> planEat(Player player) {
        int food = player.getFoodData().getFoodLevel();
        if (!Agent.wantsFood(food, inCombat(player))) {
            return Optional.empty();
        }
        Inventory inv = player.getInventory();
        List<Agent.Food> foods = new ArrayList<>();
        for (int slot = 0; slot < inv.items.size(); slot++) {
            ItemStack stack = inv.items.get(slot);
            FoodProperties props = stack.get(DataComponents.FOOD);
            if (props == null) {
                continue;
            }
            boolean harmful = props.effects().stream()
                    .anyMatch(e -> e.effect().getEffect().value().getCategory() == MobEffectCategory.HARMFUL)
                    || stack.is(Items.SUSPICIOUS_STEW);
            boolean precious = stack.is(Items.GOLDEN_APPLE) || stack.is(Items.ENCHANTED_GOLDEN_APPLE)
                    || stack.is(Items.CHORUS_FRUIT) || stack.is(GrowthContent.COPPER_APPLE.get())
                    || stack.is(Items.GOLDEN_CARROT) && food > 6;
            foods.add(new Agent.Food(slot, props.nutrition(), props.saturation(), harmful, precious));
        }
        return Agent.pickFood(foods, food).map(f -> {
            ItemStack stack = inv.items.get(f.slot());
            return new Step(Component.translatable("cogwit.agent.did.eat", stack.getHoverName()),
                    () -> eat(player, f.slot()));
        });
    }

    /** Eats one item from a slot as if the player had, returning a bowl or bottle if the food leaves one. */
    static void eat(Player player, int slot) {
        ItemStack stack = player.getInventory().items.get(slot);
        if (stack.isEmpty() || stack.get(DataComponents.FOOD) == null) {
            return;
        }
        ItemStack left = stack.getItem().finishUsingItem(stack, player.level(), player);
        if (left != stack && !left.isEmpty()) {
            if (stack.isEmpty()) {
                player.getInventory().items.set(slot, left);
            } else if (!player.getInventory().add(left)) {
                player.drop(left, false);
            }
        }
    }

    static Optional<Step> planGapple(Player player) {
        boolean bossFight = CogwitTalismanItem.worn(player).map(UltraService::isActive).orElse(false);
        if (!bossFight || player.getHealth() >= player.getMaxHealth() * Agent.GAPPLE_BELOW) {
            return Optional.empty();
        }
        Inventory inv = player.getInventory();
        for (Item apple : List.of(Items.ENCHANTED_GOLDEN_APPLE, Items.GOLDEN_APPLE)) {
            for (int slot = 0; slot < inv.items.size(); slot++) {
                if (inv.items.get(slot).is(apple)) {
                    int s = slot;
                    return Optional.of(new Step(Component.translatable("cogwit.agent.did.gapple",
                            inv.items.get(slot).getHoverName()), () -> eat(player, s)));
                }
            }
        }
        return Optional.empty();
    }

    // ---------------------------------------------------------------- falling

    private static void tickFall(Player player, Memory m, long now) {
        if (m.placedForFall != null && now >= m.removeFallBlockAt && player.onGround()) {
            cleanUpFall(player, m);
        }
        if (player.onGround() || player.isInWater() || player.getAbilities().flying || player.isFallFlying()) {
            m.fallHandled = false;
            return;
        }
        if (m.fallHandled || player.getDeltaMovement().y >= 0 || HorizonContent.has(player, Horizon.Feature.LANDING)) {
            return;
        }
        Vec3 feet = player.position();
        BlockHitResult hit = player.level().clip(new ClipContext(feet, feet.subtract(0, 4, 0), ClipContext.Block.COLLIDER,
                ClipContext.Fluid.ANY, player));
        if (hit.getType() != HitResult.Type.BLOCK) {
            return;
        }
        double below = feet.y - hit.getLocation().y;
        float damage = Agent.fallDamage(player.fallDistance, below);
        if (!Agent.fallNeedsSaving(player.fallDistance, below, damage)) {
            return;
        }
        BlockPos landing = hit.getBlockPos().above();
        if (!player.level().getBlockState(landing).canBeReplaced()) {
            return;
        }
        Optional<Step> step = planFallSave(player, m, landing);
        if (step.isPresent()) {
            m.fallHandled = true;
            attempt(player, Agent.Action.FALL_SAVE, step);
        }
    }

    private static Optional<Step> planFallSave(Player player, Memory m, BlockPos landing) {
        Inventory inv = player.getInventory();
        boolean waterWorks = !player.level().dimensionType().ultraWarm();
        for (Item saver : List.of(Items.WATER_BUCKET, Items.SLIME_BLOCK, Items.HAY_BLOCK)) {
            if (saver == Items.WATER_BUCKET && !waterWorks) {
                continue;
            }
            for (int slot = 0; slot < inv.items.size(); slot++) {
                if (!inv.items.get(slot).is(saver)) {
                    continue;
                }
                int s = slot;
                BlockState block = saver == Items.WATER_BUCKET ? Blocks.WATER.defaultBlockState()
                        : saver == Items.SLIME_BLOCK ? Blocks.SLIME_BLOCK.defaultBlockState()
                        : Blocks.HAY_BLOCK.defaultBlockState();
                BlockPos at = saver == Items.WATER_BUCKET ? landing : landing.below();
                return Optional.of(new Step(Component.translatable("cogwit.agent.did.fall_save",
                        new ItemStack(saver).getHoverName()), () -> {
                    if (saver == Items.WATER_BUCKET) {
                        player.level().setBlockAndUpdate(at, block);
                        inv.items.set(s, new ItemStack(Items.BUCKET));
                        player.level().playSound(null, at, SoundEvents.BUCKET_EMPTY, SoundSource.PLAYERS, 1f, 1f);
                    } else {
                        // the soft block replaces the ground right under the landing spot
                        BlockState was = player.level().getBlockState(at);
                        if (was.getDestroySpeed(player.level(), at) < 0) {
                            return;
                        }
                        player.level().setBlockAndUpdate(at, block);
                        inv.items.get(s).shrink(1);
                        m.placedItem = was.getBlock().asItem();
                    }
                    m.placedForFall = at;
                    m.removeFallBlockAt = player.level().getGameTime() + 10;
                    if (saver == Items.WATER_BUCKET) {
                        m.placedItem = Items.WATER_BUCKET;
                    }
                }));
            }
        }
        return Optional.empty();
    }

    /** After landing: scoop the water back up, or swap the soft block back for the ground it replaced. */
    private static void cleanUpFall(Player player, Memory m) {
        BlockPos at = m.placedForFall;
        BlockState state = player.level().getBlockState(at);
        Inventory inv = player.getInventory();
        if (m.placedItem == Items.WATER_BUCKET) {
            if (state.is(Blocks.WATER)) {
                player.level().setBlockAndUpdate(at, Blocks.AIR.defaultBlockState());
                for (int slot = 0; slot < inv.items.size(); slot++) {
                    if (inv.items.get(slot).is(Items.BUCKET)) {
                        inv.items.get(slot).shrink(1);
                        if (!inv.add(new ItemStack(Items.WATER_BUCKET))) {
                            player.drop(new ItemStack(Items.WATER_BUCKET), false);
                        }
                        break;
                    }
                }
            }
        } else if (state.is(Blocks.SLIME_BLOCK) || state.is(Blocks.HAY_BLOCK)) {
            Item back = state.getBlock().asItem();
            Item ground = m.placedItem;
            player.level().setBlockAndUpdate(at, ground instanceof net.minecraft.world.item.BlockItem bi
                    ? bi.getBlock().defaultBlockState() : Blocks.DIRT.defaultBlockState());
            if (!inv.add(new ItemStack(back))) {
                player.drop(new ItemStack(back), false);
            }
        }
        m.placedForFall = null;
        m.placedItem = null;
    }

    // ---------------------------------------------------------------- pearl and armor skills

    private static Optional<LivingEntity> nearestThreat(Player player, double range) {
        return player.level().getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(range),
                        e -> e.isAlive() && (e instanceof Enemy || e instanceof Mob mob && mob.getTarget() == player))
                .stream().min(Comparator.comparingDouble(player::distanceToSqr));
    }

    static Optional<Step> planPearl(Player player) {
        if (player.getHealth() >= player.getMaxHealth() * Agent.CRITICAL
                || player.getCooldowns().isOnCooldown(Items.ENDER_PEARL)) {
            return Optional.empty();
        }
        Optional<LivingEntity> threat = nearestThreat(player, 10);
        Inventory inv = player.getInventory();
        for (int slot = 0; slot < inv.items.size(); slot++) {
            if (inv.items.get(slot).is(Items.ENDER_PEARL) && threat.isPresent()) {
                int s = slot;
                LivingEntity from = threat.get();
                return Optional.of(new Step(Component.translatable("cogwit.agent.did.pearl"), () -> {
                    int heading = safestHeading(player, 10);
                    double[] dir;
                    if (heading >= 0) {
                        double[] h = Escape.heading(heading);
                        double flat = Math.sqrt(1 - 0.55 * 0.55);
                        dir = new double[] {h[0] * flat, 0.55, h[1] * flat};
                    } else {
                        dir = Agent.escapeDirection(player.getX(), player.getZ(), from.getX(), from.getZ());
                    }
                    ThrownEnderpearl pearl = new ThrownEnderpearl(player.level(), player);
                    pearl.setItem(new ItemStack(Items.ENDER_PEARL));
                    pearl.shoot(dir[0], dir[1], dir[2], 1.4f, 0f);
                    player.level().addFreshEntity(pearl);
                    inv.items.get(s).shrink(1);
                    player.getCooldowns().addCooldown(Items.ENDER_PEARL, 20);
                    player.level().playSound(null, player.blockPosition(), SoundEvents.ENDER_PEARL_THROW,
                            SoundSource.PLAYERS, 0.6f, 0.6f);
                }));
            }
        }
        return Optional.empty();
    }

    // ---------------------------------------------------------------- escape, combos, style

    private static List<LivingEntity> threats(Player player, double range) {
        return player.level().getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(range),
                e -> e.isAlive() && (e instanceof Enemy || e instanceof Mob mob && mob.getTarget() == player));
    }

    /** Whether there is somewhere to land about eight blocks along heading {@code k}: no wall right ahead, no lava, no drop. */
    static boolean groundOk(Player player, int k) {
        Level level = player.level();
        double[] h = Escape.heading(k);
        BlockPos feet = player.blockPosition();
        for (int d : new int[] {2, 4}) {
            BlockPos step = BlockPos.containing(player.getX() + h[0] * d, player.getY() + 0.1, player.getZ() + h[1] * d);
            if (!level.getBlockState(step).getCollisionShape(level, step).isEmpty()
                    && !level.getBlockState(step.above()).getCollisionShape(level, step.above()).isEmpty()) {
                return false;
            }
        }
        BlockPos far = BlockPos.containing(player.getX() + h[0] * 8, feet.getY() + 4, player.getZ() + h[1] * 8);
        for (int down = 0; down < 14; down++) {
            BlockPos at = far.below(down);
            if (level.getFluidState(at).is(net.minecraft.tags.FluidTags.LAVA)) {
                return false;
            }
            if (level.getFluidState(at).is(net.minecraft.tags.FluidTags.WATER)) {
                return true;
            }
            if (!level.getBlockState(at).getCollisionShape(level, at).isEmpty()) {
                return !level.getBlockState(at).is(net.minecraft.world.level.block.Blocks.MAGMA_BLOCK)
                        && level.getBlockState(at.above()).getCollisionShape(level, at.above()).isEmpty();
            }
        }
        return false;
    }

    /** The heading (see {@link Escape}) that leads away from the threats within {@code range}, or -1. */
    public static int safestHeading(Player player, double range) {
        List<double[]> offsets = new ArrayList<>();
        for (LivingEntity t : threats(player, range)) {
            offsets.add(new double[] {t.getX() - player.getX(), t.getZ() - player.getZ()});
        }
        boolean[] ok = new boolean[Escape.HEADINGS];
        for (int k = 0; k < Escape.HEADINGS; k++) {
            ok[k] = groundOk(player, k);
        }
        return Escape.safest(offsets, ok);
    }

    public static int threatCount(Player player, double range) {
        return threats(player, range).size();
    }

    /**
     * Something just set the enemies up (the well lifted them, a landing or a dash slammed into them): for the next
     * moment Cogwit follows up with his hardest trophy active, if he is allowed to use them.
     */
    public static void openCombo(Player player, Component reason) {
        if (player.level().isClientSide || CogwitTalismanItem.worn(player).isEmpty()) {
            return;
        }
        Memory m = memory(player);
        m.comboUntil = player.level().getGameTime() + Tactics.COMBO_TICKS;
        m.comboReason = reason;
    }

    /** What kind of hit this was for the style profile: a swing, a shot, a spell, or none of these. */
    public static StyleProfile.Kind kindOf(net.minecraft.world.damagesource.DamageSource source, Player attacker) {
        if (source.is(DamageTypes.PLAYER_ATTACK) && source.getDirectEntity() == attacker) {
            return StyleProfile.Kind.MELEE;
        }
        if (source.getDirectEntity() instanceof net.minecraft.world.entity.projectile.Projectile
                && !(source.getDirectEntity() instanceof net.minecraft.world.entity.projectile.AbstractHurtingProjectile)) {
            return StyleProfile.Kind.RANGED;
        }
        String ns = source.typeHolder().unwrapKey().map(k -> k.location().getNamespace()).orElse("minecraft");
        if (source.is(DamageTypes.MAGIC) || source.is(DamageTypes.INDIRECT_MAGIC) || ns.equals("irons_spellbooks")) {
            return StyleProfile.Kind.MAGIC;
        }
        return null;
    }

    /** Adds a hit to the profile; when the style becomes clear or changes, Cogwit says how he will adapt. */
    public static void recordStyle(Player player, StyleProfile.Kind kind, float damage) {
        Memory m = memory(player);
        long now = player.level().getGameTime();
        m.style.record(kind, damage, now);
        StyleProfile.Style style = m.style.style(now);
        if (style != m.announcedStyle) {
            m.announcedStyle = style;
            if (style != StyleProfile.Style.MIXED && CogwitTalismanItem.worn(player).isPresent()) {
                Speech.think(player, Component.translatable("cogwit.think.style." + style.key()));
            }
        }
    }

    public static StyleProfile.Style style(Player player) {
        return memory(player).style.style(player.level().getGameTime());
    }

    /** Horizon abilities: a gravity well into a crowd, or a blink away when cornered and out of pearls. */
    static Optional<Step> planArmorSkills(Player player) {
        int pieces = HorizonContent.pieces(player);
        long now = player.level().getGameTime();
        HorizonState hs = HorizonContent.state(player);
        List<LivingEntity> crowd = player.level().getEntitiesOfClass(LivingEntity.class,
                player.getBoundingBox().inflate(8), e -> e.isAlive() && e instanceof Enemy);
        if (Horizon.has(pieces, Horizon.Feature.CORE) && crowd.size() >= 3 && now >= hs.wellReadyAt()) {
            return Optional.of(new Step(Component.translatable("cogwit.agent.did.well", crowd.size()),
                    () -> HorizonActions.gravity(player)));
        }
        boolean cornered = player.getHealth() < player.getMaxHealth() * Agent.CRITICAL;
        boolean noPearl = player.getInventory().items.stream().noneMatch(s -> s.is(Items.ENDER_PEARL));
        Optional<LivingEntity> threat = nearestThreat(player, 6);
        if (Horizon.has(pieces, Horizon.Feature.THRUSTERS) && cornered && noPearl && now >= hs.blinkReadyAt()
                && threat.isPresent()) {
            Vec3 look = player.getLookAngle();
            LivingEntity t = threat.get();
            if (Brain.isBehind(look.x, look.z, t.getX() - player.getX(), t.getZ() - player.getZ())) {
                return Optional.of(new Step(Component.translatable("cogwit.agent.did.blink"),
                        () -> HorizonActions.blink(player)));
            }
        }
        return Optional.empty();
    }

    // ---------------------------------------------------------------- trophy actives

    /** What you are fighting: the mob you last hit, else the one that last hit you, else the nearest threat. */
    private static LivingEntity fightTarget(Player player, double range) {
        LivingEntity hit = player.getLastHurtMob();
        if (hit != null && hit.isAlive() && player.tickCount - player.getLastHurtMobTimestamp() <= Agent.COMBAT_TICKS
                && player.distanceTo(hit) <= range) {
            return hit;
        }
        LivingEntity by = player.getLastHurtByMob();
        if (by != null && by.isAlive() && player.tickCount - player.getLastHurtByMobTimestamp() <= Agent.COMBAT_TICKS
                && player.distanceTo(by) <= range && !(by instanceof Player)) {
            return by;
        }
        return nearestThreat(player, range).orElse(null);
    }

    static boolean isBoss(LivingEntity entity) {
        return entity.getType().is(Tags.EntityTypes.BOSSES) || entity.getMaxHealth() >= 150
                || Trophy.byEntity(BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString()).isPresent();
    }

    /** A trophy active that lands from here, when the fight is worth a cooldown (see {@link Tactics}). */
    static Optional<Step> planTrophies(Player player) {
        return planTrophies(player, false);
    }

    static Optional<Step> planTrophies(Player player, boolean combo) {
        if (!HorizonContent.has(player, Horizon.Feature.CORE)) {
            return Optional.empty();
        }
        LivingEntity target = fightTarget(player, 24);
        if (target == null) {
            return Optional.empty();
        }
        long now = player.level().getGameTime();
        TrophyContent.Data data = player.getData(TrophyContent.DATA);
        List<Tactics.Move> ready = new ArrayList<>();
        for (Trophy t : Trophy.values()) {
            Tactics.Move move = Tactics.MOVES.get(t.key());
            if (move != null && t.hasActive && t.implemented && data.owned().contains(t.key())
                    && now >= data.readyAt().getOrDefault(t.key(), 0L)) {
                ready.add(move);
            }
        }
        if (ready.isEmpty()) {
            return Optional.empty();
        }
        int crowd = player.level().getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(6),
                e -> e.isAlive() && (e instanceof Enemy || e instanceof Mob mob && mob.getTarget() == player)).size();
        Vec3 to = target.getBoundingBox().getCenter().subtract(player.getEyePosition()).normalize();
        double angle = Math.toDegrees(Math.acos(Math.max(-1, Math.min(1, to.dot(player.getLookAngle())))));
        Tactics.Fight fight = new Tactics.Fight(crowd, isBoss(target), player.distanceTo(target), angle,
                player.getHealth() / player.getMaxHealth(), target.getHealth());
        StyleProfile.Style style = memory(player).style.style(now);
        return Tactics.choose(fight, ready, style, combo).flatMap(move -> Trophy.byKey(move.id())).map(t -> new Step(
                Component.translatable("cogwit.agent.did.trophy",
                        Component.translatable("cogwit.trophy." + t.key() + ".name")),
                () -> TrophyService.castTrophy(player, t)));
    }

    // ---------------------------------------------------------------- armor and vault

    static Optional<Step> planArmorSave(Player player) {
        for (EquipmentSlot slot : new EquipmentSlot[] {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS,
                EquipmentSlot.FEET}) {
            ItemStack armor = player.getItemBySlot(slot);
            if (!armor.isEmpty() && armor.isDamageableItem()
                    && Agent.armorWornOut(armor.getMaxDamage(), armor.getDamageValue())) {
                return Optional.of(new Step(Component.translatable("cogwit.agent.did.armor_save", armor.getHoverName()),
                        () -> takeOff(player, slot)));
            }
        }
        return Optional.empty();
    }

    /**
     * An enchanted tool two uses from breaking leaves your hand for the inventory (or the vault), so the next block
     * doesn't destroy it. Plain tools are left alone: they're cheap to replace.
     */
    static Optional<Step> planToolSave(Player player) {
        ItemStack hand = player.getMainHandItem();
        if (!hand.isDamageableItem() || !hand.isEnchanted()
                || hand.getMaxDamage() - hand.getDamageValue() > dev.cogwit.mind.Mind.TOOL_SAVE) {
            return Optional.empty();
        }
        return Optional.of(new Step(Component.translatable("cogwit.agent.did.tool_save", hand.getHoverName(),
                hand.getMaxDamage() - hand.getDamageValue()), () -> putAway(player)));
    }

    private static void putAway(Player player) {
        Inventory inv = player.getInventory();
        ItemStack tool = inv.getSelected();
        for (int slot = Inventory.getSelectionSize(); slot < inv.items.size(); slot++) {
            if (inv.items.get(slot).isEmpty()) {
                inv.items.set(slot, tool);
                inv.items.set(inv.selected, ItemStack.EMPTY);
                return;
            }
        }
        ItemStack talisman = CogwitTalismanItem.worn(player).orElse(null);
        if (talisman != null && putInVault(talisman, tool.copy())) {
            inv.items.set(inv.selected, ItemStack.EMPTY);
            Speech.say(player, Component.translatable("cogwit.agent.vaulted", tool.getHoverName()));
        }
    }

    private static void takeOff(Player player, EquipmentSlot slot) {
        ItemStack armor = player.getItemBySlot(slot).copy();
        player.setItemSlot(slot, ItemStack.EMPTY);
        if (player.getInventory().add(armor)) {
            return;
        }
        ItemStack talisman = CogwitTalismanItem.worn(player).orElse(null);
        if (talisman != null && putInVault(talisman, armor)) {
            Speech.say(player, Component.translatable("cogwit.agent.vaulted", armor.getHoverName()));
        } else {
            player.drop(armor, false);
        }
    }

    public static NonNullList<ItemStack> vault(ItemStack talisman) {
        NonNullList<ItemStack> items = NonNullList.withSize(9, ItemStack.EMPTY);
        talisman.getOrDefault(CogwitComponents.VAULT.get(), ItemContainerContents.EMPTY).copyInto(items);
        return items;
    }

    public static boolean putInVault(ItemStack talisman, ItemStack stack) {
        NonNullList<ItemStack> items = vault(talisman);
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).isEmpty()) {
                items.set(i, stack.copy());
                talisman.set(CogwitComponents.VAULT.get(), ItemContainerContents.fromItems(items));
                return true;
            }
        }
        return false;
    }

    private static void openVault(Player player, ItemStack talisman) {
        SimpleContainer container = new SimpleContainer(9) {
            @Override
            public void setChanged() {
                super.setChanged();
                talisman.set(CogwitComponents.VAULT.get(), ItemContainerContents.fromItems(getItems()));
            }
        };
        NonNullList<ItemStack> items = vault(talisman);
        for (int i = 0; i < items.size(); i++) {
            container.setItem(i, items.get(i));
        }
        player.openMenu(new SimpleMenuProvider((id, inv, p) -> new ChestMenu(MenuType.GENERIC_9x1, id, inv, container, 1),
                Component.translatable("cogwit.agent.vault_title")));
    }

    // ---------------------------------------------------------------- weapons

    /** Sum of an item's flat main-hand modifiers to one attribute, including those mods add in code. */
    private static double mainHand(ItemStack stack, net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attribute) {
        double sum = 0;
        for (ItemAttributeModifiers.Entry e : stack.getAttributeModifiers().modifiers()) {
            if (e.attribute().is(attribute) && e.slot().test(EquipmentSlot.MAINHAND)
                    && e.modifier().operation() == AttributeModifier.Operation.ADD_VALUE) {
                sum += e.modifier().amount();
            }
        }
        return sum;
    }

    static double attackDamage(ItemStack stack) {
        return 1 + mainHand(stack, Attributes.ATTACK_DAMAGE);
    }

    /**
     * Damage per second against this target: hit damage with enchantments (Sharpness, Smite), less what the target's
     * armor soaks, times attack speed.
     */
    static double dps(Player player, ItemStack stack, LivingEntity target) {
        float damage = (float) attackDamage(stack);
        if (player.level() instanceof ServerLevel level) {
            damage = EnchantmentHelper.modifyDamage(level, stack, target, player.damageSources().playerAttack(player), damage);
        }
        double speed = Math.max(0.25, 4 + mainHand(stack, Attributes.ATTACK_SPEED));
        return Agent.throughArmor(damage, target.getArmorValue(), target.getAttributeValue(Attributes.ARMOR_TOUGHNESS))
                * speed;
    }

    static boolean isRanged(ItemStack stack) {
        return stack.getItem() instanceof BowItem || stack.getItem() instanceof CrossbowItem;
    }

    static boolean isMeleeWeapon(ItemStack stack) {
        Item item = stack.getItem();
        return item instanceof SwordItem || item instanceof AxeItem || item instanceof TridentItem
                || item instanceof MaceItem || stack.is(ItemTags.SWORDS) || stack.is(ItemTags.AXES)
                || stack.is(Tags.Items.MELEE_WEAPON_TOOLS);
    }

    static Agent.Held held(ItemStack stack) {
        if (stack.isEmpty()) {
            return Agent.Held.EMPTY;
        }
        if (isMeleeWeapon(stack)) {
            return Agent.Held.WEAPON;
        }
        return stack.getItem() instanceof DiggerItem ? Agent.Held.TOOL : Agent.Held.OTHER;
    }

    static Optional<Step> planWeapon(Player player) {
        Memory m = memory(player);
        if (m.weaponOverruled || player.isUsingItem()) {
            return Optional.empty(); // you put something else back in hand, or you are drawing, eating, blocking
        }
        Inventory inv = player.getInventory();
        ItemStack inHand = inv.items.get(inv.selected);
        Agent.Held kind = held(inHand);
        if (kind == Agent.Held.OTHER) {
            return Optional.empty();
        }
        LivingEntity target = fightTarget(player, 16);
        if (target == null) {
            return Optional.empty();
        }
        List<Agent.Weapon> weapons = new ArrayList<>();
        for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = inv.items.get(slot);
            if (isRanged(stack)) {
                weapons.add(new Agent.Weapon(slot, 6, true, !player.getProjectile(stack).isEmpty()));
            } else if (isMeleeWeapon(stack)) {
                weapons.add(new Agent.Weapon(slot, dps(player, stack, target), false, false));
            }
        }
        double distance = player.distanceTo(target);
        StyleProfile.Style style = m.style.style(player.level().getGameTime());
        boolean allowRanged = style == StyleProfile.Style.ARCHER || kind == Agent.Held.EMPTY && style != StyleProfile.Style.BRAWLER;
        Optional<Agent.Weapon> best = Agent.pickWeapon(weapons, distance, allowRanged,
                style == StyleProfile.Style.ARCHER ? Agent.ARCHER_RANGED_FROM : Agent.RANGED_FROM);
        if (best.isEmpty() || best.get().slot() == inv.selected) {
            return Optional.empty();
        }
        double heldDps = kind == Agent.Held.WEAPON ? dps(player, inHand, target) : 0;
        if (!Agent.shouldArm(kind, heldDps, best.get().dps(), distance)) {
            return Optional.empty();
        }
        int slot = best.get().slot();
        Component pick = target.getArmorValue() >= Agent.ARMORED && !best.get().ranged()
                ? Component.translatable("cogwit.agent.did.weapon_armor", inv.items.get(slot).getHoverName(),
                        target.getDisplayName(), target.getArmorValue())
                : Component.translatable("cogwit.agent.did.weapon", inv.items.get(slot).getHoverName());
        return Optional.of(new Step(pick,
                () -> {
                    inv.selected = slot;
                    m.armedSlot = slot;
                    if (player instanceof ServerPlayer sp) {
                        sp.connection.send(new ClientboundSetCarriedItemPacket(slot));
                    }
                }));
    }

    /** Out of a fight Cogwit forgets his pick; in one, switching away from it tells him to keep his claws off. */
    private static void tickWeaponChoice(Player player, Memory m) {
        if (!inCombat(player)) {
            m.armedSlot = -1;
            m.weaponOverruled = false;
        } else if (m.armedSlot >= 0 && player.getInventory().selected != m.armedSlot) {
            m.weaponOverruled = true;
        }
    }

    // ---------------------------------------------------------------- hotbar refill

    /** How many of this item you have: inventory, off hand and the stack on the cursor. */
    private static int total(Player player, ItemStack like) {
        Inventory inv = player.getInventory();
        int n = 0;
        for (ItemStack s : inv.items) {
            if (s.is(like.getItem())) {
                n += s.getCount();
            }
        }
        for (ItemStack s : inv.offhand) {
            if (s.is(like.getItem())) {
                n += s.getCount();
            }
        }
        ItemStack carried = player.containerMenu.getCarried();
        return carried.is(like.getItem()) ? n + carried.getCount() : n;
    }

    /** The backpack slot to refill from: the same stack, or for a broken tool the same tool with most durability left. */
    private static int refillSource(Inventory inv, ItemStack used) {
        int best = -1;
        for (int i = 9; i < inv.items.size(); i++) {
            ItemStack s = inv.items.get(i);
            if (used.isDamageableItem()) {
                if (s.is(used.getItem()) && (best < 0 || s.getDamageValue() < inv.items.get(best).getDamageValue())) {
                    best = i;
                }
            } else if (ItemStack.isSameItemSameComponents(s, used)) {
                return i;
            }
        }
        return best;
    }

    /** A slot inside a container item you carry: a Sophisticated backpack, a shulker box. */
    private record PackSlot(IItemHandler handler, int slot) {}

    /** Container items you carry that open up their contents: in the inventory, on the chest, in a curio slot. */
    private static List<IItemHandler> packs(Player player) {
        List<ItemStack> carried = new ArrayList<>(player.getInventory().items);
        carried.add(player.getItemBySlot(EquipmentSlot.CHEST));
        CuriosApi.getCuriosInventory(player).ifPresent(curios -> {
            var equipped = curios.getEquippedCurios();
            for (int i = 0; i < equipped.getSlots(); i++) {
                carried.add(equipped.getStackInSlot(i));
            }
        });
        List<IItemHandler> out = new ArrayList<>();
        for (ItemStack stack : carried) {
            IItemHandler handler = stack.isEmpty() ? null : stack.getCapability(Capabilities.ItemHandler.ITEM);
            if (handler != null) {
                out.add(handler);
            }
        }
        return out;
    }

    /** Where in your backpacks the used-up item can be found, by the same rule as {@link #refillSource}. */
    private static PackSlot packSource(Player player, ItemStack used) {
        PackSlot best = null;
        int bestDamage = Integer.MAX_VALUE;
        for (IItemHandler handler : packs(player)) {
            for (int i = 0; i < handler.getSlots(); i++) {
                ItemStack s = handler.getStackInSlot(i);
                if (used.isDamageableItem()) {
                    if (s.is(used.getItem()) && s.getDamageValue() < bestDamage) {
                        best = new PackSlot(handler, i);
                        bestDamage = s.getDamageValue();
                    }
                } else if (ItemStack.isSameItemSameComponents(s, used)) {
                    return new PackSlot(handler, i);
                }
            }
        }
        return best;
    }

    /**
     * When a hotbar stack is used up (eaten, placed, thrown, broken), the same item comes in from the inventory,
     * or failing that from a backpack or shulker box you carry. A stack that was only moved (to another slot,
     * the cursor, the off hand or an open chest) is left alone: then you still have as many as before.
     */
    private static void tickRefill(Player player, Memory m) {
        Inventory inv = player.getInventory();
        boolean ownInventory = player.containerMenu == player.inventoryMenu;
        for (int slot = 0; slot < 9; slot++) {
            ItemStack now = inv.items.get(slot);
            ItemStack before = m.hotbar[slot];
            if (ownInventory && now.isEmpty() && before != null && !before.isEmpty() && before.getCount() <= 2
                    && m.hotbarTotal[slot] - total(player, before) >= before.getCount()) {
                int from = refillSource(inv, before);
                int target = slot;
                ItemStack shown = before.copy();
                if (from >= 0) {
                    int source = from;
                    attempt(player, Agent.Action.REFILL, Optional.of(new Step(
                            Component.translatable("cogwit.agent.did.refill", shown.getHoverName()), () -> {
                        inv.items.set(target, inv.items.get(source));
                        inv.items.set(source, ItemStack.EMPTY);
                    })));
                } else {
                    PackSlot pack = packSource(player, before);
                    if (pack != null) {
                        attempt(player, Agent.Action.REFILL, Optional.of(new Step(
                                Component.translatable("cogwit.agent.did.refill_pack", shown.getHoverName()), () -> {
                            ItemStack got = pack.handler().extractItem(pack.slot(), shown.getMaxStackSize(), false);
                            if (!got.isEmpty() && inv.items.get(target).isEmpty()) {
                                inv.items.set(target, got);
                            }
                        })));
                    }
                }
            }
            ItemStack after = inv.items.get(slot);
            m.hotbar[slot] = after.copy();
            m.hotbarTotal[slot] = after.isEmpty() ? 0 : total(player, after);
        }
    }

    // ---------------------------------------------------------------- magnet

    private static List<ItemEntity> magnetTargets(Player player) {
        return player.level().getEntitiesOfClass(ItemEntity.class,
                player.getBoundingBox().inflate(Agent.MAGNET_RANGE), item -> item.isAlive() && !item.hasPickUpDelay()
                        && !Agent.ignoredByMagnet(BuiltInRegistries.ITEM.getKey(item.getItem().getItem()).toString(),
                        MAGNET_IGNORE));
    }

    static Optional<Step> planMagnet(Player player) {
        List<ItemEntity> items = magnetTargets(player);
        if (items.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new Step(Component.translatable("cogwit.agent.did.magnet", items.size()),
                () -> pull(player)));
    }

    public static void pull(Player player) {
        Vec3 to = player.position().add(0, 0.5, 0);
        for (ItemEntity item : magnetTargets(player)) {
            Vec3 dir = to.subtract(item.position());
            double d = dir.length();
            if (d > 0.5) {
                item.setDeltaMovement(dir.scale(Math.min(0.45, 0.12 + 0.6 / d) / d));
                item.hurtMarked = true;
            }
        }
    }

    // ---------------------------------------------------------------- replanting

    /** A grown crop was harvested: plant it again next tick from the player's own seeds. */
    public static void harvested(Player player, BlockPos pos, BlockState crop) {
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        ItemStack seed = crop.getBlock().getCloneItemStack(level, pos, crop);
        if (seed.isEmpty()) {
            return;
        }
        level.getServer().tell(new net.minecraft.server.TickTask(level.getServer().getTickCount() + 1, () -> {
            if (!level.getBlockState(pos).isAir()) {
                return;
            }
            Inventory inv = player.getInventory();
            for (int slot = 0; slot < inv.items.size(); slot++) {
                if (inv.items.get(slot).is(seed.getItem())) {
                    int s = slot;
                    attempt(player, Agent.Action.REPLANT, Optional.of(new Step(
                            Component.translatable("cogwit.agent.did.replant", seed.getHoverName()), () -> {
                        if (level.getBlockState(pos).isAir() && crop.getBlock().defaultBlockState().canSurvive(level, pos)) {
                            level.setBlockAndUpdate(pos, crop.getBlock().defaultBlockState());
                            inv.items.get(s).shrink(1);
                        }
                    })));
                    return;
                }
            }
        }));
    }
}
