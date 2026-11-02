package dev.cogwit.exoframe;

import dev.cogwit.CogwitMod;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.neoforged.neoforge.registries.DeferredItem;
import top.theillusivec4.curios.api.CuriosApi;

/**
 * Exoframes: frames worn over the armor in slots of their own on the accessories panel, an upper and a lower half.
 * They protect a little and mostly lend the body strength and speed. The copper exoframe is the first of them.
 */
public final class ExoframeContent {
    /** Curios slot ids; new exoframes and their modules go into these too. */
    public static final String UPPER_SLOT = "exo_upper", LOWER_SLOT = "exo_lower";
    /** Share of the hunger sprinting costs while the lower half is worn. */
    public static final float SPRINT_HUNGER = 0.6f;

    public static final DeferredItem<ExoframePartItem> UPPER = part("exoframe_upper", ExoframePartItem.Half.UPPER);
    public static final DeferredItem<ExoframePartItem> LOWER = part("exoframe_lower", ExoframePartItem.Half.LOWER);

    private ExoframeContent() {}

    /** Loads the class so the items are registered with the rest. */
    public static void load() {}

    private static DeferredItem<ExoframePartItem> part(String name, ExoframePartItem.Half half) {
        return CogwitMod.ITEMS.register(name, () -> new ExoframePartItem(half, new Item.Properties()
                .stacksTo(1)
                .rarity(Rarity.UNCOMMON)
                .durability(320)));
    }

    /** Whether the half is in its own slot; only its slot can hold it, so there is nowhere else to look. */
    public static boolean wears(LivingEntity entity, Item item) {
        String slot = item == UPPER.get() ? UPPER_SLOT : LOWER_SLOT;
        return CuriosApi.getCuriosInventory(entity).flatMap(inv -> inv.getStacksHandler(slot))
                .map(handler -> {
                    var stacks = handler.getStacks();
                    for (int i = 0; i < stacks.getSlots(); i++) {
                        if (stacks.getStackInSlot(i).is(item)) {
                            return true;
                        }
                    }
                    return false;
                }).orElse(false);
    }

    public static boolean wearsBoth(LivingEntity entity) {
        return wears(entity, UPPER.get()) && wears(entity, LOWER.get());
    }
}
