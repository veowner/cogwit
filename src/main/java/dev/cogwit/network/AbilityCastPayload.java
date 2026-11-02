package dev.cogwit.network;

import dev.cogwit.CogwitMod;
import dev.cogwit.trophy.TrophyService;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** An ability picked on the wheel: "blink", "gravity", or a trophy key such as "warden". */
public record AbilityCastPayload(String id) implements CustomPacketPayload {
    public static final Type<AbilityCastPayload> TYPE = new Type<>(CogwitMod.id("ability_cast"));
    public static final StreamCodec<ByteBuf, AbilityCastPayload> STREAM_CODEC =
            ByteBufCodecs.STRING_UTF8.map(AbilityCastPayload::new, AbilityCastPayload::id);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(AbilityCastPayload p, IPayloadContext context) {
        context.enqueueWork(() -> TrophyService.cast(context.player(), p.id()));
    }
}
