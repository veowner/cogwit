package dev.cogwit.cells;

import dev.cogwit.CogwitComponents;
import dev.cogwit.CogwitMod;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.neoforged.neoforge.registries.DeferredItem;

/** Item registrations for token cells. Touching this class before ITEMS is registered adds them. */
public final class CellContent {
    public static final DeferredItem<TokenCellItem> TOKEN_CELL = CogwitMod.ITEMS.register("token_cell",
            () -> new TokenCellItem(new Item.Properties()
                    .stacksTo(1)
                    .rarity(Rarity.UNCOMMON)
                    .component(CogwitComponents.CELL_CHARGE.get(), 0)));

    private CellContent() {}

    public static void load() {
        // class initialisation does the work
    }
}
