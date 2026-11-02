package dev.cogwit.client;

import dev.cogwit.CogwitMod;
import dev.cogwit.crew.CrewContent;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

@EventBusSubscriber(modid = CogwitMod.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class CrewClient {
    private CrewClient() {}

    @SubscribeEvent
    public static void onRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(CrewContent.CREATURE.get(), CogwitCreatureRenderer::new);
    }

    @SubscribeEvent
    public static void onMenuScreens(RegisterMenuScreensEvent event) {
        event.register(CrewContent.GEAR_MENU.get(), CrewGearScreen::new);
    }
}
