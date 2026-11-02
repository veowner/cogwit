package dev.cogwit.horizon.fx;

import dev.cogwit.horizon.SkillPhysics;
import dev.cogwit.network.ScreenFxPayload;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ambient.AmbientCreature;
import net.minecraft.world.entity.animal.AbstractGolem;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.WaterAnimal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;

/** Shared visuals for the armor skills: particle shapes on the server and screen effects for nearby players. */
public final class SkillFx {
    /** Tag on summoned allies (the Dead King's skeletons): no skill of any player touches them. */
    public static final String ALLY_TAG = "cogwit_ally";
    /** How far away players see a skill's particles. */
    public static final double SEEN_FROM = 64;

    private SkillFx() {}

    /**
     * Particles that players nearby see even with particles set to minimal, which otherwise drops nearly all of them:
     * an active's effect is how you read the fight, not decoration.
     */
    public static void particles(ServerLevel level, ParticleOptions particle, double x, double y, double z, int count,
            double dx, double dy, double dz, double speed) {
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(x, y, z) < SEEN_FROM * SEEN_FROM) {
                level.sendParticles(player, particle, true, x, y, z, count, dx, dy, dz, speed);
            }
        }
    }

    /** Screen effects for everyone near {@code at}, weaker with distance. */
    public static void screen(ServerLevel level, Vec3 at, double range, int kind, float strength, int ticks) {
        for (ServerPlayer player : level.players()) {
            float s = strength * SkillPhysics.screenFalloff(player.position().distanceTo(at), range);
            if (s > 0.02f) {
                PacketDistributor.sendToPlayer(player, new ScreenFxPayload(kind, s, ticks));
            }
        }
    }

    public static void screen(ServerPlayer player, int kind, float strength, int ticks) {
        PacketDistributor.sendToPlayer(player, new ScreenFxPayload(kind, strength, ticks));
    }

    /** A streak of particles from one point to another. */
    public static void line(ServerLevel level, Vec3 from, Vec3 to, ParticleOptions particle, double step) {
        Vec3 d = to.subtract(from);
        int n = (int) Math.ceil(d.length() / step);
        for (int i = 0; i <= n; i++) {
            Vec3 p = from.add(d.scale((double) i / Math.max(1, n)));
            particles(level, particle, p.x, p.y, p.z, 1, 0.04, 0.04, 0.04, 0.0);
        }
    }

    /** A flat ring of particles around {@code centre}, facing {@code normal}. */
    public static void ring(ServerLevel level, Vec3 centre, Vec3 normal, double radius, int count,
            ParticleOptions particle) {
        Vec3 n = normal.lengthSqr() < 1e-6 ? new Vec3(0, 1, 0) : normal.normalize();
        Vec3 a = Math.abs(n.y) < 0.9 ? n.cross(new Vec3(0, 1, 0)).normalize() : n.cross(new Vec3(1, 0, 0)).normalize();
        Vec3 b = n.cross(a).normalize();
        for (int i = 0; i < count; i++) {
            double t = i * 2 * Math.PI / count;
            Vec3 p = centre.add(a.scale(Math.cos(t) * radius)).add(b.scale(Math.sin(t) * radius));
            particles(level, particle, p.x, p.y, p.z, 1, 0, 0, 0, 0);
        }
    }

    /** Particles that fly into a point: Minecraft's portal particles travel from their offset back to their origin. */
    public static void implode(ServerLevel level, Vec3 centre, double radius, int count) {
        for (int i = 0; i < count; i++) {
            Vec3 off = new Vec3(level.random.nextGaussian(), level.random.nextGaussian() * 0.6,
                    level.random.nextGaussian()).normalize().scale(radius * (0.6 + 0.4 * level.random.nextDouble()));
            particles(level, ParticleTypes.PORTAL, centre.x, centre.y, centre.z, 0, off.x, off.y, off.z, 1.0);
        }
    }

    /**
     * Who a skill may push around and hurt: mobs, not players, pets, armor stands or the owner's crew, and not
     * bystanders (villagers, golems, animals) unless they are attacking the owner.
     */
    public static List<LivingEntity> victims(Entity source, Player owner, Vec3 centre, double radius) {
        return source.level().getEntitiesOfClass(LivingEntity.class, AABB.ofSize(centre, radius * 2, radius * 2,
                        radius * 2),
                e -> isVictim(e, owner) && e.position().distanceTo(centre) <= radius);
    }

    /** True when a skill of {@code owner} may hurt this creature: the filter {@link #victims} applies to each one. */
    public static boolean isVictim(LivingEntity e, Player owner) {
        return e != owner && e.isAlive() && !(e instanceof Player) && !(e instanceof ArmorStand)
                && !(e instanceof TamableAnimal pet && pet.isTame()) && !isBystander(e, owner)
                && !e.getTags().contains(ALLY_TAG);
    }

    /** A peaceful villager, golem or animal that isn't fighting the owner: area skills leave it alone. */
    public static boolean isBystander(LivingEntity e, Player owner) {
        if (e instanceof Enemy) {
            return false;
        }
        boolean peaceful = e instanceof AbstractVillager || e instanceof AbstractGolem || e instanceof Animal
                || e instanceof WaterAnimal || e instanceof AmbientCreature;
        return peaceful && !(e instanceof Mob mob && mob.getTarget() == owner);
    }
}
