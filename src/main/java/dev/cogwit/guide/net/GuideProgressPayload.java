package dev.cogwit.guide.net;

import dev.cogwit.CogwitMod;
import dev.cogwit.guide.GuideClientState;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Which milestones the player has finished, one bit per milestone ordinal. */
public record GuideProgressPayload(int doneMask) implements CustomPacketPayload {
    public static final Type<GuideProgressPayload> TYPE = new Type<>(CogwitMod.id("guide_progress"));
    public static final StreamCodec<ByteBuf, GuideProgressPayload> STREAM_CODEC =
            ByteBufCodecs.VAR_INT.map(GuideProgressPayload::new, GuideProgressPayload::doneMask);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(GuideProgressPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> GuideClientState.updateProgress(payload.doneMask()));
    }
}
