package dev.cogwit.trophy;

import dev.cogwit.CogwitMod;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.entity.living.LivingExperienceDropEvent;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.Set;

@EventBusSubscriber(modid = CogwitMod.MODID)
public final class TrophyEvents {
    private TrophyEvents() {}

    /** Trophy passives that turn hits away, and the pull and burn of an owner's melee hits. */
    @SubscribeEvent(priority = net.neoforged.bus.api.EventPriority.HIGH)
    public static void onPlayerHurt(LivingIncomingDamageEvent event) {
        if (event.getEntity().level().isClientSide) {
            return;
        }
        // the caster's evoker fangs bite only legitimate targets, whatever stands in the way
        if (event.getSource().getDirectEntity() instanceof net.minecraft.world.entity.projectile.EvokerFangs fangs
                && SpellbookPowers.fangsSpare(fangs, event.getEntity())) {
            event.setCanceled(true);
            return;
        }
        if (event.getEntity() instanceof Player player && TrophyService.shrugOff(player, event.getSource())) {
            event.setCanceled(true);
            return;
        }
        if (event.getSource().getDirectEntity() instanceof Player attacker
                && event.getSource().is(net.minecraft.world.damagesource.DamageTypes.PLAYER_ATTACK)) {
            TrophyService.meleePull(attacker, event.getEntity());
            TrophyPassives.infernalStrike(attacker, event.getEntity());
        }
    }

    /** The Wroughtnaut hands back a share of the melee blow it took. */
    @SubscribeEvent
    public static void onPlayerHurtDone(LivingDamageEvent.Post event) {
        if (event.getEntity() instanceof Player player && !player.level().isClientSide) {
            TrophyPassives.reflect(player, event.getSource(), event.getNewDamage());
        }
    }

    /** The Dead King's second life: a lethal blow leaves a quarter of the health once every ten minutes. */
    @SubscribeEvent(priority = net.neoforged.bus.api.EventPriority.HIGH)
    public static void onLethalHit(LivingDeathEvent event) {
        if (event.getEntity() instanceof Player player && !player.level().isClientSide
                && TrophyPassives.revive(player, event.getSource())) {
            event.setCanceled(true);
        }
    }

    /** A Harbinger missile and a storm fireball burst on what they hit; a summoned skeleton's arrow spares allies. */
    @SubscribeEvent
    public static void onImpact(net.neoforged.neoforge.event.entity.ProjectileImpactEvent event) {
        var projectile = event.getProjectile();
        if (projectile.level().isClientSide) {
            return;
        }
        if (projectile instanceof net.minecraft.world.entity.projectile.ShulkerBullet bullet
                && bullet.getTags().contains(CataclysmStrikes.MISSILE_TAG)) {
            event.setCanceled(true);
            CataclysmStrikes.missileHit(bullet, event.getRayTraceResult().getLocation());
        } else if (projectile.getTags().contains(SpellbookPowers.FIREBALL_TAG)) {
            event.setCanceled(true);
            SpellbookPowers.fireballHit(projectile, event.getRayTraceResult());
        } else if (SpellbookPowers.arrowShouldPass(projectile, event.getRayTraceResult())) {
            event.setCanceled(true);
        }
    }

    /**
     * A summoned skeleton never turns on a player, a pet or another summon, whatever goals other mods give skeletons.
     */
    @SubscribeEvent
    public static void onSummonTarget(net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent event) {
        if (SpellbookPowers.isSummon(event.getEntity()) && event.getNewAboutToBeSetTarget() != null
                && !SpellbookPowers.isFoe(event.getNewAboutToBeSetTarget())) {
            event.setNewAboutToBeSetTarget(null);
        }
    }

    /** Summoned allies leave nothing behind. */
    @SubscribeEvent
    public static void onSummonDrops(LivingDropsEvent event) {
        if (SpellbookPowers.isSummon(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onSummonExperience(LivingExperienceDropEvent event) {
        if (SpellbookPowers.isSummon(event.getEntity())) {
            event.setDroppedExperience(0);
        }
    }

    /** Skeletons and block displays that were saved with a world are leftovers of a power that was cut short. */
    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent event) {
        if (event.loadedFromDisk() && (SpellbookPowers.isSummon(event.getEntity())
                || event.getEntity().getTags().contains(DisplayFx.TAG))) {
            event.setCanceled(true);
        }
    }

    /** Remembers every hit on a boss, including hits by the player's crew and projectiles. */
    @SubscribeEvent
    public static void onDamage(LivingIncomingDamageEvent event) {
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide
                || Trophy.byEntity(BuiltInRegistries.ENTITY_TYPE.getKey(victim.getType()).toString()).isEmpty()) {
            return;
        }
        Player attacker = event.getSource().getEntity() instanceof Player p ? p
                : event.getSource().getEntity() instanceof dev.cogwit.crew.CogwitCreature crew
                && crew.getOwner() instanceof Player owner ? owner : null;
        if (attacker != null) {
            TrophyService.noteHit(victim, attacker);
        }
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (!event.getEntity().level().isClientSide) {
            TrophyService.bossDied(event.getEntity(), event.getSource().getEntity());
            TrophyPassives.harvest(event.getEntity(), event.getSource());
        }
    }

    /** The Minoshroom's appetite: a meal makes its owner strong for a while. */
    @SubscribeEvent
    public static void onMeal(LivingEntityUseItemEvent.Finish event) {
        if (event.getEntity() instanceof Player player && !player.level().isClientSide) {
            TrophyPassives.feast(player, event.getItem().isEmpty() ? event.getResultStack() : event.getItem());
        }
    }

    /** The Ur-Ghast's owner lands softly. */
    @SubscribeEvent
    public static void onFall(LivingFallEvent event) {
        if (event.getEntity() instanceof Player player && !player.level().isClientSide) {
            event.setDamageMultiplier(TrophyPassives.softLanding(player, event.getDamageMultiplier()));
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        if (player.level().isClientSide) {
            return;
        }
        TrophyService.tickBeams(player);
        TrophyPassives.landed(player);
        if (player.fallDistance > PowerMath.CATCH_FALL && !player.onGround()) {
            TrophyPassives.catchFall(player);
        }
        if (player.tickCount % 2 == 0) {
            Set<Trophy> passives = TrophyService.passives(player);
            if (passives.contains(Trophy.IGNIS)) {
                TrophyService.walkOnLava(player);
            }
            if (passives.contains(Trophy.YETI)) {
                TrophyPassives.iceRoad(player);
            }
        }
        if (player.tickCount % 10 == 0) {
            TrophyService.tickPassives(player);
        }
    }

    @SubscribeEvent
    public static void onEffect(MobEffectEvent.Applicable event) {
        if (event.getEntity() instanceof Player player && !player.level().isClientSide
                && TrophyService.immuneTo(player, event.getEffectInstance().getEffect())) {
            event.setResult(MobEffectEvent.Applicable.Result.DO_NOT_APPLY);
        }
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        TrophyService.sync(event.getEntity());
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        TrophyService.sync(event.getEntity());
    }

    @SubscribeEvent
    public static void onDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        TrophyService.sync(event.getEntity());
    }

    @SubscribeEvent
    public static void onStartTracking(PlayerEvent.StartTracking event) {
        if (event.getTarget() instanceof Player owner && event.getEntity() instanceof ServerPlayer viewer) {
            TrophyService.syncTo(viewer, owner);
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        TrophyService.forget(event.getEntity());
    }
}
