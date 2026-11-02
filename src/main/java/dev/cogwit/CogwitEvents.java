package dev.cogwit;

import dev.cogwit.brain.Brain;
import dev.cogwit.brain.BrainEvent;
import dev.cogwit.brain.BrainState;
import dev.cogwit.brain.DamageResult;
import dev.cogwit.brain.Growth;
import dev.cogwit.cells.CellBank;
import dev.cogwit.crew.Crew;
import dev.cogwit.crew.CrewService;
import dev.cogwit.mind.Mind;
import dev.cogwit.mind.MindService;
import dev.cogwit.ultra.UltraService;
import dev.cogwit.ultra.UltraState;
import dev.cogwit.ultra.Overdrive;
import dev.cogwit.item.CogwitTalismanItem;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@EventBusSubscriber(modid = CogwitMod.MODID)
public final class CogwitEvents {
    /** How close teammates must stand to share what their Cogwits have learned. */
    public static final double TEAM_RANGE = 16.0;

    private CogwitEvents() {}

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof Player player) || player.level().isClientSide) {
            return;
        }
        Optional<ItemStack> worn = CogwitTalismanItem.worn(player);
        if (worn.isEmpty()) {
            return;
        }
        ItemStack stack = worn.get();
        DamageSource source = event.getSource();
        long now = player.level().getGameTime();

        if (Brain.isShielded(CogwitTalismanItem.brain(stack), now)) {
            event.setCanceled(true);
            return;
        }
        if (source.getEntity() instanceof Player attacker && attacker != player) {
            refuse(player, stack, now);
            return;
        }
        Optional<String> category = DamageCategories.categorize(source);
        if (category.isEmpty()) {
            return;
        }

        CellBank.topUp(player, stack);
        if (blockFromBehind(player, stack, source, event.getAmount(), now)) {
            event.setCanceled(true);
            return;
        }
        BrainState state = CogwitTalismanItem.brain(stack);
        float grief = Crew.grief(CrewService.state(stack), now);
        float own = Brain.reductionFor(state, category.get()) * grief;
        float team = Brain.bestReduction(teammates(player), category.get(), now) * grief;
        UltraState ultra = UltraService.state(stack);
        float wanted = Overdrive.protection(Math.max(own, team), ultra, now);
        DamageResult result = Brain.onDamage(state, category.get(), event.getAmount(), wanted, now);
        int extra = Overdrive.extraCost(event.getAmount(), result.reduction(), ultra);
        BrainState paid = result.state().withTokens(Math.max(0, result.state().tokens() - extra));

        CogwitTalismanItem.setBrain(stack, paid);
        CogwitTalismanItem.warnIfHungry(player, state.tokens(), paid.tokens());
        if (result.reduction() > 0f) {
            event.setAmount(event.getAmount() * (1f - result.reduction()));
            if (team > own) {
                Speech.hint(player, Component.translatable("cogwit.hint.team",
                        DamageCategories.name(category.get()), Math.round(result.reduction() * 100)));
            }
        }
        for (BrainEvent e : result.events()) {
            announce(player, e);
        }
    }

    @SubscribeEvent
    public static void onEffectApplicable(MobEffectEvent.Applicable event) {
        if (!(event.getEntity() instanceof Player player) || player.level().isClientSide
                || !CogwitTalismanItem.SHRUGGED_EFFECTS.contains(event.getEffectInstance().getEffect())) {
            return;
        }
        CogwitTalismanItem.worn(player).ifPresent(stack -> {
            long now = player.level().getGameTime();
            CellBank.topUp(player, stack);
            BrainState before = CogwitTalismanItem.brain(stack);
            Brain.blockEffect(before, now).ifPresent(paid -> {
                CogwitTalismanItem.setBrain(stack, Growth.gain(paid, Growth.EFFECT_XP));
                announceGrowth(player, before.xp(), before.xp() + Growth.EFFECT_XP);
                CogwitTalismanItem.warnIfHungry(player, before.tokens(), paid.tokens());
                event.setResult(MobEffectEvent.Applicable.Result.DO_NOT_APPLY);
            });
        });
    }

    /** Shift + right click with copper in hand feeds one piece; holding the button keeps feeding. */
    @SubscribeEvent
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (tryHandFeed(event.getEntity(), event.getItemStack())) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
        }
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (tryHandFeed(event.getEntity(), event.getItemStack())) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
        }
    }

    private static boolean tryHandFeed(Player player, ItemStack food) {
        if (!player.isShiftKeyDown() || CogwitTalismanItem.foodValue(food) == 0) {
            return false;
        }
        Optional<ItemStack> worn = CogwitTalismanItem.worn(player);
        if (worn.isEmpty()) {
            return false;
        }
        if (!player.level().isClientSide) {
            CogwitTalismanItem.feed(player, worn.get(), food, 1);
        }
        return true;
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CogwitCommands.register(event.getDispatcher());
    }

    private static void refuse(Player player, ItemStack stack, long now) {
        BrainState state = CogwitTalismanItem.brain(stack);
        if (Brain.shouldSayRefusal(state, now)) {
            Speech.say(player, Component.translatable("cogwit.say.refusal"));
            CogwitTalismanItem.setBrain(stack, state.withLastRefusal(Math.max(now, 1)));
        }
    }

    private static List<BrainState> teammates(Player player) {
        List<BrainState> states = new ArrayList<>();
        for (Player other : player.level().getEntitiesOfClass(Player.class, player.getBoundingBox().inflate(TEAM_RANGE),
                other -> other != player && other.isAlive())) {
            CogwitTalismanItem.worn(other).ifPresent(stack -> states.add(CogwitTalismanItem.brain(stack)));
        }
        return states;
    }

    private static void announce(Player player, BrainEvent event) {
        switch (event) {
            case BrainEvent.Learned e -> Speech.say(player,
                    Component.translatable("cogwit.say.learned", DamageCategories.name(e.category())));
            case BrainEvent.LevelUp e -> Speech.say(player, Component.translatable("cogwit.say.level_up",
                    DamageCategories.name(e.category()),
                    Math.round(Brain.reductionForXp(levelXp(e.level())) * 100)));
            case BrainEvent.Forgot e -> Speech.say(player,
                    Component.translatable("cogwit.say.forgot", DamageCategories.name(e.category())));
            case BrainEvent.FellAsleep e -> Speech.say(player, Component.translatable("cogwit.say.limit"));
            case BrainEvent.Grew e -> grew(player, e.level());
        }
    }

    /** Tells the owner about every level Cogwit reached between two amounts of experience. */
    public static void announceGrowth(Player player, float before, float after) {
        for (int level : Growth.levelsGained(before, after)) {
            grew(player, level);
        }
    }

    private static void grew(Player player, int level) {
        MindService.cheer(player, Mind.HAPPY_LEVEL_TICKS);
        Growth.Perk perk = Growth.Perk.values()[level - 1];
        Speech.say(player, Component.translatable("cogwit.say.grew", level,
                Component.translatable("cogwit.perk." + perk.key())));
        Speech.say(player, Component.translatable("cogwit.perk." + perk.key() + ".desc"));
        player.level().playSound(null, player.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS,
                0.7f, 1.3f);
    }

    /** Level 3: Cogwit blocks a hit you couldn't see coming. */
    private static boolean blockFromBehind(Player player, ItemStack stack, DamageSource source, float amount,
            long now) {
        Vec3 from = source.getSourcePosition();
        if (from == null || source.getEntity() == null || source.getEntity() == player) {
            return false;
        }
        Vec3 look = player.getLookAngle();
        if (!Brain.isBehind(look.x, look.z, from.x - player.getX(), from.z - player.getZ())) {
            return false;
        }
        BrainState before = CogwitTalismanItem.brain(stack);
        Optional<BrainState> paid = Brain.backShield(before, now);
        if (paid.isEmpty()) {
            return false;
        }
        BrainState grown = Growth.gain(paid.get(), amount * Growth.XP_PER_BLOCKED_HP);
        CogwitTalismanItem.setBrain(stack, grown);
        CogwitTalismanItem.warnIfHungry(player, before.tokens(), grown.tokens());
        announceGrowth(player, before.xp(), grown.xp());
        Speech.hint(player, Component.translatable("cogwit.hint.back_shield"));
        dev.cogwit.brain.BackSlab.raise(player, from, Growth.level(grown.xp()));
        if (source.getDirectEntity() instanceof net.minecraft.world.entity.LivingEntity attacker) {
            // the slab slams into whoever swung at your back
            attacker.knockback(0.7, player.getX() - attacker.getX(), player.getZ() - attacker.getZ());
            attacker.hurtMarked = true;
        }
        return true;
    }

    private static int levelXp(int level) {
        int xp = 0;
        while (Brain.levelForXp(xp) < level) {
            xp++;
        }
        return xp;
    }
}
