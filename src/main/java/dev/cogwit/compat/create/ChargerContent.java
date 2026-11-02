package dev.cogwit.compat.create;

import com.simibubi.create.api.stress.BlockStressValues;
import dev.cogwit.CogwitMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

/**
 * Registrations for the Create integration. This class (and everything it touches) references Create,
 * so it must only be loaded through {@link CreateCompat#init} after checking that Create is present.
 */
public final class ChargerContent {
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(CogwitMod.MODID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(CogwitMod.MODID);
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, CogwitMod.MODID);

    public static final DeferredBlock<CellChargerBlock> CELL_CHARGER = BLOCKS.register("cell_charger",
            () -> new CellChargerBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_ORANGE)
                    .sound(SoundType.COPPER)
                    .strength(3.0f, 6.0f)));

    public static final DeferredItem<BlockItem> CELL_CHARGER_ITEM = ITEMS.registerSimpleBlockItem(CELL_CHARGER);

    public static final Supplier<BlockEntityType<CellChargerBlockEntity>> BLOCK_ENTITY = BLOCK_ENTITIES.register(
            "cell_charger",
            () -> BlockEntityType.Builder.of((pos, state) -> new CellChargerBlockEntity(ChargerContent.BLOCK_ENTITY.get(), pos, state),
                    CELL_CHARGER.get()).build(null));

    private ChargerContent() {}

    static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        modBus.addListener(ChargerContent::commonSetup);
        modBus.addListener(ChargerContent::addToCreativeTabs);
        if (FMLEnvironment.dist == Dist.CLIENT) {
            ChargerClient.register(modBus);
        }
    }

    private static void commonSetup(FMLCommonSetupEvent event) {
        // 4 SU per RPM: the charger is a pure consumer, so the whole stress cost is its impact.
        event.enqueueWork(() -> BlockStressValues.IMPACTS.register(CELL_CHARGER.get(),
                () -> ChargerMath.STRESS_PER_RPM));
    }

    private static void addToCreativeTabs(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.FUNCTIONAL_BLOCKS) {
            event.accept(CELL_CHARGER_ITEM.get());
        }
    }
}
