package dev.cogwit.test;

import dev.cogwit.CogwitMod;
import dev.cogwit.exoframe.ExoframeActions;
import dev.cogwit.exoframe.ExoframeContent;
import dev.cogwit.item.CogwitTalismanItem;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotContext;

/** In-game checks for the copper exoframe worn in its two accessory slots. */
@GameTestHolder(CogwitMod.MODID)
@PrefixGameTestTemplate(false)
public final class ExoframeGameTests {
    /** Curios builds a new player's slots on its first ticks and drops whatever was put in them before. */
    private static final int SETTLE = 5;

    private ExoframeGameTests() {}

    private static Player player(GameTestHelper helper) {
        Player player = TestPlayers.mock(helper);
        Vec3 spot = helper.absoluteVec(new Vec3(2.5, 2, 2.5));
        player.moveTo(spot.x, spot.y, spot.z, 0f, 0f);
        helper.getLevel().addFreshEntity(player);
        return player;
    }

    private static void wear(Player player, String slot, ItemStack stack) {
        CuriosApi.getCuriosInventory(player).flatMap(inv -> inv.getStacksHandler(slot)).orElseThrow()
                .getStacks().setStackInSlot(0, stack);
    }

    private static void wearBoth(Player player) {
        wear(player, ExoframeContent.UPPER_SLOT, new ItemStack(ExoframeContent.UPPER.get()));
        wear(player, ExoframeContent.LOWER_SLOT, new ItemStack(ExoframeContent.LOWER.get()));
    }

    @GameTest(template = "empty")
    public static void eachHalfFitsOnlyItsOwnSlotAndLendsItsBuffs(GameTestHelper helper) {
        Player player = player(helper);
        helper.runAfterDelay(SETTLE, () -> {
            var upper = ExoframeContent.UPPER.get();
            var lower = ExoframeContent.LOWER.get();
            SlotContext upperSlot = new SlotContext(ExoframeContent.UPPER_SLOT, player, 0, false, true);
            SlotContext lowerSlot = new SlotContext(ExoframeContent.LOWER_SLOT, player, 0, false, true);
            helper.assertTrue(upper.canEquip(upperSlot, new ItemStack(upper)) && !upper.canEquip(lowerSlot, new ItemStack(upper)),
                    "the upper half goes only in the upper slot");
            helper.assertTrue(lower.canEquip(lowerSlot, new ItemStack(lower)) && !lower.canEquip(upperSlot, new ItemStack(lower)),
                    "the lower half goes only in the lower slot");
            var up = upper.getAttributeModifiers(upperSlot, CogwitMod.id("test_upper"), new ItemStack(upper));
            var down = lower.getAttributeModifiers(lowerSlot, CogwitMod.id("test_lower"), new ItemStack(lower));
            helper.assertTrue(up.containsKey(Attributes.ARMOR) && up.containsKey(Attributes.ATTACK_SPEED)
                    && up.containsKey(Attributes.BLOCK_INTERACTION_RANGE), "the upper half: armor, swing and reach");
            helper.assertTrue(down.containsKey(Attributes.ARMOR) && down.containsKey(Attributes.MOVEMENT_SPEED)
                    && down.containsKey(Attributes.STEP_HEIGHT), "the lower half: armor, speed and a taller step");
            wearBoth(player);
            helper.assertTrue(ExoframeContent.wearsBoth(player), "both halves are found in their slots");
            helper.succeed();
        });
    }

    @GameTest(template = "empty")
    public static void sprintingInTheLowerHalfCostsLessHunger(GameTestHelper helper) {
        Player player = player(helper);
        helper.runAfterDelay(SETTLE, () -> {
            player.setSprinting(true);
            float before = player.getFoodData().getExhaustionLevel();
            player.causeFoodExhaustion(1f);
            float bare = player.getFoodData().getExhaustionLevel() - before;
            wear(player, ExoframeContent.LOWER_SLOT, new ItemStack(ExoframeContent.LOWER.get()));
            before = player.getFoodData().getExhaustionLevel();
            player.causeFoodExhaustion(1f);
            float framed = player.getFoodData().getExhaustionLevel() - before;
            helper.assertTrue(Math.abs(bare - 1f) < 1e-3 && Math.abs(framed - ExoframeContent.SPRINT_HUNGER) < 1e-3,
                    "sprint hunger " + bare + " bare, " + framed + " in the frame");
            player.setSprinting(false);
            before = player.getFoodData().getExhaustionLevel();
            player.causeFoodExhaustion(1f);
            helper.assertTrue(Math.abs(player.getFoodData().getExhaustionLevel() - before - 1f) < 1e-3,
                    "walking, mining and fighting cost the same as before");
            helper.succeed();
        });
    }

    @GameTest(template = "empty")
    public static void theShovePushesForwardAndCostsCogwitTokens(GameTestHelper helper) {
        Player player = player(helper);
        // Cogwit goes on at once, the way every other test wears him
        ItemStack cogwit = new ItemStack(CogwitMod.COGWIT.get());
        CogwitTalismanItem.setBrain(cogwit, CogwitTalismanItem.STARTER_BRAIN.withTokens(500));
        wear(player, "charm", cogwit);
        helper.runAfterDelay(SETTLE, () -> {
            helper.assertFalse(ExoframeActions.shove(player), "nothing without the frame");
            wearBoth(player);
            helper.assertTrue(ExoframeActions.shove(player), "fires with the frame and Cogwit");
            Vec3 v = player.getDeltaMovement();
            helper.assertTrue(Math.hypot(v.x, v.z) > 1.0, "pushed forward, got " + v);
            int tokens = CogwitTalismanItem.brain(CogwitTalismanItem.worn(player).orElseThrow()).tokens();
            helper.assertTrue(tokens == 500 - ExoframeActions.SHOVE_COST, "costs " + ExoframeActions.SHOVE_COST + ", left " + tokens);
            helper.assertFalse(ExoframeActions.shove(player), "and then it recharges");
            helper.succeed();
        });
    }
}
