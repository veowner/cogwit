package dev.cogwit.crew;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/** Three slots on a crew member: helmet, chestplate and the weapon in his claw. */
public class CrewGearMenu extends AbstractContainerMenu {
    public static final int GEAR_X = 8, GEAR_Y = 18, INVENTORY_Y = 84;

    private final Container gear;
    private final CogwitCreature creature;
    /** Entity id of the creature, so the client screen can draw it. */
    public final int creatureId;

    public CrewGearMenu(int id, Inventory inventory, Container gear, CogwitCreature creature, int creatureId) {
        super(CrewContent.GEAR_MENU.get(), id);
        this.gear = gear;
        this.creature = creature;
        this.creatureId = creatureId;
        for (int i = 0; i < CrewGear.SLOTS.length; i++) {
            EquipmentSlot slot = CrewGear.SLOTS[i];
            addSlot(new Slot(gear, i, GEAR_X, GEAR_Y + i * 18) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return slot == EquipmentSlot.MAINHAND || stack.canEquip(slot, creature != null ? creature
                            : inventory.player);
                }

                @Override
                public int getMaxStackSize() {
                    return 1;
                }
            });
        }
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(inventory, col + row * 9 + 9, 8 + col * 18, INVENTORY_Y + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(inventory, col, 8 + col * 18, INVENTORY_Y + 58));
        }
    }

    public static CrewGearMenu client(int id, Inventory inventory, RegistryFriendlyByteBuf buf) {
        return new CrewGearMenu(id, inventory, new SimpleContainer(CrewGear.SLOTS.length), null, buf.readVarInt());
    }

    public static void open(Player player, CogwitCreature creature) {
        Component title = Component.translatable("cogwit.crew.gear_title", creature.getDisplayName());
        player.openMenu(new SimpleMenuProvider((id, inventory, p) ->
                new CrewGearMenu(id, inventory, new Wrapper(creature), creature, creature.getId()), title),
                buf -> buf.writeVarInt(creature.getId()));
    }

    @Override
    public boolean stillValid(Player player) {
        return creature == null || creature.isAlive() && player.distanceToSqr(creature) < 64;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        int gearSlots = CrewGear.SLOTS.length;
        if (index < gearSlots) {
            if (!moveItemStackTo(stack, gearSlots, slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else {
            boolean moved = false;
            for (int i = 0; i < gearSlots && !moved; i++) {
                Slot target = slots.get(i);
                if (!target.hasItem() && target.mayPlace(stack)) {
                    target.setByPlayer(stack.split(1));
                    moved = true;
                }
            }
            if (!moved) {
                return ItemStack.EMPTY;
            }
        }
        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return original;
    }

    /** The creature's own equipment, seen as a three-slot container. */
    private record Wrapper(CogwitCreature creature) implements Container {
        @Override
        public int getContainerSize() {
            return CrewGear.SLOTS.length;
        }

        @Override
        public boolean isEmpty() {
            for (EquipmentSlot slot : CrewGear.SLOTS) {
                if (!creature.getItemBySlot(slot).isEmpty()) {
                    return false;
                }
            }
            return true;
        }

        @Override
        public ItemStack getItem(int i) {
            return creature.getItemBySlot(CrewGear.SLOTS[i]);
        }

        @Override
        public ItemStack removeItem(int i, int count) {
            ItemStack stack = getItem(i);
            ItemStack taken = stack.split(count);
            creature.setItemSlot(CrewGear.SLOTS[i], stack);
            return taken;
        }

        @Override
        public ItemStack removeItemNoUpdate(int i) {
            ItemStack stack = getItem(i);
            creature.setItemSlot(CrewGear.SLOTS[i], ItemStack.EMPTY);
            return stack;
        }

        @Override
        public void setItem(int i, ItemStack stack) {
            creature.setItemSlot(CrewGear.SLOTS[i], stack);
        }

        @Override
        public void setChanged() {}

        @Override
        public boolean stillValid(Player player) {
            return creature.isAlive();
        }

        @Override
        public void clearContent() {
            for (EquipmentSlot slot : CrewGear.SLOTS) {
                creature.setItemSlot(slot, ItemStack.EMPTY);
            }
        }
    }
}
