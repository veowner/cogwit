package dev.cogwit.guide.net;

import dev.cogwit.CogwitMod;
import dev.cogwit.guide.GuideClientState;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Where the compass arrow should point; {@code present == false} hides it. */
public record GuideTargetPayload(boolean present, int x, int z, String labelKey) implements CustomPacketPayload {
    public static final Type<GuideTargetPayload> TYPE = new Type<>(CogwitMod.id("guide_target"));
    public static final StreamCodec<ByteBuf, GuideTargetPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, GuideTargetPayload::present,
            ByteBufCodecs.VAR_INT, GuideTargetPayload::x,
            ByteBufCodecs.VAR_INT, GuideTargetPayload::z,
            ByteBufCodecs.STRING_UTF8, GuideTargetPayload::labelKey,
            GuideTargetPayload::new);

    public static GuideTargetPayload none() {
        return new GuideTargetPayload(false, 0, 0, "");
    }

    public static GuideTargetPayload at(int x, int z, String labelKey) {
        return new GuideTargetPayload(true, x, z, labelKey);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(GuideTargetPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> GuideClientState.updateTarget(payload));
    }
}
