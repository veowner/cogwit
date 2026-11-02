package dev.cogwit.cells;

import dev.cogwit.CogwitComponents;
import dev.cogwit.Speech;
import dev.cogwit.brain.Brain;
import dev.cogwit.item.CogwitTalismanItem;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/** A battery of 2500 tokens. Charged by clicking fuel onto it; inserted into Horizon armor. */
public class TokenCellItem extends Item {
    public TokenCellItem(Properties properties) {
        super(properties);
    }

    public static int charge(ItemStack cell) {
        return cell.getOrDefault(CogwitComponents.CELL_CHARGE.get(), 0);
    }

    public static ItemStack withCharge(ItemStack cell, int charge) {
        cell.set(CogwitComponents.CELL_CHARGE.get(), Math.max(0, Math.min(Cells.CAPACITY, charge)));
        return cell;
    }

    /** Tokens one piece of fuel puts into a cell: Cogwit's copper plus redstone and amethyst. */
    public static int fuelValue(ItemStack fuel) {
        int copper = CogwitTalismanItem.foodValue(fuel);
        if (copper > 0) return copper;
        if (fuel.is(Items.REDSTONE)) return Cells.REDSTONE_TOKENS;
        if (fuel.is(Items.REDSTONE_BLOCK)) return Cells.REDSTONE_TOKENS * 9;
        if (fuel.is(Items.AMETHYST_SHARD)) return Cells.AMETHYST_TOKENS;
        return 0;
    }

    /** Click fuel onto the cell: left click charges until full, right click adds one piece. */
    @Override
    public boolean overrideOtherStackedOnMe(ItemStack cell, ItemStack fuel, Slot slot, ClickAction action,
            Player player, SlotAccess carried) {
        int value = fuelValue(fuel);
        if (value == 0 || !slot.allowModification(player)) {
            return false;
        }
        int pieces = Brain.itemsToFill(charge(cell), value,
                action == ClickAction.PRIMARY ? fuel.getCount() : 1, Cells.CAPACITY);
        if (pieces == 0) {
            if (!player.level().isClientSide) {
                Speech.hint(player, Component.translatable("cogwit.cell.full"));
            }
            return true;
        }
        fuel.shrink(pieces);
        withCharge(cell, charge(cell) + pieces * value);
        player.playSound(SoundEvents.AMETHYST_BLOCK_CHIME, 0.6f, 1.4f);
        return true;
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return true;
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        return Math.round(13f * charge(stack) / Cells.CAPACITY);
    }

    @Override
    public int getBarColor(ItemStack stack) {
        return Mth.hsvToRgb(0.05f + 0.07f * charge(stack) / Cells.CAPACITY, 0.75f, 1f);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("cogwit.cell.charge", charge(stack), Cells.CAPACITY)
                .withStyle(ChatFormatting.GOLD));
        tooltip.add(Component.translatable("cogwit.cell.how_to_charge").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("cogwit.cell.how_to_insert").withStyle(ChatFormatting.DARK_GRAY));
    }
}
