package dev.cogwit.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.cogwit.CogwitMod;
import dev.cogwit.ClientConfig;
import dev.cogwit.brain.Brain;
import dev.cogwit.brain.BrainState;
import dev.cogwit.brain.HudView;
import dev.cogwit.item.CogwitTalismanItem;
import dev.cogwit.horizon.HorizonClientState;
import dev.cogwit.horizon.HorizonContent;
import net.minecraft.ChatFormatting;
import net.minecraft.world.item.ArmorItem;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import dev.cogwit.network.AbilityPayload;
import dev.cogwit.network.AnswerPayload;
import dev.cogwit.network.CrewPayload;
import dev.cogwit.speech.SpeechClientState;
import net.minecraft.client.gui.screens.Screen;
import dev.cogwit.network.ScanPayload;
import dev.cogwit.mind.Mind;
import dev.cogwit.mind.MindService;
import dev.cogwit.ultra.UltraService;
import dev.cogwit.network.ThinkPayload;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;
import top.theillusivec4.curios.api.client.CuriosRendererRegistry;

import java.util.Optional;

public final class CogwitClient {
    public static final KeyMapping THINK_KEY = new KeyMapping("key.cogwit.think", InputConstants.Type.KEYSYM,
            InputConstants.UNKNOWN.getValue(), "key.categories.cogwit");
    public static final KeyMapping HUD_KEY = new KeyMapping("key.cogwit.hud", InputConstants.Type.KEYSYM,
            InputConstants.UNKNOWN.getValue(), "key.categories.cogwit");
    public static final KeyMapping BLINK_KEY = new KeyMapping("key.cogwit.blink", InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_Z, "key.categories.cogwit");
    public static final KeyMapping CREW_KEY = new KeyMapping("key.cogwit.crew", InputConstants.Type.KEYSYM,
            InputConstants.UNKNOWN.getValue(), "key.categories.cogwit");
    public static final KeyMapping SCAN_KEY = new KeyMapping("key.cogwit.scan", InputConstants.Type.KEYSYM,
            InputConstants.UNKNOWN.getValue(), "key.categories.cogwit");
    public static final KeyMapping MARK_KEY = new KeyMapping("key.cogwit.mark", InputConstants.Type.KEYSYM,
            InputConstants.UNKNOWN.getValue(), "key.categories.cogwit");
    public static final KeyMapping YES_KEY = new KeyMapping("key.cogwit.yes", InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_Y, "key.categories.cogwit");
    public static final KeyMapping AGENT_KEY = new KeyMapping("key.cogwit.agent", InputConstants.Type.KEYSYM,
            InputConstants.UNKNOWN.getValue(), "key.categories.cogwit");
    public static final KeyMapping WHEEL_KEY = new KeyMapping("key.cogwit.wheel", InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_G, "key.categories.cogwit");
    public static final KeyMapping CODEX_KEY = new KeyMapping("key.cogwit.codex", InputConstants.Type.KEYSYM,
            InputConstants.UNKNOWN.getValue(), "key.categories.cogwit");
    public static final KeyMapping GRAVITY_KEY = new KeyMapping("key.cogwit.gravity", InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_X, "key.categories.cogwit");
    public static final KeyMapping VISOR_KEY = new KeyMapping("key.cogwit.visor", InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_H, "key.categories.cogwit");

    /** Icon variants picked by the "cogwit:state" model predicate. */
    static final float AWAKE = 0f, BLINK = 0.25f, THINKING = 0.5f, HAPPY = 0.6f, SCARED = 0.65f, ASLEEP = 0.75f,
            RAGE = 0.9f, GLITCH = 1f;

    private CogwitClient() {}

    static float iconState(BrainState s, long now) {
        if (Brain.isAsleep(s, now)) return ASLEEP;
        if (Brain.isThinking(s, now)) return THINKING;
        return now % 80 < 4 ? BLINK : AWAKE;
    }

