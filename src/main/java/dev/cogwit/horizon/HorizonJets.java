package dev.cogwit.horizon;

import dev.cogwit.horizon.fx.SkillFx;
import dev.cogwit.network.LandingPayload;
import dev.cogwit.network.ScreenFxPayload;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import net.neoforged.neoforge.network.PacketDistributor;

/** Fire from the boot soles and the nozzles on the sides of the leggings, and the heavy landing of the boots. */
public final class HorizonJets {
    /** Ground this close under a fast fall lights the boots up to brake. */
    private static final double BRAKE_HEIGHT = 3.5;

    private HorizonJets() {}

    /** The player's right, flat: Minecraft faces (-sin yaw, cos yaw), so the right hand is (-cos yaw, -sin yaw). */
    private static Vec3 right(Player player) {
        double yaw = Math.toRadians(player.yBodyRot);
        return new Vec3(-Math.cos(yaw), 0, -Math.sin(yaw));
    }

    /** One tick of thrust: flames down from both soles, and from the hip nozzles when {@code hips}. */
    public static void burn(ServerLevel level, Player player, boolean hips, float power) {
        Vec3 right = right(player);
        Vec3 base = player.position();
        for (int side = -1; side <= 1; side += 2) {
            Vec3 sole = base.add(right.scale(0.12 * side)).add(0, 0.02, 0);
            level.sendParticles(ParticleTypes.FLAME, sole.x, sole.y, sole.z, 0, 0, -1, 0, 0.07 + 0.09 * power);
            level.sendParticles(ParticleTypes.SMALL_FLAME, sole.x, sole.y - 0.1, sole.z, 0, 0, -1, 0, 0.2);
            if (hips) {
                Vec3 nozzle = base.add(right.scale(0.34 * side)).add(0, 0.62, 0);
                Vec3 out = right.scale(0.35 * side).add(0, -1, 0).normalize();
                level.sendParticles(ParticleTypes.FLAME, nozzle.x, nozzle.y, nozzle.z, 0, out.x, out.y, out.z,
                        0.05 + 0.07 * power);
            }
        }
        if (power > 0.6f && player.tickCount % 3 == 0) {
            level.sendParticles(ParticleTypes.SMOKE, base.x, base.y - 0.3, base.z, 2, 0.12, 0.05, 0.12, 0.01);
        }
        if (player.tickCount % 16 == 0) {
            level.playSound(null, player.blockPosition(), SoundEvents.BLAZE_BURN, SoundSource.PLAYERS, 0.15f, 1.6f);
        }
    }

    /** Every tick for a wearer of the boots: flight thrust, and the brake burn just before a hard landing. */
    static void tick(ServerLevel level, Player player, int pieces) {
        if (player.getAbilities().flying) {
            double speed = player.getDeltaMovement().length();
            burn(level, player, Horizon.has(pieces, Horizon.Feature.THRUSTERS), (float) Math.min(1, speed / 1.2));
            return;
        }
        if (player.onGround() || player.isInWater() || player.isFallFlying()
                || player.fallDistance < Horizon.LANDING_FROM || player.getDeltaMovement().y > -0.5) {
            return;
        }
        Vec3 from = player.position();
        boolean groundClose = !level.noCollision(player, player.getBoundingBox().move(0, -BRAKE_HEIGHT, 0));
        if (groundClose) {
            burn(level, player, false, 1f);
            if (player.tickCount % 4 == 0) {
                level.playSound(null, BlockPos.containing(from), SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS,
                        0.25f, 1.7f);
            }
        }
    }

    /** The boots hit the ground hard: a shockwave throws mobs back, debris flies, everyone nearby sees the crouch. */
    public static void land(Player player, float strength) {
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        Vec3 at = player.position();
        double radius = Horizon.landingRadius(strength);
        float damage = Horizon.landingDamage(strength);
        List<LivingEntity> struck = SkillFx.victims(player, player, at, radius);
        if (!struck.isEmpty()) {
            dev.cogwit.agent.AgentService.openCombo(player,
                    net.minecraft.network.chat.Component.translatable("cogwit.combo.landing"));
        }
        for (LivingEntity target : struck) {
            double falloff = 1 - Math.min(1, target.position().distanceTo(at) / radius) * 0.5;
            target.hurt(player.damageSources().mobAttack(player), (float) (damage * falloff));
            target.knockback(0.5 + 0.9 * strength * falloff, at.x - target.getX(), at.z - target.getZ());
            target.setDeltaMovement(target.getDeltaMovement().add(0, 0.25 + 0.2 * strength, 0));
            target.hurtMarked = true;
        }
        BlockState ground = level.getBlockState(player.blockPosition().below());
        if (ground.isAir()) {
            ground = Blocks.STONE.defaultBlockState();
        }
        BlockParticleOption debris = new BlockParticleOption(ParticleTypes.BLOCK, ground);
        int count = 24 + (int) (40 * strength);
        for (int i = 0; i < count; i++) {
            double angle = i * Math.PI * 2 / count;
            double dx = Math.cos(angle), dz = Math.sin(angle);
            double r = 0.4 + level.random.nextDouble() * 0.6;
            level.sendParticles(debris, at.x + dx * r, at.y + 0.1, at.z + dz * r, 0, dx, 0.6 + level.random.nextDouble(),
                    dz, 0.3 + 0.3 * strength);
        }
        SkillFx.ring(level, at.add(0, 0.15, 0), new Vec3(0, 1, 0), radius * 0.6, 28, ParticleTypes.POOF);
        burn(level, player, false, 1f);
        if (strength > 0.6f) {
            level.playSound(null, player.blockPosition(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS,
                    0.45f, 1.4f);
        }
        level.playSound(null, player.blockPosition(), SoundEvents.ANVIL_LAND, SoundSource.PLAYERS,
                0.35f + 0.35f * strength, 0.55f);
        level.playSound(null, player.blockPosition(), HorizonSounds.PLATE.get(), SoundSource.PLAYERS, 0.9f, 0.8f);
        if (player instanceof ServerPlayer sp) {
            SkillFx.screen(sp, ScreenFxPayload.SHAKE, 0.35f + 0.65f * strength, 8 + (int) (8 * strength));
        }
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(player, new LandingPayload(player.getId(), strength));
    }
}
