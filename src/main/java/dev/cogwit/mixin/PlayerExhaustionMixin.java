package dev.cogwit.mixin;

import dev.cogwit.exoframe.ExoframeContent;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Sprinting in the lower half of the copper exoframe costs less hunger: the frame carries part of the stride. */
@Mixin(Player.class)
public abstract class PlayerExhaustionMixin {
    @ModifyVariable(method = "causeFoodExhaustion", at = @At("HEAD"), argsOnly = true)
    private float cogwit$exoframeSprint(float exhaustion) {
        Player self = (Player) (Object) this;
        return self.isSprinting() && ExoframeContent.wears(self, ExoframeContent.LOWER.get())
                ? exhaustion * ExoframeContent.SPRINT_HUNGER : exhaustion;
    }
}
