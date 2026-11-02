package dev.cogwit.trophy;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.cogwit.CogwitMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/** Where trophies are kept on the player, and the entities their actives spawn. */
public final class TrophyContent {
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, CogwitMod.MODID);
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE,
            CogwitMod.MODID);
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(CogwitMod.MODID);

    /** Trophies a player has absorbed and when each active is ready again (game ticks). */
    public record Data(List<String> owned, Map<String, Long> readyAt) {
        public static final Data NONE = new Data(List.of(), Map.of());
        public static final Codec<Data> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.listOf().optionalFieldOf("owned", List.of()).forGetter(Data::owned),
                Codec.unboundedMap(Codec.STRING, Codec.LONG).optionalFieldOf("ready_at", Map.of())
                        .forGetter(Data::readyAt)
        ).apply(i, Data::new));
    }

    public static final Supplier<AttachmentType<Data>> DATA = ATTACHMENTS.register("trophies",
            () -> AttachmentType.builder(() -> Data.NONE).serialize(Data.CODEC).copyOnDeath().build());

    public static final DeferredHolder<EntityType<?>, EntityType<DragonBreathCloud>> DRAGON_BREATH =
            ENTITIES.register("dragon_breath", () -> EntityType.Builder.<DragonBreathCloud>of(DragonBreathCloud::new,
                    MobCategory.MISC).sized(6f, 1f).clientTrackingRange(8).noSave()
                    .build(CogwitMod.id("dragon_breath").toString()));

    public static final DeferredHolder<EntityType<?>, EntityType<TrophySkull>> SKULL =
            ENTITIES.register("trophy_skull", () -> EntityType.Builder.<TrophySkull>of(TrophySkull::new,
                    MobCategory.MISC).sized(0.3125f, 0.3125f).clientTrackingRange(4).updateInterval(10).noSave()
                    .build(CogwitMod.id("trophy_skull").toString()));

    public static final DeferredHolder<EntityType<?>, EntityType<SandstormEntity>> SANDSTORM =
            ENTITIES.register("sandstorm", () -> EntityType.Builder.<SandstormEntity>of(SandstormEntity::new,
                    MobCategory.MISC).sized(1f, 1f).clientTrackingRange(8).noSave()
                    .build(CogwitMod.id("sandstorm").toString()));

    /** The Sculptor's catching platform. It has no item and drops nothing; it only ever exists for a few seconds. */
    public static final DeferredBlock<StonePlatformBlock> STONE_PLATFORM = BLOCKS.register("sculptor_platform",
            () -> new StonePlatformBlock(BlockBehaviour.Properties.of().mapColor(MapColor.STONE).strength(0.5f)
                    .sound(SoundType.STONE).noLootTable().pushReaction(PushReaction.DESTROY)));

    private TrophyContent() {}

    public static void register(IEventBus modBus) {
        ATTACHMENTS.register(modBus);
        ENTITIES.register(modBus);
        BLOCKS.register(modBus);
    }
}
