package dev.cogwit.mixin;

import dev.cogwit.compat.ControlifyExitFix;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Without Controlify installed this target never loads and the mixin does nothing. */
@Pseudo
@Mixin(targets = "dev.isxander.controlify.hid.ControllerHIDService", remap = false)
public abstract class ControlifyHidExitMixin {
    @Inject(method = "start", at = @At("HEAD"), require = 0)
    private void cogwit$noHidShutdownHook(CallbackInfo ci) {
        if (ControlifyExitFix.active()) ControlifyExitFix.disableAutoShutdown(this);
    }

    @Inject(method = "stop", at = @At("HEAD"), cancellable = true, require = 0)
    private void cogwit$skipHangingHidExit(CallbackInfo ci) {
        if (ControlifyExitFix.active()) ci.cancel();
    }
}
