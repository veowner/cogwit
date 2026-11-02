package dev.cogwit.network;

import dev.cogwit.CogwitMod;
import dev.cogwit.ultra.UltraClientState;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Tells the owner's screen that Overdrive just started (glitch flash) or ended. */
public record UltraPayload(boolean started) implements CustomPacketPayload {
    public static final Type<UltraPayload> TYPE = new Type<>(CogwitMod.id("ultra"));
    public static final StreamCodec<ByteBuf, UltraPayload> STREAM_CODEC =
            ByteBufCodecs.BOOL.map(UltraPayload::new, UltraPayload::started);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(UltraPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> UltraClientState.trigger(payload.started()));
    }
}
