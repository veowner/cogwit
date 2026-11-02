package dev.cogwit.client;

import dev.cogwit.crew.CrewService;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.client.ICurioRenderer;

/** Draws Cogwit sitting on the wearer's left shoulder, following the body when it turns or sneaks. */
public class ShoulderRenderer implements ICurioRenderer {
    /** Cogwit's size on the shoulder, as a share of his item model. */
    private static final float SIZE = 0.55f;
    /**
     * The item model is a sprite one pixel thick, a cardboard cut-out from the side; stretched this many times
     * front to back, its edge pixels make him a solid little figure.
     */
    private static final float DEPTH = 4f;

    @Override
    public <T extends LivingEntity, M extends EntityModel<T>> void render(ItemStack stack, SlotContext slotContext,
            PoseStack poseStack, RenderLayerParent<T, M> parent, MultiBufferSource buffers, int light,
            float limbSwing, float limbSwingAmount, float partialTicks, float ageInTicks, float netHeadYaw,
            float headPitch) {
        if (!(parent.getModel() instanceof HumanoidModel<?> model)) {
            return;
        }
        LivingEntity entity = slotContext.entity();
        if (CrewService.isDeployed(stack)) {
            return; // he is down on the ground, fighting
        }
        poseStack.pushPose();
        // the body part already carries the sneaking lean; Curios' sneak helpers would apply it a second time
        // and sink Cogwit into the chest
        model.body.translateAndRotate(poseStack);
        // Model space: +x is the wearer's left, +y points down. Sit on top of the left shoulder.
        poseStack.translate(0.43, 0.08, 0.0);
        if (!entity.getItemBySlot(EquipmentSlot.CHEST).isEmpty()) {
            // armor layers are drawn slightly inflated, so sit on top of the shoulder plate instead of inside it
            poseStack.translate(0.04, -0.08, 0.0);
        }
        float bob = (float) Math.sin((ageInTicks + partialTicks) * 0.15f) * 0.012f;
        poseStack.translate(0.0, bob, 0.0);
        poseStack.mulPose(Axis.ZP.rotationDegrees(180f));
        poseStack.mulPose(Axis.YP.rotationDegrees(180f));
        poseStack.scale(SIZE, SIZE, SIZE * DEPTH);
        poseStack.translate(0.0, 0.5, 0.0);
        Minecraft.getInstance().getItemRenderer().renderStatic(stack, ItemDisplayContext.FIXED, light,
                OverlayTexture.NO_OVERLAY, poseStack, buffers, entity.level(), entity.getId());
        poseStack.popPose();
    }
}
