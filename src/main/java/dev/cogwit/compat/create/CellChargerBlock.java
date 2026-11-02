package dev.cogwit.compat.create;

import com.simibubi.create.content.kinetics.base.HorizontalAxisKineticBlock;
import com.simibubi.create.foundation.block.IBE;
import dev.cogwit.cells.TokenCellItem;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;

/** A casing with a shaft on its horizontal axis and a slot on top for one token cell. */
public class CellChargerBlock extends HorizontalAxisKineticBlock implements IBE<CellChargerBlockEntity> {
    public static final BooleanProperty HAS_CELL = BooleanProperty.create("has_cell");

    public CellChargerBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(HAS_CELL, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(HAS_CELL);
    }

    @Override
    public Class<CellChargerBlockEntity> getBlockEntityClass() {
        return CellChargerBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends CellChargerBlockEntity> getBlockEntityType() {
        return ChargerContent.BLOCK_ENTITY.get();
    }

    /** Right click with a token cell puts it in the slot. */
    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
            Player player, InteractionHand hand, BlockHitResult hit) {
        if (!(stack.getItem() instanceof TokenCellItem)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (state.getValue(HAS_CELL)) {
            return ItemInteractionResult.CONSUME;
        }
        if (level.isClientSide) {
            return ItemInteractionResult.SUCCESS;
        }
        return onBlockEntityUseItemOn(level, pos, be -> {
            if (!be.insert(stack)) {
                return ItemInteractionResult.CONSUME;
            }
            level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 0.7f, 1.2f);
            return ItemInteractionResult.SUCCESS;
        });
    }

    /** Right click with an empty hand takes the cell back out. */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
            BlockHitResult hit) {
        if (!state.getValue(HAS_CELL) || !player.getMainHandItem().isEmpty()) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        return onBlockEntityUse(level, pos, be -> {
            ItemStack cell = be.take();
            if (cell.isEmpty()) {
                return InteractionResult.PASS;
            }
            player.setItemInHand(InteractionHand.MAIN_HAND, cell);
            level.playSound(null, pos, SoundEvents.ITEM_PICKUP, SoundSource.BLOCKS, 0.4f, 1.1f);
            return InteractionResult.SUCCESS;
        });
    }

    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        return getBlockEntityOptional(level, pos).map(CellChargerBlockEntity::comparatorSignal).orElse(0);
    }
}
