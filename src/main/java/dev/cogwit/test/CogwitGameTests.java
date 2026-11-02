package dev.cogwit.test;

import dev.cogwit.CogwitActions;
import dev.cogwit.CogwitEvents;
import dev.cogwit.CogwitMod;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.monster.Zombie;
import dev.cogwit.brain.Brain;
import dev.cogwit.brain.BrainState;
import dev.cogwit.brain.Skill;
import dev.cogwit.item.CogwitTalismanItem;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.damagesource.DamageContainer;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import top.theillusivec4.curios.api.CuriosApi;

import java.util.List;

/** In-game checks, run with {@code ./gradlew runGameTestServer}. */
@GameTestHolder(CogwitMod.MODID)
@PrefixGameTestTemplate(false)
public final class CogwitGameTests {
    private CogwitGameTests() {}

    /** A survival mock player standing in the test area (so it ticks and can be found by neighbours). */
    private static Player player(GameTestHelper helper) {
        Player player = TestPlayers.mock(helper);
        Vec3 spot = helper.absoluteVec(new Vec3(2.5, 1, 2.5));
        player.moveTo(spot.x, spot.y, spot.z);
        helper.getLevel().addFreshEntity(player);
        player.setHealth(player.getMaxHealth());
        return player;
    }

    private static BrainState trained(String category, int xp, int tokens) {
        return new BrainState(List.of(new Skill(category, xp, 0, 0, "", 0)), tokens, 0, 0, 0, 0, 0);
    }

    private static ItemStack equip(Player player, BrainState brain) {
        ItemStack stack = new ItemStack(CogwitMod.COGWIT.get());
        CogwitTalismanItem.setBrain(stack, brain);
        CuriosApi.getCuriosInventory(player)
                .flatMap(inv -> inv.getStacksHandler("charm"))
                .orElseThrow(() -> new IllegalStateException("player has no charm slot"))
                .getStacks().setStackInSlot(0, stack);
        return stack;
    }

    private static BrainState wornBrain(Player player) {
        return CogwitTalismanItem.brain(CogwitTalismanItem.worn(player).orElseThrow());
    }

    /** Fires the same event the game fires before applying damage and returns the final amount. */
    private static float incoming(Player player, DamageSource source, float amount) {
        LivingIncomingDamageEvent event = new LivingIncomingDamageEvent(player, new DamageContainer(source, amount));
        NeoForge.EVENT_BUS.post(event);
        return event.getAmount();
    }

