package dev.cogwit.ultra;

import dev.cogwit.CogwitComponents;
import dev.cogwit.Speech;
import dev.cogwit.brain.Brain;
import dev.cogwit.brain.BrainState;
import dev.cogwit.crew.CogwitCreature;
import dev.cogwit.crew.CrewService;
import dev.cogwit.growth.GrowthContent;
import dev.cogwit.item.CogwitTalismanItem;
import dev.cogwit.network.UltraPayload;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/** Runs Overdrive on the server: notices the boss, enrages Cogwit, sends the crew and calls the phases. */
public final class UltraService {
    /** How recently the owner and the boss must have traded hits to count as fighting. */
    private static final int RECENT_HIT_TICKS = 200;
    private static final List<Holder<MobEffect>> BUFFS = List.of(MobEffects.DAMAGE_BOOST, MobEffects.MOVEMENT_SPEED,
            MobEffects.REGENERATION);

    private UltraService() {}

    public static UltraState state(ItemStack talisman) {
        return talisman.getOrDefault(CogwitComponents.ULTRA.get(), UltraState.EMPTY);
    }

    public static void setState(ItemStack talisman, UltraState state) {
        talisman.set(CogwitComponents.ULTRA.get(), state);
    }

    public static boolean isActive(ItemStack talisman) {
        return state(talisman).active();
    }

    /** The closest boss that is fighting this player, if any. */
    public static Optional<LivingEntity> engagedBoss(Player player) {
        return player.level().getEntitiesOfClass(LivingEntity.class,
                        player.getBoundingBox().inflate(Overdrive.BOSS_RANGE),
                        e -> e.isAlive() && e.getType().is(GrowthContent.BOSSES) && fighting(player, e))
                .stream().min(Comparator.comparingDouble(player::distanceToSqr));
    }

    private static boolean fighting(Player player, LivingEntity boss) {
        boolean targetsPlayer = boss instanceof Mob mob && mob.getTarget() == player;
        boolean hitPlayer = player.getLastHurtByMob() == boss;
        boolean hitByPlayer = player.getLastHurtMob() == boss
                && player.tickCount - player.getLastHurtMobTimestamp() < RECENT_HIT_TICKS;
        return targetsPlayer || hitPlayer || hitByPlayer;
    }

    /** Every second while the talisman is worn. */
    public static void tick(Player player, ItemStack talisman, long now) {
        UltraState before = state(talisman);
        BrainState brain = CogwitTalismanItem.brain(talisman);
        if (before.active() && (Brain.isAsleep(brain, now) || brain.tokens() == 0)) {
            end(player, talisman, Overdrive.collapse(before, now));
            return;
        }
        Optional<LivingEntity> boss = engagedBoss(player);
        Overdrive.Step step = Overdrive.tick(before, boss.isPresent(), Overdrive.canStart(brain, before, now), now);
        switch (step.change()) {
            case STARTED -> start(player, talisman, step.state(), boss.orElseThrow());
            case ENDED -> end(player, talisman, step.state());
            case NONE -> {
                setState(talisman, step.state());
                if (step.state().active()) {
                    rage(player, talisman, boss.orElse(null));
                }
            }
        }
    }

    private static void start(Player player, ItemStack talisman, UltraState state, LivingEntity boss) {
        boolean crewWasOut = CrewService.isDeployed(talisman);
        setState(talisman, state);
        boolean sent = crewWasOut || CrewService.deploy(player, CrewService.Reason.OVERDRIVE);
        setState(talisman, state.withCrewByUltra(sent && !crewWasOut));
        Speech.say(player, Component.translatable("cogwit.ultra.start", boss.getDisplayName()));
        if (player.level() instanceof ServerLevel level) {
            level.playSound(null, player.blockPosition(), SoundEvents.TRIAL_SPAWNER_OMINOUS_ACTIVATE,
                    SoundSource.PLAYERS, 1.2f, 1.3f);
            level.playSound(null, player.blockPosition(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 1f, 2f);
            level.sendParticles(ParticleTypes.ENCHANT, player.getX(), player.getY() + 1.2, player.getZ(), 120, 1.0,
                    1.0, 1.0, 1.2);
        }
        if (player instanceof ServerPlayer serverPlayer) {
            PacketDistributor.sendToPlayer(serverPlayer, new UltraPayload(true));
        }
        rage(player, talisman, boss);
    }

    private static void end(Player player, ItemStack talisman, UltraState state) {
        setState(talisman, state);
        for (CogwitCreature creature : CrewService.crewOf(player)) {
            creature.setRage(false);
        }
        if (state.crewByUltra()) {
            CrewService.recall(player, talisman, null);
        }
        setState(talisman, state.withCrewByUltra(false));
        Speech.say(player, Component.translatable("cogwit.ultra.end", Brain.clock(Overdrive.HANGOVER_TICKS)));
        player.level().playSound(null, player.blockPosition(), SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS,
                1f, 1.4f);
        if (player instanceof ServerPlayer serverPlayer) {
            PacketDistributor.sendToPlayer(serverPlayer, new UltraPayload(false));
        }
    }

    /** One second of rage: buffs for the owner and the crew, everyone on the boss, phase calls. */
    private static void rage(Player player, ItemStack talisman, LivingEntity boss) {
        for (Holder<MobEffect> buff : BUFFS) {
            player.addEffect(new MobEffectInstance(buff, 60, 0, true, false, true));
        }
        for (CogwitCreature creature : CrewService.crewOf(player)) {
            creature.setRage(true);
            creature.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 60, 1, true, false));
            creature.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 60, 0, true, false));
            if (boss != null && boss.isAlive()) {
                creature.setTarget(boss);
            }
            if (creature.level() instanceof ServerLevel level) {
                level.sendParticles(ParticleTypes.ELECTRIC_SPARK, creature.getX(), creature.getY() + 0.5,
                        creature.getZ(), 4, 0.3, 0.3, 0.3, 0.05);
            }
        }
        if (boss != null && boss.getMaxHealth() > 0) {
            UltraState u = state(talisman);
            float share = boss.getHealth() / boss.getMaxHealth();
            int phase = Overdrive.phaseCrossed(u.bossHealth(), share);
            if (phase > 0) {
                Speech.say(player, Component.translatable("cogwit.ultra.phase", boss.getDisplayName(), phase));
            }
            setState(talisman, u.withBossHealth(share));
        }
    }
}
