package dev.cogwit.network;

import dev.cogwit.CogwitMod;
import dev.cogwit.speech.SpeechClientState;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Cogwit asks permission: "Eat the bread?" — Y to allow, Shift+Y to always allow. */
public record AskPayload(int id, Component question) implements CustomPacketPayload {
    public static final Type<AskPayload> TYPE = new Type<>(CogwitMod.id("ask"));
    public static final StreamCodec<RegistryFriendlyByteBuf, AskPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, AskPayload::id,
            ComponentSerialization.TRUSTED_STREAM_CODEC, AskPayload::question,
            AskPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(AskPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> SpeechClientState.ask(payload.id(), payload.question()));
    }
}
