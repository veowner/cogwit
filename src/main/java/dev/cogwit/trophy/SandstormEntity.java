package dev.cogwit.trophy;

import dev.cogwit.horizon.fx.SkillFx;
import dev.cogwit.network.ScreenFxPayload;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The Ancient Remnant's sandstorm: a spinning column of sand that follows its owner for five seconds, lifting and
 * whirling mobs and loot around them, scouring and blinding the mobs. Has no model; the particles are the storm.
 */
public class SandstormEntity extends Entity {
    public static final int LIFE = 100, DAMAGE_INTERVAL = 10, EFFECT_TICKS = 40;
    public static final double RADIUS = 6, HEIGHT = 3;
    public static final float DAMAGE = 1.5f;
    /** Tangential speed of the swirl, the pull towards the owner and the lift, in blocks per tick. */
    private static final double SPIN = 0.35, PULL = 0.05, LIFT = 0.14, LIFT_CEILING = 1.8;

    /** Built on first use so the block registry is certainly ready. */
    private static final class Sand {
        static final BlockParticleOption GRAINS = new BlockParticleOption(ParticleTypes.FALLING_DUST,
                Blocks.SAND.defaultBlockState());
    }

    private Player owner;

    public SandstormEntity(EntityType<? extends SandstormEntity> type, Level level) {
        super(type, level);
        noPhysics = true;
    }

    /** Raises a storm on the owner. It rides along with them until it dies down. */
    public static SandstormEntity start(Player owner) {
        Level world = owner.level();
        SandstormEntity storm = new SandstormEntity(TrophyContent.SANDSTORM.get(), world);
        storm.owner = owner;
        storm.moveTo(owner.getX(), owner.getY(), owner.getZ());
        world.addFreshEntity(storm);
        world.playSound(null, owner.blockPosition(), SoundEvents.WIND_CHARGE_BURST.value(), SoundSource.PLAYERS, 1.2f,
                0.5f);
        world.playSound(null, owner.blockPosition(), SoundEvents.SAND_BREAK, SoundSource.PLAYERS, 0.8f, 0.5f);
        if (world instanceof ServerLevel level) {
            // a ring of dust bursts outward as the column forms
            for (int i = 0; i < 14; i++) {
                double a = i * Math.PI * 2 / 14;
                SkillFx.particles(level, ParticleTypes.CLOUD, owner.getX() + Math.cos(a) * 0.8, owner.getY() + 0.15,
                        owner.getZ() + Math.sin(a) * 0.8, 0, Math.cos(a), 0.05, Math.sin(a), 0.25);
            }
            if (owner instanceof ServerPlayer sp) {
                SkillFx.screen(sp, ScreenFxPayload.SHAKE, 0.25f, 10);
            }
        }
        return storm;
    }

    boolean isOwnedBy(Player player) {
        return owner == player;
    }

    /** 0..1: the storm builds over its first few ticks and winds down over its last ten. */
    private double strength() {
        return Math.max(0, Math.min(Math.min(1, 0.3 + tickCount * 0.1), (LIFE - tickCount) / 10.0));
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            funnel();
            return;
        }
        if (tickCount >= LIFE) {
            level().playSound(null, blockPosition(), SoundEvents.WIND_CHARGE_BURST.value(), SoundSource.PLAYERS, 0.6f,
                    1.2f);
            discard();
            return;
        }
        if (owner == null || !owner.isAlive() || owner.isRemoved() || owner.level() != level()) {
            discard();
            return;
        }
        setPos(owner.getX(), owner.getY(), owner.getZ());
        double s = strength();
        double radius = RADIUS * s;
        Vec3 centre = position();
        Vec3 mid = centre.add(0, 0.9, 0);
        boolean damageTick = tickCount % DAMAGE_INTERVAL == 0;
        for (LivingEntity target : SkillFx.victims(this, owner, mid, radius)) {
            swirl(target, centre, s);
            if (damageTick) {
                target.hurt(damageSources().indirectMagic(this, owner), DAMAGE);
                target.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, EFFECT_TICKS, 0, false, false));
                target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, EFFECT_TICKS, 1, false, false));
            }
        }
        AABB box = AABB.ofSize(mid, radius * 2, radius * 2, radius * 2);
        for (ItemEntity item : level().getEntitiesOfClass(ItemEntity.class, box,
                i -> i.position().distanceTo(mid) <= radius)) {
            swirl(item, centre, s);
        }
        if (tickCount % 12 == 0) {
            level().playSound(null, blockPosition(), SoundEvents.ELYTRA_FLYING, SoundSource.PLAYERS, 0.5f, 0.5f);
        }
        if (tickCount % 20 == 10) {
            level().playSound(null, blockPosition(), SoundEvents.SAND_BREAK, SoundSource.PLAYERS, 0.3f, 0.6f);
        }
    }

    /** Whirls something anticlockwise around the centre, drawing it in a little and floating it off the ground. */
    private static void swirl(Entity e, Vec3 centre, double strength) {
        double dx = e.getX() - centre.x, dz = e.getZ() - centre.z;
        double d = Math.sqrt(dx * dx + dz * dz);
        if (d < 0.3) {
            // dead centre has no direction; nudge it out along a direction that differs per entity
            double a = e.getId() * 2.399963;
            dx = Math.cos(a) * 0.3;
            dz = Math.sin(a) * 0.3;
            d = 0.3;
        }
        double nx = dx / d, nz = dz / d;
        double tx = -nz * SPIN * strength - nx * PULL;
        double tz = nx * SPIN * strength - nz * PULL;
        double lift = e.getY() - centre.y < LIFT_CEILING ? LIFT * strength : 0;
        Vec3 v = e.getDeltaMovement();
        e.setDeltaMovement(v.x * 0.5 + tx * 0.5, v.y * 0.6 + lift, v.z * 0.5 + tz * 0.5);
        e.hurtMarked = true;
        e.resetFallDistance();
    }

    /** The funnel: two arms of sand wind up around the column, wider at the top, advancing each tick so it turns. */
    private void funnel() {
        double s = strength();
        if (s <= 0) {
            return;
        }
        double turn = tickCount * 0.45;
        for (int i = 0; i < 10; i++) {
            double t = (i + random.nextDouble()) / 10.0;
            double h = t * HEIGHT;
            double angle = turn + t * Math.PI * 4 + (i % 2 == 0 ? 0 : Math.PI);
            double r = s * (0.9 + 4.6 * t) + (random.nextDouble() - 0.5) * 0.4;
            double x = getX() + Math.cos(angle) * r, y = getY() + h, z = getZ() + Math.sin(angle) * r;
            if (i < 7) {
                level().addParticle(Sand.GRAINS, x, y, z, 0, 0, 0);
            } else if (i < 9) {
                level().addParticle(ParticleTypes.CLOUD, x, y, z, -Math.sin(angle) * 0.12, 0.02, Math.cos(angle) * 0.12);
            } else {
                level().addParticle(ParticleTypes.ASH, x, y, z, 0, 0, 0);
            }
        }
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {}

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {}

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {}
}
