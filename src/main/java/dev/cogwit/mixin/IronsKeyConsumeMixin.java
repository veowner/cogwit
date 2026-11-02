package dev.cogwit.mixin;

import net.minecraft.client.KeyMapping;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Iron's Spells only counts a press when setDown(true) runs, but Controlify presses
 * emulated keys by writing isDown and clickCount directly. Counting queued clicks as
 * a press makes cast, spell wheel and quick casts work from a gamepad; keyboard
 * presses still report exactly one press, as before.
 */
@Pseudo
@Mixin(targets = "io.redspace.ironsspellbooks.player.ExtendedKeyMapping", remap = false)
public abstract class IronsKeyConsumeMixin {
    @Shadow private boolean canBeConsumed;

    @Inject(method = "consume", at = @At("HEAD"), cancellable = true, require = 0)
    private void cogwit$countEmulatedClicks(CallbackInfoReturnable<Boolean> cir) {
        boolean clicked = false;
        while (((KeyMapping) (Object) this).consumeClick()) clicked = true;
        if (clicked) {
            canBeConsumed = false;
            cir.setReturnValue(true);
        }
    }
}
