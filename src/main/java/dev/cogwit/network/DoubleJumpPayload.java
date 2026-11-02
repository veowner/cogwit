package dev.cogwit.network;

import dev.cogwit.CogwitMod;
import dev.cogwit.trophy.TrophyPassives;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * The player pressed jump again in mid-air. Carries their horizontal speed so the jump keeps their momentum; the
 * server decides whether they may (Valkyrie Queen passive, full set, not yet used in this airtime).
 */
public record DoubleJumpPayload(float vx, float vz) implements CustomPacketPayload {
    public static final Type<DoubleJumpPayload> TYPE = new Type<>(CogwitMod.id("double_jump"));
    public static final StreamCodec<ByteBuf, DoubleJumpPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.FLOAT, DoubleJumpPayload::vx,
            ByteBufCodecs.FLOAT, DoubleJumpPayload::vz,
            DoubleJumpPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(DoubleJumpPayload p, IPayloadContext context) {
        context.enqueueWork(() -> TrophyPassives.doubleJump(context.player(), p.vx(), p.vz()));
    }
}
