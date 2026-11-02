package dev.cogwit.client;

import dev.cogwit.CogwitMod;
import dev.cogwit.horizon.fx.CollapseEntity;
import dev.cogwit.horizon.fx.FxContent;
import dev.cogwit.horizon.fx.GravityWellEntity;
import dev.cogwit.horizon.fx.ScreenFxState;
import dev.cogwit.network.ScreenFxPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import software.bernie.geckolib.animatable.GeoAnimatable;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

/** Client side of the armor skills: the black hole and collapse models, and the screen effects. */
public final class FxClient {
    private FxClient() {}

    /** A GeckoLib renderer for an effect entity named after its model files, with a glowing layer. */
    static <T extends Entity & GeoAnimatable> GeoEntityRenderer<T> renderer(EntityRendererProvider.Context context,
            String name) {
        GeoEntityRenderer<T> renderer = new GeoEntityRenderer<>(context, model(name)) {
            @Override
            public void preRender(com.mojang.blaze3d.vertex.PoseStack poseStack, T animatable,
                    software.bernie.geckolib.cache.object.BakedGeoModel model,
                    net.minecraft.client.renderer.MultiBufferSource bufferSource,
                    com.mojang.blaze3d.vertex.VertexConsumer buffer, boolean isReRender, float partialTick,
                    int packedLight, int packedOverlay, int colour) {
                super.preRender(poseStack, animatable, model, bufferSource, buffer, isReRender, partialTick,
                        packedLight, packedOverlay, colour);
                // the collapse shell wraps its owner: from inside it would blind them, so it only shows from outside
                net.minecraft.world.phys.Vec3 eye = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
                boolean inside = eye.distanceTo(animatable.position().add(0, 1, 0)) < 2.2;
                model.getBone("sphere").ifPresent(bone -> bone.setHidden(inside));
            }
        };
        renderer.addRenderLayer(new AutoGlowingGeoLayer<>(renderer));
        return renderer;
    }

    private static <T extends Entity & GeoAnimatable> GeoModel<T> model(String name) {
        return new GeoModel<T>() {
            @Override
            public ResourceLocation getModelResource(T animatable) {
                return CogwitMod.id("geo/entity/" + name + ".geo.json");
            }

            @Override
            public ResourceLocation getTextureResource(T animatable) {
                return CogwitMod.id("textures/entity/" + name + ".png");
            }

            @Override
            public ResourceLocation getAnimationResource(T animatable) {
                return CogwitMod.id("animations/entity/" + name + ".animation.json");
            }
        };
    }

    /** Flash and darkening on top of the game, under the HUD. */
    public static void renderOverlay(GuiGraphics g) {
        int w = g.guiWidth(), h = g.guiHeight();
        float dark = darkness();
        if (dark > 0.01f) {
            g.fill(0, 0, w, h, (int) (dark * 255) << 24 | 0x0A0612);
        }
        float flash = ScreenFxState.current(ScreenFxPayload.FLASH);
        if (flash > 0.01f) {
            g.fill(0, 0, w, h, (int) (Math.min(1f, flash) * 230) << 24 | 0xF4ECFF);
        }
        float violet = ScreenFxState.current(ScreenFxPayload.FLASH_VIOLET);
        if (violet > 0.01f) {
            g.fill(0, 0, w, h, (int) (Math.min(1f, violet) * 200) << 24 | 0x9B4DDB);
        }
    }

    /** Darkening builds up over the collapse charge instead of fading out. */
    private static float darkness() {
        float t = ScreenFxState.progress(ScreenFxPayload.DARKEN);
        return t < 0 ? 0 : ScreenFxState.peak(ScreenFxPayload.DARKEN) * t * t;
    }

    @EventBusSubscriber(modid = CogwitMod.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
    public static final class ModEvents {
        private ModEvents() {}

        @SubscribeEvent
        public static void onRenderers(EntityRenderersEvent.RegisterRenderers event) {
            event.<GravityWellEntity>registerEntityRenderer(FxContent.GRAVITY_WELL.get(),
                    context -> renderer(context, "gravity_well"));
            event.<CollapseEntity>registerEntityRenderer(FxContent.COLLAPSE.get(),
                    context -> renderer(context, "collapse"));
        }
    }

    @EventBusSubscriber(modid = CogwitMod.MODID, value = Dist.CLIENT)
    public static final class GameEvents {
        private GameEvents() {}

        /** Camera shake: a fast, fading jitter of pitch, yaw and roll. */
        @SubscribeEvent
        public static void onCameraAngles(ViewportEvent.ComputeCameraAngles event) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null && mc.options.getCameraType().isFirstPerson()) {
                // the landing crouch seen from inside: the view dips and comes back up
                float crouch = dev.cogwit.horizon.LandingClientState.crouch(mc.player.getId());
                if (crouch > 0f) {
                    event.setPitch(event.getPitch() + 9f * crouch);
                }
            }
            float shake = ScreenFxState.current(ScreenFxPayload.SHAKE);
            if (shake <= 0.01f || Minecraft.getInstance().options.hideGui) {
                return;
            }
            double t = System.currentTimeMillis() / 18.0;
            float amp = shake * 3.5f;
            event.setPitch(event.getPitch() + (float) Math.sin(t * 1.7) * amp);
            event.setYaw(event.getYaw() + (float) Math.sin(t * 2.3 + 1) * amp);
            event.setRoll(event.getRoll() + (float) Math.sin(t * 2.9 + 2) * amp * 0.6f);
        }

        /** Field of view: the blink punches it wide, the well pulls it in a little. */
        @SubscribeEvent
        public static void onFov(ViewportEvent.ComputeFov event) {
            float punch = ScreenFxState.current(ScreenFxPayload.FOV_PUNCH);
            float pull = ScreenFxState.current(ScreenFxPayload.FOV_PULL);
            if (punch > 0.01f || pull > 0.01f) {
                event.setFOV(event.getFOV() * (1 + 0.28 * punch - 0.12 * pull));
            }
        }
    }
}
