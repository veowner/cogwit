package dev.cogwit.test;

import dev.cogwit.CogwitMod;
import dev.cogwit.horizon.Horizon;
import dev.cogwit.horizon.HorizonActions;
import dev.cogwit.horizon.HorizonContent;
import dev.cogwit.horizon.HorizonState;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.monster.Skeleton;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.NeoForgeMod;
import net.neoforged.neoforge.common.damagesource.DamageContainer;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** In-game checks for the Event Horizon armor, run with {@code ./gradlew runGameTestServer}. */
@GameTestHolder(CogwitMod.MODID)
@PrefixGameTestTemplate(false)
public final class HorizonGameTests {
    private HorizonGameTests() {}

    private static Player player(GameTestHelper helper, Vec3 local) {
        Player player = TestPlayers.mock(helper);
        Vec3 spot = helper.absoluteVec(local);
        player.moveTo(spot.x, spot.y, spot.z, 0f, 0f);
        helper.getLevel().addFreshEntity(player);
        player.setHealth(player.getMaxHealth());
        return player;
    }

    private static Player armored(GameTestHelper helper) {
        Player player = player(helper, new Vec3(2.5, 1, 2.5));
        wearSet(player);
        return player;
    }

    private static void wearSet(Player player) {
        player.setItemSlot(EquipmentSlot.HEAD, new ItemStack(HorizonContent.HELMET.get()));
        player.setItemSlot(EquipmentSlot.CHEST, new ItemStack(HorizonContent.CHESTPLATE.get()));
        player.setItemSlot(EquipmentSlot.LEGS, new ItemStack(HorizonContent.LEGGINGS.get()));
        player.setItemSlot(EquipmentSlot.FEET, new ItemStack(HorizonContent.BOOTS.get()));
    }

    private static LivingIncomingDamageEvent incoming(net.minecraft.world.entity.LivingEntity victim,
                                                      DamageSource source, float amount) {
        LivingIncomingDamageEvent event = new LivingIncomingDamageEvent(victim, new DamageContainer(source, amount));
        NeoForge.EVENT_BUS.post(event);
        return event;
    }

