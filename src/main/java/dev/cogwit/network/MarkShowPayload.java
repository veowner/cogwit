package dev.cogwit.network;

import dev.cogwit.CogwitMod;
import dev.cogwit.mind.MindClientState;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** A mark to draw in the world: where, who placed it and what it points at. */
public record MarkShowPayload(BlockPos pos, String author, String label) implements CustomPacketPayload {
    public static final Type<MarkShowPayload> TYPE = new Type<>(CogwitMod.id("mark_show"));
    public static final StreamCodec<ByteBuf, MarkShowPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, MarkShowPayload::pos,
            ByteBufCodecs.STRING_UTF8, MarkShowPayload::author,
            ByteBufCodecs.STRING_UTF8, MarkShowPayload::label,
            MarkShowPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(MarkShowPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> MindClientState.addMark(payload.pos(), payload.author(), payload.label()));
    }
}
