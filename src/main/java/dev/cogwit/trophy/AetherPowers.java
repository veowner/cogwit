package dev.cogwit.trophy;

import dev.cogwit.Speech;
import dev.cogwit.horizon.fx.SkillFx;
import dev.cogwit.network.ScreenFxPayload;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * The actives of the three Aether trophies: the Slider's ram, the Valkyrie Queen's lightning and the Sun Spirit's
 * ignition. Each method returns true if it fired and has no side effects when it cannot; cooldowns are
 * {@link TrophyService#castTrophy}'s business.
 */
public final class AetherPowers {
    // ram
    public static final int RAM_WINDUP_TICKS = 4, RAM_TICKS = 6;
    public static final double RAM_SPEED = 1.45, RAM_HIT_RADIUS = 2.3;
    public static final float RAM_DAMAGE = 8f;
    private static final DustParticleOptions SLIDER_GLOW = new DustParticleOptions(new Vector3f(1.0f, 0.15f, 0.10f), 1.2f);

    // lightning
    public static final int LIGHTNING_WINDUP_TICKS = 10;
    public static final double LIGHTNING_RANGE = 24, LIGHTNING_SPLASH = 3.5;
    public static final float LIGHTNING_DAMAGE = 14f, LIGHTNING_SPLASH_DAMAGE = 6f;

    // ignite
    public static final int IGNITE_WINDUP_TICKS = 6, IGNITE_SECONDS = 8;
    public static final double IGNITE_RADIUS = 8, IGNITE_WAVE_SPEED = 1.0;
    public static final float IGNITE_DAMAGE = 2f;
    private static final DustParticleOptions SUN_GOLD = new DustParticleOptions(new Vector3f(1.0f, 0.78f, 0.20f), 1.3f);

    private AetherPowers() {}

    // ---------------------------------------------------------------- Slider: ram

    /** The Slider's rush: a red glow and a lurch back, then a dash of about nine blocks that bowls over what it meets. */
    public static boolean ram(Player player) {
        if (!(player.level() instanceof ServerLevel level) || !player.isAlive()) {
            return false;
        }
        // wind-up: the carved stone glows red and the caster digs in
        SkillFx.ring(level, player.position().add(0, 0.15, 0), new Vec3(0, 1, 0), 1.3, 18, SLIDER_GLOW);
        SkillFx.particles(level, ParticleTypes.CRIT, player.getX(), player.getY() + 1, player.getZ(), 10, 0.4, 0.5, 0.4, 0.1);
        SkillFx.particles(level, new BlockParticleOption(ParticleTypes.BLOCK, Blocks.CHISELED_STONE_BRICKS.defaultBlockState()),
                player.getX(), player.getY() + 0.1, player.getZ(), 14, 0.5, 0.05, 0.5, 0.1);
        level.playSound(null, player.blockPosition(), SoundEvents.PISTON_CONTRACT, SoundSource.PLAYERS, 1.2f, 0.6f);
        level.playSound(null, player.blockPosition(), SoundEvents.ANVIL_PLACE, SoundSource.PLAYERS, 0.5f, 0.6f);
        TrophyScheduler.later(level, RAM_WINDUP_TICKS, () -> {
            if (!player.isAlive() || player.isRemoved() || player.level() != level) {
                return;
            }
            Vec3 look = player.getLookAngle();
            Vec3 flat = new Vec3(look.x, 0, look.z);
            Vec3 dir = flat.lengthSqr() < 1e-4 ? Vec3.directionFromRotation(0, player.getYRot()) : flat.normalize();
            level.playSound(null, player.blockPosition(), SoundEvents.PISTON_EXTEND, SoundSource.PLAYERS, 1.4f, 0.5f);
            level.playSound(null, player.blockPosition(), SoundEvents.IRON_GOLEM_ATTACK, SoundSource.PLAYERS, 1f, 0.5f);
            if (player instanceof ServerPlayer sp) {
                SkillFx.screen(sp, ScreenFxPayload.FOV_PUNCH, 0.8f, 10);
            }
            ramStep(level, player, dir, 0, new HashSet<>());
        });
        return true;
    }

    private static void ramStep(ServerLevel level, Player player, Vec3 dir, int step, Set<UUID> bowled) {
        if (!player.isAlive() || player.isRemoved() || player.level() != level) {
            return;
        }
        Vec3 push = dir.scale(RAM_SPEED);
        // a wall ahead ends the rush with a crash
        if (!level.noCollision(player, player.getBoundingBox().move(push.x, 0, push.z))) {
            crash(level, player, dir);
            return;
        }
        Vec3 v = player.getDeltaMovement();
        player.setDeltaMovement(push.x, player.onGround() ? 0 : v.y, push.z);
        player.hurtMarked = true;
        player.resetFallDistance();

        Vec3 at = player.position();
        SkillFx.particles(level, new BlockParticleOption(ParticleTypes.BLOCK, Blocks.STONE_BRICKS.defaultBlockState()),
                at.x, at.y + 0.1, at.z, 8, 0.4, 0.05, 0.4, 0.1);
        SkillFx.particles(level, SLIDER_GLOW, at.x, at.y + 1, at.z, 6, 0.4, 0.6, 0.4, 0);
        SkillFx.particles(level, ParticleTypes.CRIT, at.x + dir.x, at.y + 1, at.z + dir.z, 4, 0.3, 0.5, 0.3, 0.2);
        SkillFx.particles(level, ParticleTypes.CLOUD, at.x - dir.x * 0.5, at.y + 0.2, at.z - dir.z * 0.5, 2, 0.3, 0.05, 0.3, 0.02);

        boolean struck = false;
        for (LivingEntity target : SkillFx.victims(player, player, at.add(dir.scale(1.0)).add(0, 0.9, 0), RAM_HIT_RADIUS)) {
            if (!bowled.add(target.getUUID())) {
                continue;
            }
            struck = true;
            if (target.hurt(level.damageSources().indirectMagic(player, player), RAM_DAMAGE)) {
                // shoved along the dash and flipped up, so the ram reads as a blow, not a graze
                target.setDeltaMovement(target.getDeltaMovement().multiply(0.2, 0, 0.2).add(dir.x * 1.5, 0.45, dir.z * 1.5));
                target.hurtMarked = true;
                target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 30, 1, false, false));
                Vec3 c = target.getBoundingBox().getCenter();
                SkillFx.particles(level, ParticleTypes.CRIT, c.x, c.y, c.z, 12, 0.3, 0.4, 0.3, 0.3);
                SkillFx.particles(level, ParticleTypes.EXPLOSION, c.x, c.y, c.z, 1, 0, 0, 0, 0);
            }
            level.playSound(null, target.blockPosition(), SoundEvents.IRON_GOLEM_DAMAGE, SoundSource.PLAYERS, 1f, 0.6f);
            level.playSound(null, target.blockPosition(), SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.6f, 1.2f);
        }
        if (struck && player instanceof ServerPlayer sp) {
            SkillFx.screen(sp, ScreenFxPayload.SHAKE, 0.4f, 8);
        }
        if (step + 1 >= RAM_TICKS) {
            // the slide dies away instead of ending in a dead stop
            player.setDeltaMovement(push.x * 0.25, player.getDeltaMovement().y, push.z * 0.25);
            player.hurtMarked = true;
            return;
        }
        TrophyScheduler.later(level, 1, () -> ramStep(level, player, dir, step + 1, bowled));
    }

    private static void crash(ServerLevel level, Player player, Vec3 dir) {
        player.setDeltaMovement(0, Math.min(0, player.getDeltaMovement().y), 0);
        player.hurtMarked = true;
        Vec3 front = player.getEyePosition().add(dir.scale(0.9));
        BlockState wall = level.getBlockState(BlockPos.containing(front));
        if (wall.isAir()) {
            wall = Blocks.STONE.defaultBlockState();
        }
        SkillFx.particles(level, new BlockParticleOption(ParticleTypes.BLOCK, wall), front.x, front.y - 0.4, front.z, 36, 0.4,
                0.6, 0.4, 0.2);
        SkillFx.particles(level, ParticleTypes.EXPLOSION, front.x, front.y - 0.3, front.z, 1, 0, 0, 0, 0);
        level.playSound(null, player.blockPosition(), SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 1.2f, 0.5f);
        level.playSound(null, player.blockPosition(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 0.5f, 1.4f);
        if (player instanceof ServerPlayer sp) {
            SkillFx.screen(sp, ScreenFxPayload.SHAKE, 0.6f, 10);
        }
    }

    // ---------------------------------------------------------------- Valkyrie Queen: lightning

    /**
     * A bolt on the creature under the crosshair (else the one being fought, else the nearest hostile within 24
     * blocks): a short crackle gathers over it, then it is struck. The bolt is only light and thunder, so it never
     * burns the ground and never touches the caster or other players; the damage is dealt by the trophy.
     */
    public static boolean lightning(Player player) {
        if (!(player.level() instanceof ServerLevel level) || !player.isAlive()) {
            return false;
        }
        Optional<LivingEntity> target = TrophyAim.target(player, LIGHTNING_RANGE);
        if (target.isEmpty()) {
            Speech.hint(player, Component.translatable("cogwit.trophy.no_enemy_near"));
            return false;
        }
        LivingEntity mark = target.get();
        level.playSound(null, player.blockPosition(), SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 0.5f, 1.6f);
        level.playSound(null, mark.blockPosition(), SoundEvents.TRIDENT_THUNDER.value(), SoundSource.PLAYERS, 0.8f, 1.8f);
        Vec3 hand = player.getEyePosition().subtract(0, 0.3, 0);
        SkillFx.particles(level, ParticleTypes.ELECTRIC_SPARK, hand.x, hand.y, hand.z, 14, 0.4, 0.4, 0.4, 0.4);
        // the target is marked with a ring that closes, and sparks climb a column over it
        for (int t = 0; t < LIGHTNING_WINDUP_TICKS; t += 2) {
            int step = t;
            TrophyScheduler.later(level, t, () -> {
                if (!mark.isAlive()) {
                    return;
                }
                Vec3 at = mark.position();
                SkillFx.ring(level, at.add(0, 0.1, 0), new Vec3(0, 1, 0), 2.2 - step * 0.18, 14, ParticleTypes.ELECTRIC_SPARK);
                for (int h = 0; h < 6; h++) {
                    SkillFx.particles(level, ParticleTypes.ELECTRIC_SPARK, at.x, at.y + 2 + h * 1.5, at.z, 2, 0.15, 0.3, 0.15, 0.05);
                }
            });
        }
        TrophyScheduler.later(level, LIGHTNING_WINDUP_TICKS, () -> strike(level, player, mark));
        return true;
    }

    private static void strike(ServerLevel level, Player player, LivingEntity target) {
        if (!target.isAlive() || target.level() != level) {
            return;
        }
        Vec3 at = target.position();
        LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
        if (bolt != null) {
            bolt.setVisualOnly(true);
            bolt.moveTo(at);
            level.addFreshEntity(bolt);
        }
        // magic damage with the caster as attacker, so kills count for them
        if (target.hurt(level.damageSources().indirectMagic(player, player), LIGHTNING_DAMAGE) && target.isAlive()) {
            target.setDeltaMovement(target.getDeltaMovement().scale(0.3).add(0, 0.4, 0));
            target.hurtMarked = true;
            target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 1, false, false));
        }
        // the charge jumps on to anything hostile close to the first target
        Vec3 from = target.getBoundingBox().getCenter();
        for (LivingEntity near : SkillFx.victims(player, player, from, LIGHTNING_SPLASH)) {
            if (near == target) {
                continue;
            }
            near.hurt(level.damageSources().indirectMagic(player, player), LIGHTNING_SPLASH_DAMAGE);
            SkillFx.line(level, from, near.getBoundingBox().getCenter(), ParticleTypes.ELECTRIC_SPARK, 0.4);
        }
        Vec3 up = new Vec3(0, 1, 0);
        SkillFx.particles(level, ParticleTypes.FLASH, at.x, at.y + 1, at.z, 1, 0, 0, 0, 0);
        SkillFx.particles(level, ParticleTypes.ELECTRIC_SPARK, at.x, at.y + 0.8, at.z, 24, 0.3, 0.7, 0.3, 0.4);
        SkillFx.particles(level, ParticleTypes.END_ROD, at.x, at.y + 1, at.z, 12, 0.2, 0.5, 0.2, 0.3);
        SkillFx.ring(level, at.add(0, 0.1, 0), up, 1.4, 16, ParticleTypes.ELECTRIC_SPARK);
        TrophyScheduler.later(level, 2, () -> SkillFx.ring(level, at.add(0, 0.1, 0), up, 2.6, 22, ParticleTypes.ELECTRIC_SPARK));
        TrophyScheduler.later(level, 4, () -> SkillFx.ring(level, at.add(0, 0.1, 0), up, 3.6, 28, ParticleTypes.CLOUD));
        level.playSound(null, target.blockPosition(), SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.PLAYERS, 1.2f, 1.1f);
        SkillFx.screen(level, at, 28, ScreenFxPayload.FLASH, 0.35f, 6);
        if (player instanceof ServerPlayer sp) {
            SkillFx.screen(sp, ScreenFxPayload.SHAKE, 0.25f, 8);
        }
    }

    // ---------------------------------------------------------------- Sun Spirit: ignite

    /**
     * A sun rises at the caster's feet and a wave of fire rolls out to eight blocks; every hostile mob it reaches
     * catches fire for eight seconds. Only creatures burn: the wave never sets blocks alight.
     */
    public static boolean ignite(Player player) {
        if (!(player.level() instanceof ServerLevel level) || !player.isAlive()) {
            return false;
        }
        List<LivingEntity> targets = TrophyAim.hostiles(player, player.position().add(0, 1, 0), IGNITE_RADIUS);
        if (targets.isEmpty()) {
            Speech.hint(player, Component.translatable("cogwit.trophy.no_enemy_near"));
            return false;
        }
        Vec3 c = player.position();
        Vec3 up = new Vec3(0, 1, 0);
        level.playSound(null, player.blockPosition(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 1.2f, 1.8f);
        level.playSound(null, player.blockPosition(), SoundEvents.BLAZE_AMBIENT, SoundSource.PLAYERS, 1f, 0.6f);
        if (player instanceof ServerPlayer sp) {
            SkillFx.screen(sp, ScreenFxPayload.FOV_PULL, 0.2f, IGNITE_WINDUP_TICKS);
        }
        // wind-up: a sun sigil of eight rays draws itself on the ground and a ball of fire swells above the caster
        for (int t = 0; t < IGNITE_WINDUP_TICKS; t++) {
            int step = t;
            TrophyScheduler.later(level, t, () -> {
                if (!player.isAlive()) {
                    return;
                }
                Vec3 centre = player.position();
                SkillFx.ring(level, centre.add(0, 0.12, 0), up, 1.2 + step * 0.15, 20, SUN_GOLD);
                for (int ray = 0; ray < 8; ray++) {
                    double a = ray * Math.PI / 4 + step * 0.1;
                    Vec3 dir = new Vec3(Math.cos(a), 0, Math.sin(a));
                    Vec3 p = centre.add(dir.scale(0.5 + step * 0.35)).add(0, 0.12, 0);
                    SkillFx.particles(level, ParticleTypes.FLAME, p.x, p.y, p.z, 1, 0.02, 0.02, 0.02, 0.01);
                }
                SkillFx.particles(level, ParticleTypes.FLAME, centre.x, centre.y + 2.2, centre.z, 6 + step * 2, 0.15 + step * 0.04,
                        0.15 + step * 0.04, 0.15 + step * 0.04, 0.02);
            });
        }
        // the wave: a ring of fire a block further out each tick; mobs catch fire as it reaches them
        for (int r = 1; r <= IGNITE_RADIUS / IGNITE_WAVE_SPEED + 1; r++) {
            int radius = r;
            TrophyScheduler.later(level, IGNITE_WINDUP_TICKS + r, () -> {
                double rad = radius * IGNITE_WAVE_SPEED;
                SkillFx.ring(level, c.add(0, 0.2, 0), up, rad, 10 + radius * 5, ParticleTypes.FLAME);
                SkillFx.ring(level, c.add(0, 0.5, 0), up, rad, 6 + radius * 3, ParticleTypes.LAVA);
                level.playSound(null, BlockPos.containing(c), SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 0.5f,
                        0.6f + radius * 0.05f);
            });
        }
        level.playSound(null, player.blockPosition(), SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 1.4f, 0.5f);
        for (LivingEntity target : targets) {
            int arrives = IGNITE_WINDUP_TICKS + (int) Math.ceil(target.distanceTo(player) / IGNITE_WAVE_SPEED);
            TrophyScheduler.later(level, arrives, () -> {
                if (!target.isAlive() || target.level() != level) {
                    return;
                }
                // the first touch is a real hit, so the caster gets the kill even if the fire finishes it
                target.hurt(level.damageSources().indirectMagic(player, player), IGNITE_DAMAGE);
                target.igniteForSeconds(IGNITE_SECONDS);
                Vec3 t = target.position();
                for (int h = 0; h < 4; h++) {
                    SkillFx.particles(level, ParticleTypes.FLAME, t.x, t.y + h * 0.5, t.z, 4, 0.25, 0.1, 0.25, 0.04);
                }
                level.playSound(null, target.blockPosition(), SoundEvents.GENERIC_BURN, SoundSource.PLAYERS, 0.7f, 1f);
            });
        }
        if (player instanceof ServerPlayer sp) {
            TrophyScheduler.later(level, IGNITE_WINDUP_TICKS, () -> SkillFx.screen(sp, ScreenFxPayload.SHAKE, 0.3f, 8));
        }
        return true;
    }
}
