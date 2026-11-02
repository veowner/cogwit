package dev.cogwit.horizon.fx;

import dev.cogwit.CogwitMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** The armor skills that live in the world for a moment: the gravity well and the collapse. */
public final class FxContent {
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE,
            CogwitMod.MODID);

    public static final DeferredHolder<EntityType<?>, EntityType<GravityWellEntity>> GRAVITY_WELL =
            ENTITIES.register("gravity_well", () -> EntityType.Builder.<GravityWellEntity>of(GravityWellEntity::new,
                            MobCategory.MISC).sized(1f, 1f).clientTrackingRange(8).updateInterval(1).noSave()
                    .build(CogwitMod.id("gravity_well").toString()));

    public static final DeferredHolder<EntityType<?>, EntityType<CollapseEntity>> COLLAPSE =
            ENTITIES.register("collapse", () -> EntityType.Builder.<CollapseEntity>of(CollapseEntity::new,
                            MobCategory.MISC).sized(1f, 2f).clientTrackingRange(8).updateInterval(1).noSave()
                    .build(CogwitMod.id("collapse").toString()));

    private FxContent() {}

    public static void register(IEventBus modBus) {
        ENTITIES.register(modBus);
    }
}
