package dev.cogwit.horizon;

import net.minecraft.server.level.ServerPlayer;
import dev.cogwit.network.ScreenFxPayload;
import dev.cogwit.horizon.fx.SkillFx;
import dev.cogwit.horizon.fx.GravityWellEntity;
import dev.cogwit.horizon.fx.CollapseEntity;
import dev.cogwit.Speech;
import dev.cogwit.brain.Brain;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractHurtingProjectile;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** What the armor does in the world: abilities on key press and the effects that run every tick. */
public final class HorizonActions {
    public static final String REFLECTED_TAG = "cogwit_reflected";
    private static final double ATTRACT_RADIUS = 32;

    /** Where each player's gravity well is pulling; lives only as long as the well. */
    /** Last solid ground each player stood on, for the void rescue. */
    private static final Map<UUID, Vec3> SAFE_SPOTS = new ConcurrentHashMap<>();

    private HorizonActions() {}

    // ---- abilities ----

    public static void blink(Player player) {
        if (!require(player, Horizon.Feature.THRUSTERS, "cogwit.horizon.need_legs")) {
            return;
        }
        long now = player.level().getGameTime();
        AbilityResult result = Horizon.blink(HorizonContent.state(player), now);
        if (result.status() == AbilityResult.Status.COOLDOWN) {
            Speech.hint(player, Component.translatable("cogwit.horizon.cooldown",
                    Brain.clock(HorizonContent.state(player).blinkReadyAt() - now)));
            return;
        }
        HorizonContent.setState(player, result.state());
        HorizonDash.start(player);
    }

    /** Furthest spot along the look direction (up to the blink range) where the player's body fits. */
    static Vec3 blinkDestination(Player player) {
        Level level = player.level();
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        Vec3 target = level.clip(new ClipContext(eye, eye.add(look.scale(Horizon.BLINK_RANGE)),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player)).getLocation();
        for (double back = 0.5; back < Horizon.BLINK_RANGE; back += 0.5) {
            Vec3 feet = target.subtract(look.scale(back)).subtract(0, player.getEyeHeight(), 0);
            AABB box = player.getBoundingBox().move(feet.subtract(player.position()));
            if (level.noCollision(player, box)) {
                return feet;
            }
        }
        return null;
    }

    /** The gravity key: a gravity well, or Collapse once mass is full. */
    public static void gravity(Player player) {
        if (!require(player, Horizon.Feature.CORE, "cogwit.horizon.need_chest")) {
            return;
        }
        if (Horizon.gravityKey(HorizonContent.state(player)) == Horizon.Ability.COLLAPSE) {
            collapse(player);
        } else {
            well(player);
        }
    }

    static void well(Player player) {
        long now = player.level().getGameTime();
        HorizonState state = HorizonContent.state(player);
        AbilityResult result = Horizon.well(state, now);
        if (result.status() == AbilityResult.Status.COOLDOWN) {
            Speech.hint(player, Component.translatable("cogwit.horizon.cooldown", Brain.clock(state.wellReadyAt() - now)));
            return;
        }
        Vec3 eye = player.getEyePosition();
        Vec3 center = player.level().clip(new ClipContext(eye, eye.add(player.getLookAngle().scale(Horizon.WELL_DISTANCE)),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player)).getLocation();
        // float the hole a little above the ground so the disk is visible
        center = center.subtract(player.getLookAngle().scale(0.8)).add(0, 1.2, 0);
        HorizonContent.setState(player, result.state());
        GravityWellEntity.open(player, center);
        Speech.hint(player, Component.translatable("cogwit.horizon.well"));
    }

    static void collapse(Player player) {
        AbilityResult result = Horizon.collapse(HorizonContent.state(player));
        if (result.status() != AbilityResult.Status.OK) {
            return;
        }
        HorizonContent.setState(player, result.state());
        CollapseEntity.start(player);
        Speech.hint(player, Component.translatable("cogwit.horizon.collapse"));
    }

    // ---- every tick while the parts are worn ----

