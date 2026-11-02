package dev.cogwit.network;

import dev.cogwit.CogwitMod;
import dev.cogwit.agent.AgentService;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.List;

/**
 * A change from the agent screen. {@code mode} is a mode key ("careful", "combat", "miner") or empty to keep it;
 * {@code overrides} are "action=permission" pairs (e.g. "eat=never") replacing all current overrides.
 * {@code openVault} asks the server to open Cogwit's vault instead.
 */
public record AgentSettingsPayload(String mode, List<String> overrides, boolean openVault)
        implements CustomPacketPayload {
    public static final Type<AgentSettingsPayload> TYPE = new Type<>(CogwitMod.id("agent_settings"));
    public static final StreamCodec<ByteBuf, AgentSettingsPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, AgentSettingsPayload::mode,
            ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), AgentSettingsPayload::overrides,
            ByteBufCodecs.BOOL, AgentSettingsPayload::openVault,
            AgentSettingsPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(AgentSettingsPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> AgentService.applySettings(context.player(), payload));
    }
}
