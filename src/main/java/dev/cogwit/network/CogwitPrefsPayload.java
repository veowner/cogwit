package dev.cogwit.network;

import dev.cogwit.CogwitMod;
import dev.cogwit.ClientConfig;
import dev.cogwit.guide.GuideService;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** The player's client-side preferences the server needs to know: for now how chatty Cogwit should be. */
public record CogwitPrefsPayload(int chatter) implements CustomPacketPayload {
    public static final Type<CogwitPrefsPayload> TYPE = new Type<>(CogwitMod.id("prefs"));
    public static final StreamCodec<ByteBuf, CogwitPrefsPayload> STREAM_CODEC = ByteBufCodecs.VAR_INT
            .map(CogwitPrefsPayload::new, CogwitPrefsPayload::chatter);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** Client side: tell the server the current settings (on login and whenever they change). */
    public static void sendFromClient() {
        PacketDistributor.sendToServer(new CogwitPrefsPayload(ClientConfig.chatter().ordinal()));
    }

    public static void handle(CogwitPrefsPayload payload, IPayloadContext context) {
        ClientConfig.Chatter[] all = ClientConfig.Chatter.values();
        ClientConfig.Chatter chatter = all[Math.max(0, Math.min(all.length - 1, payload.chatter()))];
        context.enqueueWork(() -> GuideService.setChatter(context.player(), chatter));
    }
}
