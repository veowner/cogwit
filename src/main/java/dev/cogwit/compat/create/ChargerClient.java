package dev.cogwit.compat.create;

import com.simibubi.create.content.kinetics.base.ShaftRenderer;
import com.simibubi.create.content.kinetics.base.SingleAxisRotatingVisual;
import dev.engine_room.flywheel.lib.visualization.SimpleBlockEntityVisualizer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/** Client-only part of the Create integration: the spinning shaft through the casing. */
final class ChargerClient {
    private ChargerClient() {}

    static void register(IEventBus modBus) {
        modBus.addListener(ChargerClient::registerRenderers);
        modBus.addListener(ChargerClient::clientSetup);
    }

    private static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ChargerContent.BLOCK_ENTITY.get(), ShaftRenderer::new);
    }

    private static void clientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> SimpleBlockEntityVisualizer.builder(ChargerContent.BLOCK_ENTITY.get())
                .factory(SingleAxisRotatingVisual::shaft)
                .skipVanillaRender(be -> true)
                .apply());
    }
}
