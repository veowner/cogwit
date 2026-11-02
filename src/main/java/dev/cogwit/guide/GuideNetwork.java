package dev.cogwit.guide;

import dev.cogwit.CogwitMod;
import dev.cogwit.guide.net.GuideProgressPayload;
import dev.cogwit.guide.net.GuideTargetPayload;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** Registers the guide's server-to-client payloads. */
@EventBusSubscriber(modid = CogwitMod.MODID)
public final class GuideNetwork {
    private GuideNetwork() {}

    @SubscribeEvent
    public static void onRegisterPayloads(RegisterPayloadHandlersEvent event) {
        event.registrar("1")
                .playToClient(GuideProgressPayload.TYPE, GuideProgressPayload.STREAM_CODEC, GuideProgressPayload::handle)
                .playToClient(GuideTargetPayload.TYPE, GuideTargetPayload.STREAM_CODEC, GuideTargetPayload::handle);
    }
}
