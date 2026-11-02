package dev.cogwit;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.cogwit.brain.BrainState;
import dev.cogwit.brain.Skill;
import dev.cogwit.agent.Agent;
import dev.cogwit.agent.AgentState;
import dev.cogwit.crew.CrewState;
import dev.cogwit.mind.MindState;
import dev.cogwit.ultra.UltraState;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.world.item.component.ItemContainerContents;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.List;
import java.util.Map;

/** Cogwit's memory lives on the talisman item, so it travels with it between players and worlds. */
public final class CogwitComponents {
    public static final DeferredRegister<DataComponentType<?>> COMPONENTS =
            DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, CogwitMod.MODID);

    public static final Codec<Skill> SKILL_CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.fieldOf("category").forGetter(Skill::category),
            Codec.INT.fieldOf("xp").forGetter(Skill::xp),
            Codec.LONG.fieldOf("last_used").forGetter(Skill::lastUsed),
            Codec.LONG.fieldOf("last_xp_gain").forGetter(Skill::lastXpGain),
            Codec.STRING.optionalFieldOf("merged", "").forGetter(Skill::merged),
            Codec.INT.optionalFieldOf("merged_xp", 0).forGetter(Skill::mergedXp)
    ).apply(i, Skill::new));

    public static final Codec<BrainState> BRAIN_CODEC = RecordCodecBuilder.create(i -> i.group(
            SKILL_CODEC.listOf().fieldOf("skills").forGetter(BrainState::skills),
            Codec.INT.fieldOf("tokens").forGetter(BrainState::tokens),
            Codec.LONG.optionalFieldOf("sleep_until", 0L).forGetter(BrainState::sleepUntil),
            Codec.LONG.optionalFieldOf("compact_ready_at", 0L).forGetter(BrainState::compactReadyAt),
            Codec.LONG.optionalFieldOf("think_ready_at", 0L).forGetter(BrainState::thinkReadyAt),
            Codec.LONG.optionalFieldOf("last_hurt", 0L).forGetter(BrainState::lastHurt),
            Codec.LONG.optionalFieldOf("last_refusal", 0L).forGetter(BrainState::lastRefusal),
            Codec.FLOAT.optionalFieldOf("xp", 0f).forGetter(BrainState::xp),
            Codec.LONG.optionalFieldOf("slept_at", 0L).forGetter(BrainState::sleptAt),
            Codec.LONG.optionalFieldOf("rescued_at", 0L).forGetter(BrainState::rescuedAt),
            Codec.LONG.optionalFieldOf("back_shield_ready_at", 0L).forGetter(BrainState::backShieldReadyAt)
    ).apply(i, BrainState::new));

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<BrainState>> BRAIN =
            COMPONENTS.register("brain", () -> DataComponentType.<BrainState>builder()
                    .persistent(BRAIN_CODEC)
                    .networkSynchronized(ByteBufCodecs.fromCodec(BRAIN_CODEC))
                    .build());

    /** Tokens stored in a token cell item. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> CELL_CHARGE =
            COMPONENTS.register("cell_charge", () -> DataComponentType.<Integer>builder()
                    .persistent(Codec.INT)
                    .networkSynchronized(ByteBufCodecs.VAR_INT)
                    .build());

    /** Whether the Horizon helmet's faceplate was raised by hand. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Boolean>> VISOR_OPEN =
            COMPONENTS.register("visor_open", () -> DataComponentType.<Boolean>builder()
                    .persistent(Codec.BOOL)
                    .networkSynchronized(ByteBufCodecs.BOOL)
                    .build());

    /** Charges of the token cells inserted into a piece of Horizon armor, one entry per cell. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<List<Integer>>> SOCKETS =
            COMPONENTS.register("sockets", () -> DataComponentType.<List<Integer>>builder()
                    .persistent(Codec.INT.listOf())
                    .networkSynchronized(ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list()))
                    .build());

    public static final Codec<CrewState> CREW_CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.BOOL.optionalFieldOf("deployed", false).forGetter(CrewState::deployed),
            Codec.LONG.optionalFieldOf("spark_down_until", 0L).forGetter(CrewState::sparkDownUntil),
            Codec.LONG.optionalFieldOf("rivet_down_until", 0L).forGetter(CrewState::rivetDownUntil),
            Codec.LONG.optionalFieldOf("anvil_down_until", 0L).forGetter(CrewState::anvilDownUntil),
            Codec.LONG.optionalFieldOf("grief_until", 0L).forGetter(CrewState::griefUntil)
    ).apply(i, CrewState::new));

    /** Whether Cogwit is off the shoulder, and how his brothers are doing. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<CrewState>> CREW =
            COMPONENTS.register("crew", () -> DataComponentType.<CrewState>builder()
                    .persistent(CREW_CODEC)
                    .networkSynchronized(ByteBufCodecs.fromCodec(CREW_CODEC))
                    .build());

    /** Helmet, chestplate and weapon of Cogwit and each brother, three slots per crew member in Kind order. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<ItemContainerContents>> CREW_GEAR =
            COMPONENTS.register("crew_gear", () -> DataComponentType.<ItemContainerContents>builder()
                    .persistent(ItemContainerContents.CODEC)
                    .networkSynchronized(ItemContainerContents.STREAM_CODEC)
                    .cacheEncoding()
                    .build());

    public static final Codec<UltraState> ULTRA_CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.BOOL.optionalFieldOf("active", false).forGetter(UltraState::active),
            Codec.LONG.optionalFieldOf("started_at", 0L).forGetter(UltraState::startedAt),
            Codec.LONG.optionalFieldOf("last_boss_seen", 0L).forGetter(UltraState::lastBossSeen),
            Codec.LONG.optionalFieldOf("hangover_until", 0L).forGetter(UltraState::hangoverUntil),
            Codec.BOOL.optionalFieldOf("crew_by_ultra", false).forGetter(UltraState::crewByUltra),
            Codec.FLOAT.optionalFieldOf("boss_health", 1f).forGetter(UltraState::bossHealth)
    ).apply(i, UltraState::new));

    /** Overdrive: rage mode against bosses and the hangover after it. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<UltraState>> ULTRA =
            COMPONENTS.register("ultra", () -> DataComponentType.<UltraState>builder()
                    .persistent(ULTRA_CODEC)
                    .networkSynchronized(ByteBufCodecs.fromCodec(ULTRA_CODEC))
                    .build());

    public static final Codec<MindState> MIND_CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.LONG.optionalFieldOf("happy_until", 0L).forGetter(MindState::happyUntil),
            Codec.LONG.optionalFieldOf("scared_until", 0L).forGetter(MindState::scaredUntil),
            Codec.LONG.optionalFieldOf("scan_ready_at", 0L).forGetter(MindState::scanReadyAt)
    ).apply(i, MindState::new));

    /** Cogwit's mood (shown on his face, also to the co-op friend) and the scan cooldown. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<MindState>> MIND =
            COMPONENTS.register("mind", () -> DataComponentType.<MindState>builder()
                    .persistent(MIND_CODEC)
                    .networkSynchronized(ByteBufCodecs.fromCodec(MIND_CODEC))
                    .build());

    public static final Codec<AgentState> AGENT_CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.optionalFieldOf("mode", "careful").forGetter(a -> a.mode().key()),
            Codec.unboundedMap(Codec.STRING, Codec.STRING).optionalFieldOf("overrides", Map.of())
                    .forGetter(a -> {
                        Map<String, String> out = new java.util.HashMap<>();
                        a.overrides().forEach((k, v) -> out.put(k.key(), v.name().toLowerCase(java.util.Locale.ROOT)));
                        return out;
                    })
    ).apply(i, (mode, overrides) -> {
        Map<Agent.Action, Agent.Permission> parsed = new java.util.EnumMap<>(Agent.Action.class);
        overrides.forEach((k, v) -> {
            try {
                parsed.put(Agent.Action.valueOf(k.toUpperCase(java.util.Locale.ROOT)),
                        Agent.Permission.valueOf(v.toUpperCase(java.util.Locale.ROOT)));
            } catch (IllegalArgumentException ignored) {
                // an action or permission from a newer version of the mod: drop it
            }
        });
        Agent.Mode m;
        try {
            m = Agent.Mode.valueOf(mode.toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            m = Agent.Mode.CAREFUL;
        }
        return new AgentState(m, parsed);
    }));

    /** Agent mode and permissions, set in the agent screen. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<AgentState>> AGENT =
            COMPONENTS.register("agent", () -> DataComponentType.<AgentState>builder()
                    .persistent(AGENT_CODEC)
                    .networkSynchronized(ByteBufCodecs.fromCodec(AGENT_CODEC))
                    .build());

    /** Cogwit's vault: nine slots where he keeps armor he took off you when there was no room. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<ItemContainerContents>> VAULT =
            COMPONENTS.register("vault", () -> DataComponentType.<ItemContainerContents>builder()
                    .persistent(ItemContainerContents.CODEC)
                    .networkSynchronized(ItemContainerContents.STREAM_CODEC)
                    .cacheEncoding()
                    .build());

    private CogwitComponents() {}
}
