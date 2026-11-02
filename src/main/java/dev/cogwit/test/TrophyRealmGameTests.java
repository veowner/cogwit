package dev.cogwit.test;

import dev.cogwit.CogwitMod;
import dev.cogwit.trophy.BossRisePowers;
import dev.cogwit.trophy.Trophy;
import dev.cogwit.trophy.TrophyContent;
import dev.cogwit.trophy.TrophyEvents;
import dev.cogwit.trophy.TrophyPassives;
import dev.cogwit.trophy.TrophyService;
import dev.cogwit.trophy.TwilightPowers;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.Map;

import static dev.cogwit.test.TrophyGameTests.FLOOR;
import static dev.cogwit.test.TrophyGameTests.cancelled;
import static dev.cogwit.test.TrophyGameTests.faceAlongZ;
import static dev.cogwit.test.TrophyGameTests.husk;
import static dev.cogwit.test.TrophyGameTests.onFloor;
import static dev.cogwit.test.TrophyGameTests.player;

/**
 * In-game checks for the thirteen Twilight Forest and Bosses' Rise trophies: their passives and actives. The bosses
 * themselves are never spawned, so none of this needs either mod.
 */
@GameTestHolder(CogwitMod.MODID)
@PrefixGameTestTemplate(false)
public final class TrophyRealmGameTests {
    private TrophyRealmGameTests() {}

