package dev.cogwit.client;

import dev.cogwit.CogwitMod;
import dev.cogwit.trophy.TrophyContent;
import net.minecraft.client.renderer.entity.NoopRenderer;
import net.minecraft.client.renderer.entity.WitherSkullRenderer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;


/** The renderers for trophy entities. */
public final class TrophyClient {
    private TrophyClient() {}

    @EventBusSubscriber(modid = CogwitMod.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
    public static final class ModEvents {
        private ModEvents() {}

        @SubscribeEvent
        @SuppressWarnings({"unchecked", "rawtypes"})
        public static void onRenderers(EntityRenderersEvent.RegisterRenderers event) {
            event.registerEntityRenderer(TrophyContent.DRAGON_BREATH.get(), NoopRenderer::new);
            event.registerEntityRenderer(TrophyContent.SANDSTORM.get(), NoopRenderer::new);
            event.registerEntityRenderer((net.minecraft.world.entity.EntityType) TrophyContent.SKULL.get(),
                    context -> new WitherSkullRenderer(context));
        }
    }
}
