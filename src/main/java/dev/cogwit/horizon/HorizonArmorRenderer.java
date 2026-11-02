package dev.cogwit.horizon;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.cogwit.CogwitMod;
import dev.cogwit.cells.CellBank;
import dev.cogwit.cells.Cells;
import dev.cogwit.brain.Brain;
import dev.cogwit.brain.BrainState;
import dev.cogwit.brain.Growth;
import dev.cogwit.item.CogwitTalismanItem;
import net.minecraft.world.entity.player.Player;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.EquipmentSlot;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.renderer.GeoArmorRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

import java.util.List;

/** Renders the armor and lights each token-cell socket like a battery gauge for the cell inside it. */
public class HorizonArmorRenderer extends GeoArmorRenderer<HorizonArmorItem> {
    public HorizonArmorRenderer() {
        super(new HorizonArmorModel());
        addRenderLayer(new AutoGlowingGeoLayer<>(this));
    }

    /** Model socket numbers per armor slot, in the order cells are inserted. */
    static int[] socketsFor(EquipmentSlot slot) {
        return switch (slot) {
            case HEAD -> new int[] {0};
            case CHEST -> new int[] {1, 2, 3, 4};
            case LEGS -> new int[] {5, 6};
            case FEET -> new int[] {7};
            default -> new int[0];
        };
    }

    /** Cogwit's eye opens on the visor from his first level and shuts while he sleeps. */
    private void showCogwitEye(BakedGeoModel model) {
        BrainState brain = getCurrentEntity() instanceof Player player
                ? CogwitTalismanItem.worn(player).map(CogwitTalismanItem::brain).orElse(null)
                : null;
        boolean grown = brain != null && Growth.has(brain.xp(), Growth.Perk.EYE);
        long now = getCurrentEntity().level().getGameTime();
        // asleep, or a quick blink every five seconds
        boolean shut = grown && (Brain.isAsleep(brain, now) || (now + getCurrentEntity().getId() * 7L) % 100 < 3);
        model.getBone("cogwit_eye").ifPresent(bone -> bone.setHidden(!grown || shut));
        model.getBone("cogwit_eye_closed").ifPresent(bone -> bone.setHidden(!shut));
    }

    @Override
    public void preRender(PoseStack poseStack, HorizonArmorItem animatable, BakedGeoModel model,
            MultiBufferSource bufferSource, VertexConsumer buffer, boolean isReRender, float partialTick,
            int packedLight, int packedOverlay, int colour) {
        super.preRender(poseStack, animatable, model, bufferSource, buffer, isReRender, partialTick, packedLight,
                packedOverlay, colour);
        if (getCurrentSlot() == null || getCurrentStack() == null) {
            return;
        }
        if (getCurrentSlot() == EquipmentSlot.HEAD) {
            showCogwitEye(model);
        }
        List<Integer> cells = CellBank.cells(getCurrentStack());
        int[] sockets = socketsFor(getCurrentSlot());
        for (int i = 0; i < sockets.length; i++) {
            boolean inserted = i < cells.size();
            float fill = inserted ? (float) cells.get(i) / Cells.CAPACITY : 0f;
            model.getBone("cell_" + sockets[i]).ifPresent(bone -> bone.setHidden(!inserted));
            model.getBone("cell_" + sockets[i] + "_glow").ifPresent(bone -> {
                bone.setHidden(fill <= 0f);
                bone.setScaleY(Math.max(0.05f, fill));
            });
        }
    }
}
