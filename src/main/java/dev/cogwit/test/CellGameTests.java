package dev.cogwit.test;

import dev.cogwit.CogwitEvents;
import dev.cogwit.CogwitMod;
import dev.cogwit.brain.Brain;
import dev.cogwit.brain.BrainState;
import dev.cogwit.brain.Skill;
import dev.cogwit.cells.CellBank;
import dev.cogwit.cells.CellContent;
import dev.cogwit.cells.CellEvents;
import dev.cogwit.cells.Cells;
import dev.cogwit.cells.TokenCellItem;
import dev.cogwit.horizon.Horizon;
import dev.cogwit.horizon.HorizonContent;
import dev.cogwit.horizon.HorizonEvents;
import dev.cogwit.horizon.HorizonState;
import dev.cogwit.item.CogwitTalismanItem;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.damagesource.DamageContainer;
import net.neoforged.neoforge.event.entity.EntityStruckByLightningEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import top.theillusivec4.curios.api.CuriosApi;

import java.util.List;

/** In-game checks for token cells, run with {@code ./gradlew runGameTestServer}. */
@GameTestHolder(CogwitMod.MODID)
@PrefixGameTestTemplate(false)
public final class CellGameTests {
    private CellGameTests() {}

    private static Player player(GameTestHelper helper) {
        Player player = TestPlayers.mock(helper);
        Vec3 spot = helper.absoluteVec(new Vec3(2.5, 1, 2.5));
        player.moveTo(spot.x, spot.y, spot.z);
        helper.getLevel().addFreshEntity(player);
        player.setHealth(player.getMaxHealth());
        return player;
    }

    private static ItemStack wearCogwit(Player player, BrainState brain) {
        ItemStack stack = new ItemStack(CogwitMod.COGWIT.get());
        CogwitTalismanItem.setBrain(stack, brain);
        CuriosApi.getCuriosInventory(player).flatMap(inv -> inv.getStacksHandler("charm")).orElseThrow()
                .getStacks().setStackInSlot(0, stack);
        return stack;
    }

    private static ItemStack wearChest(Player player, List<Integer> cells) {
        ItemStack chest = new ItemStack(HorizonContent.CHESTPLATE.get());
        CellBank.setCells(chest, cells);
        player.setItemSlot(EquipmentSlot.CHEST, chest);
        return chest;
    }

    private static void wearFullSet(Player player, List<Integer> chestCells) {
        player.setItemSlot(EquipmentSlot.HEAD, new ItemStack(HorizonContent.HELMET.get()));
        wearChest(player, chestCells);
        player.setItemSlot(EquipmentSlot.LEGS, new ItemStack(HorizonContent.LEGGINGS.get()));
        player.setItemSlot(EquipmentSlot.FEET, new ItemStack(HorizonContent.BOOTS.get()));
    }

    private static ItemStack cell(int charge) {
        return TokenCellItem.withCharge(new ItemStack(CellContent.TOKEN_CELL.get()), charge);
    }

    /** Holds whatever the armor puts on the cursor. */
    private static final class Cursor implements SlotAccess {
        ItemStack held = ItemStack.EMPTY;

        @Override
        public ItemStack get() {
            return held;
        }

        @Override
        public boolean set(ItemStack stack) {
            held = stack;
            return true;
        }
    }

