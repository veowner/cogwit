package dev.cogwit.test;

import dev.cogwit.CogwitMod;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** In-game checks for how Cogwit glues other pack mods together. */
@GameTestHolder(CogwitMod.MODID)
@PrefixGameTestTemplate(false)
public final class CompatGameTests {
    private CompatGameTests() {}

    private static TagKey<Item> curios(String slot) {
        return TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath("curios", slot));
    }

    /** Cataclysm's two "rings" slots take any ring, not only Cataclysm's own. */
    @GameTest(template = "empty")
    public static void everyRingFitsCataclysmRingSlots(GameTestHelper helper) {
        var ring = curios("ring");
        var rings = curios("rings");
        // without the pack there may be no rings at all; then there is nothing to check
        for (var holder : BuiltInRegistries.ITEM.getTagOrEmpty(ring)) {
            helper.assertTrue(holder.is(rings), holder.getRegisteredName() + " should also be a curios:rings item");
        }
        helper.succeed();
    }
}
