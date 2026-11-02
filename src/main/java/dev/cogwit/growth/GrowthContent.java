package dev.cogwit.growth;

import dev.cogwit.CogwitMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.neoforged.neoforge.registries.DeferredItem;

/** The Copper Apple and the tags Cogwit's levels look at. Touching this class before ITEMS is registered adds them. */
public final class GrowthContent {
    /** Tokens a Copper Apple gives Cogwit: the eight ingots it is made of. */
    public static final int COPPER_APPLE_TOKENS = 8 * 30;

    public static final DeferredItem<CopperAppleItem> COPPER_APPLE = CogwitMod.ITEMS.register("copper_apple",
            () -> new CopperAppleItem(new Item.Properties()
                    .rarity(Rarity.UNCOMMON)
                    .food(new FoodProperties.Builder()
                            .nutrition(4)
                            .saturationModifier(0.6f)
                            .alwaysEdible()
                            .effect(() -> new MobEffectInstance(MobEffects.REGENERATION, 160, 0), 1f)
                            .build())));

    /** Bosses that grow Cogwit when they die near him. */
    public static final TagKey<EntityType<?>> BOSSES = TagKey.create(Registries.ENTITY_TYPE, CogwitMod.id("bosses"));
    /** Drops worth outlining once Cogwit learns to highlight items. */
    public static final TagKey<Item> VALUABLE = TagKey.create(Registries.ITEM, CogwitMod.id("valuable"));

    private GrowthContent() {}

    public static void load() {
        // class initialisation does the work
    }
}
