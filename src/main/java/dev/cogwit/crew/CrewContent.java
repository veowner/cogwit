package dev.cogwit.crew;

import dev.cogwit.CogwitMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Registers Cogwit's walking form and his gear screen. */
public final class CrewContent {
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE,
            CogwitMod.MODID);
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, CogwitMod.MODID);

    public static final DeferredHolder<EntityType<?>, EntityType<CogwitCreature>> CREATURE = ENTITIES.register("creature",
            () -> EntityType.Builder.of(CogwitCreature::new, MobCategory.CREATURE)
                    .sized(0.6f, 0.55f)
                    .clientTrackingRange(10)
                    .noSave()
                    .build(CogwitMod.id("creature").toString()));

    public static final DeferredHolder<MenuType<?>, MenuType<CrewGearMenu>> GEAR_MENU = MENUS.register("crew_gear",
            () -> IMenuTypeExtension.create(CrewGearMenu::client));

    private CrewContent() {}

    public static void register(IEventBus modBus) {
        ENTITIES.register(modBus);
        MENUS.register(modBus);
        modBus.addListener(CrewContent::attributes);
    }

    private static void attributes(EntityAttributeCreationEvent event) {
        event.put(CREATURE.get(), CogwitCreature.createAttributes().build());
    }
}
