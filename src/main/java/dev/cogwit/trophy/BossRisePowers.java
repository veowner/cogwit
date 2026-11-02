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
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * The actives of the five Block Factory's Bosses trophies: Ashlord's fiery dive, Nerakyss' tentacles, Sirok's sand
 * eruption, Helvar's rift slashes and Skor's ground pound. Everything is built from vanilla particles, sounds and
 * displays, so the powers work with or without the mod. Each method returns true if it fired and has no side effects
 * when it cannot; cooldowns are {@link TrophyService#castTrophy}'s business.
 */
public final class BossRisePowers {
    // ashlord: infernal dive
    public static final int DIVE_WINDUP = 4, DIVE_RISE_MIN = 5, DIVE_RISE_MAX = 14, DIVE_MAX = 36;
    public static final double DIVE_RANGE = 14, DIVE_SPEED = 1.5, DIVE_RADIUS = 4.8;
    public static final float DIVE_DAMAGE = 12f, DIVE_STRAFE_DAMAGE = 4f;
    private static final DustParticleOptions EMBER = new DustParticleOptions(new Vector3f(1.0f, 0.42f, 0.08f), 1.4f);
    private static final DustParticleOptions ASH = new DustParticleOptions(new Vector3f(0.28f, 0.24f, 0.24f), 1.2f);

    // nerakyss: tentacles
    public static final int TENTACLES = 4, TENTACLE_WINDUP = 10, TENTACLE_GAP = 3, GRAB_TICKS = 44, GRAB_PULSE = 10;
    public static final double TENTACLE_RANGE = 12, GRAB_LIFT = 2.2;
    public static final float GRAB_DAMAGE = 2.5f, GRAB_SLAM_DAMAGE = 5f;

    // sirok: sand eruption
    public static final int SAND_TARGETS = 3;
    public static final double SAND_RANGE = 14, RIDGE_SPEED = 1.1, ERUPT_RADIUS = 2.6, SAND_WAVE_RADIUS = 5;
    public static final float ERUPT_DAMAGE = 10f, SAND_WAVE_DAMAGE = 5f;
    public static final int ERUPT_HOLD_TICKS = 26;

    // helvar: rift slashes
    public static final int RIFTS = 4, RIFT_OPEN_TICKS = 8, RIFT_SECOND_SLASH = 5;
    public static final double RIFT_RANGE = 14, RIFT_LENGTH = 4.2, RIFT_REACH = 1.5;
    public static final float RIFT_DAMAGE = 7f;
    private static final DustParticleOptions SOUL_BLUE = new DustParticleOptions(new Vector3f(0.20f, 0.80f, 0.85f), 1.2f);

    // skor: ground pound
    public static final int POUND_HOP_MIN = 3, POUND_HOP_MAX = 26, POUND_WAVES = 3, POUND_RAYS = 6, POUND_HOLD_TICKS = 24;
    public static final double POUND_RADIUS = 7, POUND_FIRST_WAVE = 2.6, POUND_WAVE_STEP = 2.2;
    public static final float POUND_DAMAGE = 9f;

    private BossRisePowers() {}

    private static Component noEnemy() {
        return Component.translatable("cogwit.trophy.no_enemy_near");
    }

    // ---------------------------------------------------------------- Ashlord: infernal dive

    /**
     * Ashlord's dive: the caster spreads burning wings and springs into the air towards the nearest enemy, then plunges
     * onto it breathing fire. The landing is a burst of flame that burns and throws back everything close. Only
     * creatures are set alight, never the ground.
     */
    public static boolean infernalDive(Player player) {
        if (!(player.level() instanceof ServerLevel level) || !player.isAlive()) {
            return false;
        }
        Optional<LivingEntity> target = TrophyAim.target(player, DIVE_RANGE);
        if (target.isEmpty()) {
            Speech.hint(player, noEnemy());
            return false;
        }
        level.playSound(null, player.blockPosition(), SoundEvents.ENDER_DRAGON_GROWL, SoundSource.PLAYERS, 0.9f, 0.8f);
        level.playSound(null, player.blockPosition(), SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 1.2f, 0.5f);
        if (player instanceof ServerPlayer sp) {
            SkillFx.screen(sp, ScreenFxPayload.FOV_PULL, 0.2f, DIVE_WINDUP);
        }
        for (int t = 0; t < DIVE_WINDUP; t++) {
            int step = t;
            TrophyScheduler.later(level, t, () -> {
                if (PowerKit.live(level, player)) {
                    wings(level, player, 0.4 + step * 0.2);
                    SkillFx.ring(level, player.position().add(0, 0.15, 0), new Vec3(0, 1, 0), 1.6 - step * 0.3, 14, ParticleTypes.FLAME);
                }
            });
        }
        LivingEntity mark = target.get();
        TrophyScheduler.later(level, DIVE_WINDUP, () -> {
            if (!PowerKit.live(level, player)) {
                return;
            }
            Vec3 to = mark.position().subtract(player.position());
            Vec3 dir = PowerKit.flat(to, player);
            double speed = Math.min(0.6, Math.hypot(to.x, to.z) * 0.07 + 0.2);
            player.setDeltaMovement(dir.x * speed, 0.95, dir.z * speed);
            player.hurtMarked = true;
            level.playSound(null, player.blockPosition(), SoundEvents.ENDER_DRAGON_FLAP, SoundSource.PLAYERS, 1.2f, 0.7f);
            SkillFx.particles(level, ParticleTypes.FLAME, player.getX(), player.getY() + 0.2, player.getZ(), 20, 0.6, 0.1, 0.6, 0.12);
            diveStep(level, player, mark, mark.position(), new HashSet<>(), 0, false);
        });
        return true;
    }

    private static void wings(ServerLevel level, Player player, double spread) {
        Vec3 look = player.getLookAngle();
        Vec3 flat = PowerKit.flat(look, player);
        Vec3 side = new Vec3(-flat.z, 0, flat.x);
        Vec3 base = player.position().add(0, 1.3, 0).subtract(flat.scale(0.2));
        for (int sign = -1; sign <= 1; sign += 2) {
            for (int i = 1; i <= 6; i++) {
                double t = i / 6.0;
                Vec3 p = base.add(side.scale(sign * (0.3 + t * 1.6 * spread))).subtract(flat.scale(t * 0.7))
                        .add(0, Math.sin(t * Math.PI * 0.8) * 0.9 * spread - t * 0.3, 0);
                SkillFx.particles(level, i % 2 == 0 ? EMBER : ParticleTypes.FLAME, p.x, p.y, p.z, 1, 0.04, 0.04, 0.04, 0);
                if (i == 6) {
                    SkillFx.particles(level, ASH, p.x, p.y, p.z, 1, 0.05, 0.05, 0.05, 0);
                }
            }
        }
    }

    private static void diveStep(ServerLevel level, Player player, LivingEntity mark, Vec3 spot, Set<UUID> scorched, int t,
            boolean diving) {
        if (!PowerKit.live(level, player)) {
            return;
        }
        player.resetFallDistance();
        Vec3 goal = PowerKit.live(level, mark) ? mark.position() : spot;
        Vec3 at = player.position();
        Vec3 v = player.getDeltaMovement();
        if (!diving) {
            Vec3 to = goal.subtract(at);
            double flat = Math.hypot(to.x, to.z);
            if (flat > 0.8) {
                double speed = Math.min(0.6, flat * 0.07 + 0.2);
                player.setDeltaMovement(to.x / flat * speed, v.y, to.z / flat * speed);
                player.hurtMarked = true;
            }
            diving = t >= DIVE_RISE_MIN && v.y <= 0.05 || t >= DIVE_RISE_MAX;
            if (diving) {
                level.playSound(null, player.blockPosition(), SoundEvents.ENDER_DRAGON_SHOOT, SoundSource.PLAYERS, 1.2f, 0.8f);
            }
        } else {
            Vec3 to = goal.add(0, 0.5, 0).subtract(at);
            Vec3 plunge = to.lengthSqr() < 1e-4 ? new Vec3(0, -1, 0) : to.normalize().scale(DIVE_SPEED);
            player.setDeltaMovement(plunge);
            player.hurtMarked = true;
            // the breath streams ahead of the plunge
            Vec3 look = plunge.normalize();
            for (int i = 0; i < 8; i++) {
                double a = level.random.nextDouble() * Math.PI * 2, r = level.random.nextDouble() * 0.35;
                Vec3 d = look.add(Math.cos(a) * r, Math.sin(a) * r, Math.cos(a + 1) * r).normalize();
                SkillFx.particles(level, i % 3 == 0 ? ParticleTypes.LAVA : ParticleTypes.FLAME, at.x + look.x, at.y + 1 + look.y,
                        at.z + look.z, 0, d.x, d.y, d.z, 0.5);
            }
            for (LivingEntity target : SkillFx.victims(player, player, at.add(0, 1, 0), 2.6)) {
                if (scorched.add(target.getUUID()) && PowerKit.hit(level, player, target, DIVE_STRAFE_DAMAGE)) {
                    target.igniteForSeconds(4);
                    PowerKit.shove(target, plunge, 0.4, 0.2);
                }
            }
        }
        wings(level, player, 1.0);
        SkillFx.particles(level, EMBER, at.x, at.y + 1, at.z, 4, 0.3, 0.5, 0.3, 0);
        SkillFx.particles(level, ParticleTypes.LARGE_SMOKE, at.x, at.y + 1, at.z, 1, 0.2, 0.3, 0.2, 0.02);
        boolean landed = diving && t > DIVE_RISE_MIN && (player.onGround() || player.verticalCollisionBelow
                || goal.add(0, 1, 0).distanceTo(at.add(0, 1, 0)) < 1.8);
        if (landed || t >= DIVE_MAX) {
            impact(level, player, scorched);
            return;
        }
        boolean next = diving;
        TrophyScheduler.later(level, 1, () -> diveStep(level, player, mark, spot, scorched, t + 1, next));
    }

    private static void impact(ServerLevel level, Player player, Set<UUID> scorched) {
        Vec3 at = player.position();
        player.resetFallDistance();
        player.setDeltaMovement(0, 0, 0);
        player.hurtMarked = true;
        SkillFx.particles(level, ParticleTypes.EXPLOSION, at.x, at.y + 0.4, at.z, 2, 0.3, 0.1, 0.3, 0);
        SkillFx.particles(level, ParticleTypes.FLASH, at.x, at.y + 0.5, at.z, 1, 0, 0, 0, 0);
        SkillFx.particles(level, ParticleTypes.FLAME, at.x, at.y + 0.3, at.z, 60, 1.2, 0.3, 1.2, 0.25);
        SkillFx.particles(level, ParticleTypes.LAVA, at.x, at.y + 0.5, at.z, 12, 1, 0.3, 1, 0);
        SkillFx.particles(level, ParticleTypes.LARGE_SMOKE, at.x, at.y + 0.6, at.z, 14, 1, 0.4, 1, 0.08);
        SkillFx.particles(level, ASH, at.x, at.y + 0.6, at.z, 20, 1.3, 0.5, 1.3, 0.1);
        for (int ring = 1; ring <= 4; ring++) {
            int stage = ring;
            TrophyScheduler.later(level, ring - 1, () -> SkillFx.ring(level, at.add(0, 0.2, 0), new Vec3(0, 1, 0),
                    stage * DIVE_RADIUS / 4, 10 + stage * 7, stage % 2 == 0 ? EMBER : ParticleTypes.FLAME));
        }
        level.playSound(null, player.blockPosition(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 0.8f, 1.2f);
        level.playSound(null, player.blockPosition(), SoundEvents.DRAGON_FIREBALL_EXPLODE, SoundSource.PLAYERS, 1.3f, 0.8f);
        level.playSound(null, player.blockPosition(), SoundEvents.MACE_SMASH_GROUND, SoundSource.PLAYERS, 1f, 0.7f);
        SkillFx.screen(level, at, 24, ScreenFxPayload.SHAKE, 0.55f, 10);
        SkillFx.screen(level, at, 24, ScreenFxPayload.FLASH, 0.3f, 6);
        for (LivingEntity victim : SkillFx.victims(player, player, at.add(0, 1, 0), DIVE_RADIUS + 1)) {
            double flat = Math.hypot(victim.getX() - at.x, victim.getZ() - at.z);
            if (flat > DIVE_RADIUS + victim.getBbWidth() * 0.5 || victim.getY() > at.y + 3.5) {
                continue;
            }
            float falloff = (float) (1 - 0.4 * flat / DIVE_RADIUS);
            if (PowerKit.hit(level, player, victim, DIVE_DAMAGE * falloff)) {
                victim.igniteForSeconds(6);
                PowerKit.shove(victim, victim.position().subtract(at), 0.8 * falloff + 0.2, 0.55);
                Vec3 c = PowerKit.centre(victim);
                SkillFx.particles(level, ParticleTypes.FLAME, c.x, c.y, c.z, 12, 0.3, 0.5, 0.3, 0.1);
            }
        }
    }

    // ---------------------------------------------------------------- Nerakyss: tentacles

    /**
     * Tentacles burst from the ground under the four nearest enemies, wrap them, hold them in the air for two seconds
     * squeezing and finally dash them back to the ground. The tentacles are only scenery and sink away.
     */
    public static boolean tentacles(Player player) {
        if (!(player.level() instanceof ServerLevel level) || !player.isAlive()) {
            return false;
        }
        List<LivingEntity> targets = new ArrayList<>();
        List<Vec3> floors = new ArrayList<>();
        for (LivingEntity mob : PowerKit.marks(player, TENTACLE_RANGE, 12)) {
            Vec3 floor = TrophyAim.ground(level, mob.position().add(0, 0.6, 0));
            // a tentacle needs ground close under the victim
            if (mob.getY() - floor.y <= 3 && targets.size() < TENTACLES) {
                targets.add(mob);
                floors.add(floor);
            }
        }
        if (targets.isEmpty()) {
            Speech.hint(player, noEnemy());
            return false;
        }
        level.playSound(null, player.blockPosition(), SoundEvents.DROWNED_AMBIENT_WATER, SoundSource.PLAYERS, 1.4f, 0.5f);
        level.playSound(null, player.blockPosition(), SoundEvents.PLAYER_SPLASH_HIGH_SPEED, SoundSource.PLAYERS, 1f, 0.5f);
        if (player instanceof ServerPlayer sp) {
            SkillFx.screen(sp, ScreenFxPayload.SHAKE, 0.25f, TENTACLE_WINDUP + TENTACLE_GAP * targets.size());
        }
        for (int i = 0; i < targets.size(); i++) {
            LivingEntity mark = targets.get(i);
            Vec3 floor = floors.get(i);
            int delay = i * TENTACLE_GAP;
            for (int t = 0; t < TENTACLE_WINDUP; t += 2) {
                int step = t;
                TrophyScheduler.later(level, delay + t, () -> {
                    SkillFx.ring(level, floor.add(0, 0.1, 0), new Vec3(0, 1, 0), 1.9 - step * 0.14, 12, ParticleTypes.SPLASH);
                    SkillFx.particles(level, ParticleTypes.BUBBLE_POP, floor.x, floor.y + 0.3, floor.z, 4, 0.5, 0.2, 0.5, 0.02);
                    SkillFx.particles(level, ParticleTypes.SQUID_INK, floor.x, floor.y + 0.2, floor.z, 1, 0.4, 0.1, 0.4, 0.01);
                    level.playSound(null, BlockPos.containing(floor), SoundEvents.BUBBLE_COLUMN_BUBBLE_POP, SoundSource.PLAYERS, 0.8f,
                            0.6f + step * 0.05f);
                });
            }
            TrophyScheduler.later(level, delay + TENTACLE_WINDUP, () -> {
                if (PowerKit.live(level, player) && PowerKit.live(level, mark)) {
                    erupt(level, player, mark, floor);
                }
            });
        }
        return true;
    }

    private static void erupt(ServerLevel level, Player player, LivingEntity victim, Vec3 floor) {
        // three segments that curl more towards the tip
        double[] width = {0.8, 0.6, 0.42}, height = {1.5, 1.4, 1.2}, curl = {0.1, 0.42, 0.78};
        BlockState[] block = {Blocks.DARK_PRISMARINE.defaultBlockState(), Blocks.PRISMARINE.defaultBlockState(),
                Blocks.SEA_LANTERN.defaultBlockState()};
        double bend = level.random.nextDouble() * Math.PI * 2;
        double dx = Math.cos(bend), dz = Math.sin(bend);
        List<Display.BlockDisplay> parts = new ArrayList<>();
        Vec3 foot = floor.add(0, -0.1, 0);
        for (int k = 0; k < 3; k++) {
            float[] lean = PowerKit.lean(dx, dz, curl[k]);
            parts.add(DisplayFx.rise(level, block[k], foot, (float) width[k], (float) height[k], 0f, lean[0], lean[1], 4));
            foot = PowerKit.tip(foot, dx, dz, curl[k], height[k] * 0.95);
        }
        SkillFx.particles(level, ParticleTypes.SPLASH, floor.x, floor.y + 0.4, floor.z, 60, 0.7, 0.5, 0.7, 0.3);
        SkillFx.particles(level, ParticleTypes.BUBBLE, floor.x, floor.y + 0.6, floor.z, 20, 0.5, 0.6, 0.5, 0.1);
        SkillFx.particles(level, ParticleTypes.SQUID_INK, floor.x, floor.y + 0.8, floor.z, 12, 0.4, 0.5, 0.4, 0.05);
        SkillFx.ring(level, floor.add(0, 0.2, 0), new Vec3(0, 1, 0), 1.8, 16, ParticleTypes.CLOUD);
        level.playSound(null, BlockPos.containing(floor), SoundEvents.GUARDIAN_FLOP, SoundSource.PLAYERS, 1.4f, 0.5f);
        level.playSound(null, BlockPos.containing(floor), SoundEvents.PLAYER_SPLASH_HIGH_SPEED, SoundSource.PLAYERS, 1.2f, 0.6f);
        level.playSound(null, BlockPos.containing(floor), SoundEvents.SQUID_SQUIRT, SoundSource.PLAYERS, 1f, 0.5f);
        SkillFx.screen(level, floor, 20, ScreenFxPayload.SHAKE, 0.25f, 6);
        // it closes round the victim and lifts it
        PowerKit.hit(level, player, victim, GRAB_DAMAGE);
        grab(level, player, victim, floor, parts, 1);
    }

    private static void grab(ServerLevel level, Player player, LivingEntity victim, Vec3 floor, List<Display.BlockDisplay> parts,
            int t) {
        boolean over = t >= GRAB_TICKS || !PowerKit.live(level, victim) || !PowerKit.live(level, player);
        if (over) {
            release(level, player, victim, floor, parts);
            return;
        }
        PowerKit.stop(victim);
        double lift = GRAB_LIFT * Math.min(1, t / 8.0);
        double wanted = floor.y + lift;
        double dy = Math.min(0.5, Math.max(-0.5, wanted - victim.getY()));
        // never into a ceiling
        if (!level.noCollision(victim, victim.getBoundingBox().move(0, dy, 0))) {
            dy = 0;
        }
        victim.teleportTo(floor.x + (victim.getX() - floor.x) * 0.5, victim.getY() + dy,
                floor.z + (victim.getZ() - floor.z) * 0.5);
        victim.setDeltaMovement(Vec3.ZERO);
        victim.resetFallDistance();
        victim.hurtMarked = true;
        victim.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 6, 6, false, false));
        Vec3 c = PowerKit.centre(victim);
        SkillFx.particles(level, ParticleTypes.FALLING_WATER, c.x, c.y + victim.getBbHeight() * 0.5, c.z, 3, 0.4, 0.2, 0.4, 0);
        SkillFx.particles(level, ParticleTypes.BUBBLE, c.x, c.y, c.z, 2, 0.3, 0.4, 0.3, 0.02);
        if (t % GRAB_PULSE == 0) {
            PowerKit.hit(level, player, victim, GRAB_DAMAGE);
            SkillFx.particles(level, ParticleTypes.SQUID_INK, c.x, c.y, c.z, 6, 0.3, 0.4, 0.3, 0.05);
            SkillFx.particles(level, ParticleTypes.SPLASH, c.x, c.y, c.z, 10, 0.3, 0.4, 0.3, 0.2);
            level.playSound(null, victim.blockPosition(), SoundEvents.GUARDIAN_HURT, SoundSource.PLAYERS, 0.8f, 0.6f);
            level.playSound(null, victim.blockPosition(), SoundEvents.SLIME_SQUISH, SoundSource.PLAYERS, 0.8f, 0.5f);
        }
        TrophyScheduler.later(level, 1, () -> grab(level, player, victim, floor, parts, t + 1));
    }

    private static void release(ServerLevel level, Player player, LivingEntity victim, Vec3 floor, List<Display.BlockDisplay> parts) {
        double[] widths = {0.8, 0.6, 0.42};
        for (int k = 0; k < parts.size(); k++) {
            DisplayFx.sink(level, parts.get(k), (float) widths[k], 5);
        }
        SkillFx.particles(level, ParticleTypes.SPLASH, floor.x, floor.y + 1, floor.z, 40, 0.6, 0.8, 0.6, 0.3);
        level.playSound(null, BlockPos.containing(floor), SoundEvents.PLAYER_SPLASH, SoundSource.PLAYERS, 1.2f, 0.6f);
        if (PowerKit.live(level, victim)) {
            // dashed down: it lands hard, and the fall is paid for by the tentacle
            victim.setDeltaMovement(0, -1.1, 0);
            victim.hurtMarked = true;
            victim.resetFallDistance();
            if (player.isAlive()) {
                PowerKit.hit(level, player, victim, GRAB_SLAM_DAMAGE);
            }
            Vec3 c = PowerKit.centre(victim);
            SkillFx.particles(level, ParticleTypes.SPLASH, c.x, c.y, c.z, 24, 0.5, 0.5, 0.5, 0.3);
            SkillFx.particles(level, ParticleTypes.CLOUD, victim.getX(), floor.y + 0.2, victim.getZ(), 8, 0.4, 0.1, 0.4, 0.05);
            TrophyScheduler.later(level, 2, victim::resetFallDistance);
        }
    }

    // ---------------------------------------------------------------- Sirok: sand eruption

    /**
     * Sirok burrows: a ridge of sand races under the ground from the caster to each of the three nearest enemies and
     * bursts out under them in a column of sand and fangs, throwing them up and blinding them. A wave of sand rolls on
     * from the eruption. The fangs are only scenery and sink away.
     */
    public static boolean sandEruption(Player player) {
        if (!(player.level() instanceof ServerLevel level) || !player.isAlive()) {
            return false;
        }
        List<LivingEntity> marks = PowerKit.marks(player, SAND_RANGE, SAND_TARGETS);
        if (marks.isEmpty()) {
            Speech.hint(player, noEnemy());
            return false;
        }
        level.playSound(null, player.blockPosition(), SoundEvents.WARDEN_DIG, SoundSource.PLAYERS, 1.2f, 0.7f);
        level.playSound(null, player.blockPosition(), SoundEvents.SAND_BREAK, SoundSource.PLAYERS, 1.2f, 0.5f);
        if (player instanceof ServerPlayer sp) {
            SkillFx.screen(sp, ScreenFxPayload.SHAKE, 0.25f, 14);
        }
        Vec3 start = player.position();
        for (int i = 0; i < marks.size(); i++) {
            LivingEntity mark = marks.get(i);
            double dist = mark.position().distanceTo(start);
            int steps = (int) Math.max(5, Math.min(22, Math.ceil(dist / RIDGE_SPEED)));
            int delay = i * 2;
            TrophyScheduler.later(level, delay, () -> ridge(level, player, mark, start, 0, steps));
        }
        return true;
    }

    private static void ridge(ServerLevel level, Player player, LivingEntity mark, Vec3 start, int step, int steps) {
        if (!PowerKit.live(level, player)) {
            return;
        }
        Vec3 end = PowerKit.live(level, mark) ? mark.position() : start;
        double k = (step + 1) / (double) steps;
        Vec3 p = TrophyAim.ground(level, start.lerp(end, k).add(0, 1.5, 0));
        BlockState floor = level.getBlockState(BlockPos.containing(p).below());
        var grit = new BlockParticleOption(ParticleTypes.BLOCK, floor.isAir() ? Blocks.SAND.defaultBlockState() : floor);
        // a moving mound: grit thrown up at its head, a trail of dust behind and the earth shaking
        SkillFx.particles(level, grit, p.x, p.y + 0.4, p.z, 14, 0.4, 0.25, 0.4, 0.15);
        SkillFx.particles(level, ParticleTypes.POOF, p.x, p.y + 0.2, p.z, 2, 0.3, 0.05, 0.3, 0.02);
        Vec3 back = start.lerp(end, Math.max(0, k - 0.12));
        SkillFx.particles(level, ParticleTypes.CLOUD, back.x, p.y + 0.15, back.z, 2, 0.35, 0.03, 0.35, 0.01);
        if (step % 3 == 0) {
            level.playSound(null, BlockPos.containing(p), SoundEvents.SAND_BREAK, SoundSource.PLAYERS, 0.9f, 0.5f);
            level.playSound(null, BlockPos.containing(p), SoundEvents.GRAVEL_STEP, SoundSource.PLAYERS, 0.9f, 0.5f);
        }
        if (step + 1 >= steps) {
            Vec3 spot = TrophyAim.ground(level, end.add(0, 1.5, 0));
            eruption(level, player, spot);
            return;
        }
        TrophyScheduler.later(level, 1, () -> ridge(level, player, mark, start, step + 1, steps));
    }

    private static void eruption(ServerLevel level, Player player, Vec3 spot) {
        BlockState floor = level.getBlockState(BlockPos.containing(spot).below());
        var grit = new BlockParticleOption(ParticleTypes.BLOCK, floor.isAir() ? Blocks.SAND.defaultBlockState() : floor);
        var sand = new BlockParticleOption(ParticleTypes.BLOCK, Blocks.SAND.defaultBlockState());
        List<Display.BlockDisplay> parts = new ArrayList<>();
        List<Float> widths = new ArrayList<>();
        // the worm's body, and a ring of sandstone fangs round it
        parts.add(DisplayFx.rise(level, Blocks.SAND.defaultBlockState(), spot.add(0, -0.1, 0), 1.9f, 3.8f,
                level.random.nextFloat() * 1.5f, 0f, 0f, 4));
        widths.add(1.9f);
        double offset = level.random.nextDouble() * Math.PI;
        for (int i = 0; i < 6; i++) {
            double a = offset + i * Math.PI * 2 / 6;
            Vec3 foot = spot.add(Math.cos(a) * 1.5, -0.1, Math.sin(a) * 1.5);
            float[] lean = PowerKit.lean(Math.cos(a), Math.sin(a), 0.38);
            BlockState block = i % 2 == 0 ? Blocks.SANDSTONE.defaultBlockState() : Blocks.CUT_SANDSTONE.defaultBlockState();
            parts.add(DisplayFx.rise(level, block, foot, 0.55f, 2.3f + level.random.nextFloat() * 0.5f, (float) a, lean[0], lean[1], 4));
            widths.add(0.55f);
        }
        SkillFx.particles(level, grit, spot.x, spot.y + 0.5, spot.z, 70, 0.8, 0.8, 0.8, 0.3);
        SkillFx.particles(level, sand, spot.x, spot.y + 1.5, spot.z, 40, 0.6, 1.4, 0.6, 0.15);
        SkillFx.particles(level, ParticleTypes.CLOUD, spot.x, spot.y + 0.5, spot.z, 18, 1.0, 0.4, 1.0, 0.08);
        SkillFx.particles(level, ParticleTypes.EXPLOSION, spot.x, spot.y + 0.4, spot.z, 1, 0, 0, 0, 0);
        SkillFx.ring(level, spot.add(0, 0.2, 0), new Vec3(0, 1, 0), 2.0, 18, ParticleTypes.POOF);
        level.playSound(null, BlockPos.containing(spot), SoundEvents.WARDEN_EMERGE, SoundSource.PLAYERS, 1.2f, 0.8f);
        level.playSound(null, BlockPos.containing(spot), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 0.5f, 1.4f);
        level.playSound(null, BlockPos.containing(spot), SoundEvents.SAND_BREAK, SoundSource.PLAYERS, 1.4f, 0.5f);
        SkillFx.screen(level, spot, 22, ScreenFxPayload.SHAKE, 0.4f, 8);
        Set<UUID> struck = new HashSet<>();
        for (LivingEntity victim : SkillFx.victims(player, player, spot.add(0, 1.2, 0), ERUPT_RADIUS + 1)) {
            if (Math.hypot(victim.getX() - spot.x, victim.getZ() - spot.z) > ERUPT_RADIUS + victim.getBbWidth() * 0.5
                    || victim.getY() > spot.y + 3) {
                continue;
            }
            struck.add(victim.getUUID());
            if (PowerKit.hit(level, player, victim, ERUPT_DAMAGE)) {
                victim.setDeltaMovement(victim.getDeltaMovement().multiply(0.2, 0, 0.2).add(0, 1.0, 0));
                victim.hurtMarked = true;
                victim.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 50, 0, false, true));
                victim.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 1, false, true));
                Vec3 c = PowerKit.centre(victim);
                SkillFx.particles(level, sand, c.x, c.y, c.z, 14, 0.3, 0.4, 0.3, 0.2);
            }
        }
        // the sand rolls outwards from the burst
        TrophyScheduler.later(level, 3, () -> {
            for (int ring = 1; ring <= 3; ring++) {
                int stage = ring;
                TrophyScheduler.later(level, ring, () -> SkillFx.ring(level, spot.add(0, 0.2, 0), new Vec3(0, 1, 0),
                        SAND_WAVE_RADIUS * stage / 3.0, 12 + stage * 8, stage == 2 ? grit : ParticleTypes.CLOUD));
            }
            for (LivingEntity victim : SkillFx.victims(player, player, spot.add(0, 1, 0), SAND_WAVE_RADIUS + 1)) {
                if (struck.contains(victim.getUUID())
                        || Math.hypot(victim.getX() - spot.x, victim.getZ() - spot.z) > SAND_WAVE_RADIUS + victim.getBbWidth() * 0.5
                        || victim.getY() > spot.y + 3) {
                    continue;
                }
                if (PowerKit.hit(level, player, victim, SAND_WAVE_DAMAGE)) {
                    PowerKit.shove(victim, victim.position().subtract(spot), 0.7, 0.35);
                    victim.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 30, 0, false, true));
                }
            }
        });
        TrophyScheduler.later(level, ERUPT_HOLD_TICKS, () -> {
            for (int i = 0; i < parts.size(); i++) {
                DisplayFx.sink(level, parts.get(i), widths.get(i), 5);
            }
            SkillFx.particles(level, grit, spot.x, spot.y + 0.6, spot.z, 30, 0.9, 0.6, 0.9, 0.1);
            level.playSound(null, BlockPos.containing(spot), SoundEvents.SAND_BREAK, SoundSource.PLAYERS, 1.2f, 0.6f);
        });
    }

    // ---------------------------------------------------------------- Helvar: rift slashes

    /**
     * Rifts tear open in the air at the four nearest enemies: a seam of soul fire that pulls them in as it widens, then
     * two crossing slashes that cut through everything on the seam. Nothing is left behind but a few sparks.
     */
    public static boolean riftSlashes(Player player) {
        if (!(player.level() instanceof ServerLevel level) || !player.isAlive()) {
            return false;
        }
        List<LivingEntity> marks = PowerKit.marks(player, RIFT_RANGE, RIFTS);
        if (marks.isEmpty()) {
            Speech.hint(player, noEnemy());
            return false;
        }
        level.playSound(null, player.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1.2f, 0.6f);
        level.playSound(null, player.blockPosition(), SoundEvents.WARDEN_SONIC_CHARGE, SoundSource.PLAYERS, 0.8f, 1.6f);
        if (player instanceof ServerPlayer sp) {
            SkillFx.screen(sp, ScreenFxPayload.FOV_PUNCH, 0.3f, 10);
        }
        // the sword is drawn: an arc of soul fire in front of the caster
        Vec3 look = player.getLookAngle();
        Vec3 chest = player.getEyePosition().subtract(0, 0.4, 0).add(look.scale(0.8));
        SkillFx.particles(level, ParticleTypes.SWEEP_ATTACK, chest.x, chest.y, chest.z, 1, 0, 0, 0, 0);
        SkillFx.ring(level, chest, look, 0.8, 14, ParticleTypes.SOUL_FIRE_FLAME);
        for (int i = 0; i < marks.size(); i++) {
            LivingEntity mark = marks.get(i);
            int delay = i * 3;
            double tilt = (i % 2 == 0 ? 1 : -1) * (28 + level.random.nextInt(14));
            TrophyScheduler.later(level, delay, () -> {
                if (PowerKit.live(level, player) && PowerKit.live(level, mark)) {
                    level.playSound(null, mark.blockPosition(), SoundEvents.PORTAL_TRIGGER, SoundSource.PLAYERS, 0.3f, 1.8f);
                    rift(level, player, mark, PowerKit.centre(mark), tilt, 0);
                }
            });
        }
        return true;
    }

    /** The seam's two ends: a segment through {@code centre}, tilted {@code tilt} degrees from the vertical. */
    private static Vec3[] seam(Vec3 centre, Vec3 across, double tilt, double length) {
        double a = Math.toRadians(tilt);
        Vec3 along = across.scale(Math.sin(a)).add(0, Math.cos(a), 0);
        return new Vec3[] {centre.subtract(along.scale(length / 2)), centre.add(along.scale(length / 2))};
    }

    private static void rift(ServerLevel level, Player player, LivingEntity mark, Vec3 centre, double tilt, int t) {
        if (!PowerKit.live(level, player)) {
            return;
        }
        Vec3 c = PowerKit.live(level, mark) ? PowerKit.centre(mark) : centre;
        Vec3 away = PowerKit.flat(c.subtract(player.position()), player);
        Vec3 across = new Vec3(-away.z, 0, away.x);
        if (t < RIFT_OPEN_TICKS) {
            // the seam opens from its middle outwards, glowing, and drags the nearby in
            double grow = (t + 1) / (double) RIFT_OPEN_TICKS;
            Vec3[] ends = seam(c, across, tilt, RIFT_LENGTH * grow);
            drawSeam(level, ends[0], ends[1], t);
            for (LivingEntity near : SkillFx.victims(player, player, c, 3.5)) {
                PowerKit.shove(near, c.subtract(near.position()), 0.06, 0);
            }
            if (t % 3 == 0) {
                level.playSound(null, BlockPos.containing(c), SoundEvents.PORTAL_AMBIENT, SoundSource.PLAYERS, 0.4f, 1.8f);
            }
            TrophyScheduler.later(level, 1, () -> rift(level, player, mark, c, tilt, t + 1));
            return;
        }
        // first cut along the seam, then a second one crossing it
        Vec3[] cut = seam(c, across, tilt, RIFT_LENGTH);
        slash(level, player, cut[0], cut[1]);
        TrophyScheduler.later(level, RIFT_SECOND_SLASH, () -> {
            if (!PowerKit.live(level, player)) {
                return;
            }
            Vec3 c2 = PowerKit.live(level, mark) ? PowerKit.centre(mark) : c;
            Vec3 away2 = PowerKit.flat(c2.subtract(player.position()), player);
            Vec3[] cross = seam(c2, new Vec3(-away2.z, 0, away2.x), -tilt, RIFT_LENGTH);
            slash(level, player, cross[0], cross[1]);
        });
    }

    private static void drawSeam(ServerLevel level, Vec3 a, Vec3 b, int t) {
        Vec3 d = b.subtract(a);
        int n = (int) Math.max(2, d.length() / 0.35);
        for (int i = 0; i <= n; i++) {
            Vec3 p = a.add(d.scale(i / (double) n));
            SkillFx.particles(level, i % 2 == 0 ? ParticleTypes.SOUL_FIRE_FLAME : SOUL_BLUE, p.x, p.y, p.z, 1, 0.03, 0.03, 0.03, 0);
            if (i % 3 == 0) {
                SkillFx.particles(level, ParticleTypes.REVERSE_PORTAL, p.x, p.y, p.z, 1, 0.25, 0.25, 0.25, 0.02);
            }
        }
        Vec3 mid = a.add(d.scale(0.5));
        SkillFx.particles(level, ParticleTypes.SCULK_SOUL, mid.x, mid.y, mid.z, 1, 0.15, 0.15, 0.15, 0.01);
    }

    private static void slash(ServerLevel level, Player player, Vec3 a, Vec3 b) {
        Vec3 d = b.subtract(a);
        int n = (int) Math.max(2, d.length() / 0.6);
        for (int i = 0; i <= n; i++) {
            Vec3 p = a.add(d.scale(i / (double) n));
            SkillFx.particles(level, ParticleTypes.SWEEP_ATTACK, p.x, p.y, p.z, 1, 0, 0, 0, 0);
            SkillFx.particles(level, ParticleTypes.SOUL_FIRE_FLAME, p.x, p.y, p.z, 2, 0.1, 0.1, 0.1, 0.1);
            SkillFx.particles(level, ParticleTypes.ELECTRIC_SPARK, p.x, p.y, p.z, 1, 0.2, 0.2, 0.2, 0.3);
        }
        Vec3 mid = a.add(d.scale(0.5));
        SkillFx.particles(level, ParticleTypes.FLASH, mid.x, mid.y, mid.z, 1, 0, 0, 0, 0);
        level.playSound(null, BlockPos.containing(mid), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1.2f, 0.8f);
        level.playSound(null, BlockPos.containing(mid), SoundEvents.TRIDENT_THUNDER.value(), SoundSource.PLAYERS, 0.3f, 1.8f);
        level.playSound(null, BlockPos.containing(mid), SoundEvents.SOUL_ESCAPE.value(), SoundSource.PLAYERS, 1f, 0.6f);
        SkillFx.screen(level, mid, 16, ScreenFxPayload.SHAKE, 0.15f, 4);
        for (LivingEntity victim : SkillFx.victims(player, player, mid, RIFT_LENGTH / 2 + RIFT_REACH + 1)) {
            if (PowerKit.distanceToSegment(PowerKit.centre(victim), a, b) > RIFT_REACH + victim.getBbWidth() * 0.5) {
                continue;
            }
            if (PowerKit.hit(level, player, victim, RIFT_DAMAGE)) {
                victim.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 0, false, true));
                Vec3 c = PowerKit.centre(victim);
                SkillFx.particles(level, ParticleTypes.SOUL, c.x, c.y, c.z, 8, 0.3, 0.4, 0.3, 0.05);
                SkillFx.particles(level, ParticleTypes.CRIT, c.x, c.y, c.z, 8, 0.3, 0.4, 0.3, 0.3);
            }
        }
    }

    // ---------------------------------------------------------------- Skor: ground pound

    /**
     * Skor's pound: the caster springs up with a roar and comes down fist first. Waves of ice spikes burst out of the
     * ground in a ring round the landing, throwing enemies back and freezing them. The spikes are only scenery and sink
     * away.
     */
    public static boolean groundPound(Player player) {
        if (!(player.level() instanceof ServerLevel level) || !player.isAlive()) {
            return false;
        }
        if (TrophyAim.hostiles(player, player.position().add(0, 1, 0), POUND_RADIUS).isEmpty()) {
            Speech.hint(player, noEnemy());
            return false;
        }
        level.playSound(null, player.blockPosition(), SoundEvents.POLAR_BEAR_WARNING, SoundSource.PLAYERS, 1.6f, 0.5f);
        level.playSound(null, player.blockPosition(), SoundEvents.RAVAGER_ROAR, SoundSource.PLAYERS, 0.8f, 0.7f);
        if (player instanceof ServerPlayer sp) {
            SkillFx.screen(sp, ScreenFxPayload.FOV_PUNCH, 0.4f, 10);
        }
        player.setDeltaMovement(player.getDeltaMovement().x * 0.3, 0.85, player.getDeltaMovement().z * 0.3);
        player.hurtMarked = true;
        TrophyScheduler.later(level, 1, () -> hopStep(level, player, 0));
        return true;
    }

    private static void hopStep(ServerLevel level, Player player, int t) {
        if (!PowerKit.live(level, player)) {
            return;
        }
        player.resetFallDistance();
        if (t >= POUND_HOP_MIN && player.onGround() || t >= POUND_HOP_MAX) {
            pound(level, player);
            return;
        }
        Vec3 at = player.position();
        // frost gathers in the fists and the legs
        SkillFx.ring(level, at.add(0, 0.2, 0), new Vec3(0, 1, 0), 1.3, 10, ParticleTypes.SNOWFLAKE);
        SkillFx.particles(level, ParticleTypes.CLOUD, at.x, at.y + 0.3, at.z, 2, 0.3, 0.1, 0.3, 0.02);
        SkillFx.particles(level, ParticleTypes.ITEM_SNOWBALL, at.x, at.y + 1, at.z, 2, 0.4, 0.5, 0.4, 0.02);
        if (t > 4) {
            // coming down: the player is pulled onto the ground at a good clip
            Vec3 v = player.getDeltaMovement();
            if (v.y < 0.1 && !player.onGround()) {
                player.setDeltaMovement(v.x, Math.max(-1.3, v.y - 0.25), v.z);
                player.hurtMarked = true;
            }
        }
        TrophyScheduler.later(level, 1, () -> hopStep(level, player, t + 1));
    }

    private static void pound(ServerLevel level, Player player) {
        Vec3 at = player.position();
        player.resetFallDistance();
        var ice = new BlockParticleOption(ParticleTypes.BLOCK, Blocks.BLUE_ICE.defaultBlockState());
        SkillFx.particles(level, ParticleTypes.EXPLOSION, at.x, at.y + 0.3, at.z, 1, 0, 0, 0, 0);
        SkillFx.particles(level, ice, at.x, at.y + 0.2, at.z, 50, 0.9, 0.2, 0.9, 0.3);
        SkillFx.particles(level, ParticleTypes.SNOWFLAKE, at.x, at.y + 0.6, at.z, 40, 1.2, 0.5, 1.2, 0.2);
        level.playSound(null, player.blockPosition(), SoundEvents.MACE_SMASH_GROUND_HEAVY, SoundSource.PLAYERS, 1.5f, 0.7f);
        level.playSound(null, player.blockPosition(), SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 1.2f, 0.5f);
        level.playSound(null, player.blockPosition(), SoundEvents.POWDER_SNOW_BREAK, SoundSource.PLAYERS, 1.2f, 0.5f);
        SkillFx.screen(level, at, 26, ScreenFxPayload.SHAKE, 0.6f, 12);
        List<Display.BlockDisplay> spikes = new ArrayList<>();
        Set<UUID> frozen = new HashSet<>();
        double offset = level.random.nextDouble() * Math.PI;
        for (int wave = 0; wave < POUND_WAVES; wave++) {
            int w = wave;
            double radius = POUND_FIRST_WAVE + wave * POUND_WAVE_STEP;
            TrophyScheduler.later(level, wave * 2, () -> {
                SkillFx.ring(level, at.add(0, 0.2, 0), new Vec3(0, 1, 0), radius, 16 + w * 8, ParticleTypes.CLOUD);
                SkillFx.ring(level, at.add(0, 0.3, 0), new Vec3(0, 1, 0), radius, 12 + w * 6, ice);
                for (int ray = 0; ray < POUND_RAYS; ray++) {
                    double a = offset + ray * Math.PI * 2 / POUND_RAYS + w * 0.3;
                    Vec3 spot = TrophyAim.ground(level, at.add(Math.cos(a) * radius, 1.5, Math.sin(a) * radius));
                    float[] lean = PowerKit.lean(Math.cos(a), Math.sin(a), 0.22);
                    BlockState block = (ray + w) % 2 == 0 ? Blocks.BLUE_ICE.defaultBlockState() : Blocks.PACKED_ICE.defaultBlockState();
                    spikes.add(DisplayFx.rise(level, block, spot.add(0, -0.1, 0), 0.6f, 1.5f + w * 0.4f + level.random.nextFloat() * 0.4f,
                            (float) a, lean[0], lean[1], 3));
                    SkillFx.particles(level, ice, spot.x, spot.y + 0.3, spot.z, 8, 0.3, 0.2, 0.3, 0.12);
                }
                level.playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_BREAK, SoundSource.PLAYERS, 1.4f, 0.5f + w * 0.2f);
                for (LivingEntity victim : SkillFx.victims(player, player, at.add(0, 1, 0), radius + 1)) {
                    if (Math.hypot(victim.getX() - at.x, victim.getZ() - at.z) > radius + victim.getBbWidth() * 0.5
                            || victim.getY() > at.y + 3 || !frozen.add(victim.getUUID())) {
                        continue;
                    }
                    float falloff = (float) (1 - 0.35 * w / Math.max(1, POUND_WAVES - 1));
                    if (PowerKit.hit(level, player, victim, POUND_DAMAGE * falloff)) {
                        victim.setTicksFrozen(victim.getTicksRequiredToFreeze() + 100);
                        victim.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 2, false, true));
                        PowerKit.shove(victim, victim.position().subtract(at), 0.7, 0.55);
                        Vec3 c = PowerKit.centre(victim);
                        SkillFx.particles(level, ParticleTypes.SNOWFLAKE, c.x, c.y, c.z, 10, 0.3, 0.5, 0.3, 0.06);
                    }
                }
            });
        }
        TrophyScheduler.later(level, POUND_HOLD_TICKS, () -> {
            for (Display.BlockDisplay spike : spikes) {
                DisplayFx.sink(level, spike, 0.6f, 4);
            }
            SkillFx.particles(level, ice, at.x, at.y + 0.5, at.z, 40, POUND_RADIUS * 0.5, 0.4, POUND_RADIUS * 0.5, 0.1);
            level.playSound(null, player.blockPosition(), SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 1f, 0.8f);
        });
    }
}
