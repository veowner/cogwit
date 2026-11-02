package dev.cogwit.crew;

import dev.cogwit.CogwitComponents;
import net.minecraft.core.NonNullList;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;

import java.util.ArrayList;
import java.util.List;

/** The gear each crew member keeps on the talisman: helmet, chestplate and weapon. */
public final class CrewGear {
    public static final EquipmentSlot[] SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.MAINHAND};
    private static final int SIZE = Crew.Kind.values().length * SLOTS.length;

    private CrewGear() {}

    public static List<ItemStack> of(ItemStack talisman, Crew.Kind kind) {
        NonNullList<ItemStack> all = all(talisman);
        List<ItemStack> mine = new ArrayList<>();
        for (int i = 0; i < SLOTS.length; i++) {
            mine.add(all.get(kind.ordinal() * SLOTS.length + i).copy());
        }
        return mine;
    }

    public static void set(ItemStack talisman, Crew.Kind kind, List<ItemStack> gear) {
        NonNullList<ItemStack> all = all(talisman);
        for (int i = 0; i < SLOTS.length; i++) {
            all.set(kind.ordinal() * SLOTS.length + i, i < gear.size() ? gear.get(i).copy() : ItemStack.EMPTY);
        }
        talisman.set(CogwitComponents.CREW_GEAR.get(), ItemContainerContents.fromItems(all));
    }

    private static NonNullList<ItemStack> all(ItemStack talisman) {
        NonNullList<ItemStack> all = NonNullList.withSize(SIZE, ItemStack.EMPTY);
        talisman.getOrDefault(CogwitComponents.CREW_GEAR.get(), ItemContainerContents.EMPTY).copyInto(all);
        return all;
    }
}
