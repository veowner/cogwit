package dev.cogwit.trophy;

import dev.cogwit.horizon.fx.SkillFx;
import dev.cogwit.network.ScreenFxPayload;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ShulkerBullet;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SnowyDirtBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Four Cataclysm actives: Ignis's fire dash, the Monstrosity's lava slam, the Ender Guardian's spikes, the Harbinger's missiles. */
public final class CataclysmStrikes {
    public static final String MISSILE_TAG = "cogwit_missile";
    public static final float DASH_DAMAGE = 6f, GEYSER_DAMAGE = 6f, SPIKE_DAMAGE = 7f, MISSILE_DAMAGE = 7f;

    private CataclysmStrikes() {}

    // ---------------------------------------------------------------- Ignis: fire dash

    /** A burst forward that leaves a trail of fire; everything it passes through catches light. */
    public static boolean fireDash(Player player) {
        if (!(player.level() instanceof ServerLevel level)) {
            return false;
        }
        Vec3 look = player.getLookAngle();
        Vec3 dir = new Vec3(look.x, Math.max(-0.1, look.y) * 0.5, look.z).normalize();
        player.setDeltaMovement(dir.scale(2.3).add(0, 0.25, 0));
        player.hurtMarked = true;
        player.resetFallDistance();
        Set<UUID> burned = new HashSet<>();
        for (int t = 0; t < 9; t++) {
            TrophyScheduler.later(level, t, () -> {
                Vec3 at = player.position();
                SkillFx.particles(level, ParticleTypes.FLAME, at.x, at.y + 0.6, at.z, 14, 0.35, 0.5, 0.35, 0.03);
                SkillFx.particles(level, ParticleTypes.LAVA, at.x, at.y + 0.3, at.z, 2, 0.2, 0.1, 0.2, 0);
                SkillFx.particles(level, ParticleTypes.SMALL_FLAME, at.x, at.y + 1.2, at.z, 6, 0.4, 0.4, 0.4, 0.01);
                for (LivingEntity target : SkillFx.victims(player, player, at.add(0, 1, 0), 2)) {
                    if (burned.add(target.getUUID())) {
                        target.hurt(level.damageSources().indirectMagic(player, player), DASH_DAMAGE);
                        target.igniteForSeconds(5);
                        target.setDeltaMovement(target.getDeltaMovement().add(dir.x * 0.6, 0.35, dir.z * 0.6));
                        target.hurtMarked = true;
                    }
                }
            });
        }
        level.playSound(null, player.blockPosition(), SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 1.2f, 0.7f);
        level.playSound(null, player.blockPosition(), SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 1f, 0.6f);
        if (player instanceof ServerPlayer sp) {
            SkillFx.screen(sp, ScreenFxPayload.FOV_PUNCH, 0.9f, 10);
        }
        return true;
    }

    // ---------------------------------------------------------------- Netherite Monstrosity: lava slam

    /** The geysers stand on two rings round the player, the inner one bursting first. */
    public static final double INNER_RING = 3.5, OUTER_RING = 6.5;
    public static final int INNER_GEYSERS = 6, OUTER_GEYSERS = 9;
    private static final double[] GEYSER_RINGS = {INNER_RING, OUTER_RING};
    private static final int[] GEYSERS_PER_RING = {INNER_GEYSERS, OUTER_GEYSERS};
    public static final int GEYSER_WARN_TICKS = 5, GEYSER_HOLD_TICKS = 12;
    /** By this many ticks after the slam every geyser, crack and plate has sunk away. */
    public static final int SLAM_TICKS = 60;
    private static final int CRACKS = 8, CRACK_STEPS = 7, BLOB_TICKS = 18;
    private static final float GEYSER_CUBE = 0.62f;

