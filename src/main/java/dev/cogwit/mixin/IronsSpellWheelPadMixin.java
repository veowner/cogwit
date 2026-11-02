package dev.cogwit.mixin;

import dev.cogwit.client.GamepadWheel;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Iron's spell wheel picks by mouse position. While it is open the right stick steers the cursor
 * around the centre, and the camera, which Controlify would otherwise turn with that stick, holds still.
 */
@Pseudo
@Mixin(targets = "io.redspace.ironsspellbooks.gui.overlays.SpellWheelOverlay", remap = false)
public abstract class IronsSpellWheelPadMixin {
    @Shadow public boolean active;
    @Unique private boolean cogwit$wasActive;
    @Unique private float cogwit$yaw, cogwit$pitch;

    @Inject(method = "render", at = @At("HEAD"), require = 0)
    private void cogwit$steerWithStick(GuiGraphics g, DeltaTracker delta, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (!active || player == null) {
            cogwit$wasActive = false;
            return;
        }
        if (!cogwit$wasActive) {
            cogwit$wasActive = true;
            cogwit$yaw = player.getYRot();
            cogwit$pitch = player.getXRot();
        }
        int w = g.guiWidth(), h = g.guiHeight();
        if (GamepadWheel.steer(w / 2.0, h / 2.0, h * 0.12, h * 0.3)) {
            player.setYRot(cogwit$yaw);
            player.setXRot(cogwit$pitch);
            player.yRotO = cogwit$yaw;
            player.xRotO = cogwit$pitch;
        }
    }
}
