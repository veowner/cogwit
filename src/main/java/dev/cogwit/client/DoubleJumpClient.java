package dev.cogwit.client;

import dev.cogwit.CogwitMod;
import dev.cogwit.horizon.HorizonClientState;
import dev.cogwit.network.DoubleJumpPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The Valkyrie Queen's double jump, client half: a fresh press of the jump key while airborne asks the server for the
 * jump, once per airtime. Whether the player may is the server's call; this only keeps from spamming it.
 */
@EventBusSubscriber(modid = CogwitMod.MODID, value = Dist.CLIENT)
public final class DoubleJumpClient {
    private static boolean wasJumping, asked;

    private DoubleJumpClient() {}

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) {
            wasJumping = false;
            asked = false;
            return;
        }
        boolean jumping = player.input.jumping && mc.screen == null;
        if (player.onGround() || player.isInWater() || player.isInLava() || player.getAbilities().flying
                || player.onClimbable() || player.isPassenger()) {
            asked = false;
        } else if (jumping && !wasJumping && !asked && !player.isFallFlying()
                && HorizonClientState.latest().has(dev.cogwit.horizon.Horizon.Feature.CORE)) {
            asked = true;
            Vec3 v = player.getDeltaMovement();
            PacketDistributor.sendToServer(new DoubleJumpPayload((float) v.x, (float) v.z));
        }
        wasJumping = jumping;
    }
}
