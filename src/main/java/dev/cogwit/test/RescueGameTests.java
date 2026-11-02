package dev.cogwit.test;

import com.mojang.serialization.JsonOps;
import dev.cogwit.CogwitComponents;
import dev.cogwit.CogwitEvents;
import dev.cogwit.CogwitMod;
import dev.cogwit.brain.Brain;
import dev.cogwit.brain.BrainState;
import dev.cogwit.brain.Growth;
import dev.cogwit.growth.GrowthContent;
import dev.cogwit.growth.Perks;
import dev.cogwit.growth.RescueEvents;
import dev.cogwit.item.CogwitTalismanItem;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.ElderGuardian;
import net.minecraft.world.entity.monster.Skeleton;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.damagesource.DamageContainer;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import top.theillusivec4.curios.api.CuriosApi;

/** In-game checks for the rescue from death, the Copper Apple and Cogwit's levels. */
@GameTestHolder(CogwitMod.MODID)
@PrefixGameTestTemplate(false)
public final class RescueGameTests {
    private RescueGameTests() {}

    private static Player player(GameTestHelper helper) {
        Player player = TestPlayers.mock(helper);
        Vec3 spot = helper.absoluteVec(new Vec3(2.5, 1, 2.5));
        player.moveTo(spot.x, spot.y, spot.z, 180f, 0f);
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

    private static BrainState brainOf(Player player) {
        return CogwitTalismanItem.brain(CogwitTalismanItem.worn(player).orElseThrow());
    }

    private static BrainState fed() {
        return BrainState.EMPTY.withTokens(Brain.MAX_TOKENS);
    }

    @GameTest(template = "empty")
    public static void lethalHitIsCaughtByCogwit(GameTestHelper helper) {
        Player player = player(helper);
        wearCogwit(player, fed());
        player.addEffect(new MobEffectInstance(MobEffects.LEVITATION, 200));
        player.hurt(player.damageSources().generic(), 50f);
        helper.assertTrue(player.isAlive(), "Cogwit should keep the owner alive");
        helper.assertTrue(Math.abs(player.getHealth() - Brain.RESCUE_HEALTH) < 0.01f,
                "owner left at 2 HP, got " + player.getHealth());
        BrainState s = brainOf(player);
        long now = helper.getLevel().getGameTime();
        helper.assertTrue(Brain.isAsleep(s, now), "Cogwit pays with sleep");
        helper.assertTrue(Brain.isShielded(s, now), "owner is invulnerable for a moment");
        helper.assertTrue(s.xp() == Growth.RESCUE_XP, "a rescue grows Cogwit");
        helper.assertFalse(player.hasEffect(MobEffects.LEVITATION), "harmful effects are cleared");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void absorptionCountsAsHealth(GameTestHelper helper) {
        Player player = player(helper);
        wearCogwit(player, fed());
        player.setAbsorptionAmount(8f);
        player.hurt(player.damageSources().generic(), 10f);
        helper.assertFalse(Brain.isAsleep(brainOf(player), helper.getLevel().getGameTime()),
                "28 total health survives 10 damage, no rescue needed");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void nothingHurtsDuringTheRescueShield(GameTestHelper helper) {
        Player player = player(helper);
        long now = helper.getLevel().getGameTime();
        wearCogwit(player, Brain.rescue(fed(), now).orElseThrow());
        LivingIncomingDamageEvent event =
                new LivingIncomingDamageEvent(player, new DamageContainer(player.damageSources().generic(), 10f));
        CogwitEvents.onIncomingDamage(event);
        helper.assertTrue(event.isCanceled(), "the 3 second shield blocks the hit");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void sleepingCogwitCannotSaveYou(GameTestHelper helper) {
        Player player = player(helper);
        long now = helper.getLevel().getGameTime();
        wearCogwit(player, fed().asleep(now, now + 1000));
        player.hurt(player.damageSources().generic(), 50f);
        helper.assertTrue(player.isDeadOrDying(), "an asleep Cogwit can't catch the hit");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void killCommandIsNotRescued(GameTestHelper helper) {
        Player player = player(helper);
        wearCogwit(player, fed());
        player.hurt(player.damageSources().genericKill(), Float.MAX_VALUE);
        helper.assertTrue(player.isDeadOrDying(), "/kill and the void bypass Cogwit");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void copperAppleWakesCogwitAfterThreeMinutes(GameTestHelper helper) {
        Player player = player(helper);
        long now = helper.getLevel().getGameTime();
        ItemStack cogwit = wearCogwit(player, fed().withTokens(100).asleep(now - Brain.WAKE_AFTER_TICKS, now + 5000));
        ItemStack apples = new ItemStack(GrowthContent.COPPER_APPLE.get(), 3);
        int eaten = CogwitTalismanItem.feed(player, cogwit, apples, 3);
        BrainState s = CogwitTalismanItem.brain(cogwit);
        helper.assertTrue(eaten == 1 && apples.getCount() == 2, "one apple per wake, ate " + eaten);
        helper.assertFalse(Brain.isAsleep(s, now), "Cogwit is awake");
        helper.assertTrue(s.tokens() == 100 + GrowthContent.COPPER_APPLE_TOKENS, "and fed, got " + s.tokens());
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void copperAppleIsKeptWhenItIsTooEarly(GameTestHelper helper) {
        Player player = player(helper);
        long now = helper.getLevel().getGameTime();
        ItemStack cogwit = wearCogwit(player, fed().asleep(now - 100, now + 5000));
        ItemStack apple = new ItemStack(GrowthContent.COPPER_APPLE.get());
        helper.assertTrue(CogwitTalismanItem.feed(player, cogwit, apple, 1) == 0, "nothing eaten");
        helper.assertTrue(apple.getCount() == 1, "the apple stays in hand");
        helper.assertTrue(Brain.isAsleep(CogwitTalismanItem.brain(cogwit), now), "still asleep");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void eatingTheAppleYourselfAlsoWakesCogwit(GameTestHelper helper) {
        Player player = player(helper);
        long now = helper.getLevel().getGameTime();
        wearCogwit(player, fed().asleep(now - Brain.WAKE_AFTER_TICKS, now + 5000));
        player.getFoodData().setFoodLevel(10);
        ItemStack apple = new ItemStack(GrowthContent.COPPER_APPLE.get());
        apple.getItem().finishUsingItem(apple, helper.getLevel(), player);
        helper.assertFalse(Brain.isAsleep(brainOf(player), now), "Cogwit shares the apple and wakes");
        helper.assertTrue(player.getFoodData().getFoodLevel() > 10, "and it fed you too");
        helper.assertTrue(player.hasEffect(MobEffects.REGENERATION), "with a little regeneration");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void bossDeathGrowsNearbyCogwits(GameTestHelper helper) {
        Player player = player(helper);
        wearCogwit(player, fed());
        ElderGuardian boss = helper.spawn(EntityType.ELDER_GUARDIAN, 4, 2, 4);
        RescueEvents.onDeath(new LivingDeathEvent(boss, player.damageSources().playerAttack(player)));
        helper.assertTrue(brainOf(player).xp() == Growth.BOSS_XP, "boss experience, got " + brainOf(player).xp());
        Zombie zombie = helper.spawn(EntityType.ZOMBIE, 4, 2, 4);
        RescueEvents.onDeath(new LivingDeathEvent(zombie, player.damageSources().playerAttack(player)));
        helper.assertTrue(brainOf(player).xp() == Growth.BOSS_XP, "a zombie is not a boss");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void backShieldBlocksHitsFromBehindOnly(GameTestHelper helper) {
        Player player = player(helper);
        wearCogwit(player, fed().withXp(Growth.threshold(Growth.Perk.BACK_SHIELD.level())));
        // facing north (yaw 180): +z is behind
        Zombie behind = helper.spawn(EntityType.ZOMBIE, 2, 1, 5);
        Zombie front = helper.spawn(EntityType.ZOMBIE, 2, 1, 0);
        LivingIncomingDamageEvent fromFront = new LivingIncomingDamageEvent(player,
                new DamageContainer(player.damageSources().mobAttack(front), 4f));
        CogwitEvents.onIncomingDamage(fromFront);
        helper.assertFalse(fromFront.isCanceled(), "hits you can see are yours to block");
        LivingIncomingDamageEvent fromBehind = new LivingIncomingDamageEvent(player,
                new DamageContainer(player.damageSources().mobAttack(behind), 4f));
        CogwitEvents.onIncomingDamage(fromBehind);
        helper.assertTrue(fromBehind.isCanceled(), "Cogwit covers your back");
        LivingIncomingDamageEvent again = new LivingIncomingDamageEvent(player,
                new DamageContainer(player.damageSources().mobAttack(behind), 4f));
        CogwitEvents.onIncomingDamage(again);
        helper.assertFalse(again.isCanceled(), "then the shield is on cooldown");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void aSlabCatchesAnArrowAtYourBack(GameTestHelper helper) {
        Player player = player(helper);
        ItemStack cogwit = wearCogwit(player, fed().withXp(Growth.threshold(Growth.Perk.BACK_SHIELD.level())));
        helper.runAfterDelay(5, () -> {
            // facing north (yaw 180): an arrow from the south flies at your back
            Skeleton shooter = helper.spawn(EntityType.SKELETON, 2, 1, 4);
            shooter.setNoAi(true);
            net.minecraft.world.entity.projectile.Arrow arrow = new net.minecraft.world.entity.projectile.Arrow(
                    helper.getLevel(), shooter, new ItemStack(net.minecraft.world.item.Items.ARROW), null);
            arrow.moveTo(player.getX(), player.getY() + 1.2, player.getZ() + 3.5);
            arrow.setDeltaMovement(0, 0, -1.5);
            helper.getLevel().addFreshEntity(arrow);
            helper.runAfterDelay(2, () -> {
                helper.assertTrue(arrow.isRemoved(), "the arrow is caught before it lands");
                helper.assertTrue(CogwitTalismanItem.brain(cogwit).tokens() == Brain.MAX_TOKENS - Brain.BACK_SHIELD_TOKEN_COST,
                        "the slab costs the back shield's tokens");
                helper.assertFalse(helper.getLevel().getEntitiesOfClass(net.minecraft.world.entity.Display.BlockDisplay.class,
                        player.getBoundingBox().inflate(3)).isEmpty(), "a slab stands behind you");
                helper.assertTrue(player.getHealth() == player.getMaxHealth(), "and you take nothing");
                helper.succeed();
            });
        });
    }

    @GameTest(template = "empty")
    public static void eyeMakesHuntersGlow(GameTestHelper helper) {
        Player player = player(helper);
        BrainState grown = fed().withXp(Growth.threshold(Growth.Perk.EYE.level()));
        Zombie hunter = helper.spawn(EntityType.ZOMBIE, 5, 1, 5);
        hunter.setTarget(player);
        Zombie idle = helper.spawn(EntityType.ZOMBIE, 0, 1, 5);
        Perks.tick(player, grown);
        helper.assertTrue(hunter.hasEffect(MobEffects.GLOWING), "a mob after you glows");
        helper.assertFalse(idle.hasEffect(MobEffects.GLOWING), "a mob minding its own business doesn't");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void highlightOutlinesValuableDrops(GameTestHelper helper) {
        Player player = player(helper);
        Vec3 at = helper.absoluteVec(new Vec3(4, 1, 4));
        ItemEntity diamond = new ItemEntity(helper.getLevel(), at.x, at.y, at.z, new ItemStack(Items.DIAMOND));
        ItemEntity dirt = new ItemEntity(helper.getLevel(), at.x, at.y, at.z, new ItemStack(Items.DIRT));
        helper.getLevel().addFreshEntity(diamond);
        helper.getLevel().addFreshEntity(dirt);
        Perks.tick(player, fed().withXp(Growth.threshold(Growth.Perk.EYE.level())));
        helper.assertFalse(diamond.hasGlowingTag(), "level 1 doesn't highlight yet");
        Perks.tick(player, fed().withXp(Growth.threshold(Growth.Perk.HIGHLIGHT.level())));
        helper.assertTrue(diamond.hasGlowingTag(), "diamonds glow");
        helper.assertFalse(dirt.hasGlowingTag(), "dirt doesn't");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void grownBrainSurvivesSaving(GameTestHelper helper) {
        BrainState s = Brain.rescue(fed().withXp(321.5f), 500).orElseThrow().withBackShieldReadyAt(777);
        BrainState back = CogwitComponents.BRAIN_CODEC.encodeStart(JsonOps.INSTANCE, s)
                .flatMap(json -> CogwitComponents.BRAIN_CODEC.parse(JsonOps.INSTANCE, json)).getOrThrow();
        helper.assertTrue(back.equals(s), "round trip lost data: " + back);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void copperAppleRecipeAndBossTagLoad(GameTestHelper helper) {
        helper.assertTrue(helper.getLevel().getRecipeManager().byKey(CogwitMod.id("copper_apple")).isPresent(),
                "missing copper apple recipe");
        helper.assertTrue(EntityType.ELDER_GUARDIAN.is(GrowthContent.BOSSES), "boss tag not loaded");
        helper.assertTrue(EntityType.WITHER.is(GrowthContent.BOSSES), "c:bosses not included");
        helper.assertTrue(new ItemStack(Items.NETHERITE_SCRAP).is(GrowthContent.VALUABLE), "valuable tag not loaded");
        helper.succeed();
    }
}
