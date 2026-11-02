package dev.cogwit;

import com.mojang.logging.LogUtils;
import dev.cogwit.cells.CellContent;
import dev.cogwit.compat.create.CreateCompat;
import dev.cogwit.crew.CrewContent;
import dev.cogwit.horizon.fx.FxContent;
import dev.cogwit.trophy.TrophyContent;
import dev.cogwit.mind.MindContent;
import dev.cogwit.growth.GrowthContent;
import dev.cogwit.guide.GuideContent;
import dev.cogwit.horizon.HorizonContent;
import dev.cogwit.item.CogwitTalismanItem;
import dev.cogwit.network.AbilityPayload;
import dev.cogwit.network.AgentSettingsPayload;
import dev.cogwit.network.AnswerPayload;
import dev.cogwit.network.AskPayload;
import dev.cogwit.network.CrewPayload;
import dev.cogwit.network.DoubleJumpPayload;
import dev.cogwit.network.MarkPayload;
import dev.cogwit.network.MarkShowPayload;
import dev.cogwit.network.ScanPayload;
import dev.cogwit.network.ScreenFxPayload;
import dev.cogwit.network.TrophySyncPayload;
import dev.cogwit.network.AbilityCastPayload;
import dev.cogwit.network.ScanResultPayload;
import dev.cogwit.network.HorizonSyncPayload;
import dev.cogwit.network.SpeechPayload;
import dev.cogwit.network.ThinkPayload;
import dev.cogwit.network.UltraPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.slf4j.Logger;
import top.theillusivec4.curios.api.CuriosApi;

