package dev.cogwit.trophy;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.WitherSkull;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;

/** The Wither trophy's skull: it bursts like the Wither's own, but never breaks blocks and spares players. */
public class TrophySkull extends WitherSkull {
    public TrophySkull(EntityType<? extends WitherSkull> type, Level level) {
        super(type, level);
    }

    public static TrophySkull fire(Player owner, double dx, double dy, double dz) {
        TrophySkull skull = new TrophySkull(TrophyContent.SKULL.get(), owner.level());
        skull.setOwner(owner);
        skull.setPos(owner.getX(), owner.getEyeY() - 0.2, owner.getZ());
        skull.shoot(dx, dy, dz, 1.4f, 0f);
        skull.accelerationPower = 0.08;
        owner.level().addFreshEntity(skull);
        return skull;
    }

    @Override
    protected void onHit(HitResult result) {
        if (result.getType() == HitResult.Type.ENTITY
                && ((net.minecraft.world.phys.EntityHitResult) result).getEntity() instanceof Player) {
            discard();
            return;
        }
        if (result.getType() == HitResult.Type.ENTITY) {
            onHitEntity((net.minecraft.world.phys.EntityHitResult) result);
        }
        if (!level().isClientSide) {
            level().explode(this, getX(), getY(), getZ(), 1.2f, false, Level.ExplosionInteraction.NONE);
            discard();
        }
    }

    @Override
    public boolean canHitEntity(net.minecraft.world.entity.Entity target) {
        return super.canHitEntity(target) && !(target instanceof Player)
                && !(target instanceof net.minecraft.world.entity.TamableAnimal pet && pet.isTame())
                && (!(getOwner() instanceof LivingEntity owner) || target != owner);
    }
}
