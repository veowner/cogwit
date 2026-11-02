package dev.cogwit.mind;

import dev.cogwit.CogwitMod;
import dev.cogwit.item.CogwitTalismanItem;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

@EventBusSubscriber(modid = CogwitMod.MODID)
public final class MindEvents {
    private MindEvents() {}

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        if (player.level().isClientSide || player.tickCount % 10 != 0) {
            return;
        }
        CogwitTalismanItem.worn(player).ifPresent(t -> MindService.watch(player, t, player.level().getGameTime()));
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (event.getEntity().level().isClientSide) {
            return;
        }
        if (event.getEntity() instanceof Player player) {
            MindService.recordDeath(player, event.getSource());
        } else if (event.getSource().getEntity() instanceof Player killer) {
            MindService.cheer(killer, Mind.HAPPY_KILL_TICKS);
        }
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (!event.isEndConquered() && !event.getEntity().level().isClientSide) {
            MindService.commentOnDeath(event.getEntity());
        }
    }
}
