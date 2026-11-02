package dev.cogwit.trophy;

import dev.cogwit.horizon.fx.SkillFx;
import dev.cogwit.network.ScreenFxPayload;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The active abilities of the four Cataclysm boss trophies: Leviathan's abyss beam, the Ancient Remnant's sandstorm,
 * Maledictus' phantom blades and Scylla's storm. Each method returns true if it fired and has no side effects when it
 * cannot. Cooldowns are the caller's business ({@link TrophyService#castTrophy}).
 */
public final class CataclysmPowers {
    // abyss beam
    public static final int BEAM_TICKS = 40, BEAM_HIT_INTERVAL = 4;
    public static final double BEAM_RANGE = 20, BEAM_HIT_RADIUS = 1.2;
    public static final float BEAM_DAMAGE = 2.5f;
    private static final DustParticleOptions BEAM_CORE = new DustParticleOptions(new Vector3f(0.30f, 0.90f, 0.95f), 1.0f);
    private static final DustParticleOptions BEAM_HALO = new DustParticleOptions(new Vector3f(0.05f, 0.30f, 0.80f), 1.6f);
    private static final DustParticleOptions BEAM_FLARE = new DustParticleOptions(new Vector3f(0.70f, 1.00f, 1.00f), 2.4f);
    /** Game time each player's beam started, so a second cast cannot overlap the first. Stale entries are harmless. */
    private static final Map<UUID, Long> CHANNELS = new ConcurrentHashMap<>();

    // phantom blades
    public static final int BLADES = 6, BLADE_STEPS = 8, BLADE_PIERCE = 2, BLADE_WINDUP_TICKS = 3;
    public static final double BLADE_FAN_DEGREES = 50, BLADE_STEP = 1.5, BLADE_HIT_RADIUS = 0.9;
    public static final float BLADE_DAMAGE = 7f;
    private static final DustParticleOptions BLADE_TRAIL = new DustParticleOptions(new Vector3f(0.72f, 0.88f, 1.00f), 0.7f);

    // storm
    public static final int STORM_TARGETS = 6, STORM_GAP_TICKS = 4, STORM_WINDUP_TICKS = 8;
    public static final double STORM_RANGE = 14;
    public static final float STORM_DAMAGE = 9f;

    private CataclysmPowers() {}

    // ---------------------------------------------------------------- Leviathan: abyss beam

