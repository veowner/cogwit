package dev.cogwit.client;

import dev.cogwit.CogwitMod;
import dev.cogwit.brain.Brain;
import dev.cogwit.brain.BrainState;
import dev.cogwit.brain.Growth;
import dev.cogwit.growth.GrowthContent;
import dev.cogwit.growth.Perks;
import dev.cogwit.item.CogwitTalismanItem;
import dev.cogwit.mind.Threats;
import dev.cogwit.ultra.Overdrive;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Cogwit's eye on the client: what to bracket and label on screen this tick (from Cogwit level 1). He marks what you
 * could see yourself: hostile mobs in the open within {@link #SIGHT} blocks, never through walls. Bosses and arrows
 * already in the air are the exceptions.
 */
@EventBusSubscriber(modid = CogwitMod.MODID, value = Dist.CLIENT)
public final class ThreatSense {
    public record Threat(Entity entity, Threats.Level level, Component label, double distance, long firstSeenMillis) {}

    public static final double SIGHT = 16;
    private static final double BOSS_RANGE = 48, LOOT_RANGE = 12;
    private static volatile List<Threat> current = List.of();
    private static final Map<Integer, Long> FIRST_SEEN = new HashMap<>();

    private ThreatSense() {}

    public static List<Threat> current() {
        return current;
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null || mc.level == null) {
            current = List.of();
            return;
        }
        ItemStack cogwit = CogwitTalismanItem.worn(player).orElse(null);
        long tick = mc.level.getGameTime();
        BrainState brain = cogwit == null ? null : CogwitTalismanItem.brain(cogwit);
        if (brain == null || Brain.isAsleep(brain, tick) || !Growth.has(brain.xp(), Growth.Perk.EYE)) {
            current = List.of();
            FIRST_SEEN.clear();
            return;
        }
        boolean loot = Growth.has(brain.xp(), Growth.Perk.HIGHLIGHT);
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        List<Threat> found = new ArrayList<>();
        long now = System.currentTimeMillis();
        for (Entity e : mc.level.getEntities(player, player.getBoundingBox().inflate(BOSS_RANGE), Entity::isAlive)) {
            double d = e.distanceTo(player);
            Threats.Level level = null;
            Component tag = null;
            if (e instanceof LivingEntity living && e.getType().is(GrowthContent.BOSSES)) {
                level = Threats.Level.BOSS;
                tag = Component.translatable("cogwit.jarvis.boss",
                        Math.round(100 * living.getHealth() / Math.max(1, living.getMaxHealth())));
            } else if (e instanceof Projectile p && p.getOwner() != player && d < SIGHT) {
                Vec3 v = p.getDeltaMovement();
                if (Overdrive.incoming(p.getX(), p.getY(), p.getZ(), v.x, v.y, v.z, eye.x, eye.y - 0.4, eye.z)) {
                    level = Threats.Level.DANGER;
                    tag = Component.translatable("cogwit.jarvis.projectile");
                }
            } else if (e instanceof Enemy && e instanceof LivingEntity mob && d < SIGHT && player.hasLineOfSight(e)) {
                if (e instanceof Creeper creeper) {
                    boolean behind = Brain.isBehind(look.x, look.z, e.getX() - player.getX(),
                            e.getZ() - player.getZ());
                    level = Threats.creeper(d, behind, creeper.getSwellDir() > 0);
                    tag = level == Threats.Level.DANGER ? Component.translatable("cogwit.jarvis.explode") : null;
                } else if (e instanceof RangedAttackMob && mob.isUsingItem() && facing(mob, eye)) {
                    level = Threats.Level.WARN;
                    tag = Component.translatable("cogwit.jarvis.aiming");
                } else if (Threats.coming(mob instanceof Mob m && m.isAggressive(), approach(mob, player))) {
                    level = Threats.Level.WARN;
                    tag = Component.translatable("cogwit.jarvis.coming");
                } else {
                    level = Threats.Level.INFO;
                }
            } else if (loot && e instanceof ItemEntity item && d < LOOT_RANGE && Perks.isValuable(item.getItem())) {
                level = Threats.Level.LOOT;
                tag = Component.literal("×" + item.getItem().getCount());
            }
            if (level == null) {
                continue;
            }
            Component name = e instanceof ItemEntity item ? item.getItem().getHoverName() : e.getDisplayName();
            Component label = Component.literal(name.getString().toUpperCase(Locale.ROOT))
                    .append(" · ").append(Component.translatable("cogwit.jarvis.meters", Math.round(d)));
            if (tag != null) {
                label = label.copy().append(" · ").append(tag);
            }
            long first = FIRST_SEEN.computeIfAbsent(e.getId(), id -> now);
            found.add(new Threat(e, level, label, d, first));
        }
        List<Threat> shown = Threats.pick(found, Threat::level, Threat::distance);
        FIRST_SEEN.keySet().removeIf(id -> shown.stream().noneMatch(t -> t.entity().getId() == id));
        current = shown;
    }

    /** How fast the mob closes in on the player, in blocks per tick (negative when it moves away). */
    private static double approach(LivingEntity mob, Player player) {
        Vec3 step = new Vec3(mob.getX() - mob.xo, 0, mob.getZ() - mob.zo);
        Vec3 to = new Vec3(player.getX() - mob.getX(), 0, player.getZ() - mob.getZ());
        double len = to.length();
        return len < 1e-6 ? 0 : step.dot(to) / len;
    }

    /** True when a shooter is looking roughly at the player's eyes. */
    private static boolean facing(LivingEntity shooter, Vec3 eye) {
        Vec3 to = eye.subtract(shooter.getEyePosition()).normalize();
        return shooter.getViewVector(1f).dot(to) > 0.85;
    }

    /** RGB for a level, as floats: red danger, violet boss, orange warning, gold loot, Jarvis cyan for the rest. */
    public static float[] colour(Threats.Level level) {
        return switch (level) {
            case DANGER -> new float[] {1f, 0.32f, 0.32f};
            case BOSS -> new float[] {0.8f, 0.5f, 1f};
            case WARN -> new float[] {1f, 0.62f, 0.3f};
            case LOOT -> new float[] {1f, 0.84f, 0.4f};
            case INFO -> new float[] {0.55f, 0.88f, 1f};
        };
    }
}
