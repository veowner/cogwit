package dev.cogwit.trophy;

import dev.cogwit.horizon.fx.SkillFx;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** A pool of dragon's breath on the ground: purple fire that burns mobs for six seconds and leaves players alone. */
public class DragonBreathCloud extends Entity {
    public static final int LIFE = 120, RADIUS = 3;
    public static final float DAMAGE = 3f;
    private Player owner;

    public DragonBreathCloud(EntityType<? extends DragonBreathCloud> type, Level level) {
        super(type, level);
        noPhysics = true;
    }

    public static DragonBreathCloud breathe(Player owner, Vec3 at) {
        DragonBreathCloud cloud = new DragonBreathCloud(TrophyContent.DRAGON_BREATH.get(), owner.level());
        cloud.owner = owner;
        cloud.moveTo(at.x, at.y, at.z);
        owner.level().addFreshEntity(cloud);
        return cloud;
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            // a low, rolling pool, thicker at the start
            int n = tickCount < 20 ? 9 : 4;
            for (int i = 0; i < n; i++) {
                double a = random.nextDouble() * Math.PI * 2, r = Math.sqrt(random.nextDouble()) * RADIUS;
                level().addParticle(ParticleTypes.DRAGON_BREATH, getX() + Math.cos(a) * r, getY() + 0.1,
                        getZ() + Math.sin(a) * r, Math.cos(a) * 0.01, 0.02 + random.nextDouble() * 0.03,
                        Math.sin(a) * 0.01);
            }
            return;
        }
        if (tickCount % 10 == 0 && owner != null) {
            for (LivingEntity target : SkillFx.victims(this, owner, position().add(0, 0.5, 0), RADIUS + 0.5)) {
                target.hurt(damageSources().indirectMagic(this, owner), DAMAGE);
            }
        }
        if (tickCount % 30 == 0) {
            level().playSound(null, blockPosition(), SoundEvents.FIRE_AMBIENT, SoundSource.PLAYERS, 0.8f, 0.6f);
        }
        if (tickCount >= LIFE) {
            discard();
        }
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {}

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {}

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {}
}
