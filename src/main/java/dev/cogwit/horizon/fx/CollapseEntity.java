package dev.cogwit.horizon.fx;

import dev.cogwit.horizon.Horizon;
import dev.cogwit.horizon.SkillPhysics;
import dev.cogwit.network.ScreenFxPayload;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * Collapse, in two beats: the charge drags and lifts everything towards the player while a dark shell shrinks,
 * then the blast throws it all away with a shockwave across the ground.
 */
public class CollapseEntity extends Entity implements GeoEntity {
    private static final RawAnimation CHARGE = RawAnimation.begin().thenPlay("animation.collapse.charge");
    private static final RawAnimation BLAST = RawAnimation.begin().thenPlay("animation.collapse.blast");

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private Player owner;

    public CollapseEntity(EntityType<? extends CollapseEntity> type, Level level) {
        super(type, level);
        noPhysics = true;
    }

    public static CollapseEntity start(Player owner) {
        CollapseEntity collapse = new CollapseEntity(FxContent.COLLAPSE.get(), owner.level());
        collapse.owner = owner;
        collapse.moveTo(owner.getX(), owner.getY(), owner.getZ());
        owner.level().addFreshEntity(collapse);
        owner.level().playSound(null, owner.blockPosition(), SoundEvents.WARDEN_SONIC_CHARGE, SoundSource.PLAYERS,
                1.5f, 0.7f);
        if (owner.level() instanceof ServerLevel level) {
            SkillFx.screen(level, owner.position(), 24, ScreenFxPayload.DARKEN, 0.55f,
                    SkillPhysics.COLLAPSE_CHARGE_TICKS);
        }
        return collapse;
    }

    @Override
    public void tick() {
        super.tick();
        if (owner != null && owner.isAlive()) {
            // the collapse follows the player while it charges
            if (tickCount < SkillPhysics.COLLAPSE_CHARGE_TICKS) {
                setPos(owner.getX(), owner.getY(), owner.getZ());
            }
        }
        if (level().isClientSide) {
            clientParticles();
            return;
        }
        Vec3 c = position().add(0, 1, 0);
        if (tickCount < SkillPhysics.COLLAPSE_CHARGE_TICKS) {
            for (LivingEntity target : SkillFx.victims(this, owner, c, Horizon.COLLAPSE_RADIUS)) {
                double[] f = SkillPhysics.collapseDrag(target.getX() - c.x, target.getZ() - c.z, tickCount);
                target.setDeltaMovement(target.getDeltaMovement().scale(0.5).add(f[0], f[1], f[2]));
                target.hurtMarked = true;
                target.resetFallDistance();
            }
        } else if (tickCount == SkillPhysics.COLLAPSE_CHARGE_TICKS) {
            blast(c);
        } else if (tickCount >= SkillPhysics.COLLAPSE_CHARGE_TICKS + SkillPhysics.COLLAPSE_BLAST_TICKS) {
            discard();
        }
    }

    private void blast(Vec3 c) {
        triggerAnim("main", "blast");
        for (LivingEntity target : SkillFx.victims(this, owner, c, Horizon.COLLAPSE_RADIUS)) {
            double[] f = SkillPhysics.collapseBlast(target.getX() - c.x, target.getZ() - c.z, Horizon.COLLAPSE_RADIUS);
            if (owner != null) {
                target.hurt(damageSources().indirectMagic(this, owner), Horizon.COLLAPSE_DAMAGE);
            }
            target.setDeltaMovement(f[0], f[1], f[2]);
            target.hurtMarked = true;
        }
        if (level() instanceof ServerLevel level) {
            SkillFx.particles(level, ParticleTypes.FLASH, c.x, c.y, c.z, 2, 0, 0, 0, 0);
            SkillFx.particles(level, ParticleTypes.EXPLOSION_EMITTER, c.x, c.y, c.z, 1, 0, 0, 0, 0);
            SkillFx.particles(level, ParticleTypes.SONIC_BOOM, c.x, c.y, c.z, 1, 0, 0, 0, 0);
            SkillFx.particles(level, ParticleTypes.REVERSE_PORTAL, c.x, c.y, c.z, 250, 0.3, 0.3, 0.3, 1.2);
            for (int r = 2; r <= Horizon.COLLAPSE_RADIUS; r += 2) {
                SkillFx.ring(level, position().add(0, 0.15, 0), new Vec3(0, 1, 0), r, 8 + r * 4,
                        r % 4 == 0 ? ParticleTypes.END_ROD : ParticleTypes.REVERSE_PORTAL);
            }
            SkillFx.screen(level, c, 32, ScreenFxPayload.FLASH, 0.85f, 10);
            SkillFx.screen(level, c, 32, ScreenFxPayload.SHAKE, 1f, 18);
        }
        level().playSound(null, blockPosition(), SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 2f, 0.6f);
        level().playSound(null, blockPosition(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 1.2f, 0.7f);
    }

    /** During the charge, sparks rush in from all around. */
    private void clientParticles() {
        if (tickCount >= SkillPhysics.COLLAPSE_CHARGE_TICKS) {
            return;
        }
        Vec3 c = position().add(0, 1, 0);
        double build = (double) tickCount / SkillPhysics.COLLAPSE_CHARGE_TICKS;
        int count = 4 + (int) (build * 10);
        for (int i = 0; i < count; i++) {
            double a = random.nextDouble() * Math.PI * 2;
            double r = Horizon.COLLAPSE_RADIUS * (0.5 + random.nextDouble() * 0.5);
            level().addParticle(ParticleTypes.PORTAL, c.x, c.y, c.z, Math.cos(a) * r, random.nextDouble() * 2 - 0.5,
                    Math.sin(a) * r);
        }
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 0, state -> state.setAndContinue(CHARGE))
                .triggerableAnim("blast", BLAST));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {}

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {}

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {}

    @Override
    public boolean isPickable() {
        return false;
    }
}
