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
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/** A small black hole: pulls mobs, loot and arrows into a spiral, hurts what is inside, then implodes. */
public class GravityWellEntity extends Entity implements GeoEntity {
    private static final RawAnimation OPEN = RawAnimation.begin().thenPlay("animation.well.spawn")
            .thenLoop("animation.well.idle");
    private static final RawAnimation CLOSE = RawAnimation.begin().thenPlay("animation.well.collapse");
    public static final int CLOSE_TICKS = 8;
    public static final float IMPLOSION_DAMAGE = 4f;

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private Player owner;

    public GravityWellEntity(EntityType<? extends GravityWellEntity> type, Level level) {
        super(type, level);
        noPhysics = true;
    }

    public static GravityWellEntity open(Player owner, Vec3 at) {
        GravityWellEntity well = new GravityWellEntity(FxContent.GRAVITY_WELL.get(), owner.level());
        well.owner = owner;
        well.moveTo(at.x, at.y, at.z);
        owner.level().addFreshEntity(well);
        dev.cogwit.agent.AgentService.openCombo(owner, net.minecraft.network.chat.Component.translatable("cogwit.combo.well"));
        owner.level().playSound(null, well.blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.2f, 0.5f);
        owner.level().playSound(null, well.blockPosition(), SoundEvents.PORTAL_TRIGGER, SoundSource.PLAYERS, 0.4f, 1.8f);
        if (owner.level() instanceof ServerLevel level) {
            SkillFx.screen(level, at, 16, ScreenFxPayload.FOV_PULL, 0.6f, 12);
        }
        return well;
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            clientParticles();
            return;
        }
        int life = (int) Horizon.WELL_DURATION_TICKS;
        if (tickCount < life) {
            pull();
            if (tickCount % 20 == 0) {
                level().playSound(null, blockPosition(), SoundEvents.BEACON_AMBIENT, SoundSource.PLAYERS, 1f, 0.5f);
            }
        } else if (tickCount == life) {
            implode();
        } else if (tickCount >= life + CLOSE_TICKS) {
            discard();
        }
    }

    private void pull() {
        double radius = Horizon.WELL_RADIUS;
        Vec3 c = position();
        boolean damageTick = tickCount % Horizon.WELL_DAMAGE_INTERVAL_TICKS == 0;
        for (LivingEntity target : SkillFx.victims(this, owner, c, radius)) {
            push(target, c, radius);
            if (damageTick && owner != null) {
                target.hurt(damageSources().indirectMagic(this, owner), Horizon.WELL_DAMAGE);
            }
        }
        AABB box = AABB.ofSize(c, radius * 2, radius * 2, radius * 2);
        for (ItemEntity item : level().getEntitiesOfClass(ItemEntity.class, box)) {
            push(item, c, radius);
        }
        for (Projectile p : level().getEntitiesOfClass(Projectile.class, box, p -> p.getOwner() != owner)) {
            Vec3 v = p.getDeltaMovement();
            Vec3 to = c.subtract(p.position());
            double[] bent = SkillPhysics.bendProjectile(v.x, v.y, v.z, to.x, to.y, to.z,
                    SkillPhysics.bendFor(to.length(), radius));
            p.setDeltaMovement(bent[0], bent[1], bent[2]);
            p.hasImpulse = true;
        }
    }

    private static void push(Entity e, Vec3 c, double radius) {
        Vec3 off = e.position().add(0, e.getBbHeight() / 2, 0).subtract(c);
        double[] f = SkillPhysics.wellForce(off.x, off.y, off.z, radius);
        e.setDeltaMovement(e.getDeltaMovement().scale(0.6).add(f[0], f[1], f[2]));
        e.hurtMarked = true;
        e.resetFallDistance();
    }

    private void implode() {
        triggerAnim("main", "close");
        Vec3 c = position();
        for (LivingEntity target : SkillFx.victims(this, owner, c, Horizon.WELL_RADIUS * 0.6)) {
            Vec3 away = target.position().subtract(c).normalize();
            target.setDeltaMovement(away.x * 0.8, 0.5, away.z * 0.8);
            target.hurtMarked = true;
            if (owner != null) {
                target.hurt(damageSources().indirectMagic(this, owner), IMPLOSION_DAMAGE);
            }
        }
        if (level() instanceof ServerLevel level) {
            SkillFx.particles(level, ParticleTypes.REVERSE_PORTAL, c.x, c.y, c.z, 80, 0.2, 0.2, 0.2, 0.6);
            SkillFx.particles(level, ParticleTypes.FLASH, c.x, c.y, c.z, 1, 0, 0, 0, 0);
            SkillFx.screen(level, c, 18, ScreenFxPayload.SHAKE, 0.5f, 8);
        }
        level().playSound(null, blockPosition(), SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 1.2f, 0.6f);
        level().playSound(null, blockPosition(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 0.5f, 1.6f);
    }

    /** Spiral arms of portal sparks falling into the hole, and a few hot embers orbiting it. */
    private void clientParticles() {
        if (tickCount >= Horizon.WELL_DURATION_TICKS) {
            return;
        }
        Vec3 c = position();
        for (int arm = 0; arm < 3; arm++) {
            double phase = arm * 2 * Math.PI / 3 + tickCount * 0.25;
            double r = 2.5 + random.nextDouble() * (Horizon.WELL_RADIUS - 3);
            double ox = Math.cos(phase) * r, oz = Math.sin(phase) * r;
            level().addParticle(ParticleTypes.PORTAL, c.x, c.y, c.z, ox, (random.nextDouble() - 0.5) * 0.6, oz);
        }
        if (tickCount % 2 == 0) {
            double a = tickCount * 0.5;
            double r = 2.2;
            level().addParticle(ParticleTypes.END_ROD, c.x + Math.cos(a) * r, c.y + Math.sin(a * 0.7) * 0.3,
                    c.z + Math.sin(a) * r, -Math.sin(a) * 0.08, 0, Math.cos(a) * 0.08);
        }
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 0, state -> state.setAndContinue(OPEN))
                .triggerableAnim("close", CLOSE));
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
