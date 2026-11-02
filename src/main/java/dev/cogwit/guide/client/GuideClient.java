package dev.cogwit.guide.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.cogwit.CogwitMod;
import dev.cogwit.guide.GuideClientState;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import org.lwjgl.glfw.GLFW;

/** Client wiring for the guide: roadmap key, compass layer, and clearing state on disconnect. */
public final class GuideClient {
    public static final KeyMapping ROADMAP_KEY = new KeyMapping("key.cogwit.roadmap", InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_K, "key.categories.cogwit");

    private GuideClient() {}

    @EventBusSubscriber(modid = CogwitMod.MODID, value = Dist.CLIENT)
    public static final class ModEvents {
        private ModEvents() {}

        @SubscribeEvent
        public static void onRegisterKeys(RegisterKeyMappingsEvent event) {
            event.register(ROADMAP_KEY);
        }

        @SubscribeEvent
        public static void onRegisterGuiLayers(RegisterGuiLayersEvent event) {
            event.registerAboveAll(CogwitMod.id("guide_compass"), (graphics, delta) -> GuideHud.render(graphics));
        }
    }

    @EventBusSubscriber(modid = CogwitMod.MODID, value = Dist.CLIENT)
    public static final class GameEvents {
        private GameEvents() {}

        @SubscribeEvent
        public static void onClientTick(ClientTickEvent.Post event) {
            Minecraft mc = Minecraft.getInstance();
            while (ROADMAP_KEY.consumeClick()) {
                if (mc.player != null && mc.screen == null) {
                    mc.setScreen(new RoadmapScreen());
                }
            }
        }

        @SubscribeEvent
        public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
            GuideClientState.reset();
        }
    }
}
