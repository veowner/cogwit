package dev.cogwit.mind;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.cogwit.CogwitMod;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.function.Supplier;

/** The player's death history (for Cogwit's comments) and what the scan looks for. */
public final class MindContent {
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, CogwitMod.MODID);

    /**
     * How the player died last and how many times so far; where it happened (for the way back to the loot) and which
     * kind of creature did it, with how many deaths in a row it has on its account.
     */
    public record Deaths(String kind, String killer, int count, String dimension, BlockPos pos, String killerType,
                         int streak) {
        public static final Deaths NONE = new Deaths("", "", 0, "", BlockPos.ZERO, "", 0);
        public static final Codec<Deaths> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.optionalFieldOf("kind", "").forGetter(Deaths::kind),
                Codec.STRING.optionalFieldOf("killer", "").forGetter(Deaths::killer),
                Codec.INT.optionalFieldOf("count", 0).forGetter(Deaths::count),
                Codec.STRING.optionalFieldOf("dimension", "").forGetter(Deaths::dimension),
                BlockPos.CODEC.optionalFieldOf("pos", BlockPos.ZERO).forGetter(Deaths::pos),
                Codec.STRING.optionalFieldOf("killer_type", "").forGetter(Deaths::killerType),
                Codec.INT.optionalFieldOf("streak", 0).forGetter(Deaths::streak)
        ).apply(i, Deaths::new));
    }

    public static final Supplier<AttachmentType<Deaths>> DEATHS = ATTACHMENTS.register("deaths",
            () -> AttachmentType.builder(() -> Deaths.NONE).serialize(Deaths.CODEC).copyOnDeath().build());

    /** Extra blocks the scan counts as ores, on top of every {@code #c:ores}; a modpack can add its own here. */
    public static final TagKey<Block> SCAN_ORES = TagKey.create(Registries.BLOCK, CogwitMod.id("scan_ores"));

    /** One scanned ore: where and what. */
    public record Find(BlockPos pos, Mind.Ore ore) {}

    /** What kind of ore a block is for the scan, or null when it isn't one. */
    public static Mind.Ore ore(BlockState state) {
        if (state.is(Tags.Blocks.ORES_NETHERITE_SCRAP) || state.is(Blocks.ANCIENT_DEBRIS)) return Mind.Ore.DEBRIS;
        if (state.is(Tags.Blocks.ORES_DIAMOND)) return Mind.Ore.DIAMOND;
        if (state.is(Tags.Blocks.ORES_EMERALD)) return Mind.Ore.EMERALD;
        if (state.is(Tags.Blocks.ORES_GOLD)) return Mind.Ore.GOLD;
        if (state.is(Tags.Blocks.ORES_LAPIS)) return Mind.Ore.LAPIS;
        if (state.is(Tags.Blocks.ORES_REDSTONE)) return Mind.Ore.REDSTONE;
        if (state.is(Tags.Blocks.ORES_IRON)) return Mind.Ore.IRON;
        if (state.is(Tags.Blocks.ORES_QUARTZ)) return Mind.Ore.QUARTZ;
        if (state.is(Tags.Blocks.ORES_COPPER)) return Mind.Ore.COPPER;
        if (state.is(Tags.Blocks.ORES_COAL)) return Mind.Ore.COAL;
        return state.is(Tags.Blocks.ORES) || state.is(SCAN_ORES) ? Mind.Ore.MODDED : null;
    }

    public static boolean scanned(BlockState state) {
        return state.is(Tags.Blocks.ORES) || state.is(SCAN_ORES) || state.is(SCAN_CONTAINERS);
    }
    /** Chests and other loot containers the scan outlines in violet. */
    public static final TagKey<Block> SCAN_CONTAINERS = TagKey.create(Registries.BLOCK, CogwitMod.id("scan_containers"));

    private MindContent() {}

    public static void register(IEventBus modBus) {
        ATTACHMENTS.register(modBus);
    }
}
