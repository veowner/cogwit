package dev.cogwit.test;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.api.stress.BlockStressValues;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlock;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity;
import dev.cogwit.CogwitMod;
import dev.cogwit.cells.CellContent;
import dev.cogwit.cells.Cells;
import dev.cogwit.cells.TokenCellItem;
import dev.cogwit.compat.create.CellChargerBlock;
import dev.cogwit.compat.create.CellChargerBlockEntity;
import dev.cogwit.compat.create.ChargerContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The cell charger scenarios. Everything here references Create, so only {@link ChargerGameTests} may
 * call into this class, and only after checking that Create is loaded.
 */
final class ChargerScenarios {
    private static final BlockPos CHARGER = new BlockPos(3, 1, 2);
    private static final BlockPos MOTOR = new BlockPos(2, 1, 2);
    /** Ticks the motor needs to spin the charger up before we start measuring. */
    private static final int SPIN_UP = 10;
    private static final int MEASURE = 40;

    private ChargerScenarios() {}

    private static Player player(GameTestHelper helper) {
        Player player = TestPlayers.mock(helper);
        Vec3 spot = helper.absoluteVec(new Vec3(1.5, 1, 4.5));
        player.moveTo(spot.x, spot.y, spot.z);
        helper.getLevel().addFreshEntity(player);
        return player;
    }

    private static ItemStack cell(int charge) {
        return TokenCellItem.withCharge(new ItemStack(CellContent.TOKEN_CELL.get()), charge);
    }

    private static BlockState charger() {
        return ChargerContent.CELL_CHARGER.get().defaultBlockState()
                .setValue(CellChargerBlock.HORIZONTAL_AXIS, Direction.Axis.X);
    }

    private static BlockHitResult hit(GameTestHelper helper, BlockPos pos) {
        return new BlockHitResult(Vec3.atCenterOf(helper.absolutePos(pos)).add(0, 0.5, 0), Direction.UP,
                helper.absolutePos(pos), false);
    }

    /** What the server does for a right click: item first, then the empty-hand interaction. */
    private static InteractionResult rightClick(GameTestHelper helper, Player player, BlockPos pos) {
        BlockState state = helper.getBlockState(pos);
        BlockHitResult hit = hit(helper, pos);
        ItemStack held = player.getItemInHand(InteractionHand.MAIN_HAND);
        if (!held.isEmpty()) {
            ItemInteractionResult item = state.useItemOn(held, helper.getLevel(), player, InteractionHand.MAIN_HAND, hit);
            if (item.consumesAction()) {
                return item.result();
            }
            if (item != ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION) {
                return InteractionResult.PASS;
            }
        }
        return state.useWithoutItem(helper.getLevel(), player, hit);
    }

    private static CellChargerBlockEntity chargerEntity(GameTestHelper helper) {
        return (CellChargerBlockEntity) helper.getBlockEntity(CHARGER);
    }

    /** A creative motor right next to the charger, pointing at it, set to {@code rpm}. */
    private static void attachMotor(GameTestHelper helper, int rpm) {
        helper.setBlock(MOTOR, AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(CreativeMotorBlock.FACING, Direction.EAST));
        CreativeMotorBlockEntity motor = (CreativeMotorBlockEntity) helper.getBlockEntity(MOTOR);
        motor.generatedSpeed.value = rpm;
    }

    static void chargerIsRegisteredWithStressImpact(GameTestHelper helper) {
        helper.setBlock(CHARGER, charger());
        helper.assertTrue(helper.getBlockEntity(CHARGER) instanceof CellChargerBlockEntity, "charger needs its block entity");
        double impact = BlockStressValues.getImpact(ChargerContent.CELL_CHARGER.get());
        helper.assertTrue(impact == 4.0, "stress impact should be 4 SU per RPM, got " + impact);
        helper.assertTrue(chargerEntity(helper).calculateStressApplied() == 4f, "block entity should apply 4 SU per RPM");
        helper.assertTrue(helper.getLevel().getRecipeManager().byKey(CogwitMod.id("cell_charger")).isPresent(),
                "the charger recipe should load");
        helper.succeed();
    }

