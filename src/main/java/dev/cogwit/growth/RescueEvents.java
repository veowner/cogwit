package dev.cogwit.growth;

import dev.cogwit.CogwitEvents;
import dev.cogwit.CogwitMod;
import dev.cogwit.Speech;
import dev.cogwit.brain.Brain;
import dev.cogwit.brain.BrainState;
import dev.cogwit.brain.Growth;
import dev.cogwit.crew.CrewService;
import dev.cogwit.item.CogwitTalismanItem;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@EventBusSubscriber(modid = CogwitMod.MODID)
public final class RescueEvents {
    /** Mobs this close get shoved away when Cogwit catches a lethal hit. */
    public static final double SHOVE_RANGE = 5;
    /** Players this close to a dying boss count as having fought it. */
    public static final double BOSS_RANGE = 64;

    private RescueEvents() {}

    /** Last in line, after armor and every other mod: if this hit would kill, Cogwit takes it instead. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDamage(LivingDamageEvent.Pre event) {
        if (!(event.getEntity() instanceof Player player) || player.level().isClientSide
                || event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return;
        }
        // absorption hearts are taken after this event, so they still count as health here
        float health = player.getHealth() + player.getAbsorptionAmount();
        if (!Brain.isLethal(health, event.getNewDamage())) {
            return;
        }
        Optional<ItemStack> worn = CogwitTalismanItem.worn(player);
        if (worn.isEmpty()) {
            return;
        }
        long now = player.level().getGameTime();
        BrainState before = CogwitTalismanItem.brain(worn.get());
        Optional<BrainState> rescued = Brain.rescue(before, now);
        if (rescued.isEmpty()) {
            return;
        }
        event.setNewDamage(Brain.survivableDamage(health, event.getNewDamage()));
        CogwitTalismanItem.setBrain(worn.get(), rescued.get());
        player.clearFire();
        removeHarmfulEffects(player);
        shoveAway(player);
        Speech.say(player, Component.translatable("cogwit.say.rescued", Brain.clock(Brain.RESCUE_SLEEP_TICKS),
                Brain.clock(Brain.WAKE_AFTER_TICKS)));
        CogwitEvents.announceGrowth(player, before.xp(), rescued.get().xp());
        CrewService.deploy(player, CrewService.Reason.RESCUE);
        if (player.level() instanceof ServerLevel level) {
            level.playSound(null, player.blockPosition(), SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 0.8f, 1.4f);
            level.sendParticles(ParticleTypes.WAX_ON, player.getX(), player.getY() + 1, player.getZ(),
                    40, 0.5, 0.8, 0.5, 0.3);
            level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, player.getX(), player.getY() + 1, player.getZ(),
                    30, 0.4, 0.6, 0.4, 0.4);
        }
    }

    /** Everyone with a Cogwit near a boss when it dies grows, so the kill counts for both players in co-op. */
    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        LivingEntity dead = event.getEntity();
        if (dead.level().isClientSide || !dead.getType().is(GrowthContent.BOSSES)) {
            return;
        }
        for (Player player : dead.level().getEntitiesOfClass(Player.class, dead.getBoundingBox().inflate(BOSS_RANGE),
                Player::isAlive)) {
            CogwitTalismanItem.worn(player).ifPresent(stack -> {
                BrainState s = CogwitTalismanItem.brain(stack);
                BrainState grown = Growth.gain(s, Growth.BOSS_XP);
                CogwitTalismanItem.setBrain(stack, grown);
                Speech.say(player, Component.translatable("cogwit.say.boss_xp", dead.getDisplayName(),
                        Math.round(Growth.BOSS_XP)));
                CogwitEvents.announceGrowth(player, s.xp(), grown.xp());
            });
        }
    }

    private static void removeHarmfulEffects(Player player) {
        List<MobEffectInstance> harmful = new ArrayList<>();
        for (MobEffectInstance effect : player.getActiveEffects()) {
            if (effect.getEffect().value().getCategory() == MobEffectCategory.HARMFUL) {
                harmful.add(effect);
            }
        }
        for (MobEffectInstance effect : harmful) {
            player.removeEffect(effect.getEffect());
        }
    }

    private static void shoveAway(Player player) {
        for (Mob mob : player.level().getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(SHOVE_RANGE),
                Mob::isAlive)) {
            mob.knockback(1.5, player.getX() - mob.getX(), player.getZ() - mob.getZ());
        }
    }
}
