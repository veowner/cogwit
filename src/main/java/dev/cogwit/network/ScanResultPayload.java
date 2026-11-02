package dev.cogwit.network;

import dev.cogwit.CogwitMod;
import dev.cogwit.mind.Mind;
import dev.cogwit.mind.MindClientState;
import dev.cogwit.mind.MindContent;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.List;

/** What the scan found and where it started, for the owner's screen only. */
public record ScanResultPayload(BlockPos origin, List<MindContent.Find> ores, List<BlockPos> containers)
        implements CustomPacketPayload {
    public static final Type<ScanResultPayload> TYPE = new Type<>(CogwitMod.id("scan_result"));
    private static final StreamCodec<ByteBuf, MindContent.Find> FIND = StreamCodec.composite(
            BlockPos.STREAM_CODEC, MindContent.Find::pos,
            ByteBufCodecs.VAR_INT.map(i -> Mind.Ore.values()[Math.floorMod(i, Mind.Ore.values().length)], Enum::ordinal),
            MindContent.Find::ore,
            MindContent.Find::new);
    public static final StreamCodec<ByteBuf, ScanResultPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, ScanResultPayload::origin,
            FIND.apply(ByteBufCodecs.list()), ScanResultPayload::ores,
            BlockPos.STREAM_CODEC.apply(ByteBufCodecs.list()), ScanResultPayload::containers,
            ScanResultPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(ScanResultPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> MindClientState.showScan(payload.origin(), payload.ores(), payload.containers()));
    }
}
