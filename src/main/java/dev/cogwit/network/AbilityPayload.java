package dev.cogwit.network;

import dev.cogwit.CogwitMod;
import dev.cogwit.horizon.HorizonActions;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Sent when the player presses an armor ability key: 0 = blink, 1 = gravity (well / collapse). */
public record AbilityPayload(int key) implements CustomPacketPayload {
    public static final int BLINK = 0;
    public static final int GRAVITY = 1;

    public static final Type<AbilityPayload> TYPE = new Type<>(CogwitMod.id("ability"));
    public static final StreamCodec<ByteBuf, AbilityPayload> STREAM_CODEC =
            ByteBufCodecs.VAR_INT.map(AbilityPayload::new, AbilityPayload::key);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(AbilityPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            switch (payload.key()) {
                // without Horizon leggings the copper exoframe answers the dash key with its steam shove
                case BLINK -> {
                    if (!dev.cogwit.horizon.HorizonContent.has(context.player(), dev.cogwit.horizon.Horizon.Feature.THRUSTERS)
                            && dev.cogwit.exoframe.ExoframeContent.wearsBoth(context.player())) {
                        dev.cogwit.exoframe.ExoframeActions.shove(context.player());
                    } else {
                        HorizonActions.blink(context.player());
                    }
                }
                case GRAVITY -> HorizonActions.gravity(context.player());
                default -> { }
            }
        });
    }
}
