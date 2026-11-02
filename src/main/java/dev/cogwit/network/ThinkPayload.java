package dev.cogwit.network;

import dev.cogwit.CogwitActions;
import dev.cogwit.CogwitMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Sent when the player presses the "think" key. */
public record ThinkPayload() implements CustomPacketPayload {
    public static final Type<ThinkPayload> TYPE = new Type<>(CogwitMod.id("think"));
    public static final StreamCodec<ByteBuf, ThinkPayload> STREAM_CODEC = StreamCodec.unit(new ThinkPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(ThinkPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> CogwitActions.think(context.player()));
    }
}