    @GameTest(template = "empty")
    public static void cellsGoInAndComeBackOut(GameTestHelper helper) {
        Player player = player(helper);
        ItemStack chest = new ItemStack(HorizonContent.CHESTPLATE.get());
        ItemStack cell = cell(1200);
        Slot slot = player.inventoryMenu.getSlot(9);
        helper.assertTrue(chest.getItem().overrideOtherStackedOnMe(chest, cell, slot, ClickAction.PRIMARY, player,
                new Cursor()), "inserting should be handled");
        helper.assertTrue(CellBank.cells(chest).equals(List.of(1200)), "cell should be in the chestplate");
        helper.assertTrue(cell.isEmpty(), "the inserted cell leaves the cursor");
        Cursor cursor = new Cursor();
        chest.getItem().overrideOtherStackedOnMe(chest, ItemStack.EMPTY, slot, ClickAction.SECONDARY, player, cursor);
        helper.assertTrue(CellBank.cells(chest).isEmpty(), "socket should be empty again");
        helper.assertTrue(TokenCellItem.charge(cursor.held) == 1200, "the same charge comes back out");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void fullSocketsRefuseMoreCells(GameTestHelper helper) {
        Player player = player(helper);
        ItemStack helmet = new ItemStack(HorizonContent.HELMET.get());
        CellBank.setCells(helmet, List.of(5));
        ItemStack cell = cell(100);
        helmet.getItem().overrideOtherStackedOnMe(helmet, cell, player.inventoryMenu.getSlot(9), ClickAction.PRIMARY,
                player, new Cursor());
        helper.assertTrue(CellBank.cells(helmet).size() == Cells.SOCKETS_HEAD, "helmet holds only one cell");
        helper.assertTrue(cell.getCount() == 1, "the extra cell is not eaten");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void cogwitDrawsFromCellsInsteadOfFallingAsleep(GameTestHelper helper) {
        Player player = player(helper);
        BrainState trained = new BrainState(List.of(new Skill("fire", 50, 0, 0, "", 0)), 0, 0, 0, 0, 0, 0);
        wearCogwit(player, trained);
        ItemStack chest = wearChest(player, List.of(Cells.CAPACITY));
        LivingIncomingDamageEvent event =
                new LivingIncomingDamageEvent(player, new DamageContainer(player.damageSources().onFire(), 10f));
        CogwitEvents.onIncomingDamage(event);
        float expected = 10f * (1f - 0.6f * dev.cogwit.brain.Brain.blockScale());
        helper.assertTrue(Math.abs(event.getAmount() - expected) < 0.01f, "blocked with cell power, got " + event.getAmount());
        BrainState after = CogwitTalismanItem.brain(CogwitTalismanItem.worn(player).orElseThrow());
        helper.assertTrue(!Brain.isAsleep(after, helper.getLevel().getGameTime()), "Cogwit must stay awake");
        helper.assertTrue(CellBank.cells(chest).get(0) < Cells.CAPACITY, "the cell paid for it");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void foodBeyondCogwitsTankChargesCells(GameTestHelper helper) {
        Player player = player(helper);
        ItemStack cogwit = wearCogwit(player, BrainState.EMPTY.withTokens(Brain.MAX_TOKENS - 10));
        ItemStack chest = wearChest(player, List.of(0));
        ItemStack ingots = new ItemStack(Items.COPPER_INGOT, 2);
        CogwitTalismanItem.feed(player, cogwit, ingots, 2);
        helper.assertTrue(CogwitTalismanItem.brain(cogwit).tokens() == Brain.MAX_TOKENS, "Cogwit fills first");
        helper.assertTrue(CellBank.cells(chest).equals(List.of(50)), "the rest goes into the cell, got "
                + CellBank.cells(chest));
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void massOverflowChargesCells(GameTestHelper helper) {
        Player player = player(helper);
        wearFullSet(player, List.of(0));
        HorizonContent.setState(player, HorizonState.EMPTY.withMass(Horizon.MASS_MAX));
        HorizonEvents.onIncomingDamage(
                new LivingIncomingDamageEvent(player, new DamageContainer(player.damageSources().generic(), 20f)));
        int charge = CellBank.cells(player.getItemBySlot(EquipmentSlot.CHEST)).get(0);
        helper.assertTrue(charge == Cells.tokensFromMass(5f), "5 overflow mass should become 25 tokens, got " + charge);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void lightningFillsCellsAndIsAbsorbed(GameTestHelper helper) {
        Player player = player(helper);
        wearFullSet(player, List.of(0, 700));
        LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(helper.getLevel());
        EntityStruckByLightningEvent event = new EntityStruckByLightningEvent(player, bolt);
        CellEvents.onLightning(event);
        helper.assertTrue(event.isCanceled(), "full set absorbs the bolt");
        helper.assertTrue(CellBank.cells(player.getItemBySlot(EquipmentSlot.CHEST))
                .equals(List.of(Cells.CAPACITY, Cells.CAPACITY)), "both cells full");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void fuelClickedOntoACellChargesIt(GameTestHelper helper) {
        Player player = player(helper);
        ItemStack cell = cell(0);
        ItemStack redstone = new ItemStack(Items.REDSTONE, 5);
        cell.getItem().overrideOtherStackedOnMe(cell, redstone, player.inventoryMenu.getSlot(9), ClickAction.PRIMARY,
                player, new Cursor());
        helper.assertTrue(TokenCellItem.charge(cell) == 5 * Cells.REDSTONE_TOKENS, "5 redstone = 50 tokens");
        helper.assertTrue(redstone.isEmpty(), "all redstone used");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void tokenCellRecipeIsLoaded(GameTestHelper helper) {
        helper.assertTrue(helper.getLevel().getRecipeManager().byKey(CogwitMod.id("token_cell")).isPresent(),
                "missing token cell recipe");
        helper.succeed();
    }
}
