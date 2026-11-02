package dev.cogwit.guide;

import dev.cogwit.CogwitMod;
import dev.cogwit.brain.Growth;
import dev.cogwit.trophy.Trophy;
import dev.cogwit.trophy.TrophyHunt;
import dev.cogwit.trophy.TrophyService;
import dev.cogwit.cells.CellBank;
import dev.cogwit.cells.CellContent;
import dev.cogwit.horizon.HorizonContent;
import dev.cogwit.item.CogwitTalismanItem;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.Optional;
import java.util.Set;

/** Looks through a player's inventory, armor and curios and boils it down to {@link GuideFacts}. */
public final class GuideFactsReader {
    private static final Set<Item> NETHERITE_ARMOR = Set.of(Items.NETHERITE_HELMET, Items.NETHERITE_CHESTPLATE,
            Items.NETHERITE_LEGGINGS, Items.NETHERITE_BOOTS);

    private GuideFactsReader() {}

    public static GuideFacts read(Player player) {
        Optional<ItemStack> worn = CogwitTalismanItem.worn(player);
        boolean hasCogwit = worn.isPresent();
        boolean netherite = false, heavyCore = false, singularity = false, template = false, horizon = false;
        boolean cell = false;
        int shards = 0;

        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.isEmpty()) {
                continue;
            }
            Item item = stack.getItem();
            hasCogwit |= item == CogwitMod.COGWIT.get();
            netherite |= NETHERITE_ARMOR.contains(item);
            heavyCore |= item == Items.HEAVY_CORE || item == Items.NETHERITE_INGOT;
            singularity |= item == HorizonContent.SINGULARITY_CORE.get();
            template |= item == HorizonContent.TEMPLATE.get();
            horizon |= isHorizonPiece(stack);
            cell |= item == CellContent.TOKEN_CELL.get();
            if (item == Items.ECHO_SHARD) {
                shards += stack.getCount();
            }
        }
        int tokens = worn.map(stack -> CogwitTalismanItem.brain(stack).tokens()).orElse(0);
        int level = worn.map(stack -> Growth.level(CogwitTalismanItem.brain(stack).xp())).orElse(0);
        java.util.Set<Trophy> owned = TrophyService.owned(player);
        return new GuideFacts(hasCogwit, worn.isPresent(), tokens, netherite, heavyCore, shards, singularity,
                template, horizon, HorizonContent.wearsFullSet(player), cell, CellBank.worn(player).all().size(),
                level, owned(owned, Milestone.ALL_TROPHIES), available(owned, Milestone.ALL_TROPHIES),
                advancementDone(player, TWILIGHT_ROOT), owned(owned, Milestone.TWILIGHT_BOSSES),
                available(owned, Milestone.TWILIGHT_BOSSES), owned(owned, Milestone.WORLD_BOSSES),
                available(owned, Milestone.WORLD_BOSSES));
    }

    /** Given on entering the Twilight Forest or lighting its portal. */
    private static final ResourceLocation TWILIGHT_ROOT = ResourceLocation.fromNamespaceAndPath("twilightforest", "root");

    private static int owned(Set<Trophy> owned, Milestone step) {
        return (int) owned.stream().filter(t -> t.implemented && step.hunts(t.mod())).count();
    }

    /** Trophies a step asks for: those whose mod is in the pack, plus any already owned. */
    private static int available(Set<Trophy> owned, Milestone step) {
        return (int) TrophyHunt.countAvailable(t -> step.hunts(t.mod()) && (TrophyHunt.loaded(t) || owned.contains(t)));
    }

    private static boolean advancementDone(Player player, ResourceLocation id) {
        if (!(player instanceof ServerPlayer sp)) {
            return false;
        }
        AdvancementHolder advancement = sp.server.getAdvancements().get(id);
        return advancement != null && sp.getAdvancements().getOrStartProgress(advancement).isDone();
    }

    /** How many of {@code item} the player carries or wears. */
    public static int count(Player player, Item item) {
        Inventory inventory = player.getInventory();
        int total = 0;
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.is(item)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    private static boolean isHorizonPiece(ItemStack stack) {
        return stack.is(HorizonContent.HELMET.get()) || stack.is(HorizonContent.CHESTPLATE.get())
                || stack.is(HorizonContent.LEGGINGS.get()) || stack.is(HorizonContent.BOOTS.get());
    }
}