    /** Turns incoming projectiles around and sends them back at whoever fired them. */
    static void tickReflect(Player player) {
        List<Projectile> projectiles = player.level().getEntitiesOfClass(Projectile.class,
                player.getBoundingBox().inflate(Horizon.REFLECT_RADIUS),
                p -> p.getOwner() != player && !p.getTags().contains(REFLECTED_TAG));
        Vec3 me = player.getBoundingBox().getCenter();
        for (Projectile projectile : projectiles) {
            Vec3 v = projectile.getDeltaMovement();
            Vec3 p = projectile.position();
            if (v.lengthSqr() < 0.01 || !Horizon.isIncoming(p.x, p.y, p.z, v.x, v.y, v.z, me.x, me.y, me.z)) {
                continue;
            }
            Entity shooter = projectile.getOwner();
            Vec3 target = shooter != null && shooter.isAlive() ? shooter.getEyePosition() : p.subtract(v.scale(10));
            double[] aim = Horizon.aimAt(p.x, p.y, p.z, target.x, target.y, target.z, Math.max(v.length(), 1.2));
            projectile.setDeltaMovement(aim[0], aim[1], aim[2]);
            projectile.setOwner(player);
            projectile.addTag(REFLECTED_TAG);
            projectile.hasImpulse = true;
            if (projectile instanceof AbstractHurtingProjectile hurting) {
                hurting.accelerationPower = 0.1;
            }
            if (player.level() instanceof ServerLevel level) {
                SkillFx.ring(level, p, v, 0.7, 14, ParticleTypes.END_ROD);
                SkillFx.ring(level, p, v, 0.4, 8, ParticleTypes.ELECTRIC_SPARK);
                level.sendParticles(ParticleTypes.REVERSE_PORTAL, p.x, p.y, p.z, 10, 0.1, 0.1, 0.1, 0.15);
                level.playSound(null, BlockPos.containing(p), SoundEvents.AMETHYST_BLOCK_HIT, SoundSource.PLAYERS,
                        1.2f, 1.6f);
                level.playSound(null, BlockPos.containing(p), SoundEvents.SHIELD_BLOCK, SoundSource.PLAYERS, 0.4f, 1.8f);
            }
        }
    }

    /** Remembers solid ground and pulls the player out of the void (once per cooldown). */
    static HorizonState tickVoid(Player player, HorizonState state, long now) {
        Level level = player.level();
        if (player.onGround() && !player.isInLava() && player.getY() > level.getMinBuildHeight()) {
            SAFE_SPOTS.put(player.getUUID(), player.position());
        }
        if (player.getY() >= level.getMinBuildHeight() - 8) {
            return state;
        }
        AbilityResult result = Horizon.voidRescue(state, now);
        if (result.status() != AbilityResult.Status.OK) {
            return state;
        }
        Vec3 spot = SAFE_SPOTS.get(player.getUUID());
        if (spot == null && level instanceof ServerLevel server) {
            spot = Vec3.atBottomCenterOf(server.getSharedSpawnPos());
        }
        if (spot != null) {
            player.teleportTo(spot.x, spot.y, spot.z);
            player.setDeltaMovement(Vec3.ZERO);
            player.resetFallDistance();
            player.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                    net.minecraft.world.effect.MobEffects.SLOW_FALLING, 60, 0, false, false));
            if (level instanceof ServerLevel server) {
                for (int y = 0; y < 12; y++) {
                    SkillFx.ring(server, spot.add(0, y * 0.6, 0), new Vec3(0, 1, 0), 0.6, 10,
                            y % 2 == 0 ? ParticleTypes.END_ROD : ParticleTypes.REVERSE_PORTAL);
                }
                server.playSound(null, BlockPos.containing(spot), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS,
                        1f, 1.4f);
                if (player instanceof ServerPlayer sp) {
                    SkillFx.screen(sp, ScreenFxPayload.FLASH, 0.6f, 12);
                }
            }
            HorizonTips.say(player, Component.translatable("cogwit.horizon.void_rescue"));
        }
        return result.state();
    }

    /** Downside of all that mass: hostile mobs that can see you come for you. */
    static void attractMobs(Player player) {
        if (player.isCreative() || player.isSpectator()) {
            return;
        }
        for (Mob mob : player.level().getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(ATTRACT_RADIUS),
                m -> m instanceof Enemy && m.getTarget() == null && m.hasLineOfSight(player))) {
            mob.setTarget(player);
        }
    }

    private static List<LivingEntity> victims(Player player, Vec3 center, double radius) {
        return player.level().getEntitiesOfClass(LivingEntity.class, AABB.ofSize(center, radius * 2, radius * 2, radius * 2),
                e -> e != player && e.isAlive() && !(e instanceof Player) && !(e instanceof ArmorStand)
                        && !(e instanceof TamableAnimal pet && pet.isTame())
                        && e.position().distanceTo(center) <= radius);
    }

    private static boolean require(Player player, Horizon.Feature feature, String missing) {
        if (HorizonContent.has(player, feature)) {
            return true;
        }
        Speech.hint(player, Component.translatable(missing));
        return false;
    }
}
