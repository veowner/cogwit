package dev.cogwit.item;

import dev.cogwit.CogwitComponents;
import dev.cogwit.CogwitMod;
import dev.cogwit.DamageCategories;
import dev.cogwit.Speech;
import dev.cogwit.brain.Brain;
import dev.cogwit.cells.CellBank;
import dev.cogwit.cells.Cells;
import dev.cogwit.brain.BrainState;
import dev.cogwit.brain.Growth;
import dev.cogwit.crew.CrewService;
import dev.cogwit.mind.Mind;
import dev.cogwit.mind.MindService;
import dev.cogwit.ultra.UltraService;
import dev.cogwit.growth.GrowthContent;
import dev.cogwit.growth.Perks;
import dev.cogwit.brain.RegenResult;
import dev.cogwit.brain.Skill;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.SlotResult;
import top.theillusivec4.curios.api.type.capability.ICurioItem;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public class CogwitTalismanItem extends Item implements ICurioItem {
    /** Cogwit never stays behind with the dropped loot: he comes back with you, and can lead you to it. */
    @Override
    public top.theillusivec4.curios.api.type.capability.ICurio.DropRule getDropRule(
            top.theillusivec4.curios.api.SlotContext slotContext, net.minecraft.world.damagesource.DamageSource source,
            boolean recentlyHit, ItemStack stack) {
        return top.theillusivec4.curios.api.type.capability.ICurio.DropRule.ALWAYS_KEEP;
    }

    public static final BrainState STARTER_BRAIN = BrainState.EMPTY.withTokens(500);
    public static final int COGWIT_ORANGE = 0xC8803F;

    /** Effects Cogwit shrugs off without having to learn them first. */
    public static final Set<Holder<MobEffect>> SHRUGGED_EFFECTS = Set.of(MobEffects.POISON, MobEffects.WITHER,
            MobEffects.WEAKNESS, MobEffects.MOVEMENT_SLOWDOWN, MobEffects.DIG_SLOWDOWN, MobEffects.CONFUSION,
            MobEffects.BLINDNESS, MobEffects.DARKNESS, MobEffects.HUNGER);

    public static final int RAW_COPPER_TOKENS = 15;
    public static final int COPPER_INGOT_TOKENS = 30;

    public CogwitTalismanItem(Properties properties) {
        super(properties);
    }

    public static BrainState brain(ItemStack stack) {
        return stack.getOrDefault(CogwitComponents.BRAIN.get(), BrainState.EMPTY);
    }

    public static void setBrain(ItemStack stack, BrainState state) {
        stack.set(CogwitComponents.BRAIN.get(), state);
    }

    /** The Cogwit a player is wearing in a curio slot, if any. */
    public static Optional<ItemStack> worn(Player player) {
        return CuriosApi.getCuriosInventory(player)
                .flatMap(inv -> inv.findFirstCurio(CogwitMod.COGWIT.get()))
                .map(SlotResult::stack);
    }

    @Override
    public void curioTick(SlotContext slotContext, ItemStack stack) {
        if (!(slotContext.entity() instanceof Player player) || player.level().isClientSide) {
            return;
        }
        long now = player.level().getGameTime();
        BrainState before = brain(stack);
        BrainState s = before;

        if (now % 20 == 0) {
            CellBank.topUp(player, stack);
            s = brain(stack);
        }
        if (s.sleepUntil() != 0 && now >= s.sleepUntil()) {
            s = s.withSleepUntil(0);
            Speech.say(player, Component.translatable("cogwit.say.woke_up"));
        }
        if (now % 20 == 0 && !Brain.isAsleep(s, now)) {
            Perks.tick(player, s);
        }
        if (now % Brain.REGEN_INTERVAL_TICKS == 0) {
            RegenResult regen = Brain.regen(s, now, player.getHealth(), player.getMaxHealth());
            if (regen.heal() > 0) {
                player.heal(regen.heal());
                s = regen.state();
            }
        }
        if (Brain.isThinking(s, now) && now % 4 == 0 && player.level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.WAX_ON, player.getX(), player.getY() + 2.3, player.getZ(),
                    3, 0.3, 0.1, 0.3, 0.02);
        }
        if (Brain.isAsleep(s, now) && now % 40 == 0 && player.level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.CLOUD, player.getX(), player.getY() + 2.1, player.getZ(),
                    1, 0.1, 0.05, 0.1, 0.0);
        }
        if (!s.equals(before)) {
            setBrain(stack, s);
            warnIfHungry(player, before.tokens(), s.tokens());
        }
        if (now % 20 == 0) {
            UltraService.tick(player, stack, now);
            CrewService.tick(player, stack, now);
        }
    }

    /** Taking the talisman off calls the crew home, so their gear is never left behind. */
    @Override
    public void onUnequip(SlotContext slotContext, ItemStack newStack, ItemStack stack) {
        if (slotContext.entity() instanceof Player player && !player.level().isClientSide
                && newStack.getItem() != this) {
            CrewService.recall(player, stack, null);
        }
    }

    @Override
    public void onEquip(SlotContext slotContext, ItemStack prevStack, ItemStack stack) {
        if (!(slotContext.entity() instanceof Player player) || player.level().isClientSide) {
            return;
        }
        for (Holder<MobEffect> effect : SHRUGGED_EFFECTS) {
            player.removeEffect(effect);
        }
        if (prevStack.getItem() != this) {
            Speech.say(player, Component.translatable("cogwit.say.hello"));
        }
    }

    @Override
    public boolean canEquipFromUse(SlotContext slotContext, ItemStack stack) {
        return true;
    }

    /** Tokens one piece of food is worth; 0 if Cogwit doesn't eat it. Blocks are worth nine pieces. */
    public static int foodValue(ItemStack food) {
        if (food.is(Items.RAW_COPPER)) return RAW_COPPER_TOKENS;
        if (food.is(Items.COPPER_INGOT)) return COPPER_INGOT_TOKENS;
        if (food.is(Items.RAW_COPPER_BLOCK)) return RAW_COPPER_TOKENS * 9;
        if (food.is(Items.COPPER_BLOCK)) return COPPER_INGOT_TOKENS * 9;
        if (food.is(GrowthContent.COPPER_APPLE.get())) return GrowthContent.COPPER_APPLE_TOKENS;
        return 0;
    }

    /**
     * Feeds Cogwit from {@code food}, eating at most {@code maxPieces} and never more than it takes to get full.
     * @return how many pieces were eaten
     */
    public static int feed(Player player, ItemStack cogwit, ItemStack food, int maxPieces) {
        int value = foodValue(food);
        BrainState s = brain(cogwit);
        boolean woke = false;
        if (food.is(GrowthContent.COPPER_APPLE.get()) && Brain.isAsleep(s, player.level().getGameTime())) {
            long now = player.level().getGameTime();
            if (!Brain.canWake(s, now)) {
                if (!player.level().isClientSide) {
                    Speech.hint(player, Component.translatable("cogwit.hint.too_early",
                            Brain.clock(Brain.wakeTicksLeft(s, now))));
                }
                return 0;
            }
            s = s.withSleepUntil(0);
            setBrain(cogwit, s);
            woke = true;
            maxPieces = 1;
            if (!player.level().isClientSide) {
                Speech.say(player, Component.translatable("cogwit.say.apple_woke"));
            }
        }
        CellBank.Worn worn = CellBank.worn(player);
        int total = s.tokens() + Cells.total(worn.all());
        int capacity = Cells.capacity(Brain.MAX_TOKENS, worn.all().size());
        int eaten = Brain.itemsToFill(total, value, Math.min(maxPieces, food.getCount()), capacity);
        if (eaten == 0 && woke) {
            food.shrink(1);
            return 1;
        }
        if (eaten == 0) {
            if (value > 0 && !player.level().isClientSide) {
                Speech.hint(player, Component.translatable("cogwit.hint.full"));
            }
            return 0;
        }
        food.shrink(eaten);
        Cells.Fed split = Cells.feed(s.tokens(), Brain.MAX_TOKENS, worn.all(), eaten * value);
        BrainState fed = s.withTokens(split.ownTokens());
        setBrain(cogwit, fed);
        CellBank.write(worn, split.cells());
        player.playSound(SoundEvents.GENERIC_EAT, 0.4f, 1.5f + player.getRandom().nextFloat() * 0.3f);
        if (!player.level().isClientSide) {
            MindService.setState(cogwit, Mind.cheer(MindService.state(cogwit), player.level().getGameTime(),
                    Mind.HAPPY_FED_TICKS));
        }
        if (!player.level().isClientSide) {
            Speech.hint(player, Component.translatable("cogwit.hint.fed", fed.tokens() + Cells.total(split.cells()),
                    capacity));
        }
        return eaten;
    }

    public static void warnIfHungry(Player player, int before, int after) {
        if (Brain.becameHungry(before, after)) {
            Speech.say(player, Component.translatable("cogwit.say.hungry"));
        }
    }

    /** Click copper onto Cogwit in the inventory: left click feeds until full, right click feeds one piece. */
    @Override
    public boolean overrideOtherStackedOnMe(ItemStack cogwit, ItemStack food, Slot slot, ClickAction action,
            Player player, SlotAccess carried) {
        if (foodValue(food) == 0 || !slot.allowModification(player)) {
            return false;
        }
        feed(player, cogwit, food, action == ClickAction.PRIMARY ? food.getCount() : 1);
        return true;
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return true;
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        return Math.round(13f * brain(stack).tokens() / Brain.MAX_TOKENS);
    }

    @Override
    public int getBarColor(ItemStack stack) {
        float fill = (float) brain(stack).tokens() / Brain.MAX_TOKENS;
        return Mth.hsvToRgb(0.05f + 0.07f * fill, 0.75f, 1f);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        BrainState s = brain(stack);
        tooltip.add(Component.translatable("cogwit.tooltip.tagline").withStyle(ChatFormatting.GRAY,
                ChatFormatting.ITALIC));
        tooltip.add(Component.translatable("cogwit.tooltip.tokens", s.tokens(), Brain.MAX_TOKENS)
                .withStyle(ChatFormatting.GOLD));
        if (s.sleepUntil() != 0) {
            tooltip.add(Component.translatable("cogwit.tooltip.asleep").withStyle(ChatFormatting.DARK_AQUA));
        }
        int level = Growth.level(s.xp());
        Growth.Perk next = Growth.next(s.xp());
        tooltip.add(next == null
                ? Component.translatable("cogwit.tooltip.level_max", level).withStyle(ChatFormatting.LIGHT_PURPLE)
                : Component.translatable("cogwit.tooltip.level", level, Growth.toNext(s.xp()),
                        Component.translatable("cogwit.perk." + next.key())).withStyle(ChatFormatting.LIGHT_PURPLE));
        for (Growth.Perk perk : Growth.Perk.values()) {
            if (Growth.has(s.xp(), perk)) {
                tooltip.add(Component.literal(" ✔ ").append(Component.translatable("cogwit.perk." + perk.key()))
                        .withStyle(ChatFormatting.DARK_PURPLE));
            }
        }

        tooltip.add(Component.translatable("cogwit.tooltip.context", s.skills().size(), Brain.CONTEXT_SIZE)
                .withStyle(ChatFormatting.YELLOW));
        if (s.skills().isEmpty()) {
            tooltip.add(Component.translatable("cogwit.tooltip.nothing_learned").withStyle(ChatFormatting.DARK_GRAY));
        }
        for (Skill skill : s.skills()) {
            tooltip.add(skillLine(skill));
        }

        if (flag.hasShiftDown()) {
            for (String line : List.of("learn", "context", "effects", "regen", "tokens", "think", "compact",
                    "team", "refusal", "rescue", "levels", "crew", "ultra", "mind", "agent")) {
                tooltip.add(Component.translatable("cogwit.tooltip.help." + line).withStyle(ChatFormatting.GRAY));
            }
        } else {
            tooltip.add(Component.translatable("cogwit.tooltip.hold_shift").withStyle(ChatFormatting.DARK_GRAY));
        }
    }

    private static Component skillLine(Skill skill) {
        int percent = Math.round(skill.reductionFor(skill.category()) * 100);
        if (skill.compacted()) {
            int mergedPercent = Math.round(skill.reductionFor(skill.merged()) * 100);
            return Component.translatable("cogwit.tooltip.skill_compacted",
                    DamageCategories.name(skill.category()), percent,
                    DamageCategories.name(skill.merged()), mergedPercent).withStyle(ChatFormatting.AQUA);
        }
        int toNext = Brain.xpToNextLevel(skill.xp());
        Component progress = toNext == 0
                ? Component.translatable("cogwit.tooltip.maxed")
                : Component.translatable("cogwit.tooltip.to_next", toNext);
        return Component.translatable("cogwit.tooltip.skill", DamageCategories.name(skill.category()), percent, progress)
                .withStyle(percent > 0 ? ChatFormatting.GREEN : ChatFormatting.DARK_GREEN);
    }
}
