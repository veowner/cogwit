package dev.cogwit.mixin;

import dev.cogwit.horizon.LandingClientState;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The Horizon boots' landing crouch: the body drops and leans in, one leg forward and one back, the right fist
 * down and the left arm thrown back. Armor layers and GeckoLib armor copy these parts, so the suit follows.
 */
@Mixin(HumanoidModel.class)
public abstract class HumanoidLandingMixin {
    /** How far everything above the legs drops at the deepest point, in model pixels. */
    private static final float DROP = 4f, LEAN = 0.5f;

    @Shadow @Final public ModelPart head;
    @Shadow @Final public ModelPart hat;
    @Shadow @Final public ModelPart body;
    @Shadow @Final public ModelPart rightArm;
    @Shadow @Final public ModelPart leftArm;
    @Shadow @Final public ModelPart rightLeg;
    @Shadow @Final public ModelPart leftLeg;

    @Inject(method = "setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V", at = @At("TAIL"))
    private void cogwit$landingCrouch(LivingEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks,
            float netHeadYaw, float headPitch, CallbackInfo ci) {
        float c = LandingClientState.crouch(entity.getId());
        if (c <= 0f) {
            return;
        }
        float drop = DROP * c, lean = LEAN * c;
        body.xRot += lean;
        body.y += drop;
        head.y += drop;
        hat.copyFrom(head);
        rightArm.y += drop;
        leftArm.y += drop;
        rightArm.xRot = lerp(c, rightArm.xRot, -0.35f);
        rightArm.zRot = lerp(c, rightArm.zRot, 0.12f);
        leftArm.xRot = lerp(c, leftArm.xRot, 0.95f);
        leftArm.zRot = lerp(c, leftArm.zRot, -0.4f);
        // the hips sit under the bottom of the leaning body
        float hipY = drop + 12f * (float) Math.cos(lean), hipZ = 12f * (float) Math.sin(lean);
        rightLeg.y = lerp(c, rightLeg.y, hipY);
        leftLeg.y = lerp(c, leftLeg.y, hipY);
        rightLeg.z = lerp(c, rightLeg.z, hipZ);
        leftLeg.z = lerp(c, leftLeg.z, hipZ);
        rightLeg.xRot = lerp(c, rightLeg.xRot, -0.75f);
        leftLeg.xRot = lerp(c, leftLeg.xRot, 0.6f);
        rightLeg.zRot = lerp(c, rightLeg.zRot, 0.08f);
        leftLeg.zRot = lerp(c, leftLeg.zRot, -0.08f);
    }

    private static float lerp(float t, float from, float to) {
        return from + (to - from) * t;
    }
}
