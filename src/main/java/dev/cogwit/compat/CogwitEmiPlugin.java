package dev.cogwit.compat;

import dev.cogwit.CogwitMod;
import dev.cogwit.cells.CellContent;
import dev.cogwit.growth.GrowthContent;
import dev.cogwit.horizon.HorizonContent;
import dev.emi.emi.api.EmiEntrypoint;
import dev.emi.emi.api.EmiPlugin;
import dev.emi.emi.api.EmiRegistry;
import dev.emi.emi.api.recipe.EmiInfoRecipe;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.ItemLike;

import java.util.ArrayList;
import java.util.List;

/** "How does this work?" pages in EMI. Only loaded by EMI, so servers without it never touch this class. */
@EmiEntrypoint
public class CogwitEmiPlugin implements EmiPlugin {
    @Override
    public void register(EmiRegistry registry) {
        info(registry, "cogwit", 6, CogwitMod.COGWIT.get());
        info(registry, "horizon", 6, HorizonContent.HELMET.get(), HorizonContent.CHESTPLATE.get(),
                HorizonContent.LEGGINGS.get(), HorizonContent.BOOTS.get());
        info(registry, "singularity_core", 2, HorizonContent.SINGULARITY_CORE.get(), HorizonContent.TEMPLATE.get());
        info(registry, "token_cell", 4, CellContent.TOKEN_CELL.get());
        info(registry, "copper_apple", 2, GrowthContent.COPPER_APPLE.get());
        info(registry, "cogwit_levels", 3, CogwitMod.COGWIT.get());
        info(registry, "crew", 4, CogwitMod.COGWIT.get());
        info(registry, "overdrive", 3, CogwitMod.COGWIT.get());
        info(registry, "brain", 3, CogwitMod.COGWIT.get());
        info(registry, "agent", 3, CogwitMod.COGWIT.get());
    }

    private static void info(EmiRegistry registry, String page, int lines, ItemLike... items) {
        List<EmiIngredient> stacks = new ArrayList<>();
        for (ItemLike item : items) {
            stacks.add(EmiStack.of(item));
        }
        List<Component> text = new ArrayList<>();
        for (int i = 1; i <= lines; i++) {
            text.add(Component.translatable("cogwit.emi." + page + "." + i));
        }
        registry.addRecipe(new EmiInfoRecipe(stacks, text, CogwitMod.id("/info/" + page)));
    }
}
