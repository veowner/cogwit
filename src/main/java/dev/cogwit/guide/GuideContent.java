package dev.cogwit.guide;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.cogwit.CogwitMod;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.function.Supplier;

/** Registrations for the guide: the per-player state attachment. */
public final class GuideContent {
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, CogwitMod.MODID);

    public static final Codec<GuideState> STATE_CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.BOOL.optionalFieldOf("enabled", true).forGetter(GuideState::enabled),
            Codec.LONG.optionalFieldOf("last_hint", -1L).forGetter(GuideState::lastHintTick),
            Codec.INT.optionalFieldOf("last_completed", -1).forGetter(GuideState::lastCompleted),
            Codec.INT.optionalFieldOf("reached", 0).forGetter(GuideState::reached)
    ).apply(i, GuideState::new));

    public static final Supplier<AttachmentType<GuideState>> STATE = ATTACHMENTS.register("guide",
            () -> AttachmentType.builder(() -> GuideState.DEFAULT).serialize(STATE_CODEC).copyOnDeath().build());

    private GuideContent() {}

    public static void register(IEventBus modBus) {
        ATTACHMENTS.register(modBus);
    }

    public static GuideState state(Player player) {
        return player.getData(STATE);
    }

    public static void setState(Player player, GuideState state) {
        player.setData(STATE, state);
    }
}
