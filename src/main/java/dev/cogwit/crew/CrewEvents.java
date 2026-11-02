package dev.cogwit.crew;

import dev.cogwit.CogwitMod;
import dev.cogwit.item.CogwitTalismanItem;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/** Brings the crew home whenever their owner can't take them along. */
@EventBusSubscriber(modid = CogwitMod.MODID)
public final class CrewEvents {
    private CrewEvents() {}

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        recall(event.getEntity());
    }

    @SubscribeEvent
    public static void onDimensionChange(PlayerEvent.PlayerChangedDimensionEvent event) {
        recall(event.getEntity());
    }

    @SubscribeEvent
    public static void onOwnerDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof Player player && !player.level().isClientSide) {
            recall(player);
        }
    }

    private static void recall(Player player) {
        CogwitTalismanItem.worn(player).ifPresent(talisman -> CrewService.recall(player, talisman, null));
    }
}