    /** A channelled beam of deep water that follows the crosshair for two seconds, shoving whatever it crosses along. */
    public static boolean abyssBeam(Player player) {
        if (!(player.level() instanceof ServerLevel level) || !player.isAlive()) {
            return false;
        }
        long now = level.getGameTime();
        Long since = CHANNELS.get(player.getUUID());
        if (since != null && now >= since && now - since <= BEAM_TICKS + 1) {
            return false;
        }
        CHANNELS.put(player.getUUID(), now);
        level.playSound(null, player.blockPosition(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 1.4f, 0.5f);
        if (player instanceof ServerPlayer sp) {
            SkillFx.screen(sp, ScreenFxPayload.FOV_PULL, 0.25f, BEAM_TICKS);
        }
        beamTick(level, player, 0);
        return true;
    }

    private static void beamTick(ServerLevel level, Player player, int t) {
        if (!player.isAlive() || player.isRemoved() || player.level() != level) {
            CHANNELS.remove(player.getUUID());
            return;
        }
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        Vec3 origin = eye.add(look.scale(0.7)).subtract(0, 0.25, 0);
        net.minecraft.world.phys.BlockHitResult wall = level.clip(new ClipContext(eye, eye.add(look.scale(BEAM_RANGE)),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        boolean blocked = wall.getType() != HitResult.Type.MISS;
        Vec3 end = wall.getLocation();
        Vec3 span = end.subtract(origin);
        double len = span.length();
        if (len < 0.3) {
            // pressed against a wall: keep a stub so the beam never vanishes for a tick
            end = origin.add(look.scale(0.3));
            span = end.subtract(origin);
            len = 0.3;
        }
        Vec3 dir = span.scale(1 / len);
        // for the first four ticks the beam is a thin charging thread inside a collapsing ring, then it opens up
        boolean open = t >= BEAM_HIT_INTERVAL;
        if (t <= 4 && t % 2 == 0) {
            SkillFx.ring(level, origin.add(look.scale(0.3)), look, 1.0 - t * 0.2, 10, BEAM_CORE);
        }

        for (double d = 0; d <= len; d += 0.8) {
            Vec3 p = origin.add(dir.scale(d));
            SkillFx.particles(level, BEAM_CORE, p.x, p.y, p.z, 1, 0.03, 0.03, 0.03, 0);
        }
        if (open) {
            for (double d = 0.4; d <= len; d += 1.2) {
                Vec3 p = origin.add(dir.scale(d));
                double s = Math.max(0.08, 0.55 - 0.03 * d); // thick at the mouth, thin far away
                SkillFx.particles(level, BEAM_HALO, p.x, p.y, p.z, 2, s, s, s, 0);
            }
            Vec3 b = origin.add(dir.scale(level.random.nextDouble() * len));
            SkillFx.particles(level, ParticleTypes.BUBBLE, b.x, b.y, b.z, 2, 0.4, 0.4, 0.4, 0.02);
            if (t % 2 == 0) {
                Vec3 pop = origin.add(dir.scale(level.random.nextDouble() * len));
                SkillFx.particles(level, ParticleTypes.BUBBLE_POP, pop.x, pop.y, pop.z, 2, 0.3, 0.3, 0.3, 0.02);
            }
            SkillFx.particles(level, ParticleTypes.GLOW, origin.x, origin.y, origin.z, 1, 0.15, 0.15, 0.15, 0.02);
        }
        if (blocked) {
            Vec3 f = end.subtract(dir.scale(0.15));
            SkillFx.particles(level, BEAM_FLARE, f.x, f.y, f.z, 2, 0.2, 0.2, 0.2, 0);
            SkillFx.particles(level, ParticleTypes.GLOW, f.x, f.y, f.z, open ? 4 : 1, 0.25, 0.25, 0.25, 0.05);
            if (open && t % 2 == 0) {
                SkillFx.particles(level, ParticleTypes.BUBBLE_POP, f.x, f.y, f.z, 2, 0.3, 0.3, 0.3, 0.05);
            }
        }

        if (t % 20 == 1) {
            level.playSound(null, player.blockPosition(), SoundEvents.CONDUIT_AMBIENT, SoundSource.PLAYERS, 1.2f, 0.7f);
        }
        if (t > 0 && t % BEAM_HIT_INTERVAL == 0) {
            beamHit(level, player, origin, end, dir, len);
        }

        if (t >= BEAM_TICKS) {
            CHANNELS.remove(player.getUUID());
            Vec3 f = end.subtract(dir.scale(0.15));
            SkillFx.particles(level, ParticleTypes.FLASH, f.x, f.y, f.z, 1, 0, 0, 0, 0);
            SkillFx.particles(level, ParticleTypes.BUBBLE_POP, f.x, f.y, f.z, 12, 0.5, 0.5, 0.5, 0.15);
            level.playSound(null, player.blockPosition(), SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 1.2f, 0.7f);
            return;
        }
        TrophyScheduler.later(level, 1, () -> beamTick(level, player, t + 1));
    }

    private static void beamHit(ServerLevel level, Player player, Vec3 from, Vec3 to, Vec3 dir, double len) {
        for (LivingEntity target : SkillFx.victims(player, player, from.add(to).scale(0.5), len / 2 + 2.5)) {
            double reach = BEAM_HIT_RADIUS + target.getBbWidth() * 0.5;
            if (distanceToSegment(target.getBoundingBox().getCenter(), from, to) > reach) {
                continue;
            }
            // the beam lands every four ticks, faster than the ten-tick hurt cooldown, so clear it
            target.invulnerableTime = 0;
            if (target.hurt(level.damageSources().indirectMagic(player, player), BEAM_DAMAGE)) {
                target.setDeltaMovement(target.getDeltaMovement().scale(0.3).add(dir.x * 0.5, dir.y * 0.5 + 0.08,
                        dir.z * 0.5));
                target.hurtMarked = true;
                Vec3 c = target.getBoundingBox().getCenter();
                SkillFx.particles(level, ParticleTypes.BUBBLE_POP, c.x, c.y, c.z, 3, 0.25, 0.3, 0.25, 0.05);
                level.playSound(null, target.blockPosition(), SoundEvents.CONDUIT_ATTACK_TARGET, SoundSource.PLAYERS,
                        0.3f, 1.4f);
            }
        }
    }

    // ---------------------------------------------------------------- Ancient Remnant: sandstorm

    /** A column of sand around the caster that lifts, spins and blinds everything near. */
    public static boolean sandstorm(Player player) {
        if (!(player.level() instanceof ServerLevel level) || !player.isAlive()) {
            return false;
        }
        if (!level.getEntitiesOfClass(SandstormEntity.class, player.getBoundingBox().inflate(2),
                s -> s.isOwnedBy(player)).isEmpty()) {
            return false;
        }
        SandstormEntity.start(player);
        return true;
    }

    // ---------------------------------------------------------------- Maledictus: phantom blades

    /** One flying blade: where its tip is, which way it goes, who it has already tried and how many it has cut. */
    private static final class Blade {
        final Vec3 dir;
        Vec3 tip;
        int hits;
        final Set<Integer> tried = new HashSet<>();

        Blade(Vec3 tip, Vec3 dir) {
            this.tip = tip;
            this.dir = dir;
        }
    }

    /** A fan of six spectral blades: a brief gathering of wisps at the eyes, then they fly out and pierce two each. */
    public static boolean phantomBlades(Player player) {
        if (!(player.level() instanceof ServerLevel level) || !player.isAlive()) {
            return false;
        }
        Vec3 look = player.getLookAngle();
        Vec3 right = new Vec3(-look.z, 0, look.x);
        right = right.lengthSqr() < 1e-4 ? new Vec3(1, 0, 0) : right.normalize();
        Vec3[] dirs = new Vec3[BLADES];
        double half = BLADE_FAN_DEGREES / 2, stepDeg = BLADE_FAN_DEGREES / (BLADES - 1);
        for (int i = 0; i < BLADES; i++) {
            double a = Math.toRadians(-half + i * stepDeg);
            dirs[i] = look.scale(Math.cos(a)).add(right.scale(Math.sin(a))).normalize();
        }

        // wind-up: a pale ring opens at the caster's eyes and a wisp hovers where each blade will leave
        Vec3 eye = player.getEyePosition().subtract(0, 0.2, 0);
        SkillFx.ring(level, eye.add(look.scale(0.6)), look, 0.9, 12, BLADE_TRAIL);
        for (Vec3 d : dirs) {
            Vec3 w = eye.add(d.scale(1.1));
            SkillFx.particles(level, ParticleTypes.SOUL, w.x, w.y, w.z, 1, 0.02, 0.02, 0.02, 0.01);
        }
        level.playSound(null, player.blockPosition(), SoundEvents.VEX_CHARGE, SoundSource.PLAYERS, 0.9f, 1.3f);

        TrophyScheduler.later(level, BLADE_WINDUP_TICKS, () -> {
            if (!player.isAlive() || player.isRemoved() || player.level() != level) {
                return;
            }
            Vec3 start = player.getEyePosition().subtract(0, 0.2, 0).add(look.scale(0.5));
            for (Vec3 d : dirs) {
                bladeStep(level, player, new Blade(start, d), 1);
            }
            level.playSound(null, player.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1.2f,
                    0.8f);
            level.playSound(null, player.blockPosition(), SoundEvents.SOUL_ESCAPE.value(), SoundSource.PLAYERS, 1f,
                    1.2f);
            if (player instanceof ServerPlayer sp) {
                SkillFx.screen(sp, ScreenFxPayload.FOV_PUNCH, 0.25f, 8);
            }
        });
        return true;
    }

    private static void bladeStep(ServerLevel level, Player player, Blade blade, int step) {
        Vec3 from = blade.tip;
        Vec3 to = from.add(blade.dir.scale(BLADE_STEP));
        net.minecraft.world.phys.BlockHitResult wall = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, player));
        boolean blocked = wall.getType() != HitResult.Type.MISS;
        if (blocked) {
            to = wall.getLocation();
        }
        // a faint trail behind the tip, and the slash itself, drawn smaller than vanilla's full-size sweep
        for (int k = 0; k < 3; k++) {
            Vec3 p = from.lerp(to, k / 3.0);
            SkillFx.particles(level, BLADE_TRAIL, p.x, p.y, p.z, 1, 0.03, 0.03, 0.03, 0);
        }
        SkillFx.particles(level, ParticleTypes.SWEEP_ATTACK, to.x, to.y, to.z, 0, 0.5, 0, 0, 1.0);

        // everything the blade crossed this step, nearest first, until it has cut its fill
        List<LivingEntity> near = new ArrayList<>(SkillFx.victims(player, player, from.add(to).scale(0.5), 2.75));
        near.sort(Comparator.comparingDouble(e -> e.getBoundingBox().getCenter().subtract(from).dot(blade.dir)));
        for (LivingEntity target : near) {
            if (blade.hits >= BLADE_PIERCE) {
                break;
            }
            Vec3 c = target.getBoundingBox().getCenter();
            if (blade.tried.contains(target.getId())
                    || distanceToSegment(c, from, to) > BLADE_HIT_RADIUS + target.getBbWidth() * 0.5) {
                continue;
            }
            blade.tried.add(target.getId());
            // a neighbouring blade a moment ago may have left it in its hurt cooldown: that blade then flies on
            if (target.hurt(level.damageSources().indirectMagic(player, player), BLADE_DAMAGE)) {
                blade.hits++;
                target.setDeltaMovement(target.getDeltaMovement().add(blade.dir.x * 0.6, 0.18, blade.dir.z * 0.6));
                target.hurtMarked = true;
                SkillFx.particles(level, ParticleTypes.SOUL, c.x, c.y, c.z, 4, 0.25, 0.3, 0.25, 0.04);
                SkillFx.particles(level, ParticleTypes.SWEEP_ATTACK, c.x, c.y, c.z, 0, 0.8, 0, 0, 1.0);
            }
        }

        blade.tip = to;
        if (blocked || blade.hits >= BLADE_PIERCE || step >= BLADE_STEPS) {
            SkillFx.particles(level, ParticleTypes.SOUL, to.x, to.y, to.z, 3, 0.1, 0.1, 0.1, 0.03);
            return;
        }
        TrophyScheduler.later(level, 1, () -> bladeStep(level, player, blade, step + 1));
    }

    // ---------------------------------------------------------------- Scylla: storm

    /** Lightning on up to six creatures near the caster, hostile ones first, one strike every four ticks. */
    public static boolean storm(Player player) {
        if (!(player.level() instanceof ServerLevel level) || !player.isAlive()) {
            return false;
        }
        List<LivingEntity> targets = new ArrayList<>(SkillFx.victims(player, player, player.position().add(0, 1, 0),
                STORM_RANGE));
        if (targets.isEmpty()) {
            return false;
        }
        targets.sort(Comparator.<LivingEntity>comparingInt(e -> e instanceof Enemy ? 0 : 1)
                .thenComparingDouble(e -> e.distanceToSqr(player)));
        if (targets.size() > STORM_TARGETS) {
            targets = targets.subList(0, STORM_TARGETS);
        }

        // wind-up: thunder rolls in, and a ring of sparks marks each creature that is about to be struck
        level.playSound(null, player.blockPosition(), SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 0.6f,
                0.8f);
        if (player instanceof ServerPlayer sp) {
            SkillFx.screen(sp, ScreenFxPayload.SHAKE, 0.2f, 8);
        }
        for (int i = 0; i < targets.size(); i++) {
            LivingEntity target = targets.get(i);
            SkillFx.ring(level, target.position().add(0, 0.1, 0), new Vec3(0, 1, 0), 1.0, 8,
                    ParticleTypes.ELECTRIC_SPARK);
            TrophyScheduler.later(level, STORM_WINDUP_TICKS + i * STORM_GAP_TICKS, () -> strike(level, player, target));
        }
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
        // magic damage with the caster as attacker, so kills count for them (the bare lightning source has none)
        if (target.hurt(level.damageSources().indirectMagic(player, player), STORM_DAMAGE) && target.isAlive()) {
            target.igniteForSeconds(4);
            target.setDeltaMovement(target.getDeltaMovement().scale(0.3).add(0, 0.32, 0));
            target.hurtMarked = true;
            target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 20, 1, false, false));
        }
        Vec3 up = new Vec3(0, 1, 0);
        SkillFx.ring(level, at.add(0, 0.1, 0), up, 1.5, 14, ParticleTypes.ELECTRIC_SPARK);
        SkillFx.particles(level, ParticleTypes.ELECTRIC_SPARK, at.x, at.y + 0.8, at.z, 10, 0.3, 0.6, 0.3, 0.3);
        TrophyScheduler.later(level, 2, () -> SkillFx.ring(level, at.add(0, 0.1, 0), up, 2.6, 18,
                ParticleTypes.ELECTRIC_SPARK));
        SkillFx.screen(level, at, 24, ScreenFxPayload.FLASH, 0.25f, 6);
    }

    // ---------------------------------------------------------------- helpers

    /** Shortest distance from a point to the segment a-b. */
    private static double distanceToSegment(Vec3 p, Vec3 a, Vec3 b) {
        Vec3 ab = b.subtract(a);
        double l2 = ab.lengthSqr();
        double t = l2 < 1e-9 ? 0 : Math.max(0, Math.min(1, p.subtract(a).dot(ab) / l2));
        return p.distanceTo(a.add(ab.scale(t)));
    }
}