@Mod(CogwitMod.MODID)
public class CogwitMod {
    public static final String MODID = "cogwit";
    public static final Logger LOGGER = LogUtils.getLogger();

    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MODID);

    public static final DeferredItem<CogwitTalismanItem> COGWIT = ITEMS.register("cogwit_talisman",
            () -> new CogwitTalismanItem(new Item.Properties()
                    .stacksTo(1)
                    .rarity(Rarity.RARE)
                    .fireResistant()
                    .component(CogwitComponents.BRAIN.get(), CogwitTalismanItem.STARTER_BRAIN)));

    public CogwitMod(IEventBus modBus, ModContainer container) {
        if (net.neoforged.fml.loading.FMLEnvironment.dist.isClient()) {
            OldNameMigration.carryOverClientFiles();
        }
        modBus.addListener(net.neoforged.bus.api.EventPriority.LOWEST, OldNameMigration::addAliases);
        container.registerConfig(ModConfig.Type.CLIENT, ClientConfig.SPEC);
        container.registerConfig(ModConfig.Type.SERVER, BalanceConfig.SPEC);
        CogwitComponents.COMPONENTS.register(modBus);
        HorizonContent.register(modBus);
        dev.cogwit.horizon.HorizonSounds.register(modBus);
        dev.cogwit.speech.CogwitVoice.register(modBus);
        dev.cogwit.exoframe.ExoframeContent.load();
        CellContent.load();
        GrowthContent.load();
        CrewContent.register(modBus);
        FxContent.register(modBus);
        TrophyContent.register(modBus);
        MindContent.register(modBus);
        CreateCompat.init(modBus);
        GuideContent.register(modBus);
        ITEMS.register(modBus);
        modBus.addListener(this::commonSetup);
        modBus.addListener(this::addToCreativeTabs);
        modBus.addListener(this::registerPayloads);
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MODID, path);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            CuriosApi.registerCurio(COGWIT.get(), COGWIT.get());
            CuriosApi.registerCurio(dev.cogwit.exoframe.ExoframeContent.UPPER.get(), dev.cogwit.exoframe.ExoframeContent.UPPER.get());
            CuriosApi.registerCurio(dev.cogwit.exoframe.ExoframeContent.LOWER.get(), dev.cogwit.exoframe.ExoframeContent.LOWER.get());
        });
    }

    private void addToCreativeTabs(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES || event.getTabKey() == CreativeModeTabs.COMBAT) {
            event.accept(COGWIT.get());
            event.accept(CellContent.TOKEN_CELL.get());
        }
        if (event.getTabKey() == CreativeModeTabs.COMBAT) {
            event.accept(dev.cogwit.exoframe.ExoframeContent.UPPER.get());
            event.accept(dev.cogwit.exoframe.ExoframeContent.LOWER.get());
            event.accept(HorizonContent.HELMET.get());
            event.accept(HorizonContent.CHESTPLATE.get());
            event.accept(HorizonContent.LEGGINGS.get());
            event.accept(HorizonContent.BOOTS.get());
        }
        if (event.getTabKey() == CreativeModeTabs.FOOD_AND_DRINKS) {
            event.accept(GrowthContent.COPPER_APPLE.get());
        }
        if (event.getTabKey() == CreativeModeTabs.INGREDIENTS) {
            event.accept(HorizonContent.SINGULARITY_CORE.get());
            event.accept(HorizonContent.TEMPLATE.get());
        }
    }

    private void registerPayloads(RegisterPayloadHandlersEvent event) {
        event.registrar("1")
                .playToServer(ThinkPayload.TYPE, ThinkPayload.STREAM_CODEC, ThinkPayload::handle)
                .playToServer(dev.cogwit.network.CogwitPrefsPayload.TYPE, dev.cogwit.network.CogwitPrefsPayload.STREAM_CODEC,
                        dev.cogwit.network.CogwitPrefsPayload::handle)
                .playToServer(CrewPayload.TYPE, CrewPayload.STREAM_CODEC, CrewPayload::handle)
                .playToServer(dev.cogwit.network.VisorPayload.TYPE, dev.cogwit.network.VisorPayload.STREAM_CODEC,
                        dev.cogwit.network.VisorPayload::handle)
                .playToServer(ScanPayload.TYPE, ScanPayload.STREAM_CODEC, ScanPayload::handle)
                .playToServer(AnswerPayload.TYPE, AnswerPayload.STREAM_CODEC, AnswerPayload::handle)
                .playToServer(AgentSettingsPayload.TYPE, AgentSettingsPayload.STREAM_CODEC, AgentSettingsPayload::handle)
                .playToClient(AskPayload.TYPE, AskPayload.STREAM_CODEC, AskPayload::handle)
                .playToServer(MarkPayload.TYPE, MarkPayload.STREAM_CODEC, MarkPayload::handle)
                .playToClient(ScanResultPayload.TYPE, ScanResultPayload.STREAM_CODEC, ScanResultPayload::handle)
                .playToClient(MarkShowPayload.TYPE, MarkShowPayload.STREAM_CODEC, MarkShowPayload::handle)
                .playToClient(dev.cogwit.network.LandingPayload.TYPE, dev.cogwit.network.LandingPayload.STREAM_CODEC,
                        dev.cogwit.network.LandingPayload::handle)
                .playToServer(AbilityPayload.TYPE, AbilityPayload.STREAM_CODEC, AbilityPayload::handle)
                .playToClient(HorizonSyncPayload.TYPE, HorizonSyncPayload.STREAM_CODEC, HorizonSyncPayload::handle)
                .playToClient(UltraPayload.TYPE, UltraPayload.STREAM_CODEC, UltraPayload::handle)
                .playToClient(ScreenFxPayload.TYPE, ScreenFxPayload.STREAM_CODEC, ScreenFxPayload::handle)
                .playToClient(TrophySyncPayload.TYPE, TrophySyncPayload.STREAM_CODEC, TrophySyncPayload::handle)
                .playToServer(AbilityCastPayload.TYPE, AbilityCastPayload.STREAM_CODEC, AbilityCastPayload::handle)
                .playToServer(DoubleJumpPayload.TYPE, DoubleJumpPayload.STREAM_CODEC, DoubleJumpPayload::handle)
                .playToClient(SpeechPayload.TYPE, SpeechPayload.STREAM_CODEC, SpeechPayload::handle);
    }
}
