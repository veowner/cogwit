package dev.cogwit.network;

import dev.cogwit.CogwitMod;
import dev.cogwit.agent.AgentService;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** The player's yes to Cogwit's question; {@code always} also stops him asking about it again. */
public record AnswerPayload(int id, boolean always) implements CustomPacketPayload {
    public static final Type<AnswerPayload> TYPE = new Type<>(CogwitMod.id("answer"));
    public static final StreamCodec<ByteBuf, AnswerPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, AnswerPayload::id,
            ByteBufCodecs.BOOL, AnswerPayload::always,
            AnswerPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(AnswerPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> AgentService.answer(context.player(), payload.id(), payload.always()));
    }
}
