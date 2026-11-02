package dev.cogwit.exoframe;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.cogwit.CogwitMod;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.constant.DataTickets;
import software.bernie.geckolib.model.DefaultedItemGeoModel;
import software.bernie.geckolib.renderer.GeoArmorRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.client.ICurioRenderer;

/**
 * Draws an exoframe half from its accessory slot with the armor model renderer, a little larger than the body part
 * it hugs, so it sits over whatever armor is worn there rather than inside it.
 */
public class ExoframeCurioRenderer implements ICurioRenderer {
    private static final net.minecraft.resources.ResourceLocation TEXTURE =
            CogwitMod.id("textures/item/armor/exoframe_armor.png");

    /** The frame model, scaled per body part: wider over armor, close to the body without it. */
    private static final class Model extends DefaultedItemGeoModel<ExoframePartItem> {
        Model() {
            super(CogwitMod.id("armor/exoframe_armor"));
        }

        @Override
        public void setCustomAnimations(ExoframePartItem animatable, long instanceId, AnimationState<ExoframePartItem> state) {
            super.setCustomAnimations(animatable, instanceId, state);
            if (!(state.getData(DataTickets.ENTITY) instanceof LivingEntity wearer)) {
                return;
            }
            float head = wearer.getItemBySlot(EquipmentSlot.HEAD).isEmpty() ? 1.04f : 1.15f;
            float chest = wearer.getItemBySlot(EquipmentSlot.CHEST).isEmpty() ? 1.05f : 1.2f;
            float legs = wearer.getItemBySlot(EquipmentSlot.LEGS).isEmpty() ? 1.04f : 1.15f;
            float feet = wearer.getItemBySlot(EquipmentSlot.FEET).isEmpty() ? 1.05f : 1.2f;
            scale("armorHead", head);
            scale("armorBody", chest);
            scale("armorRightArm", chest);
            scale("armorLeftArm", chest);
            scale("armorRightLeg", legs);
            scale("armorLeftLeg", legs);
            scale("armorRightBoot", feet);
            scale("armorLeftBoot", feet);
        }

        private void scale(String name, float s) {
            getBone(name).ifPresent(bone -> {
                bone.setScaleX(s);
                bone.setScaleY(s);
                bone.setScaleZ(s);
            });
        }
    }

    private final GeoArmorRenderer<ExoframePartItem> renderer = new GeoArmorRenderer<>(new Model());

    public ExoframeCurioRenderer() {
        renderer.addRenderLayer(new AutoGlowingGeoLayer<>(renderer));
    }

    @Override
    public <T extends LivingEntity, M extends EntityModel<T>> void render(ItemStack stack, SlotContext slotContext,
            PoseStack poseStack, RenderLayerParent<T, M> parent, MultiBufferSource buffers, int light,
            float limbSwing, float limbSwingAmount, float partialTicks, float ageInTicks, float netHeadYaw,
            float headPitch) {
        if (!(parent.getModel() instanceof HumanoidModel<?> base) || !(stack.getItem() instanceof ExoframePartItem part)) {
            return;
        }
        EquipmentSlot[] slots = part.half == ExoframePartItem.Half.UPPER
                ? new EquipmentSlot[] {EquipmentSlot.HEAD, EquipmentSlot.CHEST}
                : new EquipmentSlot[] {EquipmentSlot.LEGS, EquipmentSlot.FEET};
        for (EquipmentSlot slot : slots) {
            renderer.prepForRender(slotContext.entity(), stack, slot, base, buffers, partialTicks, limbSwing,
                    limbSwingAmount, netHeadYaw, headPitch);
            renderer.renderToBuffer(poseStack, buffers.getBuffer(RenderType.armorCutoutNoCull(TEXTURE)), light,
                    OverlayTexture.NO_OVERLAY, 0xFFFFFFFF);
        }
    }
}
