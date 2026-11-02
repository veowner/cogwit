package dev.cogwit.trophy;

import dev.cogwit.horizon.fx.SkillFx;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Who a targeted trophy active should hit without the player having to aim precisely: the creature under the
 * crosshair, else the one they fought last, else the nearest hostile mob. Always a creature skills may hurt
 * ({@link SkillFx#isVictim}), never a player, pet or bystander.
 */
final class TrophyAim {
    /** A mob the player hit this recently (ticks) still counts as "the one they are fighting". */
    private static final int RECENT_FIGHT_TICKS = 200;

    private TrophyAim() {}

    static Optional<LivingEntity> target(Player player, double range) {
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(player, eye, eye.add(look.scale(range)),
                player.getBoundingBox().expandTowards(look.scale(range)).inflate(1.5),
                e -> e instanceof LivingEntity living && SkillFx.isVictim(living, player), range * range);
        if (hit != null && hit.getEntity() instanceof LivingEntity living) {
            return Optional.of(living);
        }
        LivingEntity last = player.getLastHurtMob();
        if (last != null && player.tickCount - player.getLastHurtMobTimestamp() < RECENT_FIGHT_TICKS
                && SkillFx.isVictim(last, player) && last.level() == player.level()
                && last.distanceTo(player) <= range) {
            return Optional.of(last);
        }
        return hostiles(player, player.position(), range).stream().findFirst();
    }

    /** Hostile mobs around a point, nearest to the player first. */
    static List<LivingEntity> hostiles(Player player, Vec3 centre, double radius) {
        return SkillFx.victims(player, player, centre, radius).stream().filter(e -> e instanceof Enemy)
                .sorted(Comparator.comparingDouble(player::distanceToSqr)).toList();
    }

    /** The ground surface under a point, so effects sit on the floor even on uneven terrain. */
    static Vec3 ground(ServerLevel level, Vec3 from) {
        return CataclysmStrikes.ground(level, from);
    }
}
