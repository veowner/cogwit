package dev.cogwit.trophy;

import dev.cogwit.Speech;
import dev.cogwit.horizon.fx.SkillFx;
import dev.cogwit.network.ScreenFxPayload;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.goal.target.TargetGoal;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.Skeleton;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.EvokerFangs;
import net.minecraft.world.entity.projectile.LargeFireball;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.SmallFireball;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.Unbreakable;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * The actives of the three Iron's Spells 'n Spellbooks trophies: the Dead King's skeletons, the Flamebearer's
 * firestorm and the Archevoker's fangs. All of them are built from vanilla entities, so they work with or without that
 * mod. Each method returns true if it fired and has no side effects when it cannot; cooldowns are
 * {@link TrophyService#castTrophy}'s business.
 */
public final class SpellbookPowers {
    // raise the dead
    public static final int SKELETONS = 3, SKELETON_LIFE_TICKS = 600, SKELETON_RISE_TICKS = 14, TEND_INTERVAL_TICKS = 10;
    private static final String OWNER_KEY = "cogwit_owner";

    // firestorm
    public static final int STORM_WINDUP_TICKS = 8, STORM_TICKS = 60, STORM_GAP_TICKS = 3;
    public static final double STORM_RANGE = 10, STORM_BURST_RADIUS = 2.4, STORM_HEIGHT = 11;
    public static final float STORM_DAMAGE = 6f;
    public static final String FIREBALL_TAG = "cogwit_fireball";

    // fangs
    public static final int FANG_WINDUP_TICKS = 6, FANG_TARGETS = 8;
    public static final double FANG_RANGE = 10, FANG_SPACING = 1.4;
    public static final String FANG_TAG = "cogwit_fangs";

    private SpellbookPowers() {}

    // ---------------------------------------------------------------- Dead King: raise the dead

    /**
     * Three skeletons claw their way up in front of the caster. For thirty seconds they shoot at the caster's enemies
     * and at whatever hurts the caster, never at a player; they do not burn in the sun, drop nothing, and crumble
     * away in smoke at the end.
     */
    public static boolean raiseDead(Player player) {
        if (!(player.level() instanceof ServerLevel level) || !player.isAlive()) {
            return false;
        }
        Vec3 look = player.getLookAngle();
        Vec3 flat = new Vec3(look.x, 0, look.z);
        flat = flat.lengthSqr() < 1e-4 ? Vec3.directionFromRotation(0, player.getYRot()) : flat.normalize();
        level.playSound(null, player.blockPosition(), SoundEvents.WITHER_SPAWN, SoundSource.PLAYERS, 0.4f, 1.8f);
        level.playSound(null, player.blockPosition(), SoundEvents.SKELETON_CONVERTED_TO_STRAY, SoundSource.PLAYERS, 1.2f, 0.5f);
        if (player instanceof ServerPlayer sp) {
            SkillFx.screen(sp, ScreenFxPayload.SHAKE, 0.25f, SKELETON_RISE_TICKS);
        }
        for (int i = 0; i < SKELETONS; i++) {
            double angle = Math.toRadians((i - 1) * 50);
            Vec3 dir = new Vec3(flat.x * Math.cos(angle) - flat.z * Math.sin(angle), 0,
                    flat.x * Math.sin(angle) + flat.z * Math.cos(angle));
            Vec3 spot = graveSpot(level, player, dir);
            for (int t = 0; t < SKELETON_RISE_TICKS; t += 2) {
                int step = t;
                TrophyScheduler.later(level, t, () -> grave(level, spot, step));
            }
            TrophyScheduler.later(level, SKELETON_RISE_TICKS, () -> rise(level, player, spot));
        }
        return true;
    }

    /** A free patch of floor about three blocks away in a direction, or failing that closer, or at the caster's feet. */
    private static Vec3 graveSpot(ServerLevel level, Player player, Vec3 dir) {
        for (double reach : new double[] {3, 2.2, 1.4}) {
            Vec3 spot = TrophyAim.ground(level, player.position().add(dir.scale(reach)).add(0, 1.5, 0));
            if (level.noCollision(EntityType.SKELETON.getSpawnAABB(spot.x, spot.y, spot.z))) {
                return spot;
            }
        }
        return player.position();
    }

    /** The ground stirs: soul fire spirals up out of it and a cracked ring closes. */
    private static void grave(ServerLevel level, Vec3 spot, int step) {
        BlockState floor = level.getBlockState(BlockPos.containing(spot).below());
        var dirt = new BlockParticleOption(ParticleTypes.BLOCK, floor.isAir() ? Blocks.DIRT.defaultBlockState() : floor);
        SkillFx.ring(level, spot.add(0, 0.1, 0), new Vec3(0, 1, 0), 1.2 - step * 0.06, 10, dirt);
        for (int arm = 0; arm < 2; arm++) {
            double a = step * 0.8 + arm * Math.PI, r = 0.7;
            SkillFx.particles(level, ParticleTypes.SOUL_FIRE_FLAME, spot.x + Math.cos(a) * r, spot.y + step * 0.12,
                    spot.z + Math.sin(a) * r, 1, 0.02, 0.02, 0.02, 0.01);
        }
        SkillFx.particles(level, ParticleTypes.SOUL, spot.x, spot.y + 0.3, spot.z, 1, 0.3, 0.2, 0.3, 0.02);
        if (step % 4 == 0) {
            level.playSound(null, BlockPos.containing(spot), SoundEvents.GRAVEL_BREAK, SoundSource.PLAYERS, 0.8f, 0.6f);
        }
    }

    private static void rise(ServerLevel level, Player owner, Vec3 spot) {
        if (!owner.isAlive() || owner.level() != level) {
            return;
        }
        Skeleton skeleton = EntityType.SKELETON.create(level);
        if (skeleton == null) {
            return;
        }
        skeleton.moveTo(spot.x, spot.y, spot.z, owner.getYRot(), 0);
        skeleton.finalizeSpawn(level, level.getCurrentDifficultyAt(skeleton.blockPosition()), MobSpawnType.MOB_SUMMONED,
                null);
        skeleton.setPersistenceRequired();
        skeleton.addTag(SkillFx.ALLY_TAG);
        skeleton.getPersistentData().putUUID(OWNER_KEY, owner.getUUID());
        // an unbreakable helmet is what keeps a skeleton from catching fire in daylight; nothing it wears is dropped
        ItemStack helmet = new ItemStack(Items.CHAINMAIL_HELMET);
        helmet.set(DataComponents.UNBREAKABLE, new Unbreakable(false));
        skeleton.setItemSlot(EquipmentSlot.HEAD, helmet);
        skeleton.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            skeleton.setDropChance(slot, 0f);
        }
        skeleton.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, SKELETON_LIFE_TICKS + 40, 0, false, false));
        skeleton.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 30, 2, false, false));
        // they fight for the caster: no revenge on players, no hunting players
        skeleton.targetSelector.removeAllGoals(goal -> true);
        skeleton.targetSelector.addGoal(1, new AssistOwner(skeleton, owner.getUUID()));
        skeleton.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(skeleton, Mob.class, 10, true, false,
                SpellbookPowers::isFoe));
        level.addFreshEntity(skeleton);

        SkillFx.particles(level, ParticleTypes.SOUL_FIRE_FLAME, spot.x, spot.y + 1, spot.z, 18, 0.3, 0.6, 0.3, 0.06);
        SkillFx.particles(level, ParticleTypes.POOF, spot.x, spot.y + 0.2, spot.z, 8, 0.4, 0.1, 0.4, 0.05);
        level.playSound(null, BlockPos.containing(spot), SoundEvents.SOUL_ESCAPE.value(), SoundSource.PLAYERS, 1f, 0.8f);
        level.playSound(null, BlockPos.containing(spot), SoundEvents.SKELETON_AMBIENT, SoundSource.PLAYERS, 1f, 0.7f);
        long born = level.getGameTime();
        TrophyScheduler.later(level, TEND_INTERVAL_TICKS, () -> tend(level, owner.getUUID(), skeleton, born));
    }

    /** A creature the Dead King's skeletons may fight: hostile, and not another summon. */
    static boolean isFoe(LivingEntity e) {
        return e instanceof Enemy && !(e instanceof Player) && e.isAlive() && !e.getTags().contains(SkillFx.ALLY_TAG);
    }

    private static Player ownerOf(net.minecraft.world.level.Level level, UUID id) {
        return level instanceof ServerLevel server && server.getEntity(id) instanceof Player player ? player : null;
    }

    /** Who raised this summon, or null if it is not one. */
    public static UUID summonOwner(net.minecraft.world.entity.Entity e) {
        return isSummon(e) && e.getPersistentData().hasUUID(OWNER_KEY) ? e.getPersistentData().getUUID(OWNER_KEY) : null;
    }

    public static boolean isSummon(net.minecraft.world.entity.Entity e) {
        return e.getTags().contains(SkillFx.ALLY_TAG);
    }

    /** Every half second: keep a skeleton on the caster's side, near them, and send it off when its time is up. */
    private static void tend(ServerLevel level, UUID ownerId, Skeleton skeleton, long born) {
        if (!skeleton.isAlive() || skeleton.isRemoved()) {
            return;
        }
        Player owner = ownerOf(level, ownerId);
        long age = level.getGameTime() - born;
        if (owner == null || !owner.isAlive() || age >= SKELETON_LIFE_TICKS) {
            vanish(level, skeleton);
            return;
        }
        if (skeleton.getTarget() instanceof Player || skeleton.getTarget() != null && !isFoe(skeleton.getTarget())) {
            skeleton.setTarget(null);
        }
        double away = skeleton.distanceTo(owner);
        if (away > 24) {
            Vec3 home = TrophyAim.ground(level, owner.position().add(level.random.nextGaussian() * 1.5, 1.5,
                    level.random.nextGaussian() * 1.5));
            skeleton.teleportTo(home.x, home.y, home.z);
            SkillFx.particles(level, ParticleTypes.SOUL, home.x, home.y + 1, home.z, 10, 0.3, 0.5, 0.3, 0.03);
        } else if (skeleton.getTarget() == null && away > 7) {
            skeleton.getNavigation().moveTo(owner, 1.15);
        }
        SkillFx.particles(level, ParticleTypes.SOUL, skeleton.getX(), skeleton.getY() + 1.6, skeleton.getZ(),
                age > SKELETON_LIFE_TICKS - 60 ? 3 : 1, 0.15, 0.2, 0.15, 0.01);
        TrophyScheduler.later(level, TEND_INTERVAL_TICKS, () -> tend(level, ownerId, skeleton, born));
    }

    /** The end of a summon: a puff of smoke and souls, and the bones are gone without a trace. */
    public static void vanish(ServerLevel level, LivingEntity skeleton) {
        Vec3 c = skeleton.getBoundingBox().getCenter();
        SkillFx.particles(level, ParticleTypes.LARGE_SMOKE, c.x, c.y, c.z, 14, 0.3, 0.6, 0.3, 0.03);
        SkillFx.particles(level, ParticleTypes.SOUL, c.x, c.y, c.z, 10, 0.3, 0.6, 0.3, 0.06);
        SkillFx.particles(level, ParticleTypes.POOF, c.x, c.y - 0.5, c.z, 6, 0.3, 0.3, 0.3, 0.05);
        level.playSound(null, skeleton.blockPosition(), SoundEvents.SOUL_ESCAPE.value(), SoundSource.PLAYERS, 1f, 1.1f);
        skeleton.discard();
    }

    /** Targets whatever last hurt the caster or was last hurt by them, so the skeletons join the caster's fights. */
    private static final class AssistOwner extends TargetGoal {
        private final UUID ownerId;

        AssistOwner(Mob mob, UUID ownerId) {
            super(mob, false);
            this.ownerId = ownerId;
            setFlags(EnumSet.of(Goal.Flag.TARGET));
        }

        private LivingEntity foe() {
            Player owner = ownerOf(mob.level(), ownerId);
            if (owner == null) {
                return null;
            }
            LivingEntity attacker = owner.getLastHurtByMob();
            if (attacker != null && owner.tickCount - owner.getLastHurtByMobTimestamp() < 100 && isFoe(attacker)) {
                return attacker;
            }
            LivingEntity victim = owner.getLastHurtMob();
            return victim != null && owner.tickCount - owner.getLastHurtMobTimestamp() < 100 && isFoe(victim) ? victim : null;
        }

        @Override
        public boolean canUse() {
            LivingEntity foe = foe();
            if (foe == null || !canAttack(foe, TargetingConditions.DEFAULT)) {
                return false;
            }
            targetMob = foe;
            return true;
        }

        @Override
        public void start() {
            mob.setTarget(targetMob);
            super.start();
        }
    }

    /** An arrow shot by a summon flies through everything but hostile mobs: players, pets and villagers stay safe. */
    public static boolean arrowShouldPass(Projectile arrow, HitResult hit) {
        return arrow.getOwner() != null && isSummon(arrow.getOwner()) && hit instanceof EntityHitResult e
                && !(e.getEntity() instanceof LivingEntity target && isFoe(target));
    }

    // ---------------------------------------------------------------- Echo of Tyros: firestorm

    /**
     * For three seconds fireballs fall out of the sky onto the ground round the caster's enemies, each warned by a
     * ring of embers. They burst in flame and damage but never light the world.
     */
    public static boolean firestorm(Player player) {
        if (!(player.level() instanceof ServerLevel level) || !player.isAlive()) {
            return false;
        }
        if (TrophyAim.hostiles(player, player.position().add(0, 1, 0), STORM_RANGE).isEmpty()) {
            Speech.hint(player, Component.translatable("cogwit.trophy.no_enemy_near"));
            return false;
        }
        level.playSound(null, player.blockPosition(), SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 1.4f, 0.5f);
        level.playSound(null, player.blockPosition(), SoundEvents.BLAZE_AMBIENT, SoundSource.PLAYERS, 1.2f, 0.5f);
        if (player instanceof ServerPlayer sp) {
            SkillFx.screen(sp, ScreenFxPayload.FOV_PUNCH, 0.5f, 12);
        }
        // wind-up: a ring of fire climbs the caster and the embers swirl up before the sky opens
        for (int t = 0; t < STORM_WINDUP_TICKS; t++) {
            int step = t;
            TrophyScheduler.later(level, t, () -> {
                if (!player.isAlive()) {
                    return;
                }
                Vec3 c = player.position();
                SkillFx.ring(level, c.add(0, step * 0.3, 0), new Vec3(0, 1, 0), 1.6 - step * 0.1, 16, ParticleTypes.FLAME);
                SkillFx.particles(level, ParticleTypes.LAVA, c.x, c.y + 1, c.z, 1, 0.8, 0.5, 0.8, 0);
            });
        }
        TrophyScheduler.later(level, STORM_WINDUP_TICKS, () -> stormTick(level, player, 0));
        return true;
    }

    private static void stormTick(ServerLevel level, Player player, int t) {
        if (!player.isAlive() || player.isRemoved() || player.level() != level) {
            return;
        }
        if (t % STORM_GAP_TICKS == 0) {
            drop(level, player, t / STORM_GAP_TICKS);
        }
        if (t % 10 == 0 && player instanceof ServerPlayer sp) {
            SkillFx.screen(sp, ScreenFxPayload.SHAKE, 0.12f, 10);
        }
        if (t < STORM_TICKS) {
            TrophyScheduler.later(level, 1, () -> stormTick(level, player, t + 1));
        }
    }

    private static void drop(ServerLevel level, Player player, int index) {
        List<LivingEntity> enemies = TrophyAim.hostiles(player, player.position().add(0, 1, 0), STORM_RANGE);
        if (enemies.isEmpty()) {
            return;
        }
        LivingEntity mark = enemies.get(level.random.nextInt(enemies.size()));
        Vec3 near = mark.position().add(level.random.nextGaussian() * 1.2, 0, level.random.nextGaussian() * 1.2);
        Vec3 spot = TrophyAim.ground(level, near.add(0, 2, 0));
        // from as high as the sky allows, a little off to the side so they fall at a slant
        BlockHitResult roof = level.clip(new ClipContext(spot.add(0, 1, 0), spot.add(0, STORM_HEIGHT, 0),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        double rise = roof.getType() == HitResult.Type.MISS ? STORM_HEIGHT : Math.max(1.0, roof.getLocation().y - spot.y - 0.8);
        Vec3 from = spot.add((level.random.nextDouble() - 0.5) * 4, rise, (level.random.nextDouble() - 0.5) * 4);
        Vec3 velocity = spot.subtract(from).normalize().scale(1.2);

        Projectile ball;
        if (index % 3 == 0) {
            LargeFireball large = new LargeFireball(level, player, velocity, 1);
            large.moveTo(from.x, from.y, from.z);
            ball = large;
        } else {
            SmallFireball small = new SmallFireball(level, from.x, from.y, from.z, velocity);
            small.setOwner(player);
            ball = small;
        }
        ball.setDeltaMovement(velocity);
        ball.addTag(FIREBALL_TAG);
        level.addFreshEntity(ball);
        // a fireball that somehow never lands is not left flying
        TrophyScheduler.later(level, 100, ball::discard);

        SkillFx.ring(level, spot.add(0, 0.1, 0), new Vec3(0, 1, 0), STORM_BURST_RADIUS * 0.6, 14, ParticleTypes.FLAME);
        SkillFx.particles(level, ParticleTypes.SMOKE, spot.x, spot.y + 0.1, spot.z, 4, 0.5, 0.05, 0.5, 0.01);
        level.playSound(null, BlockPos.containing(from), SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 0.8f,
                0.7f + level.random.nextFloat() * 0.5f);
    }

    /**
     * A storm fireball hit something: it bursts into flame on a hostile mob or on the ground, and flies on through
     * players, pets and bystanders. Nothing is set alight but creatures, and no block is touched.
     */
    public static void fireballHit(Projectile ball, HitResult hit) {
        if (!(ball.level() instanceof ServerLevel level) || !(ball.getOwner() instanceof Player owner)) {
            ball.discard();
            return;
        }
        if (hit instanceof EntityHitResult e && !(e.getEntity() instanceof LivingEntity target
                && SkillFx.isVictim(target, owner))) {
            return;
        }
        Vec3 at = hit.getLocation();
        SkillFx.particles(level, ParticleTypes.EXPLOSION, at.x, at.y + 0.3, at.z, 1, 0, 0, 0, 0);
        SkillFx.particles(level, ParticleTypes.FLAME, at.x, at.y + 0.3, at.z, 26, 0.5, 0.3, 0.5, 0.1);
        SkillFx.particles(level, ParticleTypes.LAVA, at.x, at.y + 0.3, at.z, 5, 0.4, 0.2, 0.4, 0);
        SkillFx.particles(level, ParticleTypes.LARGE_SMOKE, at.x, at.y + 0.5, at.z, 4, 0.3, 0.3, 0.3, 0.04);
        SkillFx.ring(level, at.add(0, 0.15, 0), new Vec3(0, 1, 0), STORM_BURST_RADIUS, 18, ParticleTypes.FLAME);
        level.playSound(null, BlockPos.containing(at), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 0.6f, 1.4f);
        level.playSound(null, BlockPos.containing(at), SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 0.8f, 0.6f);
        SkillFx.screen(level, at, 16, ScreenFxPayload.SHAKE, 0.12f, 4);
        for (LivingEntity victim : SkillFx.victims(ball, owner, at, STORM_BURST_RADIUS)) {
            victim.hurt(level.damageSources().indirectMagic(ball, owner), STORM_DAMAGE);
            victim.igniteForSeconds(4);
            Vec3 push = victim.position().subtract(at).multiply(1, 0, 1);
            push = push.lengthSqr() < 1e-4 ? new Vec3(0, 0, 0) : push.normalize().scale(0.4);
            victim.setDeltaMovement(victim.getDeltaMovement().add(push.x, 0.3, push.z));
            victim.hurtMarked = true;
        }
        ball.discard();
    }

    // ---------------------------------------------------------------- Archevoker: fangs

    /**
     * Evoker fangs run across the ground from the caster to every hostile mob within ten blocks, ring each of them
     * and bite from underneath. The fangs are the caster's own, so they never bite the caster; the rest of the filter
     * (players, pets, bystanders) is applied when they hurt ({@link #fangsSpare}).
     */
    public static boolean fangs(Player player) {
        if (!(player.level() instanceof ServerLevel level) || !player.isAlive()) {
            return false;
        }
        List<LivingEntity> targets = new ArrayList<>(TrophyAim.hostiles(player, player.position().add(0, 1, 0), FANG_RANGE));
        if (targets.isEmpty()) {
            Speech.hint(player, Component.translatable("cogwit.trophy.no_enemy_near"));
            return false;
        }
        if (targets.size() > FANG_TARGETS) {
            targets = targets.subList(0, FANG_TARGETS);
        }
        List<LivingEntity> marked = targets;
        // wind-up: runes circle the caster and rise as the evokers' own spell sound plays
        level.playSound(null, player.blockPosition(), SoundEvents.EVOKER_PREPARE_ATTACK, SoundSource.PLAYERS, 1.2f, 1f);
        level.playSound(null, player.blockPosition(), SoundEvents.EVOKER_CAST_SPELL, SoundSource.PLAYERS, 1f, 1.2f);
        for (int t = 0; t < FANG_WINDUP_TICKS; t += 2) {
            int step = t;
            TrophyScheduler.later(level, t, () -> {
                if (!player.isAlive()) {
                    return;
                }
                Vec3 c = player.position();
                SkillFx.ring(level, c.add(0, 0.2 + step * 0.3, 0), new Vec3(0, 1, 0), 1.3 - step * 0.1, 16,
                        ParticleTypes.ENCHANT);
                SkillFx.particles(level, ParticleTypes.WITCH, c.x, c.y + 1, c.z, 6, 0.5, 0.6, 0.5, 0.05);
            });
        }
        TrophyScheduler.later(level, FANG_WINDUP_TICKS, () -> bite(level, player, marked));
        return true;
    }

    private static void bite(ServerLevel level, Player player, List<LivingEntity> targets) {
        if (!player.isAlive() || player.isRemoved() || player.level() != level) {
            return;
        }
        Set<Long> taken = new HashSet<>();
        Vec3 origin = player.position();
        for (LivingEntity target : targets) {
            if (!target.isAlive() || target.level() != level) {
                continue;
            }
            Vec3 to = target.position().subtract(origin).multiply(1, 0, 1);
            double dist = to.length();
            Vec3 dir = dist < 1e-4 ? new Vec3(1, 0, 0) : to.scale(1 / dist);
            float yaw = (float) Math.atan2(dir.z, dir.x);
            int steps = (int) Math.floor((dist - 0.8) / FANG_SPACING);
            for (int i = 1; i <= steps; i++) {
                fang(level, player, origin.add(dir.scale(i * FANG_SPACING)), yaw, i, taken);
            }
            // under the target and round it, once the line has arrived
            int arrive = steps + 1;
            fang(level, player, target.position(), yaw, arrive, taken);
            for (int k = 0; k < 5; k++) {
                double a = yaw + k * Math.PI * 2 / 5;
                fang(level, player, target.position().add(Math.cos(a) * 1.3, 0, Math.sin(a) * 1.3), (float) a, arrive + 2,
                        taken);
            }
            SkillFx.particles(level, ParticleTypes.WITCH, target.getX(), target.getY() + 0.2, target.getZ(), 10, 0.5, 0.1, 0.5, 0.05);
        }
    }

    private static void fang(ServerLevel level, Player owner, Vec3 at, float yaw, int delay, Set<Long> taken) {
        Vec3 ground = TrophyAim.ground(level, at.add(0, 2, 0));
        if (!taken.add(BlockPos.containing(ground).asLong())) {
            return;
        }
        EvokerFangs fangs = new EvokerFangs(level, ground.x, ground.y, ground.z, yaw, delay, owner);
        fangs.addTag(FANG_TAG);
        level.addFreshEntity(fangs);
    }

    /**
     * Whether the caster's fangs must not hurt this creature. A fang cannot tell friend from foe (it only skips its
     * owner and the owner's team), so the damage event asks here, and anything but a legitimate target is spared.
     */
    public static boolean fangsSpare(EvokerFangs fangs, LivingEntity target) {
        return fangs.getTags().contains(FANG_TAG) && fangs.getOwner() instanceof Player owner
                && !SkillFx.isVictim(target, owner);
    }
}
