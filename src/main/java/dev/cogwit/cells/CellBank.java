package dev.cogwit.cells;

import dev.cogwit.CogwitComponents;
import dev.cogwit.brain.Brain;
import dev.cogwit.brain.BrainState;
import dev.cogwit.horizon.HorizonArmorItem;
import dev.cogwit.item.CogwitTalismanItem;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** Token cells inside the armor a player is wearing, read and written as one list (head to feet). */
public final class CellBank {
    private static final EquipmentSlot[] ORDER = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS,
            EquipmentSlot.FEET};

    /** The worn Horizon pieces and all their cells concatenated in the same order. */
    public record Worn(List<ItemStack> pieces, List<Integer> all) {}

    private CellBank() {}

    public static int sockets(ItemStack armor) {
        if (!(armor.getItem() instanceof HorizonArmorItem item)) {
            return 0;
        }
        return switch (item.getType()) {
            case HELMET -> Cells.SOCKETS_HEAD;
            case CHESTPLATE -> Cells.SOCKETS_CHEST;
            case LEGGINGS -> Cells.SOCKETS_LEGS;
            case BOOTS -> Cells.SOCKETS_FEET;
            default -> 0;
        };
    }

    public static List<Integer> cells(ItemStack armor) {
        return armor.getOrDefault(CogwitComponents.SOCKETS.get(), List.of());
    }

    public static void setCells(ItemStack armor, List<Integer> cells) {
        armor.set(CogwitComponents.SOCKETS.get(), List.copyOf(cells));
    }

    public static Worn worn(Player player) {
        List<ItemStack> pieces = new ArrayList<>();
        List<Integer> all = new ArrayList<>();
        for (EquipmentSlot slot : ORDER) {
            ItemStack stack = player.getItemBySlot(slot);
            if (sockets(stack) > 0) {
                pieces.add(stack);
                all.addAll(cells(stack));
            }
        }
        return new Worn(pieces, all);
    }

    /** Writes a changed concatenated list back into the pieces it came from. */
    public static void write(Worn worn, List<Integer> all) {
        int from = 0;
        for (ItemStack piece : worn.pieces()) {
            int size = cells(piece).size();
            List<Integer> mine = all.subList(from, from + size);
            if (!mine.equals(cells(piece))) {
                setCells(piece, mine);
            }
            from += size;
        }
    }

    /** Refills the worn Cogwit's own tank from the cells so he never sleeps while cells still hold charge. */
    public static void topUp(Player player, ItemStack cogwit) {
        BrainState brain = CogwitTalismanItem.brain(cogwit);
        if (brain.tokens() >= Brain.MAX_TOKENS) {
            return;
        }
        Worn worn = worn(player);
        if (Cells.total(worn.all()) == 0) {
            return;
        }
        Cells.TopUp t = Cells.topUp(brain.tokens(), Brain.MAX_TOKENS, worn.all());
        CogwitTalismanItem.setBrain(cogwit, brain.withTokens(t.ownTokens()));
        write(worn, t.cells());
    }

    /** Charges worn cells; returns what didn't fit. */
    public static int charge(Player player, int amount) {
        Worn worn = worn(player);
        Cells.Charged c = Cells.charge(worn.all(), amount);
        write(worn, c.cells());
        return c.leftover();
    }

    /** Fills every worn cell; returns how many cells there are. */
    public static int fillAll(Player player) {
        Worn worn = worn(player);
        write(worn, Cells.lightning(worn.all()));
        return worn.all().size();
    }
}
