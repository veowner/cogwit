package dev.cogwit.trophy;

import dev.cogwit.horizon.fx.SkillFx;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/** Small pieces the Twilight Forest and Bosses' Rise powers share: who is still around, hitting and shoving. */
final class PowerKit {
    private PowerKit() {}

    /** Whether the caster is still alive and standing in the level the power was cast in. */
    static boolean live(ServerLevel level, Player player) {
        return player.isAlive() && !player.isRemoved() && player.level() == level;
    }

    /** Whether a creature a power is still working on can be worked on. */
    static boolean live(ServerLevel level, LivingEntity target) {
        return target.isAlive() && !target.isRemoved() && target.level() == level;
    }

    /** The horizontal unit vector of {@code v}, or the way the player faces when {@code v} is straight up or down. */
    static Vec3 flat(Vec3 v, Player fallback) {
        Vec3 h = new Vec3(v.x, 0, v.z);
        return h.lengthSqr() < 1e-4 ? Vec3.directionFromRotation(0, fallback.getYRot()) : h.normalize();
    }

    /** The middle of a creature's body. */
    static Vec3 centre(LivingEntity e) {
        return e.getBoundingBox().getCenter();
    }

    /** Magic damage in the caster's name; the hurt cooldown is cleared so a rapid series of hits all land. */
    static boolean hit(ServerLevel level, Player owner, LivingEntity victim, float damage) {
        victim.invulnerableTime = 0;
        return victim.hurt(level.damageSources().indirectMagic(owner, owner), damage);
    }

    /** A push along {@code dir} (made horizontal) and upwards. */
    static void shove(LivingEntity victim, Vec3 dir, double along, double up) {
        Vec3 h = new Vec3(dir.x, 0, dir.z);
        h = h.lengthSqr() < 1e-4 ? Vec3.ZERO : h.normalize().scale(along);
        victim.setDeltaMovement(victim.getDeltaMovement().add(h.x, up, h.z));
        victim.hurtMarked = true;
    }

    /** The nearest hostile mobs within {@code range} of the caster, at most {@code max} of them. */
    static List<LivingEntity> marks(Player player, double range, int max) {
        List<LivingEntity> all = TrophyAim.hostiles(player, player.position().add(0, 1, 0), range);
        return all.size() > max ? List.copyOf(all.subList(0, max)) : all;
    }

    /** Whoever a creature was heading for stops heading there. */
    static void stop(LivingEntity victim) {
        if (victim instanceof Mob mob) {
            mob.getNavigation().stop();
        }
    }

    /** Holds a creature to a spot on the ground plane: it may sway, never leave. Returns whether it was pulled back. */
    static boolean tether(LivingEntity victim, Vec3 anchor, double slack) {
        double dx = victim.getX() - anchor.x, dz = victim.getZ() - anchor.z;
        if (Math.hypot(dx, dz) <= slack) {
            return false;
        }
        victim.teleportTo(anchor.x, victim.getY(), anchor.z);
        return true;
    }

    /**
     * The tilts (radians) to hand {@link DisplayFx#rise} so a shard stands leaning by {@code theta} towards the
     * horizontal direction ({@code dx}, {@code dz}); returns {tiltX, tiltZ}.
     */
    static float[] lean(double dx, double dz, double theta) {
        double[] tilts = PowerMath.lean(dx, dz, theta);
        return new float[] {(float) tilts[0], (float) tilts[1]};
    }

    /** Where the tip of a shard of {@code height} standing at {@code foot} and leaning that way ends up. */
    static Vec3 tip(Vec3 foot, double dx, double dz, double theta, double height) {
        double len = Math.hypot(dx, dz);
        if (len < 1e-6) {
            return foot.add(0, height, 0);
        }
        return foot.add(dx / len * Math.sin(theta) * height, Math.cos(theta) * height, dz / len * Math.sin(theta) * height);
    }

    /** How far a point is from the nearest point of the segment ab. */
    static double distanceToSegment(Vec3 p, Vec3 a, Vec3 b) {
        return PowerMath.distanceToSegment(p.x, p.y, p.z, a.x, a.y, a.z, b.x, b.y, b.z);
    }

    /** The farthest point from {@code from} towards {@code to} that is not inside a wall, so nothing starts in stone. */
    static Vec3 reach(ServerLevel level, Entity entity, Vec3 from, Vec3 to) {
        var hit = level.clip(new net.minecraft.world.level.ClipContext(from, to,
                net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.NONE, entity));
        if (hit.getType() == net.minecraft.world.phys.HitResult.Type.MISS) {
            return to;
        }
        Vec3 back = from.subtract(to);
        return hit.getLocation().add(back.lengthSqr() < 1e-6 ? Vec3.ZERO : back.normalize().scale(0.25));
    }

    /** Players, pets and bystanders are never victims. */
    static boolean fair(LivingEntity target, Player owner) {
        return SkillFx.isVictim(target, owner);
    }

    /** True for an entity that is one of the display props of a power. */
    static boolean scenery(Entity e) {
        return e.getTags().contains(DisplayFx.TAG);
    }
}
