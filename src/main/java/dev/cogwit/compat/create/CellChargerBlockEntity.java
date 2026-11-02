package dev.cogwit.compat.create;

import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import dev.cogwit.cells.Cells;
import dev.cogwit.cells.TokenCellItem;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/** Holds one token cell and charges it from rotational power. Server-side logic only ticks the charge. */
public class CellChargerBlockEntity extends KineticBlockEntity {
    /** How often a charging cell is pushed to clients, in ticks (the model does not depend on it). */
    private static final int SYNC_INTERVAL = 20;

    private ItemStack cell = ItemStack.EMPTY;
    private float fraction;
    private int syncCountdown;
    private int lastSignal;

    public CellChargerBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    public boolean hasCell() {
        return !cell.isEmpty();
    }

    public ItemStack getCell() {
        return cell;
    }

    public int charge() {
        return hasCell() ? TokenCellItem.charge(cell) : 0;
    }

    /** Takes one cell from the stack. Returns false when the slot is already taken. */
    public boolean insert(ItemStack stack) {
        if (hasCell() || !(stack.getItem() instanceof TokenCellItem)) {
            return false;
        }
        cell = stack.split(1);
        fraction = 0f;
        changed(true);
        return true;
    }

    /** Empties the slot and hands the cell, with its current charge, to the caller. */
    public ItemStack take() {
        ItemStack out = cell;
        cell = ItemStack.EMPTY;
        fraction = 0f;
        changed(true);
        return out;
    }

    @Override
    public void tick() {
        super.tick();
        if (level == null || level.isClientSide || !hasCell()) {
            return;
        }
        int before = charge();
        ChargerMath.Step step = ChargerMath.step(before, fraction, getSpeed());
        fraction = step.fraction();
        if (step.charge() != before) {
            TokenCellItem.withCharge(cell, step.charge());
            setChanged();
            if (--syncCountdown <= 0 || step.charge() >= Cells.CAPACITY) {
                syncCountdown = SYNC_INTERVAL;
                sendData();
            }
            updateComparator();
        }
    }

    private void changed(boolean sync) {
        setChanged();
        if (level == null || level.isClientSide) {
            return;
        }
        BlockState state = getBlockState();
        if (state.getValue(CellChargerBlock.HAS_CELL) != hasCell()) {
            level.setBlock(worldPosition, state.setValue(CellChargerBlock.HAS_CELL, hasCell()), Block.UPDATE_ALL);
        }
        updateComparator();
        if (sync) {
            sendData();
        }
    }

    private void updateComparator() {
        int signal = ChargerMath.signal(hasCell(), charge());
        if (signal != lastSignal) {
            lastSignal = signal;
            level.updateNeighbourForOutputSignal(worldPosition, getBlockState().getBlock());
        }
    }

    public int comparatorSignal() {
        return ChargerMath.signal(hasCell(), charge());
    }

    @Override
    public void destroy() {
        super.destroy();
        if (level != null && hasCell()) {
            Block.popResource(level, worldPosition, cell);
            cell = ItemStack.EMPTY;
        }
    }

    @Override
    protected void write(CompoundTag compound, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(compound, registries, clientPacket);
        compound.put("Cell", cell.saveOptional(registries));
        if (!clientPacket) {
            compound.putFloat("Fraction", fraction);
        }
    }

    @Override
    protected void read(CompoundTag compound, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(compound, registries, clientPacket);
        cell = ItemStack.parseOptional(registries, compound.getCompound("Cell"));
        fraction = compound.getFloat("Fraction");
        lastSignal = ChargerMath.signal(hasCell(), charge());
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        boolean added = super.addToGoggleTooltip(tooltip, isPlayerSneaking);
        if (hasCell()) {
            tooltip.add(Component.literal("    ").append(
                    Component.translatable("cogwit.cell.charge", charge(), Cells.CAPACITY)
                            .withStyle(ChatFormatting.GOLD)));
            added = true;
        }
        return added;
    }
}