    private static Optional<ItemStack> wornByLocalPlayer() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player == null ? Optional.empty() : CogwitTalismanItem.worn(mc.player);
    }

    private static void drawPanel(GuiGraphics g, ItemStack stack, int x, int y) {
        Minecraft mc = Minecraft.getInstance();
        long now = mc.level != null ? mc.level.getGameTime() : 0;
        CogwitHud.render(g, mc.font, stack, HudView.of(CogwitTalismanItem.brain(stack), now), x, y);
    }

    @EventBusSubscriber(modid = CogwitMod.MODID, value = Dist.CLIENT)
    public static final class ModEvents {
        private ModEvents() {}

        @SubscribeEvent
        public static void onClientSetup(FMLClientSetupEvent event) {
            event.enqueueWork(() -> {
                ItemProperties.register(CogwitMod.COGWIT.get(), CogwitMod.id("state"), (stack, level, entity, seed) -> {
                    var world = level != null ? level : Minecraft.getInstance().level;
                    long now = world != null ? world.getGameTime() : 0;
                    if (UltraService.isActive(stack)) {
                        // furious violet Cogwit that glitches every so often
                        return now % 23 < 2 || now % 37 == 0 ? GLITCH : RAGE;
                    }
                    float state = iconState(CogwitTalismanItem.brain(stack), now);
                    if (state == AWAKE || state == BLINK) {
                        switch (Mind.emotion(MindService.state(stack), now)) {
                            case HAPPY -> state = HAPPY;
                            case SCARED -> state = SCARED;
                            default -> { }
                        }
                    }
                    return state;
                });
                CuriosRendererRegistry.register(CogwitMod.COGWIT.get(), ShoulderRenderer::new);
                CuriosRendererRegistry.register(dev.cogwit.exoframe.ExoframeContent.UPPER.get(),
                        dev.cogwit.exoframe.ExoframeCurioRenderer::new);
                CuriosRendererRegistry.register(dev.cogwit.exoframe.ExoframeContent.LOWER.get(),
                        dev.cogwit.exoframe.ExoframeCurioRenderer::new);
            });
        }

        @SubscribeEvent
        public static void onRegisterKeys(RegisterKeyMappingsEvent event) {
            event.register(THINK_KEY);
            event.register(HUD_KEY);
            event.register(BLINK_KEY);
            event.register(GRAVITY_KEY);
            event.register(VISOR_KEY);
            event.register(CREW_KEY);
            event.register(SCAN_KEY);
            event.register(YES_KEY);
            event.register(WHEEL_KEY);
            event.register(CODEX_KEY);
            event.register(AGENT_KEY);
            event.register(MARK_KEY);
        }

        @SubscribeEvent
        public static void onRegisterGuiLayers(RegisterGuiLayersEvent event) {
            event.registerBelowAll(CogwitMod.id("screen_fx"), (graphics, delta) -> FxClient.renderOverlay(graphics));
            event.registerAboveAll(CogwitMod.id("jarvis"), (graphics, delta) -> ThreatHud.render(graphics));
            event.registerAboveAll(CogwitMod.id("widget"), (graphics, delta) -> CogwitWidget.render(graphics));
            event.registerAboveAll(CogwitMod.id("thought"), (graphics, delta) -> ThoughtLine.render(graphics));
            event.registerAboveAll(CogwitMod.id("overdrive"), (graphics, delta) -> UltraOverlay.render(graphics));
            event.registerAboveAll(CogwitMod.id("panel"), (graphics, delta) -> {
                Minecraft mc = Minecraft.getInstance();
                if (mc.options.hideGui || mc.screen != null || !ClientConfig.HUD_ENABLED.get()) {
                    return;
                }
                boolean left = ClientConfig.HUD_SIDE.get() == ClientConfig.Side.LEFT;
                wornByLocalPlayer().ifPresent(stack -> {
                    int x = left ? 4 : graphics.guiWidth() - CogwitHud.WIDTH - 4;
                    int y = Math.max(4, (graphics.guiHeight() - CogwitHud.height()) / 2);
                    drawPanel(graphics, stack, x, y);
                });
                if (HorizonClientState.latest().pieces() != 0 && HorizonHud.worthShowing()) {
                    int x = left ? graphics.guiWidth() - HorizonHud.WIDTH - 4 : 4;
                    HorizonHud.render(graphics, mc.font, x, Math.max(4, (graphics.guiHeight() - HorizonHud.HEIGHT) / 2));
                }
            });
        }
    }

    @EventBusSubscriber(modid = CogwitMod.MODID, value = Dist.CLIENT)
    public static final class GameEvents {
        private GameEvents() {}

        /** The server learns this player's chatter setting as soon as they join. */
        @SubscribeEvent
        public static void onLoggingIn(net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent.LoggingIn event) {
            dev.cogwit.network.CogwitPrefsPayload.sendFromClient();
        }

        @SubscribeEvent
        public static void onClientTick(ClientTickEvent.Post event) {
            if (Minecraft.getInstance().options != null) {
                PackKeyLayout.applyOnce();
            }
            while (THINK_KEY.consumeClick()) {
                PacketDistributor.sendToServer(new ThinkPayload());
            }
            while (BLINK_KEY.consumeClick()) {
                PacketDistributor.sendToServer(new AbilityPayload(AbilityPayload.BLINK));
            }
            while (YES_KEY.consumeClick()) {
                SpeechClientState.Line question = SpeechClientState.openQuestion();
                if (question != null) {
                    PacketDistributor.sendToServer(new AnswerPayload(question.askId(), Screen.hasShiftDown()));
                    SpeechClientState.answered(question.askId());
                }
            }
            while (WHEEL_KEY.consumeClick()) {
                if (Minecraft.getInstance().screen == null) {
                    Minecraft.getInstance().setScreen(new AbilityWheelScreen(WHEEL_KEY));
                }
            }
            while (CODEX_KEY.consumeClick()) {
                Minecraft.getInstance().setScreen(new TrophyCodexScreen());
            }
            while (AGENT_KEY.consumeClick()) {
                Minecraft.getInstance().setScreen(new AgentScreen());
            }
            while (SCAN_KEY.consumeClick()) {
                PacketDistributor.sendToServer(new ScanPayload());
            }
            while (MARK_KEY.consumeClick()) {
                MindRenderer.markWhatPlayerLooksAt();
            }
            while (CREW_KEY.consumeClick()) {
                PacketDistributor.sendToServer(new CrewPayload());
            }
            while (VISOR_KEY.consumeClick()) {
                PacketDistributor.sendToServer(new dev.cogwit.network.VisorPayload());
            }
            while (GRAVITY_KEY.consumeClick()) {
                PacketDistributor.sendToServer(new AbilityPayload(AbilityPayload.GRAVITY));
            }
            while (HUD_KEY.consumeClick()) {
                boolean shown = !ClientConfig.HUD_ENABLED.get();
                ClientConfig.HUD_ENABLED.set(shown);
                ClientConfig.SPEC.save();
                Minecraft mc = Minecraft.getInstance();
                if (mc.player != null) {
                    mc.player.displayClientMessage(
                            Component.translatable(shown ? "cogwit.hud.shown" : "cogwit.hud.hidden"), true);
                }
            }
        }

        @SubscribeEvent
        public static void onTooltip(ItemTooltipEvent event) {
            ItemStack stack = event.getItemStack();
            if (stack.getItem() instanceof ArmorItem armor && armor.getMaterial().is(HorizonContent.HORIZON.getKey())) {
                event.getToolTip().add(Component.translatable("cogwit.tooltip.horizon_set").withStyle(ChatFormatting.DARK_PURPLE));
                event.getToolTip().add(Component.translatable("cogwit.tooltip.horizon_abilities").withStyle(ChatFormatting.GRAY));
                event.getToolTip().add(Component.translatable("cogwit.tooltip.horizon_keys").withStyle(ChatFormatting.DARK_GRAY));
            } else if (stack.is(HorizonContent.SINGULARITY_CORE.get())) {
                event.getToolTip().add(Component.translatable("cogwit.tooltip.singularity_core").withStyle(ChatFormatting.GRAY));
            } else if (stack.is(HorizonContent.TEMPLATE.get())) {
                event.getToolTip().add(Component.translatable("cogwit.tooltip.horizon_template").withStyle(ChatFormatting.GRAY));
            }
        }

        /** In the player's own inventory the panel sits next to the window, even if the HUD is hidden. */
        @SubscribeEvent
        public static void onScreenRender(ScreenEvent.Render.Post event) {
            if (!(event.getScreen() instanceof AbstractContainerScreen<?> screen) || !isInventory(screen)) {
                return;
            }
            wornByLocalPlayer().ifPresent(stack -> {
                int x = screen.getGuiLeft() - CogwitHud.WIDTH - 6;
                if (x < 2) {
                    x = Math.min(screen.getGuiLeft() + screen.getXSize() + 6,
                            event.getGuiGraphics().guiWidth() - CogwitHud.WIDTH - 2);
                }
                int y = Math.max(2, screen.getGuiTop());
                event.getGuiGraphics().pose().pushPose();
                event.getGuiGraphics().pose().translate(0, 0, 400);
                drawPanel(event.getGuiGraphics(), stack, x, y);
                event.getGuiGraphics().pose().popPose();
            });
        }

        private static boolean isInventory(AbstractContainerScreen<?> screen) {
            return screen instanceof InventoryScreen || screen instanceof CreativeModeInventoryScreen
                    || screen.getClass().getName().contains("curios");
        }
    }
}