    private static boolean fireNear(GameTestHelper helper, int x0, int z0, int x1, int z1) {
        for (int x = x0; x <= x1; x++) {
            for (int y = 0; y <= 6; y++) {
                for (int z = z0; z <= z1; z++) {
                    if (helper.getBlockState(new BlockPos(x, y, z)).is(Blocks.FIRE)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * The test area is boxed in by barriers a block above its three layers, which leaves mobs with their heads in them;
     * powers that rise or fall from above need the sky, so the lid is lifted.
     */
    private static void openSky(GameTestHelper helper) {
        for (int x = -1; x <= 5; x++) {
            for (int z = -1; z <= 5; z++) {
                for (int y = 3; y <= 12; y++) {
                    helper.setBlock(new BlockPos(x, y, z), Blocks.AIR);
                }
            }
        }
    }

    private static AABB around(Player player, double radius) {
        return player.getBoundingBox().inflate(radius);
    }

    private static <T extends Display> List<T> displays(GameTestHelper helper, Class<T> type, Player near) {
        return helper.getLevel().getEntitiesOfClass(type, around(near, 16));
    }

    /** Which way a sword display's blade points, read back from its saved transformation. */
    private static Vec3 bladeAxis(Display.ItemDisplay sword) {
        var rotation = sword.saveWithoutId(new net.minecraft.nbt.CompoundTag()).getCompound("transformation").getList("left_rotation", 5);
        var turn = new org.joml.Quaternionf(rotation.getFloat(0), rotation.getFloat(1), rotation.getFloat(2), rotation.getFloat(3));
        var blade = turn.transform(new org.joml.Vector3f(1, 1, 0).normalize());
        return new Vec3(blade.x, blade.y, blade.z);
    }

    private static float hurtBy(LivingEntity e, float before) {
        return before - e.getHealth();
    }

    // ---------------------------------------------------------------- the pack is complete

    @GameTest(template = "empty", batch = "tr_theThirteenNewBossesAreBuilt")
    public static void theThirteenNewBossesAreBuilt(GameTestHelper helper) {
        Trophy[] newcomers = {Trophy.NAGA, Trophy.LICH, Trophy.MINOSHROOM, Trophy.HYDRA, Trophy.KNIGHT_PHANTOM,
                Trophy.UR_GHAST, Trophy.ALPHA_YETI, Trophy.SNOW_QUEEN, Trophy.YETI, Trophy.SANDWORM, Trophy.INFERNAL_DRAGON,
                Trophy.KRAKEN, Trophy.UNDERWORLD_KNIGHT};
        for (Trophy t : newcomers) {
            helper.assertTrue(t.implemented && t.hasActive && t.cooldownTicks >= 100, t.key() + " is built, with an active");
            helper.assertTrue(Trophy.byEntity(t.entity).orElseThrow() == t, t.key() + " is found by its boss");
        }
        helper.assertTrue(Trophy.values().length == 35, "35 trophies in all");
        helper.succeed();
    }

    // ---------------------------------------------------------------- passives

    @GameTest(template = "empty", batch = "tr_attributePassivesComeAndGoWithTheTrophy")
    public static void attributePassivesComeAndGoWithTheTrophy(GameTestHelper helper) {
        Player player = onFloor(helper, 2.5, 2.5);
        // trophy, the attribute it touches, the modifier it adds and how much
        Object[][] table = {
                {Trophy.NAGA, Attributes.MOVEMENT_SPEED, "trophy_naga_speed", TrophyPassives.NAGA_SPEED_BONUS},
                {Trophy.KNIGHT_PHANTOM, Attributes.ARMOR, "trophy_knight_armor", TrophyPassives.KNIGHT_ARMOR_BONUS},
                {Trophy.KNIGHT_PHANTOM, Attributes.ARMOR_TOUGHNESS, "trophy_knight_toughness", TrophyPassives.KNIGHT_TOUGHNESS_BONUS},
                {Trophy.ALPHA_YETI, Attributes.MAX_HEALTH, "trophy_alpha_yeti_health", TrophyPassives.YETI_HEALTH_BONUS},
                {Trophy.KRAKEN, Attributes.ENTITY_INTERACTION_RANGE, "trophy_kraken_reach", TrophyPassives.KRAKEN_REACH_BONUS}};
        for (Trophy t : new Trophy[] {Trophy.NAGA, Trophy.KNIGHT_PHANTOM, Trophy.ALPHA_YETI, Trophy.KRAKEN}) {
            TrophyService.award(player, t);
        }
        TrophyService.tickPassives(player);
        TrophyService.tickPassives(player);
        for (Object[] row : table) {
            @SuppressWarnings("unchecked")
            var attribute = (net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute>) row[1];
            var modifier = player.getAttribute(attribute).getModifier(CogwitMod.id((String) row[2]));
            helper.assertTrue(modifier != null && modifier.amount() == (double) row[3], row[2] + " is on, once, worth " + row[3]);
        }
        helper.assertTrue(player.getAttributeValue(Attributes.MAX_HEALTH) >= 20 + TrophyPassives.YETI_HEALTH_BONUS, "two more hearts");
        // without the trophies each bonus goes, unless a suited friend nearby still lends the passive
        player.setData(TrophyContent.DATA, new TrophyContent.Data(List.of(), Map.of()));
        TrophyService.tickPassives(player);
        var kept = TrophyService.passives(player);
        for (Object[] row : table) {
            @SuppressWarnings("unchecked")
            var attribute = (net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute>) row[1];
            boolean present = player.getAttribute(attribute).hasModifier(CogwitMod.id((String) row[2]));
            helper.assertTrue(present == kept.contains((Trophy) row[0]), row[2] + " follows the passive, present " + present);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "tr_theLichShieldAbsorbsOneBlowEveryThirtySeconds")
    public static void theLichShieldAbsorbsOneBlowEveryThirtySeconds(GameTestHelper helper) {
        Player player = onFloor(helper, 2.5, 2.5);
        Player bare = player(helper, 0.5, 0.5);
        Husk husk = husk(helper, 3.5, 3.5);
        helper.assertFalse(cancelled(bare, bare.damageSources().mobAttack(husk)), "no trophy, no shield");
        TrophyService.award(player, Trophy.LICH);
        helper.assertTrue(TrophyService.hasPassive(player, Trophy.LICH) && TrophyPassives.lichShieldReady(player),
                "the shield starts up");
        // the shield itself, called directly: through the damage event, suited players of tests running next door
        // would lend this one their passives (co-op sharing) and turn hits away before the shield sees them
        helper.assertFalse(TrophyPassives.lichShield(player, player.damageSources().magic()), "poison and the like are not blows");
        helper.assertFalse(TrophyPassives.lichShield(player, player.damageSources().generic()), "neither is a bare hit from nowhere");
        helper.assertTrue(TrophyPassives.lichShieldReady(player), "and they leave the shield alone");
        helper.assertTrue(TrophyPassives.lichShield(player, player.damageSources().mobAttack(husk)), "a mob's blow is absorbed");
        helper.assertFalse(TrophyPassives.lichShieldReady(player), "the shield is spent");
        helper.assertFalse(TrophyPassives.lichShield(player, player.damageSources().mobAttack(husk)), "the second blow gets through");
        long ready = player.getData(TrophyContent.DATA).readyAt().getOrDefault(TrophyPassives.LICH_KEY, 0L);
        helper.assertTrue(ready >= player.level().getGameTime() + TrophyPassives.LICH_SHIELD_COOLDOWN_TICKS - 1, "thirty seconds to wait");
        TrophyContent.Data data = player.getData(TrophyContent.DATA);
        player.setData(TrophyContent.DATA, new TrophyContent.Data(data.owned(), Map.of(TrophyPassives.LICH_KEY, 0L)));
        helper.assertTrue(TrophyPassives.lichShield(player, player.damageSources().arrow(null, husk)),
                "back again after the wait: arrows count too");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "tr_theMinoshroomOwnerIsStrongAfterAMeal")
    public static void theMinoshroomOwnerIsStrongAfterAMeal(GameTestHelper helper) {
        Player player = onFloor(helper, 2.5, 2.5);
        Player bare = player(helper, 0.5, 0.5);
        TrophyPassives.feast(bare, new ItemStack(Items.BREAD));
        helper.assertFalse(bare.hasEffect(MobEffects.DAMAGE_BOOST), "no trophy, no strength");
        TrophyService.award(player, Trophy.MINOSHROOM);
        TrophyPassives.feast(player, new ItemStack(Items.STICK));
        helper.assertFalse(player.hasEffect(MobEffects.DAMAGE_BOOST), "a stick is not a meal");
        TrophyEvents.onMeal(new LivingEntityUseItemEvent.Finish(player, new ItemStack(Items.BREAD), 0, ItemStack.EMPTY));
        MobEffectInstance strength = player.getEffect(MobEffects.DAMAGE_BOOST);
        helper.assertTrue(strength != null && strength.getAmplifier() == 0 && strength.getDuration() == TrophyPassives.STRENGTH_TICKS,
                "Strength I for twenty seconds");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "tr_theHydraClosesWoundsBelowHalfHealth")
    public static void theHydraClosesWoundsBelowHalfHealth(GameTestHelper helper) {
        Player player = onFloor(helper, 2.5, 2.5);
        TrophyService.award(player, Trophy.HYDRA);
        player.setHealth(15f);
        player.tickCount = TrophyPassives.HYDRA_HEAL_INTERVAL;
        TrophyService.tickPassives(player);
        helper.assertTrue(player.getHealth() == 15f, "above half nothing happens");
        player.setHealth(8f);
        TrophyService.tickPassives(player);
        helper.assertTrue(player.getHealth() == 8f + TrophyPassives.HYDRA_HEAL, "below half the wound closes a little, got " + player.getHealth());
        player.tickCount = TrophyPassives.HYDRA_HEAL_INTERVAL + 10;
        TrophyService.tickPassives(player);
        helper.assertTrue(player.getHealth() == 8f + TrophyPassives.HYDRA_HEAL, "once a second, not at every check");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "tr_theUrGhastLandsSoftlyAndDriftsWhenSneaking")
    public static void theUrGhastLandsSoftlyAndDriftsWhenSneaking(GameTestHelper helper) {
        Player player = onFloor(helper, 2.5, 2.5);
        Player bare = player(helper, 0.5, 0.5);
        TrophyService.award(player, Trophy.UR_GHAST);
        LivingFallEvent mine = new LivingFallEvent(player, 10f, 1f), theirs = new LivingFallEvent(bare, 10f, 1f);
        TrophyEvents.onFall(mine);
        TrophyEvents.onFall(theirs);
        helper.assertTrue(mine.getDamageMultiplier() == TrophyPassives.GHAST_FALL_SHARE, "half the fall damage");
        helper.assertTrue(theirs.getDamageMultiplier() == 1f, "everyone else lands as usual");
        player.setOnGround(false);
        player.setDeltaMovement(0, -0.6, 0);
        TrophyService.tickPassives(player);
        helper.assertFalse(player.hasEffect(MobEffects.SLOW_FALLING), "falling without sneaking is a plain fall");
        player.setShiftKeyDown(true);
        TrophyService.tickPassives(player);
        helper.assertTrue(player.hasEffect(MobEffects.SLOW_FALLING), "sneaking in the air drifts down");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "tr_theSnowQueenChillsHostilesNearby")
    public static void theSnowQueenChillsHostilesNearby(GameTestHelper helper) {
        Player player = onFloor(helper, 2.5, 2.5);
        TrophyService.award(player, Trophy.SNOW_QUEEN);
        Husk near = husk(helper, 0.5, 2.5);
        Husk far = husk(helper, 4.5, 4.5);
        Vec3 away = player.position().add(8, 0, 0);
        far.moveTo(away.x, away.y, away.z);
        far.setNoGravity(true);
        var cow = helper.spawn(EntityType.COW, new BlockPos(2, (int) FLOOR, 4));
        cow.setNoAi(true);
        Player friend = player(helper, 3.5, 2.5);
        TrophyService.tickPassives(player);
        helper.assertTrue(near.hasEffect(MobEffects.MOVEMENT_SLOWDOWN), "the husk beside the owner slows");
        helper.assertFalse(far.hasEffect(MobEffects.MOVEMENT_SLOWDOWN), "one far away does not");
        helper.assertFalse(cow.hasEffect(MobEffects.MOVEMENT_SLOWDOWN) || friend.hasEffect(MobEffects.MOVEMENT_SLOWDOWN),
                "animals and players are left in peace");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "tr_ashlordSetsWhatYouHitAlight")
    public static void ashlordSetsWhatYouHitAlight(GameTestHelper helper) {
        Player player = onFloor(helper, 2.5, 2.5);
        TrophyService.award(player, Trophy.INFERNAL_DRAGON);
        Husk husk = husk(helper, 3.5, 2.5);
        var cow = helper.spawn(EntityType.COW, new BlockPos(1, (int) FLOOR, 2));
        cow.setNoAi(true);
        Player friend = player(helper, 0.5, 4.5);
        for (LivingEntity victim : new LivingEntity[] {husk, cow, friend}) {
            LivingIncomingDamageEvent hit = new LivingIncomingDamageEvent(victim,
                    new net.neoforged.neoforge.common.damagesource.DamageContainer(player.damageSources().playerAttack(player), 5f));
            TrophyEvents.onPlayerHurt(hit);
        }
        helper.assertTrue(husk.isOnFire(), "the husk burns");
        helper.assertTrue(husk.getRemainingFireTicks() >= TrophyPassives.INFERNAL_BURN_SECONDS * 20 - 1, "for three seconds");
        helper.assertFalse(cow.isOnFire() || friend.isOnFire(), "the cow and the friend do not");
        helper.assertFalse(fireNear(helper, 0, 0, 4, 4), "no block burns");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "tr_sirokShrugsOffPoisonAndRunsOnSand")
    public static void sirokShrugsOffPoisonAndRunsOnSand(GameTestHelper helper) {
        Player player = onFloor(helper, 2.5, 2.5);
        TrophyService.award(player, Trophy.SANDWORM);
        var poison = new net.neoforged.neoforge.event.entity.living.MobEffectEvent.Applicable(player,
                new MobEffectInstance(MobEffects.POISON, 100), null);
        TrophyEvents.onEffect(poison);
        helper.assertTrue(poison.getResult() == net.neoforged.neoforge.event.entity.living.MobEffectEvent.Applicable.Result.DO_NOT_APPLY,
                "poison cannot land");
        player.addEffect(new MobEffectInstance(MobEffects.POISON, 100, 0));
        TrophyService.tickPassives(player);
        helper.assertFalse(player.hasEffect(MobEffects.POISON), "and poison already there is lifted");
        helper.assertFalse(player.hasEffect(MobEffects.MOVEMENT_SPEED), "on stone there is no speed");
        helper.setBlock(new BlockPos(2, 1, 2), Blocks.SAND);
        player.setOnGround(true);
        TrophyService.tickPassives(player);
        helper.assertTrue(player.hasEffect(MobEffects.MOVEMENT_SPEED), "on sand the owner is quick");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "tr_helvarGivesBackAHeartForEveryHostileKill")
    public static void helvarGivesBackAHeartForEveryHostileKill(GameTestHelper helper) {
        Player player = onFloor(helper, 2.5, 2.5);
        Player bare = player(helper, 0.5, 0.5);
        TrophyService.award(player, Trophy.UNDERWORLD_KNIGHT);
        Husk husk = husk(helper, 3.5, 3.5);
        var cow = helper.spawn(EntityType.COW, new BlockPos(1, (int) FLOOR, 3));
        cow.setNoAi(true);
        player.setHealth(10f);
        bare.setHealth(10f);
        TrophyPassives.harvest(cow, player.damageSources().playerAttack(player));
        helper.assertTrue(player.getHealth() == 10f, "a cow's soul gives nothing");
        TrophyPassives.harvest(husk, bare.damageSources().playerAttack(bare));
        helper.assertTrue(bare.getHealth() == 10f, "no trophy, no harvest");
        TrophyPassives.harvest(husk, player.damageSources().playerAttack(player));
        helper.assertTrue(player.getHealth() == 10f + TrophyPassives.REAP_HEAL, "a heart for a hostile kill");
        TrophyPassives.harvest(husk, player.damageSources().fall());
        helper.assertTrue(player.getHealth() == 10f + TrophyPassives.REAP_HEAL, "nobody killed it, nothing is gained");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "tr_skorFreezesWaterUnderfootAndOnlyWater")
    public static void skorFreezesWaterUnderfootAndOnlyWater(GameTestHelper helper) {
        Player player = onFloor(helper, 2.5, 2.5);
        Player bare = player(helper, 0.5, 0.5);
        TrophyService.award(player, Trophy.YETI);
        helper.setBlock(new BlockPos(2, 1, 2), Blocks.WATER);
        helper.setBlock(new BlockPos(3, 1, 2), Blocks.WATER);
        helper.setBlock(new BlockPos(0, 1, 0), Blocks.WATER);
        player.setOnGround(true);
        player.setShiftKeyDown(true);
        helper.assertTrue(TrophyPassives.iceRoad(player) == 0, "sneaking walks on the old floor: no ice");
        player.setShiftKeyDown(false);
        int frozen = TrophyPassives.iceRoad(player);
        helper.assertTrue(frozen >= 2, "the water under the owner freezes, " + frozen + " blocks");
        helper.assertBlockPresent(Blocks.FROSTED_ICE, new BlockPos(2, 1, 2));
        helper.assertBlockPresent(Blocks.FROSTED_ICE, new BlockPos(3, 1, 2));
        helper.assertBlockPresent(Blocks.STONE, new BlockPos(2, 1, 3));
        helper.assertBlockPresent(Blocks.WATER, new BlockPos(0, 1, 0));
        bare.setOnGround(true);
        helper.assertTrue(TrophyPassives.iceRoad(bare) == 0, "no trophy, no ice");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "tr_theNewTrophiesCleanUpWhatTheyLeave")
    public static void theNewTrophiesCleanUpWhatTheyLeave(GameTestHelper helper) {
        // a display saved with a world after a stop mid-ability is thrown out when it loads again
        Display.BlockDisplay shard = EntityType.BLOCK_DISPLAY.create(helper.getLevel());
        shard.addTag("cogwit_fx");
        var join = new net.neoforged.neoforge.event.entity.EntityJoinLevelEvent(shard, helper.getLevel(), true);
        TrophyEvents.onJoin(join);
        helper.assertTrue(join.isCanceled(), "a leftover display is refused on load");
        Display.ItemDisplay sword = EntityType.ITEM_DISPLAY.create(helper.getLevel());
        sword.addTag("cogwit_fx");
        var swordJoin = new net.neoforged.neoforge.event.entity.EntityJoinLevelEvent(sword, helper.getLevel(), true);
        TrophyEvents.onJoin(swordJoin);
        helper.assertTrue(swordJoin.isCanceled(), "and so is a leftover sword");
        helper.succeed();
    }

    // ---------------------------------------------------------------- actives

    @GameTest(template = "empty", batch = "tr_realmPowersNeedSomeoneToHit")
    public static void realmPowersNeedSomeoneToHit(GameTestHelper helper) {
        Player player = onFloor(helper, 2.5, 2.5);
        faceAlongZ(helper, player);
        for (Trophy t : new Trophy[] {Trophy.NAGA, Trophy.LICH, Trophy.MINOSHROOM, Trophy.KNIGHT_PHANTOM, Trophy.UR_GHAST,
                Trophy.ALPHA_YETI, Trophy.SNOW_QUEEN, Trophy.INFERNAL_DRAGON, Trophy.KRAKEN, Trophy.SANDWORM,
                Trophy.UNDERWORLD_KNIGHT, Trophy.YETI}) {
            TrophyService.award(player, t);
            helper.assertFalse(TrophyService.castTrophy(player, t), t.key() + " with nobody around does nothing, but found "
                    + helper.getLevel().getEntitiesOfClass(LivingEntity.class, around(player, 30), e -> e != player));
            helper.assertTrue(player.getData(TrophyContent.DATA).readyAt().getOrDefault(t.key(), 0L) == 0L,
                    t.key() + " keeps its cooldown when it did not fire");
        }
        helper.assertTrue(displays(helper, Display.class, player).isEmpty(), "and leaves nothing standing");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "tr_realmPowersNeedTheFullSet")
    public static void realmPowersNeedTheFullSet(GameTestHelper helper) {
        Player bare = player(helper, 2.5, 2.5);
        Husk husk = husk(helper, 3.5, 3.5);
        for (Trophy t : new Trophy[] {Trophy.NAGA, Trophy.LICH, Trophy.MINOSHROOM, Trophy.HYDRA, Trophy.KNIGHT_PHANTOM,
                Trophy.UR_GHAST, Trophy.ALPHA_YETI, Trophy.SNOW_QUEEN, Trophy.YETI, Trophy.SANDWORM, Trophy.INFERNAL_DRAGON,
                Trophy.KRAKEN, Trophy.UNDERWORLD_KNIGHT}) {
            TrophyService.award(bare, t);
            helper.assertFalse(TrophyService.castTrophy(bare, t), t.key() + " needs the armor");
        }
        helper.assertTrue(husk.getHealth() == husk.getMaxHealth(), "nothing happened");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "tr_theNagaCoilsWhatItCatches", timeoutTicks = 140)
    public static void theNagaCoilsWhatItCatches(GameTestHelper helper) {
        Player player = onFloor(helper, 2.5, 0.5);
        faceAlongZ(helper, player);
        TrophyService.award(player, Trophy.NAGA);
        Husk husk = husk(helper, 2.5, 3.5);
        Vec3 anchor = husk.position();
        Player friend = player(helper, 3.5, 3.5);
        float before = husk.getHealth(), friendHp = friend.getHealth();
        helper.assertTrue(TrophyService.castTrophy(player, Trophy.NAGA), "the charge is cast");
        helper.assertFalse(TrophyService.castTrophy(player, Trophy.NAGA), "then it recharges");
        helper.runAfterDelay(TwilightPowers.COIL_WINDUP + 8, () -> {
            helper.assertTrue(husk.getHealth() <= before - TwilightPowers.COIL_DAMAGE + 0.01f, "the husk is hit by the rush");
            MobEffectInstance hold = husk.getEffect(MobEffects.MOVEMENT_SLOWDOWN);
            helper.assertTrue(hold != null && hold.getAmplifier() >= 6, "and held fast");
            helper.assertTrue(husk.hasEffect(MobEffects.WEAKNESS), "and weakened");
            // an attempt to tear free is undone
            husk.teleportTo(anchor.x + 1.4, husk.getY(), anchor.z);
        });
        helper.runAfterDelay(TwilightPowers.COIL_WINDUP + 11, () ->
                helper.assertTrue(Math.hypot(husk.getX() - anchor.x, husk.getZ() - anchor.z) < 0.6, "it cannot leave the coil"));
        helper.runAfterDelay(TwilightPowers.COIL_WINDUP + TwilightPowers.COIL_TICKS + 14, () -> {
            float lost = hurtBy(husk, before);
            helper.assertTrue(lost >= TwilightPowers.COIL_DAMAGE + 4 * TwilightPowers.SQUEEZE_DAMAGE - 0.01f,
                    "squeezed again and again, lost " + lost);
            helper.assertTrue(friend.getHealth() == friendHp, "the friend is spared");
            helper.succeed();
        });
    }

    @GameTest(template = "empty", batch = "tr_theLichSendsSoulBoltsAtEnemies", timeoutTicks = 120)
    public static void theLichSendsSoulBoltsAtEnemies(GameTestHelper helper) {
        openSky(helper);
        Player player = onFloor(helper, 2.5, 0.5);
        faceAlongZ(helper, player);
        TrophyService.award(player, Trophy.LICH);
        Husk husk = husk(helper, 2.5, 3.5);
        Player friend = player(helper, 3.5, 3.5);
        var cow = helper.spawn(EntityType.COW, new BlockPos(1, (int) FLOOR, 3));
        cow.setNoAi(true);
        float before = husk.getHealth(), friendHp = friend.getHealth(), cowHp = cow.getHealth(), ownHp = player.getHealth();
        helper.assertTrue(TrophyService.castTrophy(player, Trophy.LICH), "the skulls rise");
        helper.runAfterDelay(TwilightPowers.BOLT_WINDUP - 4, () ->
                helper.assertTrue(husk.getHealth() == before, "still gathering: the husk is untouched"));
        helper.runAfterDelay(TwilightPowers.BOLT_WINDUP + TwilightPowers.BOLTS * TwilightPowers.BOLT_GAP + 30, () -> {
            helper.assertTrue(husk.isDeadOrDying() || hurtBy(husk, before) >= 2 * TwilightPowers.BOLT_DAMAGE - 0.01f,
                    "several bolts hit the husk, health " + husk.getHealth());
            helper.assertTrue(friend.getHealth() == friendHp && player.getHealth() >= ownHp && cow.getHealth() == cowHp,
                    "no player or animal is hurt: friend " + friend.getHealth() + "/" + friendHp + ", caster " + player.getHealth() + "/"
                            + ownHp + ", cow " + cow.getHealth() + "/" + cowHp);
            helper.succeed();
        });
    }

    @GameTest(template = "empty", batch = "tr_theMinoshroomLeapsAndSlams", timeoutTicks = 100)
    public static void theMinoshroomLeapsAndSlams(GameTestHelper helper) {
        openSky(helper);
        Player player = onFloor(helper, 2.5, 0.5);
        faceAlongZ(helper, player);
        TrophyService.award(player, Trophy.MINOSHROOM);
        Husk husk = husk(helper, 2.5, 3.5);
        Player friend = player(helper, 0.5, 2.5);
        var villager = helper.spawn(EntityType.VILLAGER, new BlockPos(4, (int) FLOOR, 1));
        villager.setNoAi(true);
        float before = husk.getHealth(), friendHp = friend.getHealth(), villagerHp = villager.getHealth(), ownHp = player.getHealth();
        helper.assertTrue(TrophyService.castTrophy(player, Trophy.MINOSHROOM), "the leap is cast");
        helper.runAfterDelay(2, () -> helper.assertTrue(player.getDeltaMovement().y > 0.2 || !player.onGround(), "the caster is in the air"));
        helper.runAfterDelay(TwilightPowers.LEAP_MAX_TICKS + 10, () -> {
            helper.assertTrue(husk.isDeadOrDying() || husk.getHealth() < before, "the slam hurts the husk, health " + husk.getHealth());
            helper.assertTrue(friend.getHealth() == friendHp && villager.getHealth() == villagerHp, "no player or villager is hurt");
            helper.assertTrue(player.getHealth() >= ownHp, "and the caster takes no fall damage");
            helper.assertBlockPresent(Blocks.STONE, new BlockPos(2, 1, 3));
            helper.succeed();
        });
    }

    @GameTest(template = "empty", batch = "tr_theHydraBreathesFireFromThreeHeads", timeoutTicks = 100)
    public static void theHydraBreathesFireFromThreeHeads(GameTestHelper helper) {
        Player player = onFloor(helper, 2.5, 0.5);
        faceAlongZ(helper, player);
        TrophyService.award(player, Trophy.HYDRA);
        Husk ahead = husk(helper, 2.5, 3.5);
        Husk behind = husk(helper, 2.5, 2.5);
        Vec3 back = player.position().add(0, 0, -3);
        behind.moveTo(back.x, back.y, back.z);
        Player friend = player(helper, 3.5, 3.5);
        float aheadHp = ahead.getHealth(), behindHp = behind.getHealth(), friendHp = friend.getHealth();
        helper.assertTrue(TrophyService.castTrophy(player, Trophy.HYDRA), "the heads rear up");
        helper.assertFalse(TrophyService.castTrophy(player, Trophy.HYDRA), "then they recover");
        helper.runAfterDelay(TwilightPowers.HEADS_WINDUP - 2, () ->
                helper.assertTrue(ahead.getHealth() == aheadHp, "still gathering: nothing is burnt yet"));
        helper.runAfterDelay(TwilightPowers.HEADS_WINDUP + 30, () -> {
            helper.assertTrue(ahead.isDeadOrDying() || ahead.getHealth() < aheadHp, "the husk ahead is burnt");
            helper.assertTrue(ahead.isOnFire() || ahead.isDeadOrDying(), "and burning");
            helper.assertTrue(behind.getHealth() == behindHp, "the one behind is not");
            helper.assertTrue(friend.getHealth() == friendHp, "neither is the friend");
            helper.assertFalse(fireNear(helper, 0, 0, 4, 4), "no block is set alight");
            helper.succeed();
        });
    }

    @GameTest(template = "empty", batch = "tr_theKnightPhantomThrowsSwordsThatVanish", timeoutTicks = 140)
    public static void theKnightPhantomThrowsSwordsThatVanish(GameTestHelper helper) {
        Player player = onFloor(helper, 2.5, 0.5);
        faceAlongZ(helper, player);
        TrophyService.award(player, Trophy.KNIGHT_PHANTOM);
        Husk husk = husk(helper, 2.5, 3.5);
        Player friend = player(helper, 3.5, 3.5);
        var villager = helper.spawn(EntityType.VILLAGER, new BlockPos(1, (int) FLOOR, 3));
        villager.setNoAi(true);
        float before = husk.getHealth(), friendHp = friend.getHealth(), villagerHp = villager.getHealth();
        helper.assertTrue(TrophyService.castTrophy(player, Trophy.KNIGHT_PHANTOM), "the swords appear");
        List<Display.ItemDisplay> swords = displays(helper, Display.ItemDisplay.class, player);
        helper.assertTrue(swords.size() == TwilightPowers.SWORDS, "six swords hover");
        helper.assertTrue(swords.stream().allMatch(sword -> sword.getSlot(0).get().is(Items.DIAMOND_SWORD)), "each one a sword");
        helper.assertTrue(swords.stream().allMatch(sword -> bladeAxis(sword).y > 0.99), "all of them hang point up");
        helper.runAfterDelay(TwilightPowers.SWORD_WINDUP - 4, () -> helper.assertTrue(husk.getHealth() == before, "none has flown yet"));
        helper.runAfterDelay(TwilightPowers.SWORD_WINDUP + 1, () -> {
            // the first sword to go has swung round from pointing up to pointing towards the husk (it is already on its way)
            Vec3 toHusk = husk.getBoundingBox().getCenter().subtract(player.position()).normalize();
            long aimed = swords.stream().filter(sword -> !sword.isRemoved() && bladeAxis(sword).y < 0.5 && bladeAxis(sword).dot(toHusk) > 0.7)
                    .count();
            helper.assertTrue(aimed >= 1, "a sword points at its mark");
        });
        helper.runAfterDelay(TwilightPowers.SWORD_WINDUP + TwilightPowers.SWORDS * TwilightPowers.SWORD_GAP + 20, () -> {
            helper.assertTrue(husk.isDeadOrDying() || hurtBy(husk, before) >= 2 * TwilightPowers.SWORD_DAMAGE - 0.01f,
                    "the swords cut the husk, health " + husk.getHealth());
            helper.assertTrue(friend.getHealth() == friendHp && villager.getHealth() == villagerHp, "no player or villager is hurt");
        });
        helper.runAfterDelay(TwilightPowers.SWORD_WINDUP + TwilightPowers.SWORDS * TwilightPowers.SWORD_GAP + TwilightPowers.SWORD_LIFE_TICKS + 20, () -> {
            helper.assertTrue(displays(helper, Display.class, player).isEmpty(), "every sword has vanished");
            helper.succeed();
        });
    }

    @GameTest(template = "empty", batch = "tr_theUrGhastRainsTearBombs", timeoutTicks = 140)
    public static void theUrGhastRainsTearBombs(GameTestHelper helper) {
        Player player = onFloor(helper, 2.5, 0.5);
        faceAlongZ(helper, player);
        TrophyService.award(player, Trophy.UR_GHAST);
        Husk husk = husk(helper, 2.5, 3.5);
        Player friend = player(helper, 3.5, 3.5);
        float before = husk.getHealth(), friendHp = friend.getHealth(), ownHp = player.getHealth();
        helper.assertTrue(TrophyService.castTrophy(player, Trophy.UR_GHAST), "the tears gather");
        helper.runAfterDelay(TwilightPowers.TEAR_WINDUP - 3, () -> helper.assertTrue(husk.getHealth() == before, "none has fallen yet"));
        helper.runAfterDelay(TwilightPowers.TEAR_WINDUP + TwilightPowers.TEARS * TwilightPowers.TEAR_GAP + 30, () -> {
            helper.assertTrue(husk.isDeadOrDying() || hurtBy(husk, before) >= TwilightPowers.TEAR_DAMAGE - 0.01f,
                    "the bombs burst on the husk, health " + husk.getHealth());
            helper.assertTrue(husk.isDeadOrDying() || husk.hasEffect(MobEffects.WEAKNESS), "and weaken it");
            helper.assertTrue(friend.getHealth() == friendHp && player.getHealth() >= ownHp, "no player is hurt");
            helper.assertFalse(fireNear(helper, 0, 0, 4, 4), "nothing burns");
            helper.assertBlockPresent(Blocks.STONE, new BlockPos(2, 1, 3));
            helper.succeed();
        });
    }

    @GameTest(template = "empty", batch = "tr_theAlphaYetiHurlsAnIceBlock", timeoutTicks = 140)
    public static void theAlphaYetiHurlsAnIceBlock(GameTestHelper helper) {
        Player player = onFloor(helper, 2.5, 0.5);
        faceAlongZ(helper, player);
        TrophyService.award(player, Trophy.ALPHA_YETI);
        Husk husk = husk(helper, 2.5, 3.5);
        Player friend = player(helper, 4.5, 4.5);
        float before = husk.getHealth(), friendHp = friend.getHealth();
        helper.assertTrue(TrophyService.castTrophy(player, Trophy.ALPHA_YETI), "the slab is torn out of the ground");
        helper.assertTrue(displays(helper, Display.BlockDisplay.class, player).size() == 1, "one slab of ice");
        helper.runAfterDelay(TwilightPowers.ICE_WINDUP - 4, () -> helper.assertTrue(husk.getHealth() == before, "it is still being hoisted"));
        helper.runAfterDelay(TwilightPowers.ICE_WINDUP + 40, () -> {
            helper.assertTrue(husk.isDeadOrDying() || hurtBy(husk, before) >= TwilightPowers.ICE_DAMAGE * 0.6f,
                    "the ice shatters on the husk, health " + husk.getHealth());
            helper.assertTrue(husk.isDeadOrDying() || husk.getTicksFrozen() > 0 && husk.hasEffect(MobEffects.MOVEMENT_SLOWDOWN),
                    "freezing and slowing it");
            helper.assertTrue(friend.getHealth() == friendHp, "the friend is spared");
            helper.assertTrue(displays(helper, Display.class, player).isEmpty(), "and the slab is gone");
            helper.succeed();
        });
    }

    @GameTest(template = "empty", batch = "tr_theSnowQueenLocksEnemiesInIce", timeoutTicks = 200)
    public static void theSnowQueenLocksEnemiesInIce(GameTestHelper helper) {
        Player player = onFloor(helper, 2.5, 0.5);
        faceAlongZ(helper, player);
        TrophyService.award(player, Trophy.SNOW_QUEEN);
        Husk husk = husk(helper, 2.5, 3.5);
        Player friend = player(helper, 4.5, 4.5);
        Vec3 anchor = husk.position();
        float before = husk.getHealth(), friendHp = friend.getHealth();
        helper.assertTrue(TrophyService.castTrophy(player, Trophy.SNOW_QUEEN), "the cage is called");
        helper.runAfterDelay(TwilightPowers.PRISON_WINDUP - 3, () -> helper.assertTrue(displays(helper, Display.class, player).isEmpty(),
                "the ice has not risen yet"));
        helper.runAfterDelay(TwilightPowers.PRISON_WINDUP + 8, () -> {
            helper.assertTrue(displays(helper, Display.BlockDisplay.class, player).size() == 8, "eight bars of ice stand round the husk");
            husk.teleportTo(anchor.x + 1.5, husk.getY(), anchor.z);
        });
        helper.runAfterDelay(TwilightPowers.PRISON_WINDUP + 11, () -> {
            helper.assertTrue(Math.hypot(husk.getX() - anchor.x, husk.getZ() - anchor.z) < 0.5, "the prisoner cannot get out");
            MobEffectInstance hold = husk.getEffect(MobEffects.MOVEMENT_SLOWDOWN);
            helper.assertTrue(hold != null && hold.getAmplifier() >= 6, "and cannot move");
        });
        helper.runAfterDelay(TwilightPowers.PRISON_WINDUP + TwilightPowers.PRISON_TICKS + 4, () -> {
            float lost = hurtBy(husk, before);
            helper.assertTrue(husk.isDeadOrDying() || lost >= TwilightPowers.PRISON_SHATTER_DAMAGE + 3 * TwilightPowers.PRISON_PULSE_DAMAGE - 0.01f,
                    "the cold gnawed and the cage burst, lost " + lost);
            helper.assertTrue(friend.getHealth() == friendHp, "the friend is spared");
        });
        helper.runAfterDelay(TwilightPowers.PRISON_WINDUP + TwilightPowers.PRISON_TICKS + 14, () -> {
            helper.assertTrue(displays(helper, Display.class, player).isEmpty(), "the cage has sunk away");
            helper.succeed();
        });
    }

    @GameTest(template = "empty", batch = "tr_ashlordDivesOnTheTargetAndBurnsIt", timeoutTicks = 120)
    public static void ashlordDivesOnTheTargetAndBurnsIt(GameTestHelper helper) {
        openSky(helper);
        Player player = onFloor(helper, 2.5, 0.5);
        faceAlongZ(helper, player);
        TrophyService.award(player, Trophy.INFERNAL_DRAGON);
        Husk husk = husk(helper, 2.5, 3.5);
        Player friend = player(helper, 4.5, 4.5);
        var cow = helper.spawn(EntityType.COW, new BlockPos(0, (int) FLOOR, 4));
        cow.setNoAi(true);
        float before = husk.getHealth(), friendHp = friend.getHealth(), cowHp = cow.getHealth(), ownHp = player.getHealth();
        helper.assertTrue(TrophyService.castTrophy(player, Trophy.INFERNAL_DRAGON), "the dive is cast");
        helper.runAfterDelay(BossRisePowers.DIVE_WINDUP + 2, () -> helper.assertTrue(player.getDeltaMovement().y > 0.3 || !player.onGround(),
                "the caster has sprung into the air"));
        helper.runAfterDelay(BossRisePowers.DIVE_MAX + BossRisePowers.DIVE_WINDUP + 12, () -> {
            helper.assertTrue(husk.isDeadOrDying() || hurtBy(husk, before) >= BossRisePowers.DIVE_DAMAGE * 0.6f,
                    "the landing hurts the husk, health " + husk.getHealth());
            helper.assertTrue(husk.isOnFire() || husk.isDeadOrDying(), "and sets it alight");
            helper.assertTrue(friend.getHealth() == friendHp && cow.getHealth() == cowHp && !friend.isOnFire() && !cow.isOnFire(),
                    "no player or animal is hurt or burnt");
            helper.assertTrue(player.getHealth() >= ownHp && !player.isOnFire(), "the caster pays nothing for the dive");
            helper.assertFalse(fireNear(helper, 0, 0, 4, 6), "no block is set alight");
            helper.assertBlockPresent(Blocks.STONE, new BlockPos(2, 1, 3));
            helper.succeed();
        });
    }

    @GameTest(template = "empty", batch = "tr_nerakyssLiftsSqueezesAndSlamsItsPrey", timeoutTicks = 160)
    public static void nerakyssLiftsSqueezesAndSlamsItsPrey(GameTestHelper helper) {
        openSky(helper);
        Player player = onFloor(helper, 2.5, 0.5);
        faceAlongZ(helper, player);
        TrophyService.award(player, Trophy.KRAKEN);
        Husk husk = husk(helper, 2.5, 3.5);
        double floor = husk.getY();
        Player friend = player(helper, 4.5, 4.5);
        var cow = helper.spawn(EntityType.COW, new BlockPos(0, (int) FLOOR, 3));
        cow.setNoAi(true);
        float before = husk.getHealth(), friendHp = friend.getHealth(), cowHp = cow.getHealth();
        helper.assertTrue(TrophyService.castTrophy(player, Trophy.KRAKEN), "the tentacles are called");
        helper.runAfterDelay(BossRisePowers.TENTACLE_WINDUP - 3, () -> helper.assertTrue(husk.getHealth() == before && husk.getY() == floor,
                "the water stirs, nothing has happened yet"));
        helper.runAfterDelay(BossRisePowers.TENTACLE_WINDUP + 16, () -> {
            helper.assertTrue(husk.getY() > floor + 1.0, "the husk is held in the air, " + (husk.getY() - floor) + " up");
            helper.assertTrue(displays(helper, Display.BlockDisplay.class, player).size() == 3, "a tentacle of three parts");
            helper.assertTrue(hurtBy(husk, before) >= BossRisePowers.GRAB_DAMAGE, "and squeezed");
        });
        helper.runAfterDelay(BossRisePowers.TENTACLE_WINDUP + BossRisePowers.GRAB_TICKS + 20, () -> {
            helper.assertTrue(husk.isDeadOrDying() || hurtBy(husk, before) >= BossRisePowers.GRAB_SLAM_DAMAGE + 3 * BossRisePowers.GRAB_DAMAGE,
                    "squeezed and dashed down, health " + husk.getHealth());
            // a mob without AI has no gravity in this arena, so it is the downward dash that shows it was let go
            helper.assertTrue(husk.getY() < floor + 0.8 || husk.getDeltaMovement().y < -0.5, "dashed back down, " + (husk.getY() - floor)
                    + " up, vy " + husk.getDeltaMovement().y);
            helper.assertTrue(friend.getHealth() == friendHp && cow.getHealth() == cowHp, "no player or animal is touched");
            helper.assertTrue(displays(helper, Display.class, player).isEmpty(), "the tentacle has sunk away");
            helper.succeed();
        });
    }

    @GameTest(template = "empty", batch = "tr_sirokBurrowsAndErupts", timeoutTicks = 140)
    public static void sirokBurrowsAndErupts(GameTestHelper helper) {
        Player player = onFloor(helper, 2.5, 0.5);
        faceAlongZ(helper, player);
        TrophyService.award(player, Trophy.SANDWORM);
        Husk husk = husk(helper, 2.5, 3.5);
        Player friend = player(helper, 4.5, 3.5);
        var villager = helper.spawn(EntityType.VILLAGER, new BlockPos(0, (int) FLOOR, 3));
        villager.setNoAi(true);
        float before = husk.getHealth(), friendHp = friend.getHealth(), villagerHp = villager.getHealth(), ownHp = player.getHealth();
        helper.assertTrue(TrophyService.castTrophy(player, Trophy.SANDWORM), "the worm burrows");
        helper.runAfterDelay(2, () -> helper.assertTrue(husk.getHealth() == before, "the ridge is still running under the ground"));
        helper.runAfterDelay(14, () -> {
            helper.assertTrue(husk.isDeadOrDying() || hurtBy(husk, before) >= BossRisePowers.ERUPT_DAMAGE - 0.01f,
                    "the eruption hurts the husk, health " + husk.getHealth());
            helper.assertTrue(husk.isDeadOrDying() || husk.hasEffect(MobEffects.BLINDNESS), "and blinds it");
            // only this arena: tests running next door raise displays of their own about ten blocks away
            var sand = helper.getLevel().getEntitiesOfClass(Display.BlockDisplay.class, around(player, 6));
            helper.assertTrue(sand.size() == 7, "a body and six fangs of sand, found " + sand.size());
            helper.assertTrue(friend.getHealth() == friendHp && villager.getHealth() == villagerHp && player.getHealth() >= ownHp,
                    "no player or villager is hurt");
        });
        helper.runAfterDelay(BossRisePowers.ERUPT_HOLD_TICKS + 24, () -> {
            helper.assertTrue(helper.getLevel().getEntitiesOfClass(Display.class, around(player, 6)).isEmpty(), "the fangs have sunk away");
            helper.assertBlockPresent(Blocks.STONE, new BlockPos(2, 1, 3));
            helper.succeed();
        });
    }

    @GameTest(template = "empty", batch = "tr_helvarTearsRiftsAndSlashesTwice", timeoutTicks = 100)
    public static void helvarTearsRiftsAndSlashesTwice(GameTestHelper helper) {
        Player player = onFloor(helper, 2.5, 0.5);
        faceAlongZ(helper, player);
        TrophyService.award(player, Trophy.UNDERWORLD_KNIGHT);
        Husk husk = husk(helper, 2.5, 3.5);
        Player friend = player(helper, 3.5, 3.5);
        var cow = helper.spawn(EntityType.COW, new BlockPos(1, (int) FLOOR, 3));
        cow.setNoAi(true);
        float before = husk.getHealth(), friendHp = friend.getHealth(), cowHp = cow.getHealth();
        helper.assertTrue(TrophyService.castTrophy(player, Trophy.UNDERWORLD_KNIGHT), "the rift opens");
        helper.runAfterDelay(BossRisePowers.RIFT_OPEN_TICKS - 3, () -> helper.assertTrue(husk.getHealth() == before, "the seam is still opening"));
        helper.runAfterDelay(BossRisePowers.RIFT_OPEN_TICKS + 2, () -> helper.assertTrue(
                hurtBy(husk, before) >= BossRisePowers.RIFT_DAMAGE - 0.01f, "the first slash lands, lost " + hurtBy(husk, before)));
        helper.runAfterDelay(BossRisePowers.RIFT_OPEN_TICKS + BossRisePowers.RIFT_SECOND_SLASH + 6, () -> {
            helper.assertTrue(husk.isDeadOrDying() || hurtBy(husk, before) >= 2 * BossRisePowers.RIFT_DAMAGE - 0.01f,
                    "the crossing slash lands too, lost " + hurtBy(husk, before));
            helper.assertTrue(friend.getHealth() == friendHp && cow.getHealth() == cowHp, "no player or animal is cut");
            helper.assertBlockPresent(Blocks.STONE, new BlockPos(2, 1, 3));
            helper.succeed();
        });
    }

    @GameTest(template = "empty", batch = "tr_skorPoundsTheGroundWithIce", timeoutTicks = 140)
    public static void skorPoundsTheGroundWithIce(GameTestHelper helper) {
        Player player = onFloor(helper, 2.5, 2.5);
        TrophyService.award(player, Trophy.YETI);
        Husk near = husk(helper, 4.5, 2.5);
        Player friend = player(helper, 0.5, 2.5);
        var cow = helper.spawn(EntityType.COW, new BlockPos(2, (int) FLOOR, 4));
        cow.setNoAi(true);
        float before = near.getHealth(), friendHp = friend.getHealth(), cowHp = cow.getHealth(), ownHp = player.getHealth();
        helper.assertTrue(TrophyService.castTrophy(player, Trophy.YETI), "the pound begins");
        helper.assertFalse(TrophyService.castTrophy(player, Trophy.YETI), "then it recharges");
        // when the pound lands depends on the leap, so the most spikes ever seen standing at once is what counts
        int[] peak = {0};
        helper.onEachTick(() -> peak[0] = Math.max(peak[0], displays(helper, Display.BlockDisplay.class, player).size()));
        helper.runAfterDelay(BossRisePowers.POUND_HOP_MAX + 12, () -> {
            helper.assertTrue(near.isDeadOrDying() || hurtBy(near, before) >= BossRisePowers.POUND_DAMAGE * 0.6f,
                    "the wave hurts the husk, health " + near.getHealth());
            helper.assertTrue(near.isDeadOrDying() || near.getTicksFrozen() > 0 && near.hasEffect(MobEffects.MOVEMENT_SLOWDOWN),
                    "freezing and slowing it");
            helper.assertTrue(peak[0] == BossRisePowers.POUND_RAYS * BossRisePowers.POUND_WAVES,
                    "spikes of ice stand in rings, found at most " + peak[0]);
            helper.assertTrue(friend.getHealth() == friendHp && cow.getHealth() == cowHp && player.getHealth() >= ownHp,
                    "no player or animal is hurt, nor the caster");
        });
        helper.runAfterDelay(BossRisePowers.POUND_HOP_MAX + BossRisePowers.POUND_HOLD_TICKS + 24, () -> {
            helper.assertTrue(displays(helper, Display.class, player).isEmpty(), "the spikes have sunk away");
            helper.assertBlockPresent(Blocks.STONE, new BlockPos(2, 1, 3));
            helper.succeed();
        });
    }
}
