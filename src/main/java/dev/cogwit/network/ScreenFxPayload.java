package dev.cogwit.network;

import dev.cogwit.CogwitMod;
import dev.cogwit.horizon.fx.ScreenFxState;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** A screen effect from an armor skill: a flash, a shake, a darkening or a field-of-view punch. */
public record ScreenFxPayload(int kind, float strength, int ticks) implements CustomPacketPayload {
    public static final int FLASH = 0, SHAKE = 1, DARKEN = 2, FOV_PUNCH = 3, FOV_PULL = 4, FLASH_VIOLET = 5;
    public static final Type<ScreenFxPayload> TYPE = new Type<>(CogwitMod.id("screen_fx"));
    public static final StreamCodec<ByteBuf, ScreenFxPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, ScreenFxPayload::kind,
            ByteBufCodecs.FLOAT, ScreenFxPayload::strength,
            ByteBufCodecs.VAR_INT, ScreenFxPayload::ticks,
            ScreenFxPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(ScreenFxPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> ScreenFxState.start(payload.kind(), payload.strength(), payload.ticks()));
    }
}