    @GameTest(template = "empty")
    public static void fullSetCutsDamageAndBuildsMass(GameTestHelper helper) {
        Player player = armored(helper);
        LivingIncomingDamageEvent event = incoming(player, player.damageSources().generic(), 10f);
        // 30% off with an empty meter, plus a sliver for the 2.5 mass this very hit stored
        float expected = 10f * dev.cogwit.horizon.Horizon.damageMultiplier(2.5f, dev.cogwit.BalanceConfig.armorReduction(),
                dev.cogwit.BalanceConfig.armorMassReduction());
        helper.assertTrue(Math.abs(event.getAmount() - expected) < 0.01f && expected < 7.1f,
                "expected " + expected + " damage, got " + event.getAmount());
        helper.assertTrue(Math.abs(HorizonContent.state(player).mass() - 2.5f) < 0.01f, "25% of 10 becomes mass");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void theFaceplateFlipsOnlyOnAHorizonHelmetAndLiftsForAMeal(GameTestHelper helper) {
        Player bare = player(helper, new Vec3(1.5, 1, 1.5));
        bare.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
        dev.cogwit.horizon.HorizonVisor.toggle(bare);
        helper.assertFalse(bare.getItemBySlot(EquipmentSlot.HEAD).has(dev.cogwit.CogwitComponents.VISOR_OPEN.get()),
                "an iron helmet has no faceplate to raise");

        Player player = player(helper, new Vec3(3.5, 1, 3.5));
        ItemStack helmet = new ItemStack(HorizonContent.HELMET.get());
        player.setItemSlot(EquipmentSlot.HEAD, helmet);
        helper.assertFalse(dev.cogwit.horizon.HorizonVisor.shouldBeOpen(player, helmet), "down by default");
        dev.cogwit.horizon.HorizonVisor.toggle(player);
        helper.assertTrue(dev.cogwit.horizon.HorizonVisor.raised(player.getItemBySlot(EquipmentSlot.HEAD)), "raised by hand");
        dev.cogwit.horizon.HorizonVisor.toggle(player);
        helper.assertFalse(dev.cogwit.horizon.HorizonVisor.raised(player.getItemBySlot(EquipmentSlot.HEAD)), "and lowered again");

        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(Items.BREAD));
        player.startUsingItem(net.minecraft.world.InteractionHand.MAIN_HAND);
        helper.assertTrue(dev.cogwit.horizon.HorizonVisor.shouldBeOpen(player, player.getItemBySlot(EquipmentSlot.HEAD)),
                "it lifts while the wearer eats");
        player.stopUsingItem();
        helper.assertFalse(dev.cogwit.horizon.HorizonVisor.shouldBeOpen(player, player.getItemBySlot(EquipmentSlot.HEAD)),
                "and drops once the meal is over");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void theChestplateAloneRunsTheCore(GameTestHelper helper) {
        Player player = player(helper, new Vec3(2.5, 1, 2.5));
        player.setItemSlot(EquipmentSlot.CHEST, new ItemStack(HorizonContent.CHESTPLATE.get()));
        LivingIncomingDamageEvent event = incoming(player, player.damageSources().generic(), 10f);
        float expected = 10f * Horizon.damageMultiplier(Horizon.Piece.CHEST.bit, 2.5f,
                dev.cogwit.BalanceConfig.armorReduction(), dev.cogwit.BalanceConfig.armorMassReduction());
        helper.assertTrue(Math.abs(event.getAmount() - expected) < 0.01f && event.getAmount() < 10f,
                "the chest takes its 9/24 share: expected " + expected + ", got " + event.getAmount());
        helper.assertTrue(Math.abs(HorizonContent.state(player).mass() - 2.5f) < 0.01f, "the core stores mass");
        helper.assertFalse(incoming(player, player.damageSources().fall(), 20f).isCanceled(), "falls still hurt");
        helper.assertFalse(incoming(player, player.damageSources().lava(), 4f).isCanceled(), "lava still burns");
        HorizonActions.blink(player);
        helper.assertFalse(dev.cogwit.horizon.HorizonDash.isDashing(player), "no dash without the leggings");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void theLeggingsAloneDash(GameTestHelper helper) {
        Player player = player(helper, new Vec3(2.5, 1, 2.5));
        player.setItemSlot(EquipmentSlot.LEGS, new ItemStack(HorizonContent.LEGGINGS.get()));
        HorizonActions.blink(player);
        helper.assertTrue(dev.cogwit.horizon.HorizonDash.isDashing(player), "the side thrusters dash");
        helper.assertTrue(HorizonContent.state(player).mass() == 0f, "no core, no mass");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void theBootsTakeTheFallAndLandWithAShockwave(GameTestHelper helper) {
        Player player = player(helper, new Vec3(2.5, 1, 2.5));
        player.setItemSlot(EquipmentSlot.FEET, new ItemStack(HorizonContent.BOOTS.get()));
        helper.assertTrue(incoming(player, player.damageSources().fall(), 20f).isCanceled(), "no fall damage");
        Zombie zombie = helper.spawn(EntityType.ZOMBIE, new BlockPos(4, 1, 2));
        float before = zombie.getHealth();
        net.neoforged.neoforge.event.entity.living.LivingFallEvent fall =
                new net.neoforged.neoforge.event.entity.living.LivingFallEvent(player, 18f, 1f);
        NeoForge.EVENT_BUS.post(fall);
        helper.assertTrue(fall.getDamageMultiplier() == 0f, "the boots soak the landing");
        helper.assertTrue(zombie.getHealth() < before, "the shockwave hits the zombie nearby");
        helper.assertTrue(player.getHealth() == player.getMaxHealth(), "and never the wearer");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void theClosedFaceplateSeesInTheDark(GameTestHelper helper) {
        Player player = player(helper, new Vec3(2.5, 1, 2.5));
        player.setItemSlot(EquipmentSlot.HEAD, new ItemStack(HorizonContent.HELMET.get()));
        player.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                net.minecraft.world.effect.MobEffects.BLINDNESS, 200));
        helper.runAfterDelay(21, () -> {
            helper.assertTrue(player.hasEffect(net.minecraft.world.effect.MobEffects.NIGHT_VISION), "night vision");
            helper.assertFalse(player.hasEffect(net.minecraft.world.effect.MobEffects.BLINDNESS), "no blindness");
            dev.cogwit.horizon.HorizonVisor.toggle(player);
            helper.runAfterDelay(2, () -> {
                helper.assertFalse(player.hasEffect(net.minecraft.world.effect.MobEffects.NIGHT_VISION),
                        "raising the faceplate takes the night vision away");
                helper.succeed();
            });
        });
    }

    @GameTest(template = "empty")
    public static void legsAndBootsFlyWithoutTheRest(GameTestHelper helper) {
        Player player = player(helper, new Vec3(2.5, 1, 2.5));
        player.setItemSlot(EquipmentSlot.LEGS, new ItemStack(HorizonContent.LEGGINGS.get()));
        player.setItemSlot(EquipmentSlot.FEET, new ItemStack(HorizonContent.BOOTS.get()));
        helper.runAfterDelay(5, () -> {
            boolean noFuel = player.getAttributeValue(NeoForgeMod.CREATIVE_FLIGHT) == 0;
            player.setItemSlot(EquipmentSlot.LEGS, ItemStack.EMPTY);
            helper.runAfterDelay(5, () -> {
                helper.assertTrue(noFuel, "legs and boots fly only on tokens, and there are none here");
                helper.assertTrue(Horizon.has(Horizon.Piece.LEGS.bit | Horizon.Piece.BOOTS.bit, Horizon.Feature.FLIGHT),
                        "but together they are what flies");
                helper.assertTrue(HorizonContent.has(player, Horizon.Feature.LANDING)
                        && !HorizonContent.has(player, Horizon.Feature.FLIGHT), "boots alone don't fly");
                helper.succeed();
            });
        });
    }

    @GameTest(template = "empty")
    public static void fallAndFireAreIgnored(GameTestHelper helper) {
        Player player = armored(helper);
        helper.assertTrue(incoming(player, player.damageSources().fall(), 20f).isCanceled(), "fall cancelled");
        helper.assertTrue(incoming(player, player.damageSources().lava(), 4f).isCanceled(), "lava cancelled");
        helper.assertTrue(incoming(player, player.damageSources().onFire(), 1f).isCanceled(), "burning cancelled");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void fullSetGrantsFlightAndTakesItBack(GameTestHelper helper) {
        Player player = armored(helper);
        fuel(player);
        helper.runAfterDelay(5, () -> {
            helper.assertTrue(player.getAttributeValue(NeoForgeMod.CREATIVE_FLIGHT) > 0, "flight with full set");
            player.setItemSlot(EquipmentSlot.FEET, ItemStack.EMPTY);
            helper.runAfterDelay(5, () -> {
                helper.assertTrue(player.getAttributeValue(NeoForgeMod.CREATIVE_FLIGHT) == 0, "no flight without boots");
                helper.succeed();
            });
        });
    }

    @GameTest(template = "empty")
    public static void arrowsTurnBackAtTheShooter(GameTestHelper helper) {
        Player player = armored(helper);
        Skeleton skeleton = helper.spawn(EntityType.SKELETON, new BlockPos(0, 1, 0));
        skeleton.setNoAi(true);
        Arrow arrow = new Arrow(helper.getLevel(), skeleton, new ItemStack(Items.ARROW), null);
        Vec3 start = player.position().add(4, 1, 0);
        arrow.setPos(start.x, start.y, start.z);
        arrow.setDeltaMovement(-1.5, 0, 0);
        helper.getLevel().addFreshEntity(arrow);
        helper.succeedWhen(() -> {
            helper.assertTrue(arrow.getTags().contains(HorizonActions.REFLECTED_TAG), "arrow should be reflected");
            helper.assertTrue(arrow.getOwner() == player, "reflected arrow now belongs to the wearer");
        });
    }

    @GameTest(template = "empty")
    public static void massPowersMeleeHits(GameTestHelper helper) {
        Player player = armored(helper);
        HorizonContent.setState(player, HorizonState.EMPTY.withMass(50f));
        Zombie zombie = helper.spawn(EntityType.ZOMBIE, new BlockPos(1, 1, 1));
        LivingIncomingDamageEvent event = incoming(zombie, player.damageSources().playerAttack(player), 10f);
        helper.assertTrue(Math.abs(event.getAmount() - 15f) < 0.01f, "50 mass adds +5, got " + event.getAmount());
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "skills", timeoutTicks = 100)
    public static void collapseCrushesNearbyMobsAndSpendsMass(GameTestHelper helper) {
        Player player = armored(helper);
        HorizonContent.setState(player, HorizonState.EMPTY.withMass(Horizon.MASS_MAX));
        Zombie zombie = helper.spawn(EntityType.ZOMBIE, new BlockPos(0, 1, 0));
        float before = zombie.getHealth();
        HorizonActions.gravity(player);
        helper.assertTrue(HorizonContent.state(player).mass() == 0f, "Collapse spends all mass");
        helper.assertTrue(zombie.getHealth() == before, "it charges first, nothing is hit yet");
        // after the charge the blast lands
        helper.succeedWhen(() -> helper.assertTrue(zombie.getHealth() < before || zombie.isDeadOrDying(),
                "zombie should be hit by Collapse"));
    }

    @GameTest(template = "empty", batch = "skills", timeoutTicks = 100)
    public static void collapseDragsInThenThrowsOut(GameTestHelper helper) {
        Player player = armored(helper);
        HorizonContent.setState(player, HorizonState.EMPTY.withMass(Horizon.MASS_MAX));
        Zombie zombie = helper.spawn(EntityType.ZOMBIE, new BlockPos(6, 1, 2));
        HorizonActions.gravity(player);
        // horizontal distance: the charge also lifts mobs off the ground, which is the point
        java.util.function.DoubleSupplier flat = () -> Math.hypot(zombie.getX() - player.getX(),
                zombie.getZ() - player.getZ());
        double start = flat.getAsDouble();
        helper.runAfterDelay(dev.cogwit.horizon.SkillPhysics.COLLAPSE_CHARGE_TICKS - 4, () -> {
            helper.assertTrue(flat.getAsDouble() < start, "dragged in during the charge");
            double dragged = flat.getAsDouble();
            helper.runAfterDelay(10, () -> {
                helper.assertTrue(zombie.isDeadOrDying() || flat.getAsDouble() > dragged,
                        "thrown out by the blast");
                helper.succeed();
            });
        });
    }

    @GameTest(template = "empty", batch = "skills", timeoutTicks = 60)
    public static void theWellSwallowsLootAndBendsArrows(GameTestHelper helper) {
        // no armor on this one: the full set would reflect the arrow before the well gets it
        Player player = TestPlayers.mock(helper);
        Vec3 centre = helper.absoluteVec(new Vec3(2.5, 3, 2.5));
        dev.cogwit.horizon.fx.GravityWellEntity.open(player, centre);
        Vec3 lootAt = helper.absoluteVec(new Vec3(6.5, 2, 2.5));
        net.minecraft.world.entity.item.ItemEntity loot = new net.minecraft.world.entity.item.ItemEntity(
                helper.getLevel(), lootAt.x, lootAt.y, lootAt.z, new net.minecraft.world.item.ItemStack(
                net.minecraft.world.item.Items.GOLD_INGOT));
        loot.setDeltaMovement(Vec3.ZERO);
        helper.getLevel().addFreshEntity(loot);
        net.minecraft.world.entity.projectile.Arrow arrow = new net.minecraft.world.entity.projectile.Arrow(
                EntityType.ARROW, helper.getLevel());
        // fired straight away from the hole: the well should start turning it back
        Vec3 away = new Vec3(0, 0, 1);
        Vec3 arrowAt = centre.add(away.scale(2)).add(1, 0, 0);
        arrow.moveTo(arrowAt.x, arrowAt.y, arrowAt.z);
        arrow.setNoGravity(true);
        arrow.setDeltaMovement(away.scale(0.8));
        helper.getLevel().addFreshEntity(arrow);
        double lootStart = loot.position().distanceTo(centre);
        helper.runAfterDelay(2, () -> {
            double stillAway = arrow.getDeltaMovement().normalize().dot(away);
            helper.assertTrue(stillAway < 0.9, "the arrow turns back towards the well, heading " + stillAway);
            helper.runAfterDelay(4, () -> {
                helper.assertTrue(loot.position().distanceTo(centre) < lootStart, "loot pulled towards the hole");
                helper.succeed();
            });
        });
    }

    @GameTest(template = "empty", batch = "skills")
    public static void gravityWellDamagesMobsInside(GameTestHelper helper) {
        Player player = armored(helper);
        player.setXRot(90f);
        Zombie zombie = helper.spawn(EntityType.ZOMBIE, new BlockPos(3, 1, 2));
        float before = zombie.getHealth();
        HorizonActions.gravity(player);
        helper.assertTrue(Horizon.isWellActive(HorizonContent.state(player), helper.getLevel().getGameTime()),
                "well should be active");
        helper.succeedWhen(() -> helper.assertTrue(zombie.getHealth() < before, "well should hurt the zombie"));
    }

    @GameTest(template = "empty")
    public static void dashMathLiftsOffTheFloorAndKeepsRunningSpeed(GameTestHelper helper) {
        Vec3 ground = dev.cogwit.horizon.HorizonDash.direction(new Vec3(1, -0.3, 0), true);
        helper.assertTrue(ground.y > 0 && Math.abs(ground.length() - 1) < 1e-6, "a ground dash never digs in");
        Vec3 dir = new Vec3(0, 0, 1);
        Vec3 standing = dev.cogwit.horizon.HorizonDash.thrust(dir, Vec3.ZERO);
        Vec3 running = dev.cogwit.horizon.HorizonDash.thrust(dir, new Vec3(0, -0.5, 0.3));
        helper.assertTrue(running.z > standing.z && running.y == 0, "running speed kept, falling speed not");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void theDashThrustsAndDodges(GameTestHelper helper) {
        // the 5x5 test area is too small to see the carried momentum (that's checked in the dash math test);
        // here: the thrust really pushes along the look, and hits pass through while it lasts
        Player player = armored(helper);
        player.setXRot(-20f);
        Vec3 look = player.getLookAngle();
        HorizonActions.blink(player);
        helper.assertTrue(dev.cogwit.horizon.HorizonDash.isDashing(player), "dashing right away");
        helper.assertTrue(incoming(player, player.damageSources().generic(), 6f).isCanceled(), "dodges mid-dash");
        double[] peak = {0};
        helper.onEachTick(() -> peak[0] = Math.max(peak[0], player.getDeltaMovement().dot(look)));
        helper.runAfterDelay(dev.cogwit.horizon.HorizonDash.TICKS + 2, () -> {
            helper.assertTrue(peak[0] > 1.0, "thrust along the look, peak speed " + peak[0]);
            helper.assertTrue(!dev.cogwit.horizon.HorizonDash.isDashing(player), "the dash ends");
            helper.succeed();
        });
    }

    @GameTest(template = "empty")
    public static void voidRescuePutsYouBackOnGround(GameTestHelper helper) {
        Player player = armored(helper);
        helper.runAfterDelay(3, () -> {
            player.teleportTo(player.getX(), helper.getLevel().getMinBuildHeight() - 20, player.getZ());
            helper.succeedWhen(() -> helper.assertTrue(player.getY() > helper.getLevel().getMinBuildHeight(),
                    "player should be pulled out of the void"));
        });
    }

    @GameTest(template = "empty")
    public static void armorRecipesAreLoaded(GameTestHelper helper) {
        var recipes = helper.getLevel().getRecipeManager();
        for (String id : new String[] {"singularity_core", "horizon_template", "horizon_template_duplicate",
                "horizon_helmet", "horizon_chestplate", "horizon_leggings", "horizon_boots"}) {
            helper.assertTrue(recipes.byKey(CogwitMod.id(id)).isPresent(), "missing recipe " + id);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void fullSetTipIsShownOnce(GameTestHelper helper) {
        Player player = armored(helper);
        helper.succeedWhen(() -> helper.assertTrue(HorizonContent.state(player).hasSeenTip("full_set"),
                "first full set should trigger the explanation"));
    }

    /** One full token cell in the chestplate: flight runs on tokens. */
    private static void fuel(Player player) {
        dev.cogwit.cells.CellBank.setCells(player.getItemBySlot(EquipmentSlot.CHEST),
                java.util.List.of(dev.cogwit.cells.Cells.CAPACITY));
    }

    @GameTest(template = "empty")
    public static void noTokensNoFlight(GameTestHelper helper) {
        Player player = armored(helper);
        helper.runAfterDelay(5, () -> {
            helper.assertTrue(player.getAttributeValue(NeoForgeMod.CREATIVE_FLIGHT) == 0, "an empty suit can't fly");
            helper.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 80)
    public static void flyingBurnsTokensFromCells(GameTestHelper helper) {
        Player player = armored(helper);
        fuel(player);
        player.getAbilities().flying = true;
        helper.runAfterDelay(45, () -> {
            int left = dev.cogwit.cells.CellBank.cells(player.getItemBySlot(EquipmentSlot.CHEST)).get(0);
            helper.assertTrue(left < dev.cogwit.cells.Cells.CAPACITY, "the cell pays for flight, has " + left);
            helper.succeed();
        });
    }
}
