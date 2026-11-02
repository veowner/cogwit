package dev.cogwit.trophy;

import dev.cogwit.Speech;
import dev.cogwit.horizon.fx.SkillFx;
import dev.cogwit.network.ScreenFxPayload;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ItemParticleOption;
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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The actives of the eight Twilight Forest trophies: the Naga's constricting charge, the Lich's soul bolts, the
 * Minoshroom's axe slam, the Hydra's three heads, the Knight Phantom's swords, the Ur-Ghast's tear bombs, the Alpha
 * Yeti's ice block and the Snow Queen's ice prison. Everything is built from vanilla particles, sounds and displays, so
 * the powers work with or without the mod. Each method returns true if it fired and has no side effects when it cannot;
 * cooldowns are {@link TrophyService#castTrophy}'s business.
 */
public final class TwilightPowers {
    // naga: constricting charge
    public static final int COIL_WINDUP = 4, COIL_STEPS = 9, COIL_TICKS = 40, COIL_PULSE = 8;
    public static final double COIL_RANGE = 11, COIL_SPEED = 1.25, COIL_CATCH = 2.0;
    public static final float COIL_DAMAGE = 4f, SQUEEZE_DAMAGE = 2f;
    private static final DustParticleOptions SCALES = new DustParticleOptions(new Vector3f(0.30f, 0.62f, 0.25f), 1.2f);
    private static final DustParticleOptions SCALES_DEEP = new DustParticleOptions(new Vector3f(0.12f, 0.36f, 0.14f), 1.0f);

    // lich: soul bolts
    public static final int BOLTS = 5, BOLT_WINDUP = 14, BOLT_GAP = 2, BOLT_LIFE_TICKS = 45;
    public static final double BOLT_RANGE = 16, BOLT_SPEED = 1.0, BOLT_TURN = 0.3, BOLT_REACH = 0.9;
    public static final float BOLT_DAMAGE = 5f;
    private static final DustParticleOptions SOUL_VIOLET = new DustParticleOptions(new Vector3f(0.55f, 0.30f, 0.88f), 1.2f);
    private static final DustParticleOptions BONE = new DustParticleOptions(new Vector3f(0.92f, 0.92f, 0.85f), 0.9f);

    // minoshroom: axe slam
    public static final int LEAP_MAX_TICKS = 40, LEAP_MIN_TICKS = 4;
    public static final double LEAP_RANGE = 12, SLAM_RADIUS = 4.5, LEAP_SPEED = 0.75;
    public static final float SLAM_DAMAGE = 10f;

    // hydra: three heads
    public static final int HEADS_WINDUP = 8, HEADS_TICKS = 44, HEADS_HIT_INTERVAL = 4;
    public static final double HEADS_REACH = 9, HEADS_HALF_ANGLE = 12, HEADS_ARC = 65;
    public static final float HEAD_DAMAGE = 2f;

    // knight phantom: swords
    public static final int SWORDS = 6, SWORD_WINDUP = 14, SWORD_GAP = 2, SWORD_LIFE_TICKS = 30;
    public static final double SWORD_RANGE = 14, SWORD_SPEED = 1.5;
    public static final float SWORD_DAMAGE = 5f;

    // ur-ghast: tear bombs
    public static final int TEARS = 6, TEAR_WINDUP = 10, TEAR_GAP = 4;
    public static final double TEAR_RANGE = 14, TEAR_RADIUS = 3.2, TEAR_HEIGHT = 12, TEAR_FALL_SPEED = 1.1;
    public static final float TEAR_DAMAGE = 8f;

    // alpha yeti: ice block
    public static final int ICE_WINDUP = 12;
    public static final double ICE_RANGE = 20, ICE_RADIUS = 3.4, ICE_GRAVITY = 0.035;
    public static final float ICE_DAMAGE = 13f;

    // snow queen: ice prison
    public static final int PRISON_TARGETS = 3, PRISON_WINDUP = 8, PRISON_TICKS = 60, PRISON_PULSE = 15;
    public static final double PRISON_RANGE = 12, PRISON_RADIUS = 1.2;
    public static final float PRISON_PULSE_DAMAGE = 1.5f, PRISON_SHATTER_DAMAGE = 6f;

    private TwilightPowers() {}

    private static Component noEnemy() {
        return Component.translatable("cogwit.trophy.no_enemy_near");
    }

    private static Vec3 rotateY(Vec3 v, double degrees) {
        double a = Math.toRadians(degrees), c = Math.cos(a), s = Math.sin(a);
        return new Vec3(v.x * c - v.z * s, v.y, v.x * s + v.z * c);
    }

    // ---------------------------------------------------------------- Naga: constricting charge

    /**
     * The Naga's rush: the caster coils up, then slithers at the nearest enemy and wraps whatever the charge meets.
     * The wrapped are held where they stand for two seconds and squeezed.
     */
    public static boolean coil(Player player) {
        if (!(player.level() instanceof ServerLevel level) || !player.isAlive()) {
            return false;
        }
        Optional<LivingEntity> target = TrophyAim.target(player, COIL_RANGE);
        if (target.isEmpty()) {
            Speech.hint(player, noEnemy());
            return false;
        }
        Vec3 dir = PowerKit.flat(target.get().position().subtract(player.position()), player);
        // wind-up: the body gathers into a spiral of scales and the head rears back
        for (int t = 0; t < COIL_WINDUP; t++) {
            int step = t;
            TrophyScheduler.later(level, t, () -> {
                if (!PowerKit.live(level, player)) {
                    return;
                }
                Vec3 c = player.position();
                for (int arm = 0; arm < 2; arm++) {
                    double a = step * 1.1 + arm * Math.PI, r = 1.4 - step * 0.25;
                    SkillFx.particles(level, arm == 0 ? SCALES : SCALES_DEEP, c.x + Math.cos(a) * r, c.y + 0.2 + step * 0.25,
                            c.z + Math.sin(a) * r, 3, 0.05, 0.05, 0.05, 0);
                }
            });
        }
        level.playSound(null, player.blockPosition(), SoundEvents.SLIME_SQUISH, SoundSource.PLAYERS, 1.2f, 0.6f);
        level.playSound(null, player.blockPosition(), SoundEvents.ENDER_DRAGON_FLAP, SoundSource.PLAYERS, 0.9f, 0.7f);
        TrophyScheduler.later(level, COIL_WINDUP, () -> chargeStep(level, player, dir, 0));
        return true;
    }

    private static void chargeStep(ServerLevel level, Player player, Vec3 dir, int step) {
        if (!PowerKit.live(level, player) || step >= COIL_STEPS) {
            return;
        }
        Vec3 push = dir.scale(COIL_SPEED);
        if (!level.noCollision(player, player.getBoundingBox().move(push.x, 0, push.z))) {
            Vec3 at = player.position();
            SkillFx.particles(level, ParticleTypes.POOF, at.x + dir.x, at.y + 0.6, at.z + dir.z, 8, 0.3, 0.3, 0.3, 0.05);
            level.playSound(null, player.blockPosition(), SoundEvents.SLIME_BLOCK_HIT, SoundSource.PLAYERS, 1f, 0.6f);
            return;
        }
        Vec3 v = player.getDeltaMovement();
        player.setDeltaMovement(push.x, player.onGround() ? 0 : v.y, push.z);
        player.hurtMarked = true;
        player.resetFallDistance();
        Vec3 at = player.position();
        // the body trails behind: a ribbon of scales that undulates side to side
        Vec3 side = new Vec3(-dir.z, 0, dir.x);
        for (int k = 0; k < 6; k++) {
            double back = k * 0.55, sway = Math.sin(step * 0.9 - k * 0.8) * 0.35;
            Vec3 p = at.subtract(dir.scale(back)).add(side.scale(sway));
            SkillFx.particles(level, k % 2 == 0 ? SCALES : SCALES_DEEP, p.x, p.y + 0.3 + (5 - k) * 0.04, p.z, 2, 0.08, 0.08, 0.08, 0);
        }
        SkillFx.particles(level, ParticleTypes.ITEM_SLIME, at.x, at.y + 0.2, at.z, 2, 0.3, 0.05, 0.3, 0.02);
        if (step % 3 == 0) {
            level.playSound(null, player.blockPosition(), SoundEvents.SLIME_SQUISH_SMALL, SoundSource.PLAYERS, 0.8f, 0.7f);
        }
        List<LivingEntity> caught = SkillFx.victims(player, player, at.add(dir.scale(1.0)).add(0, 0.9, 0), COIL_CATCH + 1);
        caught.removeIf(e -> Math.hypot(e.getX() - at.x - dir.x, e.getZ() - at.z - dir.z) > COIL_CATCH + e.getBbWidth() * 0.5);
        if (!caught.isEmpty()) {
            player.setDeltaMovement(0, player.getDeltaMovement().y, 0);
            player.hurtMarked = true;
            wrap(level, player, caught);
            return;
        }
        TrophyScheduler.later(level, 1, () -> chargeStep(level, player, dir, step + 1));
    }

    private static void wrap(ServerLevel level, Player player, List<LivingEntity> caught) {
        level.playSound(null, player.blockPosition(), SoundEvents.SLIME_BLOCK_BREAK, SoundSource.PLAYERS, 1.4f, 0.5f);
        level.playSound(null, player.blockPosition(), SoundEvents.BONE_MEAL_USE, SoundSource.PLAYERS, 1f, 0.6f);
        if (player instanceof ServerPlayer sp) {
            SkillFx.screen(sp, ScreenFxPayload.SHAKE, 0.3f, 8);
        }
        for (LivingEntity victim : caught) {
            PowerKit.hit(level, player, victim, COIL_DAMAGE);
            Vec3 anchor = victim.position();
            TrophyScheduler.later(level, 1, () -> squeeze(level, player, victim, anchor, 1));
        }
    }

    private static void squeeze(ServerLevel level, Player player, LivingEntity victim, Vec3 anchor, int t) {
        if (!PowerKit.live(level, victim)) {
            return;
        }
        // held in place: no walking, no weapon swings worth the name
        PowerKit.stop(victim);
        PowerKit.tether(victim, anchor, 0.4);
        Vec3 v = victim.getDeltaMovement();
        victim.setDeltaMovement(0, Math.min(0, v.y), 0);
        victim.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 6, 6, false, false));
        victim.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 40, 1, false, false));
        double r = victim.getBbWidth() * 0.5 + 0.3, h = victim.getBbHeight();
        for (int strand = 0; strand < 2; strand++) {
            for (int k = 0; k < 3; k++) {
                double rise = (t * 0.1 + k / 3.0 + strand * 0.17) % 1.0;
                double a = t * 0.55 + strand * Math.PI + rise * 5;
                SkillFx.particles(level, strand == 0 ? SCALES : SCALES_DEEP, victim.getX() + Math.cos(a) * r,
                        victim.getY() + rise * h, victim.getZ() + Math.sin(a) * r, 1, 0.02, 0.02, 0.02, 0);
            }
        }
        if (t % COIL_PULSE == 0) {
            PowerKit.hit(level, player, victim, SQUEEZE_DAMAGE);
            Vec3 c = PowerKit.centre(victim);
            SkillFx.particles(level, ParticleTypes.ITEM_SLIME, c.x, c.y, c.z, 6, 0.3, 0.4, 0.3, 0.05);
            SkillFx.particles(level, ParticleTypes.CRIT, c.x, c.y, c.z, 4, 0.3, 0.4, 0.3, 0.1);
            level.playSound(null, victim.blockPosition(), SoundEvents.SLIME_SQUISH, SoundSource.PLAYERS, 0.9f, 0.5f);
            level.playSound(null, victim.blockPosition(), SoundEvents.BONE_MEAL_USE, SoundSource.PLAYERS, 0.6f, 0.5f);
        }
        if (t >= COIL_TICKS) {
            // the coils fall slack
            Vec3 c = PowerKit.centre(victim);
            SkillFx.particles(level, ParticleTypes.POOF, c.x, c.y, c.z, 10, 0.3, 0.4, 0.3, 0.05);
            SkillFx.particles(level, SCALES, c.x, c.y, c.z, 12, 0.4, 0.5, 0.4, 0.05);
            level.playSound(null, victim.blockPosition(), SoundEvents.SLIME_BLOCK_FALL, SoundSource.PLAYERS, 1f, 0.7f);
            return;
        }
        TrophyScheduler.later(level, 1, () -> squeeze(level, player, victim, anchor, t + 1));
    }

    // ---------------------------------------------------------------- Lich: soul bolts

    /**
     * Five skulls of soul fire rise round the caster's head, then fly one after another at the nearest enemies, bending
     * towards them as they go. A wall stops a bolt.
     */
    public static boolean soulBolts(Player player) {
        if (!(player.level() instanceof ServerLevel level) || !player.isAlive()) {
            return false;
        }
        List<LivingEntity> marks = PowerKit.marks(player, BOLT_RANGE, BOLTS);
        if (marks.isEmpty()) {
            Speech.hint(player, noEnemy());
            return false;
        }
        level.playSound(null, player.blockPosition(), SoundEvents.WITHER_SPAWN, SoundSource.PLAYERS, 0.4f, 1.8f);
        level.playSound(null, player.blockPosition(), SoundEvents.EVOKER_PREPARE_SUMMON, SoundSource.PLAYERS, 1.2f, 0.8f);
        if (player instanceof ServerPlayer sp) {
            SkillFx.screen(sp, ScreenFxPayload.FOV_PULL, 0.15f, BOLT_WINDUP);
        }
        int last = BOLT_WINDUP + (BOLTS - 1) * BOLT_GAP;
        for (int t = 0; t <= last; t++) {
            int step = t;
            TrophyScheduler.later(level, t, () -> {
                if (PowerKit.live(level, player)) {
                    skulls(level, player, step);
                }
            });
        }
        for (int i = 0; i < BOLTS; i++) {
            int slot = i;
            TrophyScheduler.later(level, BOLT_WINDUP + i * BOLT_GAP, () -> {
                if (PowerKit.live(level, player)) {
                    launch(level, player, slot, marks.get(slot % marks.size()));
                }
            });
        }
        return true;
    }

    private static Vec3 skullSlot(Player player, int slot, int tick) {
        double a = tick * 0.22 + slot * Math.PI * 2 / BOLTS;
        Vec3 wanted = player.position().add(Math.cos(a) * 1.2, 2.2 + 0.15 * Math.sin(tick * 0.3 + slot), Math.sin(a) * 1.2);
        // under a low ceiling the skulls stay in the room
        return player.level() instanceof ServerLevel level ? PowerKit.reach(level, player, player.getEyePosition(), wanted) : wanted;
    }

    /** The skulls still waiting circle the caster's head: a pale core wrapped in soul fire. */
    private static void skulls(ServerLevel level, Player player, int tick) {
        for (int slot = 0; slot < BOLTS; slot++) {
            if (tick >= BOLT_WINDUP + slot * BOLT_GAP) {
                continue;
            }
            Vec3 p = skullSlot(player, slot, tick);
            double grow = Math.min(1, tick / 6.0);
            SkillFx.particles(level, BONE, p.x, p.y, p.z, 2, 0.07 * grow, 0.07 * grow, 0.07 * grow, 0);
            SkillFx.particles(level, ParticleTypes.SOUL_FIRE_FLAME, p.x, p.y + 0.1, p.z, 1, 0.08, 0.08, 0.08, 0.01);
            SkillFx.particles(level, SOUL_VIOLET, p.x, p.y, p.z, 1, 0.12, 0.12, 0.12, 0);
        }
        if (tick % 5 == 0) {
            level.playSound(null, player.blockPosition(), SoundEvents.SOUL_ESCAPE.value(), SoundSource.PLAYERS, 0.6f,
                    0.8f + tick * 0.02f);
        }
    }

    private static void launch(ServerLevel level, Player player, int slot, LivingEntity mark) {
        int tick = BOLT_WINDUP + slot * BOLT_GAP;
        Vec3 from = skullSlot(player, slot, tick);
        Vec3 out = from.subtract(player.position().add(0, 1.6, 0));
        Vec3 vel = out.lengthSqr() < 1e-4 ? new Vec3(0, 0.5, 0) : out.normalize().scale(BOLT_SPEED);
        level.playSound(null, BlockPos.containing(from), SoundEvents.WITHER_SHOOT, SoundSource.PLAYERS, 0.6f, 1.6f);
        level.playSound(null, BlockPos.containing(from), SoundEvents.SKELETON_AMBIENT, SoundSource.PLAYERS, 1f, 0.6f);
        SkillFx.particles(level, ParticleTypes.SOUL_FIRE_FLAME, from.x, from.y, from.z, 8, 0.15, 0.15, 0.15, 0.05);
        bolt(level, player, mark, from, vel, 0);
    }

    private static void bolt(ServerLevel level, Player player, LivingEntity mark, Vec3 pos, Vec3 vel, int age) {
        if (!PowerKit.live(level, player) || age > BOLT_LIFE_TICKS) {
            return;
        }
        LivingEntity target = mark;
        if (!PowerKit.live(level, target)) {
            // its mark fell: another enemy near the bolt takes its place, or the bolt fizzles out
            target = TrophyAim.hostiles(player, pos, BOLT_RANGE).stream().findFirst().orElse(null);
            if (target == null) {
                SkillFx.particles(level, ParticleTypes.SOUL, pos.x, pos.y, pos.z, 8, 0.2, 0.2, 0.2, 0.04);
                return;
            }
        }
        Vec3 centre = PowerKit.centre(target);
        Vec3 want = centre.subtract(pos);
        Vec3 steer = want.lengthSqr() < 1e-6 ? vel : want.normalize().scale(BOLT_SPEED);
        Vec3 blend = vel.lerp(steer, BOLT_TURN);
        Vec3 next = pos.add(blend.lengthSqr() < 1e-6 ? vel : blend.normalize().scale(BOLT_SPEED));
        BlockHitResult wall = level.clip(new ClipContext(pos, next, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        trail(level, pos, next);
        if (wall.getType() != HitResult.Type.MISS) {
            burst(level, wall.getLocation());
            return;
        }
        AABB body = target.getBoundingBox().inflate(BOLT_REACH * 0.5);
        if (body.contains(next) || body.clip(pos, next).isPresent()) {
            burst(level, next);
            if (PowerKit.hit(level, player, target, BOLT_DAMAGE)) {
                target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 60, 0, false, true));
                PowerKit.shove(target, next.subtract(pos), 0.25, 0.1);
            }
            return;
        }
        Vec3 heading = next.subtract(pos);
        LivingEntity chased = target;
        TrophyScheduler.later(level, 1, () -> bolt(level, player, chased, next, heading, age + 1));
    }

    private static void trail(ServerLevel level, Vec3 from, Vec3 to) {
        Vec3 mid = from.add(to).scale(0.5);
        for (Vec3 p : new Vec3[] {from, mid}) {
            SkillFx.particles(level, ParticleTypes.SOUL_FIRE_FLAME, p.x, p.y, p.z, 1, 0.05, 0.05, 0.05, 0.005);
            SkillFx.particles(level, SOUL_VIOLET, p.x, p.y, p.z, 2, 0.06, 0.06, 0.06, 0);
        }
        SkillFx.particles(level, ParticleTypes.SMOKE, to.x, to.y, to.z, 1, 0.05, 0.05, 0.05, 0.01);
        SkillFx.particles(level, BONE, to.x, to.y, to.z, 1, 0.03, 0.03, 0.03, 0);
    }

    private static void burst(ServerLevel level, Vec3 at) {
        SkillFx.particles(level, ParticleTypes.SOUL, at.x, at.y, at.z, 10, 0.25, 0.25, 0.25, 0.08);
        SkillFx.particles(level, ParticleTypes.SOUL_FIRE_FLAME, at.x, at.y, at.z, 12, 0.2, 0.2, 0.2, 0.1);
        SkillFx.particles(level, ParticleTypes.SCULK_SOUL, at.x, at.y, at.z, 4, 0.2, 0.2, 0.2, 0.05);
        SkillFx.ring(level, at, new Vec3(0, 1, 0), 0.7, 10, SOUL_VIOLET);
        level.playSound(null, BlockPos.containing(at), SoundEvents.VEX_HURT, SoundSource.PLAYERS, 0.8f, 0.6f);
        level.playSound(null, BlockPos.containing(at), SoundEvents.SOUL_ESCAPE.value(), SoundSource.PLAYERS, 0.8f, 1.4f);
    }

    // ---------------------------------------------------------------- Minoshroom: axe slam

    /**
     * The Minoshroom's leap: the caster crouches, springs over the nearest enemy and comes down with a slam that
     * throws everything within four and a half blocks up and away.
     */
    public static boolean axeSlam(Player player) {
        if (!(player.level() instanceof ServerLevel level) || !player.isAlive()) {
            return false;
        }
        Optional<LivingEntity> target = TrophyAim.target(player, LEAP_RANGE);
        if (target.isEmpty()) {
            Speech.hint(player, noEnemy());
            return false;
        }
        Vec3 spot = target.get().position();
        var dust = new BlockParticleOption(ParticleTypes.BLOCK, Blocks.RED_MUSHROOM_BLOCK.defaultBlockState());
        SkillFx.particles(level, dust, player.getX(), player.getY() + 0.2, player.getZ(), 16, 0.5, 0.1, 0.5, 0.1);
        level.playSound(null, player.blockPosition(), SoundEvents.RAVAGER_STEP, SoundSource.PLAYERS, 1.2f, 0.6f);
        level.playSound(null, player.blockPosition(), SoundEvents.COW_HURT, SoundSource.PLAYERS, 1f, 0.5f);
        player.setDeltaMovement(0, 0.68, 0);
        player.hurtMarked = true;
        TrophyScheduler.later(level, 1, () -> leapStep(level, player, spot, 0));
        return true;
    }

    private static void leapStep(ServerLevel level, Player player, Vec3 spot, int t) {
        if (!PowerKit.live(level, player)) {
            return;
        }
        player.resetFallDistance();
        boolean landed = t >= LEAP_MIN_TICKS && player.onGround();
        if (landed || t >= LEAP_MAX_TICKS) {
            slam(level, player);
            return;
        }
        // in the air the leap keeps its heading, easing off as it nears the spot
        Vec3 to = spot.subtract(player.position());
        double flat = Math.hypot(to.x, to.z);
        Vec3 v = player.getDeltaMovement();
        if (flat > 0.6) {
            double speed = Math.min(LEAP_SPEED, flat * 0.3 + 0.12);
            Vec3 dir = to.multiply(1, 0, 1).normalize();
            player.setDeltaMovement(dir.x * speed, v.y, dir.z * speed);
            player.hurtMarked = true;
        }
        Vec3 at = player.position();
        SkillFx.particles(level, ParticleTypes.CLOUD, at.x, at.y + 0.2, at.z, 2, 0.25, 0.1, 0.25, 0.02);
        SkillFx.particles(level, new BlockParticleOption(ParticleTypes.BLOCK, Blocks.RED_MUSHROOM_BLOCK.defaultBlockState()),
                at.x, at.y + 0.6, at.z, 2, 0.3, 0.3, 0.3, 0.02);
        TrophyScheduler.later(level, 1, () -> leapStep(level, player, spot, t + 1));
    }

    private static void slam(ServerLevel level, Player player) {
        Vec3 at = player.position();
        BlockState floorState = level.getBlockState(player.blockPosition().below());
        var rubble = new BlockParticleOption(ParticleTypes.BLOCK, floorState.isAir() ? Blocks.STONE.defaultBlockState() : floorState);
        var cap = new BlockParticleOption(ParticleTypes.BLOCK, Blocks.RED_MUSHROOM_BLOCK.defaultBlockState());
        level.playSound(null, player.blockPosition(), SoundEvents.MACE_SMASH_GROUND_HEAVY, SoundSource.PLAYERS, 1.4f, 0.8f);
        level.playSound(null, player.blockPosition(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 0.5f, 1.5f);
        level.playSound(null, player.blockPosition(), SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.7f, 0.5f);
        SkillFx.screen(level, at, 24, ScreenFxPayload.SHAKE, 0.5f, 10);
        SkillFx.particles(level, ParticleTypes.EXPLOSION, at.x, at.y + 0.3, at.z, 1, 0, 0, 0, 0);
        SkillFx.particles(level, rubble, at.x, at.y + 0.2, at.z, 40, 0.8, 0.2, 0.8, 0.3);
        // the shock rolls outwards ring by ring: dust, rubble and torn-up mushroom caps
        for (int ring = 1; ring <= 3; ring++) {
            int stage = ring;
            TrophyScheduler.later(level, ring - 1, () -> {
                double radius = SLAM_RADIUS * stage / 3.0;
                SkillFx.ring(level, at.add(0, 0.15, 0), new Vec3(0, 1, 0), radius, 10 + stage * 8, ParticleTypes.CLOUD);
                SkillFx.ring(level, at.add(0, 0.25, 0), new Vec3(0, 1, 0), radius, 8 + stage * 6, stage % 2 == 0 ? cap : rubble);
                SkillFx.particles(level, ParticleTypes.CRIMSON_SPORE, at.x, at.y + 0.5, at.z, 10, radius * 0.5, 0.3, radius * 0.5, 0);
            });
        }
        for (LivingEntity victim : SkillFx.victims(player, player, at.add(0, 1, 0), SLAM_RADIUS + 1)) {
            double flat = Math.hypot(victim.getX() - at.x, victim.getZ() - at.z);
            if (flat > SLAM_RADIUS + victim.getBbWidth() * 0.5 || victim.getY() > at.y + 3) {
                continue;
            }
            float falloff = (float) (1 - 0.4 * flat / SLAM_RADIUS);
            if (PowerKit.hit(level, player, victim, SLAM_DAMAGE * falloff)) {
                victim.setDeltaMovement(victim.getDeltaMovement().multiply(0.2, 0, 0.2));
                PowerKit.shove(victim, victim.position().subtract(at), 0.7 * falloff + 0.2, 0.75);
                victim.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 1, false, false));
                Vec3 c = PowerKit.centre(victim);
                SkillFx.particles(level, ParticleTypes.CRIT, c.x, c.y, c.z, 10, 0.3, 0.4, 0.3, 0.3);
            }
        }
    }

    // ---------------------------------------------------------------- Hydra: three heads

    /**
     * Three heads rear up over the caster's shoulders and breathe fire, each turning on an enemy in front of the caster
     * (or fanning out over the ground ahead when there is none) and weaving as it burns. Only creatures are burnt; no
     * block is ever set alight.
     */
    public static boolean threeHeads(Player player) {
        if (!(player.level() instanceof ServerLevel level) || !player.isAlive()) {
            return false;
        }
        level.playSound(null, player.blockPosition(), SoundEvents.ENDER_DRAGON_GROWL, SoundSource.PLAYERS, 0.8f, 1.5f);
        level.playSound(null, player.blockPosition(), SoundEvents.RAVAGER_ROAR, SoundSource.PLAYERS, 0.9f, 1.2f);
        if (player instanceof ServerPlayer sp) {
            SkillFx.screen(sp, ScreenFxPayload.FOV_PULL, 0.2f, HEADS_WINDUP + HEADS_TICKS);
        }
        headsTick(level, player, 0);
        return true;
    }

    private static Vec3 headOrigin(Player player, Vec3 look, int head) {
        Vec3 flat = new Vec3(-look.z, 0, look.x);
        Vec3 right = flat.lengthSqr() < 1e-4 ? new Vec3(1, 0, 0) : flat.normalize();
        return player.getEyePosition().subtract(0, 0.4, 0).add(right.scale((head - 1) * 0.9)).add(0, head == 1 ? 0.6 : 0.2, 0)
                .add(look.scale(0.5));
    }

    private static void headsTick(ServerLevel level, Player player, int t) {
        if (!PowerKit.live(level, player)) {
            return;
        }
        Vec3 look = player.getLookAngle();
        int open = t - HEADS_WINDUP;
        List<LivingEntity> foes = open < 0 ? List.of() : foesAhead(player, look);
        for (int head = 0; head < 3; head++) {
            Vec3 origin = headOrigin(player, look, head);
            if (open < 0) {
                // the heads gather smoke and embers
                SkillFx.particles(level, ParticleTypes.SMOKE, origin.x, origin.y, origin.z, 2, 0.12, 0.12, 0.12, 0.01);
                SkillFx.particles(level, ParticleTypes.FLAME, origin.x, origin.y, origin.z, 1, 0.1, 0.1, 0.1, 0.01);
                if (t == 0) {
                    SkillFx.particles(level, ParticleTypes.LAVA, origin.x, origin.y, origin.z, 4, 0.2, 0.2, 0.2, 0);
                }
                continue;
            }
            // each head picks an enemy in front of the caster, and the heads share them out; with nobody there they fan out
            double weave = Math.sin((open + head * 7) * 0.22);
            Vec3 dir;
            if (foes.isEmpty()) {
                dir = rotateY(look, (head - 1) * 24 + weave * 14);
            } else {
                Vec3 to = PowerKit.centre(foes.get(head % foes.size())).subtract(origin);
                dir = rotateY(to.lengthSqr() < 1e-4 ? look : to.normalize(), weave * 4);
            }
            BlockHitResult wall = level.clip(new ClipContext(origin, origin.add(dir.scale(HEADS_REACH)),
                    ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
            double reach = wall.getType() == HitResult.Type.MISS ? HEADS_REACH : Math.max(0.5, wall.getLocation().distanceTo(origin));
            Vec3 right = dir.cross(new Vec3(0, 1, 0));
            right = right.lengthSqr() < 1e-4 ? new Vec3(1, 0, 0) : right.normalize();
            Vec3 up = right.cross(dir).normalize();
            double spread = Math.tan(Math.toRadians(HEADS_HALF_ANGLE)) * 0.8;
            for (int i = 0; i < 7; i++) {
                double a = level.random.nextDouble() * Math.PI * 2, r = Math.sqrt(level.random.nextDouble()) * spread;
                Vec3 d = dir.add(right.scale(Math.cos(a) * r)).add(up.scale(Math.sin(a) * r)).normalize();
                Vec3 from = origin.add(d.scale(0.3 + level.random.nextDouble() * 0.6));
                double speed = 0.45 * (reach / HEADS_REACH) + 0.1;
                var kind = switch (i % 4) {
                    case 0, 1 -> ParticleTypes.FLAME;
                    case 2 -> ParticleTypes.SMOKE;
                    default -> i == 3 ? ParticleTypes.LAVA : ParticleTypes.SOUL_FIRE_FLAME;
                };
                SkillFx.particles(level, kind, from.x, from.y, from.z, 0, d.x, d.y, d.z, speed * (0.7 + level.random.nextDouble() * 0.6));
            }
            if ((open + head) % HEADS_HIT_INTERVAL == 0) {
                scorch(level, player, origin, dir);
            }
        }
        if (open >= 0 && open % 7 == 0) {
            level.playSound(null, player.blockPosition(), SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 1f, 0.6f);
            level.playSound(null, player.blockPosition(), SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 0.5f, 0.7f);
        }
        if (open == 0) {
            level.playSound(null, player.blockPosition(), SoundEvents.ENDER_DRAGON_SHOOT, SoundSource.PLAYERS, 0.9f, 1.2f);
        }
        if (open >= HEADS_TICKS) {
            level.playSound(null, player.blockPosition(), SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.8f, 0.7f);
            return;
        }
        TrophyScheduler.later(level, 1, () -> headsTick(level, player, t + 1));
    }

    /** Hostile mobs within reach and within a wide arc in front of the caster, nearest first. */
    private static List<LivingEntity> foesAhead(Player player, Vec3 look) {
        double cos = Math.cos(Math.toRadians(HEADS_ARC));
        Vec3 eye = player.getEyePosition();
        return TrophyAim.hostiles(player, player.position().add(0, 1, 0), HEADS_REACH).stream()
                .filter(e -> {
                    Vec3 to = PowerKit.centre(e).subtract(eye);
                    return to.lengthSqr() > 1e-4 && to.normalize().dot(look) >= cos;
                }).toList();
    }

    private static void scorch(ServerLevel level, Player player, Vec3 origin, Vec3 dir) {
        for (LivingEntity target : SkillFx.victims(player, player, origin.add(dir.scale(HEADS_REACH / 2)), HEADS_REACH / 2 + 2)) {
            Vec3 c = PowerKit.centre(target);
            Vec3 d = c.subtract(origin);
            if (!PowerMath.inCone(d.x, d.y, d.z, dir.x, dir.y, dir.z, HEADS_REACH, HEADS_HALF_ANGLE, target.getBbWidth() * 0.5)
                    || level.clip(new ClipContext(origin, c, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player))
                    .getType() != HitResult.Type.MISS) {
                continue;
            }
            if (PowerKit.hit(level, player, target, HEAD_DAMAGE)) {
                target.igniteForSeconds(5);
                PowerKit.shove(target, dir, 0.1, 0);
                SkillFx.particles(level, ParticleTypes.FLAME, c.x, c.y, c.z, 6, 0.25, 0.4, 0.25, 0.05);
                level.playSound(null, target.blockPosition(), SoundEvents.GENERIC_BURN, SoundSource.PLAYERS, 0.4f, 1.2f);
            }
        }
    }

    // ---------------------------------------------------------------- Knight Phantom: swords

    /**
     * Six spectral swords rise in a ring round the caster, then fly one after another into the nearest enemies. The swords
     * are item displays and vanish when they land; nothing of them is ever saved.
     */
    public static boolean phantomSwords(Player player) {
        if (!(player.level() instanceof ServerLevel level) || !player.isAlive()) {
            return false;
        }
        List<LivingEntity> marks = PowerKit.marks(player, SWORD_RANGE, SWORDS);
        if (marks.isEmpty()) {
            Speech.hint(player, noEnemy());
            return false;
        }
        level.playSound(null, player.blockPosition(), SoundEvents.ALLAY_ITEM_GIVEN, SoundSource.PLAYERS, 1f, 0.5f);
        level.playSound(null, player.blockPosition(), SoundEvents.VEX_CHARGE, SoundSource.PLAYERS, 1f, 0.7f);
        level.playSound(null, player.blockPosition(), SoundEvents.ANVIL_PLACE, SoundSource.PLAYERS, 0.3f, 1.8f);
        if (player instanceof ServerPlayer sp) {
            SkillFx.screen(sp, ScreenFxPayload.FOV_PULL, 0.15f, SWORD_WINDUP);
        }
        List<Display.ItemDisplay> blades = new ArrayList<>();
        for (int i = 0; i < SWORDS; i++) {
            Vec3 at = swordSlot(player, i, 0);
            Display.ItemDisplay blade = DisplayFx.blade(level, new ItemStack(Items.DIAMOND_SWORD), at, 1.5f, new Vec3(0, 1, 0));
            blades.add(blade);
            SkillFx.particles(level, ParticleTypes.END_ROD, at.x, at.y, at.z, 5, 0.2, 0.2, 0.2, 0.03);
        }
        int last = SWORD_WINDUP + (SWORDS - 1) * SWORD_GAP;
        for (int t = 1; t <= last; t++) {
            int step = t;
            TrophyScheduler.later(level, t, () -> {
                for (int i = 0; i < SWORDS; i++) {
                    if (step < SWORD_WINDUP + i * SWORD_GAP && PowerKit.live(level, player)) {
                        Vec3 at = swordSlot(player, i, step);
                        DisplayFx.fly(blades.get(i), at, 0, 0);
                        SkillFx.particles(level, ParticleTypes.SOUL_FIRE_FLAME, at.x, at.y - 0.4, at.z, 1, 0.05, 0.1, 0.05, 0.01);
                    }
                }
            });
        }
        for (int i = 0; i < SWORDS; i++) {
            int slot = i;
            TrophyScheduler.later(level, SWORD_WINDUP + i * SWORD_GAP, () -> {
                Display.ItemDisplay blade = blades.get(slot);
                if (!PowerKit.live(level, player)) {
                    discard(level, blade);
                    return;
                }
                strike(level, player, blade, marks.get(slot % marks.size()));
            });
        }
        // whatever happens, nothing is left hanging in the air
        TrophyScheduler.later(level, last + SWORD_LIFE_TICKS + 10, () -> blades.forEach(b -> discard(level, b)));
        return true;
    }

    private static Vec3 swordSlot(Player player, int slot, int tick) {
        double a = tick * 0.18 + slot * Math.PI * 2 / SWORDS;
        double rise = Math.min(1, tick / 8.0);
        Vec3 wanted = player.position().add(Math.cos(a) * 1.6, 0.4 + rise * 1.9 + 0.1 * Math.sin(tick * 0.4 + slot), Math.sin(a) * 1.6);
        return player.level() instanceof ServerLevel level ? PowerKit.reach(level, player, player.getEyePosition(), wanted) : wanted;
    }

    private static void discard(ServerLevel level, Display blade) {
        if (blade != null && !blade.isRemoved()) {
            Vec3 at = blade.position();
            SkillFx.particles(level, ParticleTypes.END_ROD, at.x, at.y, at.z, 4, 0.2, 0.2, 0.2, 0.02);
            blade.discard();
        }
    }

    private static void strike(ServerLevel level, Player player, Display.ItemDisplay blade, LivingEntity mark) {
        if (blade == null || blade.isRemoved()) {
            return;
        }
        Vec3 from = blade.position();
        Vec3 aim = PowerKit.centre(mark).subtract(from);
        DisplayFx.aim(blade, aim, 1.5f, 3);
        level.playSound(null, BlockPos.containing(from), SoundEvents.TRIDENT_THROW.value(), SoundSource.PLAYERS, 0.8f, 1.5f);
        flySword(level, player, blade, mark, from, aim.lengthSqr() < 1e-6 ? new Vec3(0, -1, 0) : aim.normalize().scale(SWORD_SPEED), 0);
    }

    private static void flySword(ServerLevel level, Player player, Display.ItemDisplay blade, LivingEntity mark, Vec3 pos,
            Vec3 vel, int age) {
        if (blade.isRemoved()) {
            return;
        }
        LivingEntity target = mark;
        if (!PowerKit.live(level, player) || age > SWORD_LIFE_TICKS) {
            discard(level, blade);
            return;
        }
        if (!PowerKit.live(level, target)) {
            target = TrophyAim.hostiles(player, pos, SWORD_RANGE).stream().findFirst().orElse(null);
            if (target == null) {
                discard(level, blade);
                return;
            }
        }
        Vec3 centre = PowerKit.centre(target);
        Vec3 want = centre.subtract(pos);
        Vec3 blend = want.lengthSqr() < 1e-6 ? vel : vel.lerp(want.normalize().scale(SWORD_SPEED), 0.35);
        Vec3 heading = blend.lengthSqr() < 1e-6 ? vel : blend.normalize().scale(SWORD_SPEED);
        Vec3 next = pos.add(heading);
        BlockHitResult wall = level.clip(new ClipContext(pos, next, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        if (wall.getType() != HitResult.Type.MISS) {
            SkillFx.particles(level, ParticleTypes.END_ROD, wall.getLocation().x, wall.getLocation().y, wall.getLocation().z, 6, 0.2,
                    0.2, 0.2, 0.05);
            level.playSound(null, BlockPos.containing(wall.getLocation()), SoundEvents.ALLAY_HURT, SoundSource.PLAYERS, 0.6f, 1.6f);
            discard(level, blade);
            return;
        }
        DisplayFx.fly(blade, next, 0, 0);
        Vec3 back = pos.subtract(next);
        for (int k = 0; k < 3; k++) {
            Vec3 p = next.add(back.scale(k / 3.0));
            SkillFx.particles(level, ParticleTypes.SOUL_FIRE_FLAME, p.x, p.y, p.z, 1, 0.03, 0.03, 0.03, 0);
        }
        SkillFx.particles(level, ParticleTypes.END_ROD, next.x, next.y, next.z, 1, 0.05, 0.05, 0.05, 0);
        AABB body = target.getBoundingBox().inflate(0.5);
        if (body.contains(next) || body.clip(pos, next).isPresent()) {
            if (PowerKit.hit(level, player, target, SWORD_DAMAGE)) {
                PowerKit.shove(target, heading, 0.2, 0.1);
            }
            Vec3 c = PowerKit.centre(target);
            SkillFx.particles(level, ParticleTypes.SWEEP_ATTACK, c.x, c.y, c.z, 1, 0, 0, 0, 0);
            SkillFx.particles(level, ParticleTypes.CRIT, c.x, c.y, c.z, 10, 0.3, 0.4, 0.3, 0.3);
            SkillFx.particles(level, ParticleTypes.SOUL_FIRE_FLAME, c.x, c.y, c.z, 8, 0.25, 0.35, 0.25, 0.08);
            level.playSound(null, target.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.9f, 1.3f);
            level.playSound(null, target.blockPosition(), SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 0.7f, 1.2f);
            // the blade hangs in the wound for a moment, then fades
            TrophyScheduler.later(level, 3, () -> discard(level, blade));
            return;
        }
        LivingEntity chased = target;
        TrophyScheduler.later(level, 1, () -> flySword(level, player, blade, chased, next, heading, age + 1));
    }

    // ---------------------------------------------------------------- Ur-Ghast: tear bombs

    /**
     * Six tears of the Ur-Ghast fall out of the sky onto the ground under the nearest enemies, each warned by a ring of
     * water. They burst without hurting a block, weakening and slowing what they catch.
     */
    public static boolean tearBombs(Player player) {
        if (!(player.level() instanceof ServerLevel level) || !player.isAlive()) {
            return false;
        }
        if (TrophyAim.hostiles(player, player.position().add(0, 1, 0), TEAR_RANGE).isEmpty()) {
            Speech.hint(player, noEnemy());
            return false;
        }
        level.playSound(null, player.blockPosition(), SoundEvents.GHAST_WARN, SoundSource.PLAYERS, 1.4f, 0.6f);
        level.playSound(null, player.blockPosition(), SoundEvents.GHAST_AMBIENT, SoundSource.PLAYERS, 1f, 0.5f);
        if (player instanceof ServerPlayer sp) {
            SkillFx.screen(sp, ScreenFxPayload.FOV_PUNCH, 0.35f, 12);
        }
        var tear = new ItemParticleOption(ParticleTypes.ITEM, new ItemStack(Items.GHAST_TEAR));
        for (int t = 0; t < TEAR_WINDUP; t++) {
            int step = t;
            TrophyScheduler.later(level, t, () -> {
                if (!PowerKit.live(level, player)) {
                    return;
                }
                Vec3 c = player.position();
                SkillFx.ring(level, c.add(0, 0.5 + step * 0.25, 0), new Vec3(0, 1, 0), 1.5 - step * 0.1, 12, ParticleTypes.FALLING_OBSIDIAN_TEAR);
                SkillFx.particles(level, tear, c.x, c.y + 2.6, c.z, 3, 0.6, 0.2, 0.6, 0.02);
            });
        }
        for (int i = 0; i < TEARS; i++) {
            int index = i;
            TrophyScheduler.later(level, TEAR_WINDUP + i * TEAR_GAP, () -> {
                if (PowerKit.live(level, player)) {
                    dropTear(level, player, index);
                }
            });
        }
        return true;
    }

    private static void dropTear(ServerLevel level, Player player, int index) {
        List<LivingEntity> enemies = TrophyAim.hostiles(player, player.position().add(0, 1, 0), TEAR_RANGE);
        if (enemies.isEmpty()) {
            return;
        }
        LivingEntity mark = enemies.get(index % enemies.size());
        // lead a running target a little: the tear needs about ten ticks to land
        Vec3 aimed = mark.position().add(mark.getDeltaMovement().multiply(8, 0, 8));
        Vec3 spot = TrophyAim.ground(level, aimed.add(0, 2, 0));
        BlockHitResult roof = level.clip(new ClipContext(spot.add(0, 1, 0), spot.add(0, TEAR_HEIGHT, 0),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        double rise = roof.getType() == HitResult.Type.MISS ? TEAR_HEIGHT : Math.max(1.5, roof.getLocation().y - spot.y - 0.6);
        Vec3 from = spot.add(0, rise, 0);
        level.playSound(null, BlockPos.containing(from), SoundEvents.GHAST_SHOOT, SoundSource.PLAYERS, 0.8f, 0.6f);
        SkillFx.ring(level, spot.add(0, 0.1, 0), new Vec3(0, 1, 0), TEAR_RADIUS * 0.7, 16, ParticleTypes.FALLING_OBSIDIAN_TEAR);
        SkillFx.ring(level, spot.add(0, 0.1, 0), new Vec3(0, 1, 0), TEAR_RADIUS, 20, ParticleTypes.SNOWFLAKE);
        fall(level, player, from, spot, 0);
    }

    private static void fall(ServerLevel level, Player player, Vec3 pos, Vec3 spot, int age) {
        if (!PowerKit.live(level, player)) {
            return;
        }
        var tear = new ItemParticleOption(ParticleTypes.ITEM, new ItemStack(Items.GHAST_TEAR));
        double y = Math.max(spot.y, pos.y - TEAR_FALL_SPEED);
        Vec3 next = new Vec3(pos.x, y, pos.z);
        for (double yy = pos.y; yy > y; yy -= 0.35) {
            SkillFx.particles(level, ParticleTypes.FALLING_OBSIDIAN_TEAR, pos.x, yy, pos.z, 1, 0.15, 0.05, 0.15, 0);
        }
        SkillFx.particles(level, tear, next.x, next.y + 0.4, next.z, 2, 0.12, 0.2, 0.12, 0.01);
        SkillFx.particles(level, ParticleTypes.END_ROD, next.x, next.y + 1.2, next.z, 1, 0.1, 0.5, 0.1, 0);
        if (y <= spot.y + 0.01 || age > 40) {
            tearBurst(level, player, spot);
            return;
        }
        TrophyScheduler.later(level, 1, () -> fall(level, player, next, spot, age + 1));
    }

    private static void tearBurst(ServerLevel level, Player player, Vec3 at) {
        var tear = new ItemParticleOption(ParticleTypes.ITEM, new ItemStack(Items.GHAST_TEAR));
        SkillFx.particles(level, ParticleTypes.EXPLOSION, at.x, at.y + 0.4, at.z, 1, 0, 0, 0, 0);
        SkillFx.particles(level, ParticleTypes.FLASH, at.x, at.y + 0.5, at.z, 1, 0, 0, 0, 0);
        SkillFx.particles(level, tear, at.x, at.y + 0.5, at.z, 30, 0.6, 0.4, 0.6, 0.25);
        SkillFx.particles(level, ParticleTypes.ELECTRIC_SPARK, at.x, at.y + 0.5, at.z, 12, 0.5, 0.4, 0.5, 0.3);
        SkillFx.particles(level, ParticleTypes.FALLING_OBSIDIAN_TEAR, at.x, at.y + 1, at.z, 14, TEAR_RADIUS * 0.4, 0.6, TEAR_RADIUS * 0.4, 0);
        SkillFx.ring(level, at.add(0, 0.2, 0), new Vec3(0, 1, 0), TEAR_RADIUS, 24, ParticleTypes.CLOUD);
        level.playSound(null, BlockPos.containing(at), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 0.6f, 1.6f);
        level.playSound(null, BlockPos.containing(at), SoundEvents.GHAST_HURT, SoundSource.PLAYERS, 0.6f, 1.3f);
        level.playSound(null, BlockPos.containing(at), SoundEvents.AMETHYST_CLUSTER_BREAK, SoundSource.PLAYERS, 1.2f, 0.6f);
        SkillFx.screen(level, at, 16, ScreenFxPayload.SHAKE, 0.15f, 5);
        for (LivingEntity victim : SkillFx.victims(player, player, at.add(0, 1, 0), TEAR_RADIUS + 1)) {
            double flat = Math.hypot(victim.getX() - at.x, victim.getZ() - at.z);
            if (flat > TEAR_RADIUS + victim.getBbWidth() * 0.5 || victim.getY() > at.y + 3) {
                continue;
            }
            if (PowerKit.hit(level, player, victim, TEAR_DAMAGE)) {
                victim.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 80, 0, false, true));
                victim.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 0, false, true));
                PowerKit.shove(victim, victim.position().subtract(at), 0.35, 0.45);
            }
        }
    }

    // ---------------------------------------------------------------- Alpha Yeti: ice block

    /**
     * The Alpha Yeti tears a slab of blue ice out of the ground, hoists it over its head and hurls it at the target in a
     * high arc. It shatters on whatever it meets, freezing everything near.
     */
    public static boolean iceBlock(Player player) {
        if (!(player.level() instanceof ServerLevel level) || !player.isAlive()) {
            return false;
        }
        Optional<LivingEntity> target = TrophyAim.target(player, ICE_RANGE);
        if (target.isEmpty()) {
            Speech.hint(player, noEnemy());
            return false;
        }
        Vec3 flat = PowerKit.flat(target.get().position().subtract(player.position()), player);
        Vec3 ground = player.position().add(flat.scale(1.6));
        Display.BlockDisplay slab = DisplayFx.prop(level, Blocks.BLUE_ICE.defaultBlockState(), ground.add(0, 0.2, 0), 1.6f);
        level.playSound(null, player.blockPosition(), SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 1f, 0.5f);
        level.playSound(null, player.blockPosition(), SoundEvents.PLAYER_HURT_FREEZE, SoundSource.PLAYERS, 1f, 0.5f);
        level.playSound(null, player.blockPosition(), SoundEvents.RAVAGER_ROAR, SoundSource.PLAYERS, 0.8f, 0.6f);
        if (player instanceof ServerPlayer sp) {
            SkillFx.screen(sp, ScreenFxPayload.SHAKE, 0.25f, ICE_WINDUP);
        }
        liftIce(level, player, target.get(), slab, ground, 0);
        return true;
    }

    private static void liftIce(ServerLevel level, Player player, LivingEntity target, Display.BlockDisplay slab, Vec3 ground,
            int t) {
        if (slab == null || slab.isRemoved()) {
            return;
        }
        if (!PowerKit.live(level, player)) {
            slab.discard();
            return;
        }
        double k = t / (double) ICE_WINDUP;
        Vec3 overhead = player.position().add(0, 3.1, 0);
        Vec3 pos = ground.add(0, 0.2, 0).lerp(overhead, k * k * (3 - 2 * k));
        DisplayFx.fly(slab, pos, t * 9f, 0);
        var ice = new BlockParticleOption(ParticleTypes.BLOCK, Blocks.BLUE_ICE.defaultBlockState());
        SkillFx.particles(level, ice, ground.x, ground.y + 0.1, ground.z, 4, 0.7, 0.1, 0.7, 0.1);
        SkillFx.particles(level, ParticleTypes.SNOWFLAKE, pos.x, pos.y, pos.z, 3, 0.6, 0.6, 0.6, 0.02);
        if (t < ICE_WINDUP) {
            TrophyScheduler.later(level, 1, () -> liftIce(level, player, target, slab, ground, t + 1));
            return;
        }
        // thrown at wherever the target is now, in an arc that lands on it
        LivingEntity aim = PowerKit.live(level, target) ? target
                : TrophyAim.hostiles(player, player.position(), ICE_RANGE).stream().findFirst().orElse(null);
        Vec3 goal = aim == null ? player.position().add(PowerKit.flat(player.getLookAngle(), player).scale(8)) : PowerKit.centre(aim);
        Vec3 to = goal.subtract(pos);
        int flight = (int) Math.max(8, Math.min(22, Math.ceil(Math.hypot(to.x, to.z) / 1.3)));
        Vec3 vel = new Vec3(to.x / flight, (to.y + ICE_GRAVITY * flight * (flight - 1) / 2.0) / flight, to.z / flight);
        level.playSound(null, player.blockPosition(), SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.PLAYERS, 1.2f, 0.5f);
        level.playSound(null, player.blockPosition(), SoundEvents.WIND_CHARGE_THROW, SoundSource.PLAYERS, 1.2f, 0.6f);
        throwIce(level, player, slab, pos, vel, 0, flight + 10);
    }

    private static void throwIce(ServerLevel level, Player player, Display.BlockDisplay slab, Vec3 pos, Vec3 vel, int age,
            int maxAge) {
        if (slab.isRemoved()) {
            return;
        }
        if (!PowerKit.live(level, player)) {
            slab.discard();
            return;
        }
        Vec3 next = pos.add(vel);
        Vec3 impact = null;
        BlockHitResult wall = level.clip(new ClipContext(pos, next, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        if (wall.getType() != HitResult.Type.MISS) {
            impact = wall.getLocation();
        } else {
            AABB box = new AABB(next, next).inflate(0.8);
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box.inflate(0.5), e -> SkillFx.isVictim(e, player))) {
                if (e.getBoundingBox().inflate(0.8).clip(pos, next).isPresent() || e.getBoundingBox().inflate(0.8).contains(next)) {
                    impact = next;
                    break;
                }
            }
        }
        if (impact != null || age >= maxAge) {
            shatter(level, player, slab, impact == null ? next : impact);
            return;
        }
        DisplayFx.fly(slab, next, age * 22f, age * 14f);
        var ice = new BlockParticleOption(ParticleTypes.BLOCK, Blocks.BLUE_ICE.defaultBlockState());
        SkillFx.particles(level, ParticleTypes.SNOWFLAKE, next.x, next.y, next.z, 4, 0.5, 0.5, 0.5, 0.03);
        SkillFx.particles(level, ice, next.x, next.y, next.z, 2, 0.4, 0.4, 0.4, 0.05);
        SkillFx.particles(level, ParticleTypes.CLOUD, pos.x, pos.y, pos.z, 1, 0.3, 0.3, 0.3, 0.01);
        Vec3 fallen = new Vec3(vel.x, vel.y - ICE_GRAVITY, vel.z);
        TrophyScheduler.later(level, 1, () -> throwIce(level, player, slab, next, fallen, age + 1, maxAge));
    }

    private static void shatter(ServerLevel level, Player player, Display.BlockDisplay slab, Vec3 at) {
        slab.discard();
        var ice = new BlockParticleOption(ParticleTypes.BLOCK, Blocks.BLUE_ICE.defaultBlockState());
        var packed = new BlockParticleOption(ParticleTypes.BLOCK, Blocks.PACKED_ICE.defaultBlockState());
        SkillFx.particles(level, ParticleTypes.EXPLOSION, at.x, at.y, at.z, 1, 0, 0, 0, 0);
        SkillFx.particles(level, ice, at.x, at.y + 0.3, at.z, 60, 0.7, 0.5, 0.7, 0.35);
        SkillFx.particles(level, packed, at.x, at.y + 0.3, at.z, 30, 0.9, 0.4, 0.9, 0.25);
        SkillFx.particles(level, ParticleTypes.SNOWFLAKE, at.x, at.y + 0.5, at.z, 40, 1.2, 0.7, 1.2, 0.12);
        SkillFx.particles(level, ParticleTypes.ITEM_SNOWBALL, at.x, at.y + 0.5, at.z, 20, 1, 0.6, 1, 0.2);
        SkillFx.ring(level, at.add(0, 0.2, 0), new Vec3(0, 1, 0), ICE_RADIUS, 28, ParticleTypes.SNOWFLAKE);
        SkillFx.ring(level, at.add(0, 0.3, 0), new Vec3(0, 1, 0), ICE_RADIUS * 0.6, 16, ParticleTypes.CLOUD);
        for (int i = 0; i < 3; i++) {
            level.playSound(null, BlockPos.containing(at), SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 1.3f, 0.5f + i * 0.25f);
        }
        level.playSound(null, BlockPos.containing(at), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 0.6f, 1.3f);
        level.playSound(null, BlockPos.containing(at), SoundEvents.POWDER_SNOW_BREAK, SoundSource.PLAYERS, 1.2f, 0.5f);
        SkillFx.screen(level, at, 22, ScreenFxPayload.SHAKE, 0.35f, 8);
        for (LivingEntity victim : SkillFx.victims(player, player, at, ICE_RADIUS + 1)) {
            double dist = victim.getBoundingBox().getCenter().distanceTo(at);
            if (dist > ICE_RADIUS + victim.getBbWidth() * 0.5) {
                continue;
            }
            float falloff = (float) (1 - 0.4 * Math.min(1, dist / ICE_RADIUS));
            if (PowerKit.hit(level, player, victim, ICE_DAMAGE * falloff)) {
                victim.setTicksFrozen(victim.getTicksRequiredToFreeze() + 120);
                victim.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 70, 2, false, true));
                PowerKit.shove(victim, victim.position().subtract(at), 0.5 * falloff, 0.4);
                Vec3 c = PowerKit.centre(victim);
                SkillFx.particles(level, ParticleTypes.SNOWFLAKE, c.x, c.y, c.z, 10, 0.3, 0.5, 0.3, 0.05);
            }
        }
    }

    // ---------------------------------------------------------------- Snow Queen: ice prison

    /**
     * The Snow Queen's cage of ice closes round up to three of the nearest enemies. Spikes of ice rise round each and lean
     * together over its head; for three seconds the prisoner cannot leave, the cold gnaws at it, and the cage bursts at
     * the end. The spikes are only scenery.
     */
    public static boolean icePrison(Player player) {
        if (!(player.level() instanceof ServerLevel level) || !player.isAlive()) {
            return false;
        }
        List<LivingEntity> marks = PowerKit.marks(player, PRISON_RANGE, PRISON_TARGETS);
        if (marks.isEmpty()) {
            Speech.hint(player, noEnemy());
            return false;
        }
        level.playSound(null, player.blockPosition(), SoundEvents.PLAYER_HURT_FREEZE, SoundSource.PLAYERS, 1.2f, 0.7f);
        level.playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.2f, 0.5f);
        if (player instanceof ServerPlayer sp) {
            SkillFx.screen(sp, ScreenFxPayload.FOV_PULL, 0.2f, PRISON_WINDUP);
        }
        for (LivingEntity mark : marks) {
            // a thread of frost runs from the caster to each prisoner while the ice gathers
            for (int t = 0; t < PRISON_WINDUP; t++) {
                int step = t;
                TrophyScheduler.later(level, t, () -> {
                    if (PowerKit.live(level, player) && PowerKit.live(level, mark)) {
                        Vec3 from = player.getEyePosition().subtract(0, 0.3, 0);
                        Vec3 to = from.lerp(PowerKit.centre(mark), (step + 1) / (double) PRISON_WINDUP);
                        SkillFx.line(level, from, to, ParticleTypes.SNOWFLAKE, 0.6);
                        SkillFx.ring(level, mark.position().add(0, 0.1, 0), new Vec3(0, 1, 0), 2.2 - step * 0.15, 14,
                                ParticleTypes.SNOWFLAKE);
                    }
                });
            }
            TrophyScheduler.later(level, PRISON_WINDUP, () -> {
                if (PowerKit.live(level, mark)) {
                    closeCage(level, player, mark);
                }
            });
        }
        return true;
    }

    private static void closeCage(ServerLevel level, Player player, LivingEntity prisoner) {
        Vec3 anchor = prisoner.position();
        List<Display.BlockDisplay> bars = new ArrayList<>();
        int count = 8;
        double height = 2.9 + Math.max(0, prisoner.getBbHeight() - 1.8);
        double theta = Math.atan(PRISON_RADIUS / height);
        for (int i = 0; i < count; i++) {
            double a = i * Math.PI * 2 / count;
            Vec3 foot = anchor.add(Math.cos(a) * PRISON_RADIUS, -0.1, Math.sin(a) * PRISON_RADIUS);
            float[] lean = PowerKit.lean(-Math.cos(a), -Math.sin(a), theta * 0.9);
            BlockState block = i % 2 == 0 ? Blocks.BLUE_ICE.defaultBlockState() : Blocks.PACKED_ICE.defaultBlockState();
            bars.add(DisplayFx.rise(level, block, foot, 0.45f, (float) (height / Math.cos(theta * 0.9)), 0f, lean[0], lean[1], 6));
        }
        var ice = new BlockParticleOption(ParticleTypes.BLOCK, Blocks.ICE.defaultBlockState());
        SkillFx.particles(level, ice, anchor.x, anchor.y + 0.2, anchor.z, 40, PRISON_RADIUS, 0.2, PRISON_RADIUS, 0.2);
        SkillFx.particles(level, ParticleTypes.SNOWFLAKE, anchor.x, anchor.y + 1, anchor.z, 30, 0.8, 0.8, 0.8, 0.05);
        level.playSound(null, prisoner.blockPosition(), SoundEvents.GLASS_PLACE, SoundSource.PLAYERS, 1.4f, 0.5f);
        level.playSound(null, prisoner.blockPosition(), SoundEvents.POWDER_SNOW_STEP, SoundSource.PLAYERS, 1.2f, 0.5f);
        SkillFx.screen(level, anchor, 16, ScreenFxPayload.SHAKE, 0.2f, 6);
        imprison(level, player, prisoner, anchor, bars, 1);
    }

    private static void imprison(ServerLevel level, Player player, LivingEntity prisoner, Vec3 anchor,
            List<Display.BlockDisplay> bars, int t) {
        boolean over = t >= PRISON_TICKS || !PowerKit.live(level, prisoner) || !PowerKit.live(level, player);
        if (over) {
            shatterCage(level, player, prisoner, anchor, bars);
            return;
        }
        PowerKit.stop(prisoner);
        if (PowerKit.tether(prisoner, anchor, 0.3)) {
            prisoner.setDeltaMovement(0, prisoner.getDeltaMovement().y, 0);
        }
        prisoner.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 6, 6, false, false));
        prisoner.setTicksFrozen(Math.min(prisoner.getTicksRequiredToFreeze() - 1, prisoner.getTicksFrozen() + 4));
        double r = PRISON_RADIUS * 0.6;
        double a = t * 0.4;
        SkillFx.particles(level, ParticleTypes.SNOWFLAKE, anchor.x + Math.cos(a) * r, anchor.y + (t % 10) * 0.25, anchor.z + Math.sin(a) * r, 1,
                0.05, 0.05, 0.05, 0);
        SkillFx.particles(level, ParticleTypes.SNOWFLAKE, anchor.x - Math.cos(a) * r, anchor.y + ((t + 5) % 10) * 0.25,
                anchor.z - Math.sin(a) * r, 1, 0.05, 0.05, 0.05, 0);
        if (t % PRISON_PULSE == 0) {
            PowerKit.hit(level, player, prisoner, PRISON_PULSE_DAMAGE);
            Vec3 c = PowerKit.centre(prisoner);
            SkillFx.particles(level, ParticleTypes.ITEM_SNOWBALL, c.x, c.y, c.z, 6, 0.3, 0.4, 0.3, 0.05);
            level.playSound(null, prisoner.blockPosition(), SoundEvents.PLAYER_HURT_FREEZE, SoundSource.PLAYERS, 0.8f, 1.2f);
            level.playSound(null, prisoner.blockPosition(), SoundEvents.GLASS_HIT, SoundSource.PLAYERS, 0.8f, 0.6f);
        }
        TrophyScheduler.later(level, 1, () -> imprison(level, player, prisoner, anchor, bars, t + 1));
    }

    private static void shatterCage(ServerLevel level, Player player, LivingEntity prisoner, Vec3 anchor,
            List<Display.BlockDisplay> bars) {
        for (Display.BlockDisplay bar : bars) {
            DisplayFx.sink(level, bar, 0.45f, 4);
        }
        var ice = new BlockParticleOption(ParticleTypes.BLOCK, Blocks.BLUE_ICE.defaultBlockState());
        SkillFx.particles(level, ice, anchor.x, anchor.y + 1, anchor.z, 50, PRISON_RADIUS, 1, PRISON_RADIUS, 0.25);
        SkillFx.particles(level, ParticleTypes.SNOWFLAKE, anchor.x, anchor.y + 1, anchor.z, 30, 1, 1, 1, 0.1);
        SkillFx.ring(level, anchor.add(0, 0.3, 0), new Vec3(0, 1, 0), PRISON_RADIUS * 1.6, 20, ParticleTypes.CLOUD);
        level.playSound(null, BlockPos.containing(anchor), SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 1.4f, 0.6f);
        level.playSound(null, BlockPos.containing(anchor), SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 1.2f, 1.1f);
        if (PowerKit.live(level, prisoner) && player.isAlive() && PowerKit.hit(level, player, prisoner, PRISON_SHATTER_DAMAGE)) {
            PowerKit.shove(prisoner, prisoner.position().subtract(anchor), 0.3, 0.3);
            prisoner.setTicksFrozen(prisoner.getTicksRequiredToFreeze() + 60);
        }
    }
}
