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

/** Something Cogwit says to his owner, shown in his speech bubble instead of the chat; kind 0 say, 1 hint, 2 agent action. */
public record SpeechPayload(Component text, int kind) implements CustomPacketPayload {
    public static final Type<SpeechPayload> TYPE = new Type<>(CogwitMod.id("speech"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SpeechPayload> STREAM_CODEC = StreamCodec.composite(
            ComponentSerialization.TRUSTED_STREAM_CODEC, SpeechPayload::text,
            ByteBufCodecs.VAR_INT, SpeechPayload::kind,
            SpeechPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SpeechPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (payload.kind() == 2) {
                SpeechClientState.action(payload.text());
            } else if (payload.kind() == 3) {
                SpeechClientState.think(payload.text());
            } else {
                SpeechClientState.add(payload.text(), payload.kind() == 1);
            }
        });
    }
}