    /**
     * A slam into the ground: the ground round the feet buckles, glowing cracks run outwards, and along them the
     * ground heats up and bursts into columns of magma that throw mobs into the air. All of it is block displays, so
     * it shows whatever the particle setting; particles only add sparks on top.
     */
    public static boolean lavaSlam(Player player) {
        if (!(player.level() instanceof ServerLevel level)) {
            return false;
        }
        Vec3 c = player.position();
        level.playSound(null, player.blockPosition(), SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 1.2f, 0.5f);
        level.playSound(null, player.blockPosition(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 0.8f,
                0.6f);
        for (LivingEntity target : SkillFx.victims(player, player, c, 6)) {
            Vec3 away = target.position().subtract(c).multiply(1, 0, 1).normalize();
            target.setDeltaMovement(away.x * 0.9, 0.35, away.z * 0.9);
            target.hurtMarked = true;
        }
        buckle(level, c);
        crack(level, c);
        for (int r = 0; r < GEYSER_RINGS.length; r++) {
            double ring = GEYSER_RINGS[r];
            int count = GEYSERS_PER_RING[r];
            for (int i = 0; i < count; i++) {
                double a = i * 2 * Math.PI / count + ring;
                Vec3 spot = ground(level, c.add(Math.cos(a) * ring, 1.5, Math.sin(a) * ring));
                // the inner ring heats up as the cracks reach it, the outer one a little later, going round
                TrophyScheduler.later(level, (r == 0 ? 3 : 8) + i * 2, () -> heat(level, player, spot));
            }
        }
        SkillFx.particles(level, new BlockParticleOption(ParticleTypes.BLOCK, Blocks.NETHERRACK.defaultBlockState()),
                c.x, c.y + 0.1, c.z, 40, 1.2, 0.1, 1.2, 0.3);
        if (player instanceof ServerPlayer sp) {
            SkillFx.screen(sp, ScreenFxPayload.SHAKE, 0.7f, 14);
        }
        return true;
    }

    /** Plates of the ground round the feet tip up and outwards like the rim of a crater, then settle back. */
    private static void buckle(ServerLevel level, Vec3 c) {
        BlockState floor = level.getBlockState(BlockPos.containing(c).below());
        if (floor.getBlock() instanceof SnowyDirtBlock) {
            // torn-up turf shows its dirt
            floor = Blocks.DIRT.defaultBlockState();
        } else if (floor.getRenderShape() != RenderShape.MODEL) {
            floor = Blocks.NETHERRACK.defaultBlockState();
        }
        List<Display.BlockDisplay> plates = new ArrayList<>();
        int count = 10;
        for (int i = 0; i < count; i++) {
            double a = i * 2 * Math.PI / count + level.random.nextDouble() * 0.25;
            double dx = Math.cos(a), dz = Math.sin(a);
            float[] lean = PowerKit.lean(dx, dz, 0.85 + level.random.nextDouble() * 0.35);
            BlockState block = i % 3 == 0 ? Blocks.BLACKSTONE.defaultBlockState() : floor;
            plates.add(DisplayFx.rise(level, block, c.add(dx * 1.3, -0.3, dz * 1.3), 0.85f,
                    0.8f + level.random.nextFloat() * 0.5f, (float) a, lean[0], lean[1], 2));
        }
        TrophyScheduler.later(level, 30, () -> plates.forEach(plate -> DisplayFx.sink(level, plate, 0.85f, 8)));
    }

    /** Glowing cracks run out from the feet a step a tick, zigzagging, then close up. */
    private static void crack(ServerLevel level, Vec3 c) {
        BlockState magma = Blocks.MAGMA_BLOCK.defaultBlockState();
        List<Display.BlockDisplay> cracks = new ArrayList<>();
        for (int k = 0; k < CRACKS; k++) {
            double heading = k * 2 * Math.PI / CRACKS + 0.2;
            for (int step = 1; step <= CRACK_STEPS; step++) {
                double a = heading + Math.sin(step * 1.9 + k * 2.3) * 0.22;
                Vec3 at = ground(level, c.add(Math.cos(a) * (step - 0.4), 1.5, Math.sin(a) * (step - 0.4)))
                        .add(0, 0.01, 0);
                // a wall's depth runs along (sin yaw, cos yaw): along the crack
                float yaw = (float) Math.atan2(Math.cos(a), Math.sin(a)), width = 0.3f - step * 0.025f;
                TrophyScheduler.later(level, step, () -> cracks.add(
                        DisplayFx.glow(DisplayFx.wall(level, magma, at, width, 1.1f, 0.07f, yaw, 1))));
            }
        }
        TrophyScheduler.later(level, 34, () -> cracks.forEach(crack -> DisplayFx.sinkWall(level, crack, 0.02f, 1.1f, 6)));
    }

    /** The ground at a geyser glows red for a moment as a warning, then bursts. */
    private static void heat(ServerLevel level, Player owner, Vec3 at) {
        Display.BlockDisplay pool = DisplayFx.glow(DisplayFx.wall(level, Blocks.MAGMA_BLOCK.defaultBlockState(),
                at.add(0, 0.015, 0), 1.3f, 1.3f, 0.09f, level.random.nextFloat() * 6.3f, GEYSER_WARN_TICKS));
        level.playSound(null, BlockPos.containing(at), SoundEvents.LAVA_AMBIENT, SoundSource.PLAYERS, 0.9f, 0.7f);
        SkillFx.particles(level, ParticleTypes.SMOKE, at.x, at.y + 0.2, at.z, 6, 0.4, 0.05, 0.4, 0.01);
        TrophyScheduler.later(level, GEYSER_WARN_TICKS, () -> geyser(level, owner, at, pool));
    }

    /**
     * A column of magma cubes stacked two a tick, in a sheath of glowing glass with fire on top, spitting gobs of
     * magma; then it goes down from the top. Stacked cubes rather than one stretched block, which reads as a log.
     */
    private static void geyser(ServerLevel level, Player owner, Vec3 at, Display.BlockDisplay pool) {
        int cubes = 4 + level.random.nextInt(3);
        float height = cubes * GEYSER_CUBE;
        List<Display.BlockDisplay> column = new ArrayList<>();
        for (int k = 0; k < cubes; k++) {
            Vec3 centre = at.add(0, GEYSER_CUBE * (k + 0.5), 0);
            float twist = level.random.nextFloat() * 90f;
            TrophyScheduler.later(level, k / 2, () -> {
                Display.BlockDisplay cube = DisplayFx.glow(DisplayFx.box(level, Blocks.MAGMA_BLOCK.defaultBlockState(),
                        centre, 0.05f, 0.05f, 0.05f));
                DisplayFx.fly(cube, centre, twist, 0);
                TrophyScheduler.later(level, 1, () -> DisplayFx.resize(cube, GEYSER_CUBE, GEYSER_CUBE, GEYSER_CUBE, 2));
                column.add(cube);
            });
        }
        Display.BlockDisplay sheath = DisplayFx.glow(DisplayFx.rise(level,
                Blocks.ORANGE_STAINED_GLASS.defaultBlockState(), at.add(0, -0.1, 0), 1.0f, height * 0.7f,
                level.random.nextFloat() * 6.3f, 0, 0, 3));
        // the fire grows at the top as the column reaches it
        Display.BlockDisplay crown = DisplayFx.glow(DisplayFx.box(level, Blocks.FIRE.defaultBlockState(),
                at.add(0, height + 0.5, 0), 0.05f, 0.05f, 0.05f));
        TrophyScheduler.later(level, cubes / 2, () -> DisplayFx.resize(crown, 1.2f, 1.4f, 1.2f, 2));
        for (int i = 0; i < 3; i++) {
            blob(level, at.add(0, height * 0.8, 0));
        }
        SkillFx.particles(level, ParticleTypes.LAVA, at.x, at.y + 0.5, at.z, 8, 0.25, 0.6, 0.25, 0);
        SkillFx.particles(level, ParticleTypes.FLAME, at.x, at.y + height, at.z, 10, 0.3, 0.3, 0.3, 0.05);
        level.playSound(null, BlockPos.containing(at), SoundEvents.LAVA_POP, SoundSource.PLAYERS, 1f, 0.8f);
        level.playSound(null, BlockPos.containing(at), SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 0.7f, 0.6f);
        for (LivingEntity target : SkillFx.victims(owner, owner, at.add(0, 1, 0), 1.6)) {
            target.hurt(level.damageSources().indirectMagic(owner, owner), GEYSER_DAMAGE);
            target.igniteForSeconds(4);
            target.setDeltaMovement(target.getDeltaMovement().multiply(0.3, 0, 0.3).add(0, 1.1, 0));
            target.hurtMarked = true;
        }
        TrophyScheduler.later(level, GEYSER_HOLD_TICKS, () -> {
            DisplayFx.resize(crown, 0.05f, 0.05f, 0.05f, 2);
            TrophyScheduler.later(level, 3, () -> DisplayFx.remove(crown));
            // down from the top, two cubes a tick
            for (int k = 0; k < column.size(); k++) {
                Display.BlockDisplay cube = column.get(k);
                int after = (column.size() - 1 - k) / 2;
                TrophyScheduler.later(level, after, () -> DisplayFx.resize(cube, 0.05f, 0.05f, 0.05f, 2));
                TrophyScheduler.later(level, after + 3, () -> DisplayFx.remove(cube));
            }
            DisplayFx.sink(level, sheath, 1.0f, 6);
            DisplayFx.sinkWall(level, pool, 0.05f, 0.05f, 8);
            SkillFx.particles(level, ParticleTypes.CAMPFIRE_COSY_SMOKE, at.x, at.y + 0.6, at.z, 2, 0.2, 0.2, 0.2, 0.02);
            level.playSound(null, BlockPos.containing(at), SoundEvents.LAVA_EXTINGUISH, SoundSource.PLAYERS, 0.5f, 1f);
        });
    }

    /** A gob of magma thrown out of a geyser: it arcs and spins and is gone when it lands. */
    private static void blob(ServerLevel level, Vec3 from) {
        double a = level.random.nextDouble() * Math.PI * 2, out = 0.12 + level.random.nextDouble() * 0.14;
        Vec3 velocity = new Vec3(Math.cos(a) * out, 0.45 + level.random.nextDouble() * 0.25, Math.sin(a) * out);
        Display.BlockDisplay gob = DisplayFx.glow(DisplayFx.prop(level, Blocks.MAGMA_BLOCK.defaultBlockState(), from,
                0.28f + level.random.nextFloat() * 0.12f));
        fling(level, gob, from, velocity, 0);
    }

    private static void fling(ServerLevel level, Display.BlockDisplay gob, Vec3 at, Vec3 velocity, int age) {
        if (gob == null || gob.isRemoved()) {
            return;
        }
        Vec3 next = at.add(velocity);
        BlockPos below = BlockPos.containing(next);
        if (age >= BLOB_TICKS || !level.getBlockState(below).getCollisionShape(level, below).isEmpty()) {
            SkillFx.particles(level, ParticleTypes.LAVA, at.x, at.y, at.z, 2, 0.1, 0.05, 0.1, 0);
            gob.discard();
            return;
        }
        DisplayFx.fly(gob, next, age * 25f, age * 17f);
        Vec3 fallen = velocity.add(0, -0.08, 0);
        TrophyScheduler.later(level, 1, () -> fling(level, gob, next, fallen, age + 1));
    }

    // ---------------------------------------------------------------- Ender Guardian: void spikes

    /** A wave of void spikes bursting from the ground ahead, one after another. */
    public static boolean voidSpikes(Player player) {
        if (!(player.level() instanceof ServerLevel level)) {
            return false;
        }
        Vec3 look = player.getLookAngle();
        Vec3 dir = new Vec3(look.x, 0, look.z).normalize();
        Set<UUID> hit = new HashSet<>();
        for (int i = 1; i <= 12; i++) {
            Vec3 spot = ground(level, player.position().add(dir.scale(i * 1.1)).add(0, 1.5, 0));
            int step = i;
            TrophyScheduler.later(level, i * 2, () -> spike(level, player, spot, hit, step));
        }
        level.playSound(null, player.blockPosition(), SoundEvents.ENDERMAN_SCREAM, SoundSource.PLAYERS, 0.5f, 0.5f);
        return true;
    }

    private static void spike(ServerLevel level, Player owner, Vec3 at, Set<UUID> hit, int step) {
        BlockParticleOption shard = new BlockParticleOption(ParticleTypes.BLOCK,
                (step % 2 == 0 ? Blocks.CRYING_OBSIDIAN : Blocks.OBSIDIAN).defaultBlockState());
        SkillFx.particles(level, shard, at.x, at.y + 0.6, at.z, 18, 0.15, 0.7, 0.15, 0.15);
        for (int y = 0; y < 5; y++) {
            SkillFx.particles(level, ParticleTypes.REVERSE_PORTAL, at.x, at.y + y * 0.45, at.z, 2, 0.05, 0.05, 0.05, 0.02);
        }
        SkillFx.particles(level, ParticleTypes.END_ROD, at.x, at.y + 2.3, at.z, 2, 0.05, 0.05, 0.05, 0.02);
        level.playSound(null, BlockPos.containing(at), SoundEvents.EVOKER_FANGS_ATTACK, SoundSource.PLAYERS, 0.7f,
                0.6f + step * 0.04f);
        for (LivingEntity target : SkillFx.victims(owner, owner, at.add(0, 1, 0), 1.5)) {
            if (hit.add(target.getUUID())) {
                target.hurt(level.damageSources().indirectMagic(owner, owner), SPIKE_DAMAGE);
                target.setDeltaMovement(target.getDeltaMovement().multiply(0.2, 0, 0.2).add(0, 0.9, 0));
                target.hurtMarked = true;
            }
        }
    }

    // ---------------------------------------------------------------- Harbinger: homing missiles

    /** Four homing missiles, one per nearby enemy (wrapping round if there are fewer). */
    public static boolean missiles(Player player) {
        if (!(player.level() instanceof ServerLevel level)) {
            return false;
        }
        List<LivingEntity> targets = SkillFx.victims(player, player, player.position(), 20).stream()
                .filter(e -> e instanceof net.minecraft.world.entity.monster.Enemy)
                .sorted(Comparator.comparingDouble(player::distanceToSqr)).toList();
        if (targets.isEmpty()) {
            dev.cogwit.Speech.hint(player, net.minecraft.network.chat.Component.translatable("cogwit.trophy.no_targets"));
            return false;
        }
        for (int i = 0; i < 4; i++) {
            LivingEntity target = targets.get(i % targets.size());
            int index = i;
            TrophyScheduler.later(level, i * 4, () -> {
                if (!target.isAlive()) {
                    return;
                }
                ShulkerBullet missile = new ShulkerBullet(level, player, target, Direction.Axis.Y);
                missile.addTag(MISSILE_TAG);
                missile.setPos(player.getX(), player.getEyeY() + 0.3, player.getZ());
                missile.setDeltaMovement(0, 0.6, 0);
                level.addFreshEntity(missile);
                SkillFx.particles(level, ParticleTypes.LARGE_SMOKE, player.getX(), player.getEyeY() + 0.3, player.getZ(),
                        6, 0.1, 0.1, 0.1, 0.02);
                level.playSound(null, player.blockPosition(), SoundEvents.FIREWORK_ROCKET_LAUNCH, SoundSource.PLAYERS,
                        1f, 0.8f + index * 0.1f);
            });
        }
        return true;
    }

    /** A missile hit: a fiery burst instead of the shulker's levitation; it never hurts players. */
    public static void missileHit(ShulkerBullet missile, Vec3 at) {
        if (!(missile.level() instanceof ServerLevel level) || !(missile.getOwner() instanceof Player owner)) {
            missile.discard();
            return;
        }
        SkillFx.particles(level, ParticleTypes.EXPLOSION, at.x, at.y, at.z, 1, 0, 0, 0, 0);
        SkillFx.particles(level, ParticleTypes.FLAME, at.x, at.y, at.z, 16, 0.3, 0.3, 0.3, 0.08);
        level.playSound(null, BlockPos.containing(at), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 0.6f,
                1.3f);
        for (LivingEntity target : SkillFx.victims(missile, owner, at, 2.5)) {
            target.hurt(level.damageSources().indirectMagic(missile, owner), MISSILE_DAMAGE);
            Vec3 push = target.position().subtract(at).normalize();
            target.setDeltaMovement(target.getDeltaMovement().add(push.x * 0.5, 0.35, push.z * 0.5));
            target.hurtMarked = true;
        }
        missile.discard();
    }

    /** The ground surface under a point, so effects sit on the floor even on uneven terrain. */
    static Vec3 ground(ServerLevel level, Vec3 from) {
        return level.clip(new ClipContext(from, from.subtract(0, 6, 0), ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, net.minecraft.world.phys.shapes.CollisionContext.empty())).getLocation();
    }
}
