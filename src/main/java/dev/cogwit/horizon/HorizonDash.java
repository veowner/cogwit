package dev.cogwit.horizon;

import dev.cogwit.horizon.fx.SkillFx;
import dev.cogwit.network.ScreenFxPayload;
import dev.cogwit.trophy.TrophyScheduler;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The armor's dash: a few ticks of thrust along the look direction that keeps your running speed, shoulders mobs
 * aside, dodges hits while it lasts and hands the momentum back at the end instead of stopping dead.
 */
public final class HorizonDash {
    public static final int TICKS = 6;
    public static final double SPEED = 2.0;
    /** Share of the dash speed left as momentum when the thrust ends. */
    public static final double CARRY = 0.55;
    /** Share of the speed you already had that the dash keeps on top of its own thrust. */
    public static final double INERTIA = 0.6;

    private static final Map<UUID, Long> DASHING = new ConcurrentHashMap<>();

    private HorizonDash() {}

    /** Direction of the thrust: where you look, lifted a little on the ground so it doesn't scrape the floor. */
    public static Vec3 direction(Vec3 look, boolean onGround) {
        Vec3 dir = look.normalize();
        if (onGround && dir.y < 0.08) {
            dir = new Vec3(dir.x, 0.08, dir.z).normalize();
        }
        return dir;
    }

    /** Velocity during one tick of thrust: the dash plus part of the horizontal speed you came in with. */
    public static Vec3 thrust(Vec3 dir, Vec3 carried) {
        return dir.scale(SPEED).add(carried.x * INERTIA, 0, carried.z * INERTIA);
    }

    public static boolean isDashing(Player player) {
        Long until = DASHING.get(player.getUUID());
        return until != null && player.level().getGameTime() <= until;
    }

    public static void start(Player player) {
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        Vec3 dir = direction(player.getLookAngle(), player.onGround());
        Vec3 carried = player.getDeltaMovement();
        DASHING.put(player.getUUID(), level.getGameTime() + TICKS);
        Vec3 chest = player.position().add(0, 1, 0);
        // a ring of air bursts well behind you as you launch, out of your own first-person view
        SkillFx.ring(level, chest.subtract(dir.scale(1.6)), dir, 0.9, 18, ParticleTypes.CLOUD);
        SkillFx.ring(level, chest.subtract(dir.scale(2.0)), dir, 0.6, 12, ParticleTypes.REVERSE_PORTAL);
        // the side nozzles on the leggings fire backwards
        Vec3 back = dir.scale(-1);
        Vec3 side = new Vec3(-dir.z, 0, dir.x).normalize();
        for (int s = -1; s <= 1; s += 2) {
            Vec3 nozzle = player.position().add(side.scale(0.34 * s)).add(0, 0.62, 0);
            for (int i = 0; i < 6; i++) {
                SkillFx.particles(level, ParticleTypes.FLAME, nozzle.x, nozzle.y, nozzle.z, 0,
                        back.x + side.x * s * 0.3, back.y - 0.2, back.z + side.z * s * 0.3, 0.15 + 0.05 * i);
            }
        }
        level.playSound(null, player.blockPosition(), SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 0.4f, 1.5f);
        level.playSound(null, player.blockPosition(), SoundEvents.BREEZE_WIND_CHARGE_BURST.value(), SoundSource.PLAYERS,
                0.9f, 1.4f);
        level.playSound(null, player.blockPosition(), SoundEvents.ENDER_DRAGON_FLAP, SoundSource.PLAYERS, 0.5f, 1.8f);
        if (player instanceof ServerPlayer sp) {
            SkillFx.screen(sp, ScreenFxPayload.FOV_PUNCH, 1f, 8);
        }
        boolean[] stopped = {false};
        for (int t = 0; t < TICKS; t++) {
            int tick = t;
            TrophyScheduler.later(level, t, () -> {
                if (stopped[0] || !player.isAlive()) {
                    return;
                }
                if (tick > 1 && player.horizontalCollision) {
                    stopped[0] = true;
                    impact(level, player, dir);
                    return;
                }
                push(level, player, dir, carried);
            });
        }
        TrophyScheduler.later(level, TICKS, () -> {
            if (!stopped[0] && player.isAlive()) {
                player.setDeltaMovement(dir.scale(SPEED * CARRY).add(carried.x * INERTIA, 0, carried.z * INERTIA));
                player.hurtMarked = true;
            }
            DASHING.remove(player.getUUID());
        });
    }

    private static void push(ServerLevel level, Player player, Vec3 dir, Vec3 carried) {
        player.setDeltaMovement(thrust(dir, carried));
        player.hurtMarked = true;
        player.resetFallDistance();
        Vec3 at = player.position();
        // afterimage: a violet silhouette left behind you, below eye level so it never blocks your own view
        Vec3 trail = at.subtract(dir.scale(1.2));
        for (double y = 0.1; y < 1.2; y += 0.35) {
            SkillFx.particles(level, ParticleTypes.REVERSE_PORTAL, trail.x, trail.y + y, trail.z, 2, 0.12, 0.08, 0.12, 0.0);
        }
        SkillFx.particles(level, ParticleTypes.END_ROD, trail.x, trail.y + 0.7, trail.z, 2, 0.15, 0.25, 0.15, 0.01);
        Vec3 side = new Vec3(-dir.z, 0, dir.x).normalize();
        var shoved = SkillFx.victims(player, player, at.add(0, 1, 0), 1.8);
        if (!shoved.isEmpty()) {
            dev.cogwit.agent.AgentService.openCombo(player,
                    net.minecraft.network.chat.Component.translatable("cogwit.combo.dash"));
        }
        for (LivingEntity mob : shoved) {
            double sign = mob.position().subtract(at).dot(side) >= 0 ? 1 : -1;
            mob.setDeltaMovement(mob.getDeltaMovement().add(side.scale(0.8 * sign)).add(dir.scale(0.3)).add(0, 0.25, 0));
            mob.hurtMarked = true;
        }
    }

    /** Ran into a wall: the thrust stops with a thud instead of sliding along it. */
    private static void impact(ServerLevel level, Player player, Vec3 dir) {
        player.setDeltaMovement(dir.scale(-0.2).add(0, 0.15, 0));
        player.hurtMarked = true;
        Vec3 at = player.position().add(dir.scale(0.5)).add(0, 1, 0);
        SkillFx.ring(level, at, dir, 0.8, 14, ParticleTypes.CRIT);
        level.playSound(null, player.blockPosition(), SoundEvents.MACE_SMASH_GROUND, SoundSource.PLAYERS, 0.6f, 1.3f);
        if (player instanceof ServerPlayer sp) {
            SkillFx.screen(sp, ScreenFxPayload.SHAKE, 0.4f, 6);
        }
    }

    public static void forget(UUID player) {
        DASHING.remove(player);
    }
}
