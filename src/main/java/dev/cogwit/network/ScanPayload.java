package dev.cogwit.network;

import dev.cogwit.CogwitMod;
import dev.cogwit.mind.MindService;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** The scan key: Cogwit looks through the walls for ores and chests. */
public record ScanPayload() implements CustomPacketPayload {
    public static final Type<ScanPayload> TYPE = new Type<>(CogwitMod.id("scan"));
    public static final StreamCodec<ByteBuf, ScanPayload> STREAM_CODEC = StreamCodec.unit(new ScanPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(ScanPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> MindService.scan(context.player()));
    }
}