    @GameTest(template = "empty")
    public static void trainedCogwitBlocksRealFireDamage(GameTestHelper helper) {
        Player player = player(helper);
        equip(player, trained("fire", 25, Brain.MAX_TOKENS));
        player.invulnerableTime = 0;
        player.hurt(player.damageSources().onFire(), 10f);
        // at least the 40% skill (scaled by the world's balance): a neighbouring test's mock player may share a stronger one
        float blocked = 10f * 0.40f * Brain.blockScale();
        helper.assertTrue(player.getHealth() >= 10f + blocked - 0.01f && player.getHealth() < 20f,
                "expected at least " + blocked + " of 10 fire damage blocked, health is " + player.getHealth());
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void firstHitIsALessonNotAShield(GameTestHelper helper) {
        Player player = player(helper);
        equip(player, BrainState.EMPTY.withTokens(Brain.MAX_TOKENS));
        float dealt = incoming(player, player.damageSources().fall(), 4f);
        helper.assertTrue(dealt == 4f, "fresh Cogwit should not block yet, got " + dealt);
        helper.assertTrue(wornBrain(player).find("fall").isPresent(), "fall should be learned");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void otherPlayersAreRefused(GameTestHelper helper) {
        Player player = player(helper);
        Player friend = player(helper);
        equip(player, trained("mob", 50, Brain.MAX_TOKENS));
        float dealt = incoming(player, player.damageSources().playerAttack(friend), 6f);
        helper.assertTrue(dealt == 6f, "PvP damage must not be blocked, got " + dealt);
        helper.assertTrue(wornBrain(player).lastRefusal() > 0, "Cogwit should have said it can't help");
        helper.assertTrue(wornBrain(player).skills().size() == 1, "PvP must not teach anything");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void poisonIsShruggedOff(GameTestHelper helper) {
        Player player = player(helper);
        equip(player, BrainState.EMPTY.withTokens(100));
        player.addEffect(new MobEffectInstance(MobEffects.POISON, 200));
        helper.assertTrue(!player.hasEffect(MobEffects.POISON), "poison should not stick");
        helper.assertTrue(wornBrain(player).tokens() == 100 - Brain.EFFECT_TOKEN_COST, "shrugging costs tokens");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void teammateSharesExperience(GameTestHelper helper) {
        Player player = player(helper);
        Player friend = player(helper);
        equip(player, BrainState.EMPTY.withTokens(Brain.MAX_TOKENS));
        equip(friend, trained("explosion", 50, Brain.MAX_TOKENS));
        float dealt = incoming(player, player.damageSources().explosion(null, null), 10f);
        float expected = 10f * (1f - 0.60f * Brain.blockScale());
        helper.assertTrue(Math.abs(dealt - expected) < 0.01f, "friend's explosion skill should apply, got " + dealt);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void emptyTankSendsCogwitToSleep(GameTestHelper helper) {
        Player player = player(helper);
        equip(player, trained("fire", 50, 4));
        incoming(player, player.damageSources().onFire(), 10f);
        long now = player.level().getGameTime();
        helper.assertTrue(Brain.isAsleep(wornBrain(player), now), "Cogwit should be asleep");
        float dealt = incoming(player, player.damageSources().onFire(), 10f);
        helper.assertTrue(dealt == 10f, "sleeping Cogwit blocks nothing, got " + dealt);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void recipeIsLoaded(GameTestHelper helper) {
        helper.assertTrue(helper.getLevel().getRecipeManager().byKey(CogwitMod.id("cogwit_talisman")).isPresent(),
                "crafting recipe should be registered");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void thinkingSlowsEnemiesAndStrengthensPlayer(GameTestHelper helper) {
        Player player = player(helper);
        equip(player, BrainState.EMPTY.withTokens(Brain.MAX_TOKENS));
        Zombie zombie = helper.spawn(EntityType.ZOMBIE, new BlockPos(1, 1, 1));
        Cow cow = helper.spawn(EntityType.COW, new BlockPos(3, 1, 3));
        CogwitActions.think(player);
        helper.assertTrue(zombie.hasEffect(MobEffects.MOVEMENT_SLOWDOWN), "zombie should be slowed");
        helper.assertTrue(!cow.hasEffect(MobEffects.MOVEMENT_SLOWDOWN), "peaceful mobs are left alone");
        helper.assertTrue(player.hasEffect(MobEffects.DAMAGE_BOOST), "player should get strength");
        helper.assertTrue(wornBrain(player).tokens() == Brain.MAX_TOKENS - Brain.THINK_TOKEN_COST, "costs tokens");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void neverEatsFromInventoryOnItsOwn(GameTestHelper helper) {
        Player player = player(helper);
        equip(player, BrainState.EMPTY);
        player.getInventory().add(new ItemStack(Items.COPPER_INGOT, 3));
        helper.runAfterDelay(60, () -> {
            helper.assertTrue(wornBrain(player).tokens() == 0, "Cogwit must only be hand-fed");
            helper.assertTrue(player.getInventory().countItem(Items.COPPER_INGOT) == 3, "copper must stay untouched");
            helper.succeed();
        });
    }

    @GameTest(template = "empty")
    public static void shiftRightClickFeedsOnePiece(GameTestHelper helper) {
        Player player = player(helper);
        equip(player, BrainState.EMPTY);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.RAW_COPPER, 5));
        player.setShiftKeyDown(true);
        var event = new PlayerInteractEvent.RightClickItem(player, InteractionHand.MAIN_HAND);
        // Call Cogwit's handler directly: other pack mods react to this event with client-only code
        // when the player is a test mock instead of a real ServerPlayer.
        CogwitEvents.onRightClickItem(event);
        helper.assertTrue(event.isCanceled(), "feeding should replace the normal right click");
        helper.assertTrue(wornBrain(player).tokens() == CogwitTalismanItem.RAW_COPPER_TOKENS, "raw copper = 15");
        helper.assertTrue(player.getMainHandItem().getCount() == 4, "one piece eaten");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void rightClickWithoutShiftDoesNotFeed(GameTestHelper helper) {
        Player player = player(helper);
        equip(player, BrainState.EMPTY);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.COPPER_INGOT, 5));
        var event = new PlayerInteractEvent.RightClickItem(player, InteractionHand.MAIN_HAND);
        // Call Cogwit's handler directly: other pack mods react to this event with client-only code
        // when the player is a test mock instead of a real ServerPlayer.
        CogwitEvents.onRightClickItem(event);
        helper.assertTrue(!event.isCanceled() && wornBrain(player).tokens() == 0, "plain right click is untouched");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void clickFeedingFillsToFullThenStops(GameTestHelper helper) {
        Player player = player(helper);
        ItemStack cogwit = equip(player, BrainState.EMPTY.withTokens(Brain.MAX_TOKENS - 50));
        ItemStack ingots = new ItemStack(Items.COPPER_INGOT, 10);
        Slot slot = player.inventoryMenu.getSlot(9);
        boolean handled = cogwit.getItem().overrideOtherStackedOnMe(cogwit, ingots, slot, ClickAction.PRIMARY, player,
                SlotAccess.NULL);
        helper.assertTrue(handled, "clicking copper onto Cogwit should be handled");
        helper.assertTrue(wornBrain(player).tokens() == Brain.MAX_TOKENS, "should reach exactly full");
        helper.assertTrue(ingots.getCount() == 8, "two ingots (60 tokens) needed for the last 50, got "
                + ingots.getCount());
        cogwit.getItem().overrideOtherStackedOnMe(cogwit, ingots, slot, ClickAction.PRIMARY, player, SlotAccess.NULL);
        helper.assertTrue(ingots.getCount() == 8, "a full Cogwit eats nothing more");
        helper.succeed();
    }
}
