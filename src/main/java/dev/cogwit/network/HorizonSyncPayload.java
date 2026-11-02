package dev.cogwit.network;

import dev.cogwit.CogwitMod;
import dev.cogwit.horizon.HorizonClientState;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** What the HUD needs to know about the local player's armor; cooldowns are sent as ticks remaining. */
public record HorizonSyncPayload(float mass, int blinkLeft, int wellLeft, int voidRescueLeft, int pieces)
        implements CustomPacketPayload {
    public static final Type<HorizonSyncPayload> TYPE = new Type<>(CogwitMod.id("horizon_sync"));
    public static final StreamCodec<ByteBuf, HorizonSyncPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.FLOAT, HorizonSyncPayload::mass,
            ByteBufCodecs.VAR_INT, HorizonSyncPayload::blinkLeft,
            ByteBufCodecs.VAR_INT, HorizonSyncPayload::wellLeft,
            ByteBufCodecs.VAR_INT, HorizonSyncPayload::voidRescueLeft,
            ByteBufCodecs.VAR_INT, HorizonSyncPayload::pieces,
            HorizonSyncPayload::new);

    public boolean fullSet() {
        return pieces == dev.cogwit.horizon.Horizon.FULL;
    }

    public boolean has(dev.cogwit.horizon.Horizon.Feature feature) {
        return dev.cogwit.horizon.Horizon.has(pieces, feature);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(HorizonSyncPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> HorizonClientState.update(payload));
    }
}
