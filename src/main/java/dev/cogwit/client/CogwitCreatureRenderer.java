package dev.cogwit.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.cogwit.CogwitMod;
import dev.cogwit.crew.CogwitCreature;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.DyedItemColor;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.BlockAndItemGeoLayer;

import java.util.Map;

/** Draws Cogwit and his brothers; worn armour tints the shell and cap, the weapon sits in the right claw. */
public class CogwitCreatureRenderer extends GeoEntityRenderer<CogwitCreature> {
    private static final Map<String, Integer> MATERIAL_COLORS = Map.of(
            "leather", 0xA0653A, "chainmail", 0x8C8C92, "iron", 0xD8D8D8, "gold", 0xF6D35A,
            "diamond", 0x5DE3D6, "turtle", 0x47A036, "netherite", 0x4A4448, "horizon", 0x34344A);
    private static final int UNKNOWN_ARMOR = 0xB4B4BC;

    public CogwitCreatureRenderer(EntityRendererProvider.Context context) {
        super(context, new Model());
        addRenderLayer(new BlockAndItemGeoLayer<>(this, (bone, creature) ->
                bone.getName().equals("item_r") ? creature.getMainHandItem() : null, (bone, creature) -> null) {
            @Override
            protected ItemDisplayContext getTransformTypeForStack(GeoBone bone, ItemStack stack, CogwitCreature creature) {
                return ItemDisplayContext.THIRD_PERSON_RIGHT_HAND;
            }

            @Override
            protected void renderStackForBone(PoseStack poseStack, GeoBone bone, ItemStack stack, CogwitCreature creature,
                    MultiBufferSource buffers, float partialTick, int light, int overlay) {
                poseStack.mulPose(Axis.XP.rotationDegrees(-90f));
                poseStack.scale(0.55f, 0.55f, 0.55f);
                super.renderStackForBone(poseStack, bone, stack, creature, buffers, partialTick, light, overlay);
            }
        });
        shadowRadius = 0.35f;
    }

    /** The colour an armour piece paints the shell or cap, or -1 when nothing is worn there. */
    public static int tint(ItemStack armor) {
        if (armor.isEmpty()) {
            return -1;
        }
        DyedItemColor dyed = armor.get(DataComponents.DYED_COLOR);
        if (dyed != null) {
            return dyed.rgb();
        }
        if (armor.getItem() instanceof ArmorItem item) {
            return item.getMaterial().unwrapKey()
                    .map(key -> MATERIAL_COLORS.getOrDefault(key.location().getPath(), UNKNOWN_ARMOR))
                    .orElse(UNKNOWN_ARMOR);
        }
        return UNKNOWN_ARMOR;
    }

    @Override
    public void preRender(PoseStack poseStack, CogwitCreature creature, BakedGeoModel model, MultiBufferSource buffers,
            VertexConsumer buffer, boolean isReRender, float partialTick, int light, int overlay, int colour) {
        super.preRender(poseStack, creature, model, buffers, buffer, isReRender, partialTick, light, overlay, colour);
        model.getBone("shell").ifPresent(b -> b.setHidden(creature.getItemBySlot(EquipmentSlot.CHEST).isEmpty()));
        model.getBone("helmet").ifPresent(b -> b.setHidden(creature.getItemBySlot(EquipmentSlot.HEAD).isEmpty()));
    }

    @Override
    public void renderRecursively(PoseStack poseStack, CogwitCreature creature, GeoBone bone, RenderType renderType,
            MultiBufferSource buffers, VertexConsumer buffer, boolean isReRender, float partialTick, int light,
            int overlay, int colour) {
        int tint = switch (bone.getName()) {
            case "shell" -> tint(creature.getItemBySlot(EquipmentSlot.CHEST));
            case "helmet" -> tint(creature.getItemBySlot(EquipmentSlot.HEAD));
            default -> -1;
        };
        if (creature.rage()) {
            // Overdrive: the whole crew turns violet and flickers
            int flicker = (creature.tickCount + creature.getId()) % 5 == 0 ? 0xFF7AD8 : 0xB070FF;
            tint = tint == -1 ? flicker : (multiply(tint >> 16, flicker >> 16) << 16
                    | multiply(tint >> 8, flicker >> 8) << 8 | multiply(tint, flicker));
        }
        if (tint != -1) {
            colour = (colour & 0xFF000000) | (multiply(colour >> 16, tint >> 16) << 16)
                    | (multiply(colour >> 8, tint >> 8) << 8) | multiply(colour, tint);
        }
        super.renderRecursively(poseStack, creature, bone, renderType, buffers, buffer, isReRender, partialTick, light,
                overlay, colour);
    }

    private static int multiply(int a, int b) {
        return (a & 0xFF) * (b & 0xFF) / 255;
    }

    private static final class Model extends GeoModel<CogwitCreature> {
        private static final ResourceLocation GEO = CogwitMod.id("geo/entity/cogwit_creature.geo.json");
        private static final ResourceLocation ANIMATIONS = CogwitMod.id("animations/entity/cogwit_creature.animation.json");

        @Override
        public ResourceLocation getModelResource(CogwitCreature creature) {
            return GEO;
        }

        @Override
        public ResourceLocation getTextureResource(CogwitCreature creature) {
            return CogwitMod.id("textures/entity/" + creature.kind().key() + ".png");
        }

        @Override
        public ResourceLocation getAnimationResource(CogwitCreature creature) {
            return ANIMATIONS;
        }
    }
}
