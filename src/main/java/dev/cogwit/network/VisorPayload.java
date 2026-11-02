package dev.cogwit.network;

import dev.cogwit.CogwitMod;
import dev.cogwit.horizon.HorizonVisor;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** The H key or the wheel: raise or lower the Horizon helmet's faceplate. */
public record VisorPayload() implements CustomPacketPayload {
    public static final Type<VisorPayload> TYPE = new Type<>(CogwitMod.id("visor"));
    public static final StreamCodec<ByteBuf, VisorPayload> STREAM_CODEC = StreamCodec.unit(new VisorPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(VisorPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> HorizonVisor.toggle(context.player()));
    }
}
