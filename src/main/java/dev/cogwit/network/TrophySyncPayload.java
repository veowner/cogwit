package dev.cogwit.network;

import dev.cogwit.CogwitMod;
import dev.cogwit.trophy.TrophyClientState;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.List;

/** A player's trophies and, for the player themself, when each active is ready again. */
public record TrophySyncPayload(int entityId, List<String> trophies, List<Long> readyAt, boolean self)
        implements CustomPacketPayload {
    public static final Type<TrophySyncPayload> TYPE = new Type<>(CogwitMod.id("trophy_sync"));
    public static final StreamCodec<ByteBuf, TrophySyncPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, TrophySyncPayload::entityId,
            ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), TrophySyncPayload::trophies,
            ByteBufCodecs.VAR_LONG.apply(ByteBufCodecs.list()), TrophySyncPayload::readyAt,
            ByteBufCodecs.BOOL, TrophySyncPayload::self,
            TrophySyncPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(TrophySyncPayload p, IPayloadContext context) {
        context.enqueueWork(() -> TrophyClientState.update(p.entityId(), p.trophies(), p.readyAt(), p.self()));
    }
}
