package dev.cogwit.network;

import dev.cogwit.CogwitMod;
import dev.cogwit.horizon.LandingClientState;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** A player in Horizon boots just landed hard: crouch their model for a moment. */
public record LandingPayload(int entityId, float strength) implements CustomPacketPayload {
    public static final Type<LandingPayload> TYPE = new Type<>(CogwitMod.id("landing"));
    public static final StreamCodec<ByteBuf, LandingPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, LandingPayload::entityId,
            ByteBufCodecs.FLOAT, LandingPayload::strength,
            LandingPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(LandingPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> LandingClientState.landed(payload.entityId(), payload.strength()));
    }
}
