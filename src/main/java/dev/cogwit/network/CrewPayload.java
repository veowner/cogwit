package dev.cogwit.network;

import dev.cogwit.CogwitMod;
import dev.cogwit.crew.CrewService;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** The V key: Cogwit jumps into the fight or climbs back onto the shoulder. */
public record CrewPayload() implements CustomPacketPayload {
    public static final Type<CrewPayload> TYPE = new Type<>(CogwitMod.id("crew"));
    public static final StreamCodec<ByteBuf, CrewPayload> STREAM_CODEC = StreamCodec.unit(new CrewPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(CrewPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> CrewService.toggle(context.player()));
    }
}
