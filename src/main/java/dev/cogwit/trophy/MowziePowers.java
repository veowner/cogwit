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
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The actives of the three Mowzie's Mobs trophies: Frostmaw's icy breath, Umvuthi's solar beam and the Sculptor's
 * stone pillars. Like {@link CataclysmPowers}, each method returns true if it fired and has no side effects when it
 * cannot; cooldowns are {@link TrophyService#castTrophy}'s business.
 */
public final class MowziePowers {
    // ice breath
    public static final int BREATH_WINDUP = 5, BREATH_TICKS = 30, BREATH_HIT_INTERVAL = 5;
    public static final double BREATH_RANGE = 8, BREATH_HALF_ANGLE = 35;
    public static final float BREATH_DAMAGE = 2f;
    private static final DustParticleOptions FROST = new DustParticleOptions(new Vector3f(0.70f, 0.90f, 1.00f), 1.3f);
    private static final DustParticleOptions FROST_DEEP = new DustParticleOptions(new Vector3f(0.35f, 0.65f, 1.00f), 1.0f);

    // solar beam
    public static final int BEAM_CHARGE_TICKS = 15, BEAM_BURN_TICKS = 40, BEAM_HIT_INTERVAL = 5;
    public static final double BEAM_RANGE = 20, BEAM_RADIUS = 1.6, BEAM_HEIGHT = 14;
    public static final float BEAM_DAMAGE = 2.5f;
    private static final int BEAM_RING = 10, BEAM_FLAMES = 4;

    // stone pillars
    public static final int PILLAR_TARGETS = 5, PILLAR_WARN_TICKS = 10, PILLAR_GAP_TICKS = 3, PILLAR_HOLD_TICKS = 26;
    public static final double PILLAR_RANGE = 10, PILLAR_RADIUS = 1.5;
    public static final float PILLAR_DAMAGE = 9f;

    private MowziePowers() {}

    // ---------------------------------------------------------------- Frostmaw: ice breath

    /** A cone of frost in front of the caster: a gathering breath, then a second and a half of snow and ice. */
    public static boolean iceBreath(Player player) {
        if (!(player.level() instanceof ServerLevel level) || !player.isAlive()) {
            return false;
        }
        level.playSound(null, player.blockPosition(), SoundEvents.BREEZE_INHALE, SoundSource.PLAYERS, 1.2f, 1.3f);
        if (player instanceof ServerPlayer sp) {
            SkillFx.screen(sp, ScreenFxPayload.FOV_PULL, 0.2f, BREATH_WINDUP + BREATH_TICKS);
        }
        breathTick(level, player, 0);
        return true;
    }

    private static void breathTick(ServerLevel level, Player player, int t) {
        if (!player.isAlive() || player.isRemoved() || player.level() != level) {
            return;
        }
        Vec3 look = player.getLookAngle();
        Vec3 mouth = player.getEyePosition().subtract(0, 0.25, 0).add(look.scale(0.5));
        if (t < BREATH_WINDUP) {
            // frost gathers in front of the face: a ring of flakes that closes on the mouth
            SkillFx.ring(level, mouth.add(look.scale(0.5)), look, 1.3 - t * 0.24, 12, ParticleTypes.SNOWFLAKE);
            SkillFx.particles(level, ParticleTypes.CLOUD, mouth.x, mouth.y, mouth.z, 2, 0.1, 0.1, 0.1, 0.01);
        } else {
            int open = t - BREATH_WINDUP;
            if (open == 0) {
                level.playSound(null, player.blockPosition(), SoundEvents.WIND_CHARGE_BURST.value(), SoundSource.PLAYERS,
                        1.2f, 1.4f);
                level.playSound(null, player.blockPosition(), SoundEvents.PLAYER_HURT_FREEZE, SoundSource.PLAYERS, 1f,
                        0.7f);
            }
            if (open % 6 == 0) {
                level.playSound(null, player.blockPosition(), SoundEvents.POWDER_SNOW_BREAK, SoundSource.PLAYERS, 0.9f,
                        0.8f + level.random.nextFloat() * 0.4f);
                level.playSound(null, player.blockPosition(), SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 0.25f, 2f);
            }
            double reach = breathReach(level, player, mouth, look);
            spray(level, mouth, look, reach, open);
            if (open % BREATH_HIT_INTERVAL == 0) {
                chill(level, player, mouth, look);
            }
        }
        if (t >= BREATH_WINDUP + BREATH_TICKS) {
            SkillFx.particles(level, ParticleTypes.SNOWFLAKE, mouth.x, mouth.y, mouth.z, 12, 0.3, 0.2, 0.3, 0.05);
            level.playSound(null, player.blockPosition(), SoundEvents.SNOW_BREAK, SoundSource.PLAYERS, 0.8f, 0.6f);
            return;
        }
        TrophyScheduler.later(level, 1, () -> breathTick(level, player, t + 1));
    }

    /** How far the breath carries along the line of sight before a wall stops it. */
    private static double breathReach(ServerLevel level, Player player, Vec3 mouth, Vec3 look) {
        BlockHitResult wall = level.clip(new ClipContext(mouth, mouth.add(look.scale(BREATH_RANGE)),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        return wall.getType() == HitResult.Type.MISS ? BREATH_RANGE : Math.max(0.5, wall.getLocation().distanceTo(mouth));
    }

    /** One tick of the cone: flakes, mist, ice shards and blue dust flying out and spreading, faster at the core. */
    private static void spray(ServerLevel level, Vec3 mouth, Vec3 look, double reach, int open) {
        double speed = 0.35 * (reach / BREATH_RANGE) + 0.1;
        // two vectors across the cone, so a random point of its disc can be picked
        Vec3 right = new Vec3(-look.z, 0, look.x);
        right = right.lengthSqr() < 1e-4 ? new Vec3(1, 0, 0) : right.normalize();
        Vec3 up = right.cross(look).normalize();
        double spread = Math.tan(Math.toRadians(BREATH_HALF_ANGLE)) * 0.85;
        var ice = new BlockParticleOption(ParticleTypes.BLOCK, Blocks.PACKED_ICE.defaultBlockState());
        for (int i = 0; i < 13; i++) {
            double a = level.random.nextDouble() * Math.PI * 2, r = Math.sqrt(level.random.nextDouble()) * spread;
            Vec3 dir = look.add(right.scale(Math.cos(a) * r)).add(up.scale(Math.sin(a) * r)).normalize();
            Vec3 from = mouth.add(dir.scale(0.3 + level.random.nextDouble() * 0.8));
            var kind = switch (i % 5) {
                case 0, 1 -> ParticleTypes.SNOWFLAKE;
                case 2 -> ParticleTypes.CLOUD;
                case 3 -> i % 2 == 0 ? FROST : FROST_DEEP;
                default -> open % 3 == 0 ? ice : ParticleTypes.ITEM_SNOWBALL;
            };
            SkillFx.particles(level, kind, from.x, from.y, from.z, 0, dir.x, dir.y, dir.z,
                    speed * (0.7 + level.random.nextDouble() * 0.6));
        }
    }

    private static void chill(ServerLevel level, Player player, Vec3 mouth, Vec3 look) {
        for (LivingEntity target : SkillFx.victims(player, player, mouth.add(look.scale(BREATH_RANGE / 2)),
                BREATH_RANGE / 2 + 2)) {
            Vec3 c = target.getBoundingBox().getCenter();
            Vec3 d = c.subtract(mouth);
            if (!PowerMath.inCone(d.x, d.y, d.z, look.x, look.y, look.z, BREATH_RANGE, BREATH_HALF_ANGLE,
                    target.getBbWidth() * 0.5)
                    || level.clip(new ClipContext(mouth, c, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player))
                    .getType() != HitResult.Type.MISS) {
                continue;
            }
            boolean immune = target.getType().is(EntityTypeTags.FREEZE_IMMUNE_ENTITY_TYPES);
            float damage = BREATH_DAMAGE * (target.getType().is(EntityTypeTags.FREEZE_HURTS_EXTRA_TYPES) ? 2 : immune ? 0.5f : 1);
            // the breath lands every five ticks, faster than the ten-tick hurt cooldown, so clear it
            target.invulnerableTime = 0;
            if (target.hurt(level.damageSources().indirectMagic(player, player), damage)) {
                target.clearFire();
                if (!immune) {
                    target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 100, 3, false, true));
                    target.setTicksFrozen(target.getTicksRequiredToFreeze() + 60);
                }
                Vec3 push = new Vec3(look.x, 0, look.z).normalize().scale(0.12);
                target.setDeltaMovement(target.getDeltaMovement().add(push));
                target.hurtMarked = true;
                SkillFx.particles(level, ParticleTypes.SNOWFLAKE, c.x, c.y, c.z, 8, 0.3, 0.4, 0.3, 0.04);
                SkillFx.particles(level, ParticleTypes.ITEM_SNOWBALL, c.x, c.y, c.z, 4, 0.3, 0.4, 0.3, 0.08);
            }
        }
    }

    // ---------------------------------------------------------------- Umvuthi: solar beam

    /**
     * A beam of sunlight from the sky onto the creature under the crosshair (else the one being fought, else the
     * nearest hostile): it charges for three quarters of a second, then burns for two seconds, following the target.
     */
    public static boolean solarBeam(Player player) {
        if (!(player.level() instanceof ServerLevel level) || !player.isAlive()) {
            return false;
        }
        Optional<LivingEntity> target = TrophyAim.target(player, BEAM_RANGE);
        if (target.isEmpty()) {
            Speech.hint(player, Component.translatable("cogwit.trophy.no_enemy_near"));
            return false;
        }
        level.playSound(null, player.blockPosition(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 1.2f, 1.6f);
        level.playSound(null, target.get().blockPosition(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.5f,
                0.6f);
        if (player instanceof ServerPlayer sp) {
            SkillFx.screen(sp, ScreenFxPayload.FOV_PULL, 0.15f, BEAM_CHARGE_TICKS);
        }
        Vec3 spot = target.get().position();
        List<Display.BlockDisplay> ring = new ArrayList<>();
        for (int i = 0; i < BEAM_RING; i++) {
            ring.add(DisplayFx.glow(DisplayFx.prop(level, Blocks.GOLD_BLOCK.defaultBlockState(), spot, 0.22f)));
        }
        Beam beam = new Beam(
                DisplayFx.glow(DisplayFx.box(level, Blocks.WHITE_CONCRETE.defaultBlockState(), spot, 0.04f, 1f, 0.04f)),
                DisplayFx.glow(DisplayFx.box(level, Blocks.ORANGE_STAINED_GLASS.defaultBlockState(), spot, 0.01f, 1f, 0.01f)),
                DisplayFx.glow(DisplayFx.box(level, Blocks.SHROOMLIGHT.defaultBlockState(), spot, 0.01f, 0.04f, 0.01f)),
                ring, new ArrayList<>());
        beamTick(level, player, target.get(), spot, 0, beam);
        return true;
    }

    /**
     * What the beam is made of, handed on from tick to tick: a pale core inside a halo of orange glass, a scorched
     * disc where it lands, a ring of gold that closes in while it charges and flames round its foot while it burns.
     * Block displays all, so the beam shows whatever the particle setting.
     */
    private record Beam(Display.BlockDisplay core, Display.BlockDisplay halo, Display.BlockDisplay scorch,
            List<Display.BlockDisplay> ring, List<Display.BlockDisplay> flames) {}

    private static void beamTick(ServerLevel level, Player player, LivingEntity target, Vec3 spot, int t, Beam beam) {
        if (!target.isAlive() || target.isRemoved() || target.level() != level || !player.isAlive()) {
            beamEnd(level, spot, beam, t > BEAM_CHARGE_TICKS);
            return;
        }
        // the light drifts after its target: quickly while it charges, slower once it burns, so a runner can slip away
        boolean burning = t >= BEAM_CHARGE_TICKS;
        spot = spot.lerp(target.position(), burning ? 0.3 : 0.6);
        Vec3 ground = spot;
        BlockHitResult ceiling = level.clip(new ClipContext(ground.add(0, 0.5, 0), ground.add(0, BEAM_HEIGHT, 0),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        double top = ceiling.getType() == HitResult.Type.MISS ? ground.y + BEAM_HEIGHT : ceiling.getLocation().y;
        float height = (float) (top - ground.y);
        Vec3 middle = new Vec3(ground.x, ground.y + height / 2, ground.z);

        if (!burning) {
            double charge = t / (double) BEAM_CHARGE_TICKS;
            // a thread of light that thickens from the sky and a ring of gold closing in on the ground
            float thread = (float) (0.04 + charge * 0.16);
            DisplayFx.fly(beam.core(), middle, 0, 0);
            DisplayFx.resize(beam.core(), thread, height, thread, 1);
            DisplayFx.fly(beam.halo(), middle, 0, 0);
            DisplayFx.fly(beam.scorch(), ground.add(0, 0.03, 0), 0, 0);
            double radius = 2.6 - charge * 1.8;
            for (int i = 0; i < beam.ring().size(); i++) {
                double a = i * 2 * Math.PI / beam.ring().size() + t * 0.25;
                DisplayFx.fly(beam.ring().get(i), ground.add(Math.cos(a) * radius, 0.2, Math.sin(a) * radius),
                        t * 20f, 0);
            }
            SkillFx.particles(level, ParticleTypes.END_ROD, ground.x, top, ground.z, 1, 0.2, 0.2, 0.2, 0.02);
            if (t == BEAM_CHARGE_TICKS - 1) {
                level.playSound(null, BlockPos.containing(ground), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS,
                        1.6f, 1.5f);
            }
        } else {
            int burn = t - BEAM_CHARGE_TICKS;
            if (burn == 0) {
                level.playSound(null, BlockPos.containing(ground), SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS,
                        0.7f, 1.7f);
                level.playSound(null, BlockPos.containing(ground), SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 1.3f, 0.6f);
                SkillFx.screen(level, ground, 24, ScreenFxPayload.FLASH, 0.4f, 8);
                SkillFx.particles(level, ParticleTypes.FLASH, ground.x, ground.y + 0.3, ground.z, 1, 0, 0, 0, 0);
                beam.ring().forEach(DisplayFx::remove);
                for (int i = 0; i < BEAM_FLAMES; i++) {
                    beam.flames().add(DisplayFx.glow(DisplayFx.box(level, Blocks.FIRE.defaultBlockState(), ground,
                            0.9f, 1.1f, 0.9f)));
                }
            }
            // the column swells in two ticks, then breathes; the scorched disc spreads under it
            float swell = Math.min(1f, (burn + 1) / 2f);
            float core = 0.2f + 0.25f * swell, halo = 1.7f * swell + 0.2f * (float) Math.sin(burn * 0.8);
            float disc = (float) (BEAM_RADIUS * 2) * Math.min(1f, (burn + 1) / 4f);
            DisplayFx.fly(beam.core(), middle, 0, 0);
            DisplayFx.resize(beam.core(), core, height, core, 1);
            DisplayFx.fly(beam.halo(), middle, 0, 0);
            DisplayFx.resize(beam.halo(), halo, height, halo, 1);
            DisplayFx.fly(beam.scorch(), ground.add(0, 0.03, 0), 0, 0);
            DisplayFx.resize(beam.scorch(), disc, 0.04f, disc, 1);
            for (int i = 0; i < beam.flames().size(); i++) {
                double a = i * 2 * Math.PI / beam.flames().size() + burn * 0.15;
                DisplayFx.fly(beam.flames().get(i), ground.add(Math.cos(a) * 0.95, 0.55, Math.sin(a) * 0.95),
                        (float) Math.toDegrees(a), 0);
            }
            for (int i = 0; i < 3; i++) {
                double y = ground.y + level.random.nextDouble() * (top - ground.y);
                SkillFx.particles(level, ParticleTypes.END_ROD, ground.x + (level.random.nextDouble() - 0.5) * 0.8, y,
                        ground.z + (level.random.nextDouble() - 0.5) * 0.8, 0, 0, -1, 0, 0.5);
            }
            SkillFx.particles(level, ParticleTypes.FLAME, ground.x, ground.y + 0.2, ground.z, 6, 0.8, 0.1, 0.8, 0.04);
            if (burn % 8 == 2) {
                level.playSound(null, BlockPos.containing(ground), SoundEvents.FIRE_AMBIENT, SoundSource.PLAYERS, 1f, 0.6f);
                level.playSound(null, BlockPos.containing(ground), SoundEvents.BEACON_AMBIENT, SoundSource.PLAYERS, 1f, 1.4f);
            }
            if (burn % BEAM_HIT_INTERVAL == 0) {
                scorch(level, player, ground);
            }
            if (burn >= BEAM_BURN_TICKS) {
                beamEnd(level, ground, beam, true);
                return;
            }
        }
        Vec3 next = spot;
        TrophyScheduler.later(level, 1, () -> beamTick(level, player, target, next, t + 1, beam));
    }

    private static void scorch(ServerLevel level, Player player, Vec3 ground) {
        for (LivingEntity victim : SkillFx.victims(player, player, ground.add(0, 1.5, 0), 4)) {
            double dx = victim.getX() - ground.x, dz = victim.getZ() - ground.z;
            if (Math.hypot(dx, dz) > BEAM_RADIUS + victim.getBbWidth() * 0.5 || victim.getY() < ground.y - 2) {
                continue;
            }
            // lands every five ticks, faster than the ten-tick hurt cooldown, so clear it
            victim.invulnerableTime = 0;
            if (victim.hurt(level.damageSources().indirectMagic(player, player), BEAM_DAMAGE)) {
                victim.igniteForSeconds(6);
                Vec3 c = victim.getBoundingBox().getCenter();
                SkillFx.particles(level, ParticleTypes.FLAME, c.x, c.y, c.z, 6, 0.25, 0.4, 0.25, 0.05);
                level.playSound(null, victim.blockPosition(), SoundEvents.GENERIC_BURN, SoundSource.PLAYERS, 0.5f, 1.3f);
            }
        }
    }

    /** The column thins to nothing and the disc and flames go out; a flash if it had been burning. */
    private static void beamEnd(ServerLevel level, Vec3 ground, Beam beam, boolean burnt) {
        beam.ring().forEach(DisplayFx::remove);
        DisplayFx.resize(beam.core(), 0.01f, 1f, 0.01f, 4);
        DisplayFx.resize(beam.halo(), 0.01f, 1f, 0.01f, 4);
        DisplayFx.resize(beam.scorch(), 0.01f, 0.04f, 0.01f, 6);
        beam.flames().forEach(flame -> DisplayFx.resize(flame, 0.05f, 0.05f, 0.05f, 3));
        TrophyScheduler.later(level, 7, () -> {
            DisplayFx.remove(beam.core());
            DisplayFx.remove(beam.halo());
            DisplayFx.remove(beam.scorch());
            beam.flames().forEach(DisplayFx::remove);
        });
        if (!burnt) {
            return;
        }
        SkillFx.particles(level, ParticleTypes.FLASH, ground.x, ground.y + 0.3, ground.z, 1, 0, 0, 0, 0);
        SkillFx.particles(level, ParticleTypes.FLAME, ground.x, ground.y + 0.3, ground.z, 24, 0.6, 0.3, 0.6, 0.12);
        SkillFx.particles(level, ParticleTypes.CAMPFIRE_COSY_SMOKE, ground.x, ground.y + 0.5, ground.z, 4, 0.3, 0.2, 0.3,
                0.03);
        level.playSound(null, BlockPos.containing(ground), SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 1.2f, 1.2f);
    }

    // ---------------------------------------------------------------- Sculptor: stone pillars

    /**
     * Stone pillars erupt under the five nearest hostile mobs, one after another: the ground cracks first, then a
     * pillar of stone shoots up and throws its victim into the air. The pillars are only scenery and sink back.
     */
    public static boolean stonePillars(Player player) {
        if (!(player.level() instanceof ServerLevel level) || !player.isAlive()) {
            return false;
        }
        List<LivingEntity> targets = new ArrayList<>();
        List<Vec3> spots = new ArrayList<>();
        for (LivingEntity mob : TrophyAim.hostiles(player, player.position().add(0, 1, 0), PILLAR_RANGE)) {
            // a pillar needs ground to stand on: fliers high above the floor are out of reach
            BlockHitResult floor = level.clip(new ClipContext(mob.position().add(0, 0.5, 0), mob.position().subtract(0, 8, 0),
                    ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mob));
            if (floor.getType() != HitResult.Type.MISS && targets.size() < PILLAR_TARGETS) {
                targets.add(mob);
                spots.add(floor.getLocation());
            }
        }
        if (targets.isEmpty()) {
            Speech.hint(player, Component.translatable("cogwit.trophy.no_enemy_near"));
            return false;
        }
        level.playSound(null, player.blockPosition(), SoundEvents.DEEPSLATE_BREAK, SoundSource.PLAYERS, 1.4f, 0.5f);
        level.playSound(null, player.blockPosition(), SoundEvents.GRINDSTONE_USE, SoundSource.PLAYERS, 0.8f, 0.5f);
        if (player instanceof ServerPlayer sp) {
            SkillFx.screen(sp, ScreenFxPayload.SHAKE, 0.3f, PILLAR_WARN_TICKS + PILLAR_GAP_TICKS * targets.size());
        }
        for (int i = 0; i < targets.size(); i++) {
            LivingEntity target = targets.get(i);
            int delay = i * PILLAR_GAP_TICKS;
            Vec3 first = spots.get(i);
            TrophyScheduler.later(level, delay, () -> crack(level, first, 0));
            TrophyScheduler.later(level, delay + 4, () -> crack(level, first, 1));
            TrophyScheduler.later(level, delay + 7, () -> crack(level, first, 2));
            TrophyScheduler.later(level, delay + PILLAR_WARN_TICKS, () -> erupt(level, player, target, first));
        }
        return true;
    }

    /** The warning: the ground splits in a ring that tightens round the spot where the pillar will come up. */
    private static void crack(ServerLevel level, Vec3 spot, int stage) {
        var rubble = new BlockParticleOption(ParticleTypes.BLOCK, Blocks.STONE.defaultBlockState());
        SkillFx.ring(level, spot.add(0, 0.1, 0), new Vec3(0, 1, 0), 1.7 - stage * 0.4, 12, rubble);
        SkillFx.particles(level, ParticleTypes.POOF, spot.x, spot.y + 0.1, spot.z, 3, 0.4, 0.05, 0.4, 0.01);
        level.playSound(null, BlockPos.containing(spot), SoundEvents.STONE_BREAK, SoundSource.PLAYERS, 0.7f,
                0.5f + stage * 0.15f);
    }

    private static void erupt(ServerLevel level, Player player, LivingEntity target, Vec3 spot) {
        Vec3 up = new Vec3(0, 1, 0);
        var stone = new BlockParticleOption(ParticleTypes.BLOCK, Blocks.STONE.defaultBlockState());
        List<Display.BlockDisplay> shards = new ArrayList<>();
        shards.add(DisplayFx.rise(level, Blocks.STONE.defaultBlockState(), spot, 1.4f, 3.4f,
                level.random.nextFloat() * 1.5f, 0f, 0f, 4));
        // two leaning slabs of rougher rock either side of the main pillar
        for (int k = 0; k < 2; k++) {
            double a = level.random.nextDouble() * Math.PI * 2 + k * Math.PI;
            Vec3 foot = spot.add(Math.cos(a) * 0.9, -0.1, Math.sin(a) * 0.9);
            shards.add(DisplayFx.rise(level, (k == 0 ? Blocks.ANDESITE : Blocks.COBBLESTONE).defaultBlockState(), foot,
                    0.8f, 1.8f + level.random.nextFloat() * 0.8f, (float) a, (float) Math.sin(a) * 0.35f,
                    (float) -Math.cos(a) * 0.35f, 4));
        }
        SkillFx.particles(level, stone, spot.x, spot.y + 0.3, spot.z, 50, 0.7, 0.3, 0.7, 0.25);
        SkillFx.particles(level, ParticleTypes.POOF, spot.x, spot.y + 0.2, spot.z, 10, 0.5, 0.1, 0.5, 0.08);
        SkillFx.ring(level, spot.add(0, 0.2, 0), up, 1.8, 16, ParticleTypes.CLOUD);
        level.playSound(null, BlockPos.containing(spot), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 0.5f, 1.6f);
        level.playSound(null, BlockPos.containing(spot), SoundEvents.POINTED_DRIPSTONE_LAND, SoundSource.PLAYERS, 1.4f, 0.5f);
        level.playSound(null, BlockPos.containing(spot), SoundEvents.STONE_BREAK, SoundSource.PLAYERS, 1.2f, 0.6f);
        SkillFx.screen(level, spot, 20, ScreenFxPayload.SHAKE, 0.25f, 6);

        if (target.isAlive() && target.level() == level) {
            // the pillar throws up whatever stands on it, and whatever stands close is knocked about
            for (LivingEntity victim : SkillFx.victims(player, player, spot.add(0, 1.5, 0), 3.2)) {
                double reach = PILLAR_RADIUS + victim.getBbWidth() * 0.5;
                if (Math.hypot(victim.getX() - spot.x, victim.getZ() - spot.z) > reach) {
                    continue;
                }
                if (victim.hurt(level.damageSources().indirectMagic(player, player), PILLAR_DAMAGE)) {
                    victim.setDeltaMovement(victim.getDeltaMovement().multiply(0.2, 0, 0.2).add(0, 0.9, 0));
                    victim.hurtMarked = true;
                    victim.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 30, 1, false, false));
                    Vec3 c = victim.getBoundingBox().getCenter();
                    SkillFx.particles(level, stone, c.x, c.y, c.z, 14, 0.3, 0.4, 0.3, 0.2);
                }
            }
        }

        TrophyScheduler.later(level, PILLAR_HOLD_TICKS, () -> {
            for (Display.BlockDisplay shard : shards) {
                DisplayFx.sink(level, shard, shard == shards.get(0) ? 1.4f : 0.8f, 5);
            }
            SkillFx.particles(level, stone, spot.x, spot.y + 0.4, spot.z, 24, 0.6, 0.6, 0.6, 0.1);
            level.playSound(null, BlockPos.containing(spot), SoundEvents.DEEPSLATE_BREAK, SoundSource.PLAYERS, 0.9f, 0.7f);
        });
    }
}