    static void chargerTakesOneCellAndGivesItBack(GameTestHelper helper) {
        Player player = player(helper);
        helper.setBlock(CHARGER, charger());
        player.setItemInHand(InteractionHand.MAIN_HAND, cell(123));
        helper.assertTrue(rightClick(helper, player, CHARGER).consumesAction(), "inserting a cell should be consumed");
        helper.assertTrue(chargerEntity(helper).hasCell(), "the charger should hold the cell");
        helper.assertTrue(chargerEntity(helper).charge() == 123, "held charge should be 123");
        helper.assertTrue(player.getMainHandItem().isEmpty(), "the cell should leave the hand");
        helper.assertTrue(helper.getBlockState(CHARGER).getValue(CellChargerBlock.HAS_CELL), "the model should show the cell");
        helper.assertTrue(helper.getBlockState(CHARGER).getValue(CellChargerBlock.HORIZONTAL_AXIS) == Direction.Axis.X,
                "the axis must survive the state change");

        ItemStack second = cell(7);
        player.setItemInHand(InteractionHand.MAIN_HAND, second);
        rightClick(helper, player, CHARGER);
        helper.assertTrue(second.getCount() == 1 && TokenCellItem.charge(second) == 7, "a second cell must stay in the hand");
        helper.assertTrue(chargerEntity(helper).charge() == 123, "the first cell stays put");

        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STICK));
        rightClick(helper, player, CHARGER);
        helper.assertTrue(chargerEntity(helper).hasCell(), "a stick must not take the cell out");

        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        helper.assertTrue(rightClick(helper, player, CHARGER).consumesAction(), "taking the cell out should be consumed");
        ItemStack back = player.getMainHandItem();
        helper.assertTrue(back.getItem() instanceof TokenCellItem, "an empty hand should receive the cell, got " + back);
        helper.assertTrue(TokenCellItem.charge(back) == 123, "the cell keeps its charge, got " + TokenCellItem.charge(back));
        helper.assertTrue(!chargerEntity(helper).hasCell(), "the slot should be empty again");
        helper.assertTrue(!helper.getBlockState(CHARGER).getValue(CellChargerBlock.HAS_CELL), "the model should show an empty slot");
        helper.succeed();
    }

    static void spinningMotorChargesTheCell(GameTestHelper helper) {
        Player player = player(helper);
        helper.setBlock(CHARGER, charger());
        attachMotor(helper, 256);
        player.setItemInHand(InteractionHand.MAIN_HAND, cell(100));
        rightClick(helper, player, CHARGER);

        int[] atStart = new int[1];
        helper.runAfterDelay(SPIN_UP, () -> {
            CellChargerBlockEntity be = chargerEntity(helper);
            helper.assertTrue(Math.abs(be.getSpeed()) == 256f, "the charger should spin at 256 RPM, got " + be.getSpeed());
            atStart[0] = be.charge();
        });
        helper.runAfterDelay(SPIN_UP + MEASURE, () -> {
            CellChargerBlockEntity be = chargerEntity(helper);
            int gained = be.charge() - atStart[0];
            // 256 RPM = 128 tokens per second = 6.4 per tick, so 40 ticks add 256
            helper.assertTrue(Math.abs(gained - 256) <= 7, "expected about 256 tokens in 40 ticks, got " + gained);
            int charge = be.charge();

            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            helper.assertTrue(rightClick(helper, player, CHARGER).consumesAction(), "taking the cell out should work while spinning");
            ItemStack back = player.getMainHandItem();
            helper.assertTrue(back.getItem() instanceof TokenCellItem, "a token cell should come back, got " + back);
            helper.assertTrue(TokenCellItem.charge(back) == charge,
                    "the cell should keep the charge it gained: " + TokenCellItem.charge(back) + " vs " + charge);
            helper.succeed();
        });
    }

    static void chargingStopsAtCapacity(GameTestHelper helper) {
        Player player = player(helper);
        helper.setBlock(CHARGER, charger());
        attachMotor(helper, 256);
        player.setItemInHand(InteractionHand.MAIN_HAND, cell(Cells.CAPACITY - 5));
        rightClick(helper, player, CHARGER);

        helper.runAfterDelay(SPIN_UP + MEASURE, () -> {
            CellChargerBlockEntity be = chargerEntity(helper);
            helper.assertTrue(be.charge() == Cells.CAPACITY, "the cell should be full, got " + be.charge());
            helper.assertTrue(helper.getLevel().getBlockState(helper.absolutePos(CHARGER))
                    .getAnalogOutputSignal(helper.getLevel(), helper.absolutePos(CHARGER)) == 15,
                    "a full cell should give comparator strength 15");
            helper.succeed();
        });
    }

    static void idleChargerDoesNothingAndSyncsToClient(GameTestHelper helper) {
        Player player = player(helper);
        helper.setBlock(CHARGER, charger());
        player.setItemInHand(InteractionHand.MAIN_HAND, cell(400));
        rightClick(helper, player, CHARGER);
        helper.runAfterDelay(20, () -> {
            CellChargerBlockEntity be = chargerEntity(helper);
            helper.assertTrue(be.charge() == 400, "an unpowered charger must not charge, got " + be.charge());

            CompoundTag disk = be.saveWithoutMetadata(helper.getLevel().registryAccess());
            CellChargerBlockEntity loaded = new CellChargerBlockEntity(be.getType(), be.getBlockPos(), be.getBlockState());
            loaded.loadWithComponents(disk, helper.getLevel().registryAccess());
            helper.assertTrue(loaded.hasCell() && loaded.charge() == 400, "the cell must persist through NBT");

            CellChargerBlockEntity client = new CellChargerBlockEntity(be.getType(), be.getBlockPos(), be.getBlockState());
            client.handleUpdateTag(be.getUpdateTag(helper.getLevel().registryAccess()), helper.getLevel().registryAccess());
            helper.assertTrue(client.hasCell() && client.charge() == 400, "the cell must reach the client");
            helper.succeed();
        });
    }
}
