package dev.cogwit.network;

import dev.cogwit.CogwitMod;
import dev.cogwit.mind.MindService;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** The mark key: the player points at a mob (entityId >= 0) or a block, Cogwit shows it to everyone nearby. */
public record MarkPayload(int entityId, BlockPos pos) implements CustomPacketPayload {
    public static final Type<MarkPayload> TYPE = new Type<>(CogwitMod.id("mark"));
    public static final StreamCodec<ByteBuf, MarkPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, MarkPayload::entityId,
            BlockPos.STREAM_CODEC, MarkPayload::pos,
            MarkPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(MarkPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> MindService.mark(context.player(), payload.entityId(), payload.pos()));
    }
}
