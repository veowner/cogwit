package dev.cogwit.ultra;

import dev.cogwit.CogwitMod;
import dev.cogwit.Speech;
import dev.cogwit.item.CogwitTalismanItem;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** While in Overdrive: glyphs swirl around the owner and Cogwit calls out danger. */
@EventBusSubscriber(modid = CogwitMod.MODID)
public final class UltraEvents {
    private static final long WARNING_COOLDOWN = 40;
    private static final Map<UUID, Long> LAST_WARNING = new ConcurrentHashMap<>();
    private static final Map<UUID, Boolean> WAS_LOW = new ConcurrentHashMap<>();

    private UltraEvents() {}

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        if (!(player.level() instanceof ServerLevel level) || player.tickCount % 2 != 0) {
            return;
        }
        ItemStack talisman = CogwitTalismanItem.worn(player).orElse(null);
        if (talisman == null || !UltraService.isActive(talisman)) {
            return;
        }
        glyphs(player, level);
        if (player.tickCount % 6 == 0) {
            warnAboutProjectiles(player, level);
        }
        boolean low = player.getHealth() < player.getMaxHealth() * 0.3f;
        if (low && !WAS_LOW.getOrDefault(player.getUUID(), false)) {
            Speech.hint(player, Component.translatable("cogwit.ultra.low_hp"));
        }
        WAS_LOW.put(player.getUUID(), low);
    }

    /** Enchanting-table glyphs orbiting the owner: Overdrive's mark, visible to the co-op friend too. */
    private static void glyphs(Player player, ServerLevel level) {
        double t = player.tickCount * 0.25;
        for (int k = 0; k < 3; k++) {
            double a = t + k * 2.094;
            double x = player.getX() + Math.cos(a) * 1.1, z = player.getZ() + Math.sin(a) * 1.1;
            level.sendParticles(ParticleTypes.ENCHANT, x, player.getY() + 0.4 + (k * 0.6), z, 2, 0.05, 0.2, 0.05,
                    0.6);
        }
        if (player.tickCount % 10 == 0) {
            level.sendParticles(ParticleTypes.REVERSE_PORTAL, player.getX(), player.getY() + 1.0, player.getZ(), 6,
                    0.4, 0.6, 0.4, 0.02);
        }
    }

    public static void warnAboutProjectiles(Player player, ServerLevel level) {
        long now = level.getGameTime();
        if (now - LAST_WARNING.getOrDefault(player.getUUID(), -WARNING_COOLDOWN) < WARNING_COOLDOWN) {
            return;
        }
        Vec3 target = player.position().add(0, player.getBbHeight() / 2, 0);
        for (Projectile projectile : level.getEntitiesOfClass(Projectile.class, player.getBoundingBox().inflate(16),
                p -> p.getOwner() != player)) {
            Vec3 v = projectile.getDeltaMovement();
            if (Overdrive.incoming(projectile.getX(), projectile.getY(), projectile.getZ(), v.x, v.y, v.z,
                    target.x, target.y, target.z)) {
                LAST_WARNING.put(player.getUUID(), now);
                Speech.hint(player, Component.translatable("cogwit.ultra.projectile", projectile.getDisplayName()));
                return;
            }
        }
    }
}
