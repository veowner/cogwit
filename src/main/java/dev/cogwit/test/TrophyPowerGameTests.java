package dev.cogwit.test;

import dev.cogwit.CogwitMod;
import dev.cogwit.trophy.AetherPowers;
import dev.cogwit.trophy.MowziePowers;
import dev.cogwit.trophy.PowerMath;
import dev.cogwit.trophy.SpellbookPowers;
import dev.cogwit.trophy.Trophy;
import dev.cogwit.trophy.TrophyContent;
import dev.cogwit.trophy.TrophyEvents;
import dev.cogwit.trophy.TrophyPassives;
import dev.cogwit.trophy.TrophyService;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.entity.monster.Skeleton;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.EvokerFangs;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
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
import static dev.cogwit.test.TrophyGameTests.suited;

/** In-game checks for the ten Mowzie's Mobs, Aether and Iron's Spellbooks trophies: their passives and actives. */
@GameTestHolder(CogwitMod.MODID)
@PrefixGameTestTemplate(false)
public final class TrophyPowerGameTests {
    private TrophyPowerGameTests() {}

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

    // ---------------------------------------------------------------- passives

    @GameTest(template = "empty", batch = "tp_everyPowerOfThePackIsBuilt")
    public static void everyPowerOfThePackIsBuilt(GameTestHelper helper) {
        for (Trophy t : Trophy.values()) {
            helper.assertTrue(t.implemented, t.key() + " should be implemented");
        }
        helper.assertFalse(Trophy.FERROUS_WROUGHTNAUT.hasActive, "the Wroughtnaut stays passive only");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "tp_theWroughtnautSendsAThirdOfAMeleeBlowBack")
    public static void theWroughtnautSendsAThirdOfAMeleeBlowBack(GameTestHelper helper) {
        Player player = onFloor(helper, 2.5, 2.5);
        TrophyService.award(player, Trophy.FERROUS_WROUGHTNAUT);
        Husk husk = husk(helper, 3.5, 2.5);
        float before = husk.getHealth();
        TrophyPassives.reflect(player, player.damageSources().mobAttack(husk), 10f);
        // thorns damage is softened by the husk's two armor points, so a little under 3 arrives
        float lost = before - husk.getHealth();
        helper.assertTrue(lost > 2f && lost <= PowerMath.reflected(10f) + 0.01f, "30% of 10 comes back, husk lost " + lost);
        float afterMelee = husk.getHealth();
        husk.invulnerableTime = 0;
        TrophyPassives.reflect(player, player.damageSources().arrow(null, husk), 10f);
        helper.assertTrue(husk.getHealth() == afterMelee, "arrows are not melee and are not reflected");
        Player bare = player(helper, 0.5, 0.5);
        Husk other = husk(helper, 1.5, 0.5);
        float otherBefore = other.getHealth();
        TrophyPassives.reflect(bare, bare.damageSources().mobAttack(other), 10f);
        helper.assertTrue(other.getHealth() == otherBefore, "no trophy, no reflection");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "tp_frostmawShrugsOffFrostAndSlowness")
    public static void frostmawShrugsOffFrostAndSlowness(GameTestHelper helper) {
        Player player = onFloor(helper, 2.5, 2.5);
        TrophyService.award(player, Trophy.FROSTMAW);
        player.setTicksFrozen(200);
        TrophyService.tickPassives(player);
        helper.assertTrue(player.getTicksFrozen() == 0, "the frost melts off");
        helper.assertTrue(cancelled(player, player.damageSources().freeze()), "freezing damage is cancelled");
        MobEffectInstance slow = new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 100, 2);
        MobEffectEvent.Applicable event = new MobEffectEvent.Applicable(player, slow, null);
        TrophyEvents.onEffect(event);
        helper.assertTrue(event.getResult() == MobEffectEvent.Applicable.Result.DO_NOT_APPLY, "slowness cannot land");
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 100, 0));
        TrophyService.tickPassives(player);
        helper.assertFalse(player.hasEffect(MobEffects.MOVEMENT_SLOWDOWN), "and one already there is lifted");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "tp_umvuthiMendsInTheSun")
    public static void umvuthiMendsInTheSun(GameTestHelper helper) {
        Player player = onFloor(helper, 2.5, 2.5);
        TrophyService.award(player, Trophy.UMVUTHI);
        player.setHealth(10f);
        TrophyPassives.sunHeal(player);
        helper.assertTrue(player.getHealth() == 10f + TrophyPassives.SUN_HEAL, "a sunbeam's worth of healing");
        // the passive heals through tickPassives only when the sun is really on the player
        player.setHealth(10f);
        TrophyService.tickPassives(player);
        boolean sun = TrophyPassives.underSun(player);
        helper.assertTrue(sun == player.getHealth() > 10f || player.tickCount % TrophyPassives.SUN_HEAL_INTERVAL != 0,
                "healed exactly when under the sun (sun=" + sun + ", health " + player.getHealth() + ")");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "tp_theSculptorCatchesALongFallWithATemporaryPlatform", timeoutTicks = 140)
    public static void theSculptorCatchesALongFallWithATemporaryPlatform(GameTestHelper helper) {
        Player player = onFloor(helper, 2.5, 2.5);
        TrophyService.award(player, Trophy.SCULPTOR);
        // the test area is only three blocks tall, so the player hangs just above its floor
        Vec3 high = helper.absoluteVec(new Vec3(2.5, FLOOR + 1.5, 2.5));
        player.moveTo(high.x, high.y, high.z);
        player.setOnGround(false);
        player.fallDistance = 3f;
        helper.assertFalse(TrophyPassives.catchFall(player), "a short fall is not caught");
        player.fallDistance = 12f;
        // a stone already beside the landing spot must survive; only air is replaced
        helper.setBlock(new BlockPos(3, 2, 2), Blocks.GOLD_BLOCK);
        helper.assertTrue(TrophyPassives.catchFall(player), "the long fall is caught");
        helper.assertBlockPresent(TrophyContent.STONE_PLATFORM.get(), new BlockPos(2, 2, 2));
        helper.assertBlockPresent(TrophyContent.STONE_PLATFORM.get(), new BlockPos(1, 2, 1));
        helper.assertBlockPresent(Blocks.GOLD_BLOCK, new BlockPos(3, 2, 2));
        helper.assertTrue(player.fallDistance == 0f, "the fall starts over");
        helper.assertTrue(player.getDeltaMovement().y > -0.5, "and the speed is eased");
        helper.assertFalse(TrophyPassives.catchFall(player), "no second catch right away");
        helper.runAfterDelay(80 + 15, () -> {
            helper.assertBlockNotPresent(TrophyContent.STONE_PLATFORM.get(), new BlockPos(2, 2, 2));
            helper.assertBlockNotPresent(TrophyContent.STONE_PLATFORM.get(), new BlockPos(1, 2, 1));
            helper.assertBlockPresent(Blocks.GOLD_BLOCK, new BlockPos(3, 2, 2));
            helper.succeed();
        });
    }

    @GameTest(template = "empty", batch = "tp_theSculptorDoesNotCatchOverOpenAirOrWater")
    public static void theSculptorDoesNotCatchOverOpenAirOrWater(GameTestHelper helper) {
        Player player = onFloor(helper, 2.5, 2.5);
        TrophyService.award(player, Trophy.SCULPTOR);
        // far above the floor: nothing to land on within reach
        Vec3 high = helper.absoluteVec(new Vec3(2.5, FLOOR + 12, 2.5));
        player.moveTo(high.x, high.y, high.z);
        player.setOnGround(false);
        player.fallDistance = 12f;
        helper.assertFalse(TrophyPassives.catchFall(player), "ground too far below");
        helper.assertBlockNotPresent(TrophyContent.STONE_PLATFORM.get(), new BlockPos(2, 13, 2));
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "tp_theValkyrieJumpsAgainOncePerAirtime")
    public static void theValkyrieJumpsAgainOncePerAirtime(GameTestHelper helper) {
        Player player = suited(helper, 2.5, 2.5);
        Vec3 high = helper.absoluteVec(new Vec3(2.5, FLOOR + 2, 2.5));
        player.moveTo(high.x, high.y, high.z);
        player.setOnGround(false);
        helper.assertFalse(TrophyPassives.doubleJump(player, 0.1, 0), "no trophy, no double jump");
        TrophyService.award(player, Trophy.VALKYRIE_QUEEN);
        player.setOnGround(true);
        helper.assertFalse(TrophyPassives.doubleJump(player, 0.1, 0), "on the ground it is just a jump");
        player.setOnGround(false);
        helper.assertTrue(TrophyPassives.doubleJump(player, 0.1, 0.2), "in the air it works");
        helper.assertTrue(Math.abs(player.getDeltaMovement().y - TrophyPassives.DOUBLE_JUMP_SPEED) < 1e-6,
                "and lifts the player");
        helper.assertTrue(Math.abs(player.getDeltaMovement().x - 0.1) < 1e-6, "keeping their momentum");
        helper.assertFalse(TrophyPassives.doubleJump(player, 0.1, 0.2), "but only once in the same airtime");
        player.setOnGround(true);
        // the landing is noticed in the player tick
        TrophyEvents.onPlayerTick(new net.neoforged.neoforge.event.tick.PlayerTickEvent.Post(player));
        player.setOnGround(false);
        helper.assertTrue(TrophyPassives.doubleJump(player, 5, 5), "after landing it is back");
        double h = Math.hypot(player.getDeltaMovement().x, player.getDeltaMovement().z);
        helper.assertTrue(h <= TrophyPassives.DOUBLE_JUMP_MAX_SPEED + 1e-6, "a forged speed is capped, got " + h);
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "tp_theDeadKingRevivesOnceEveryTenMinutes")
    public static void theDeadKingRevivesOnceEveryTenMinutes(GameTestHelper helper) {
        Player player = onFloor(helper, 2.5, 2.5);
        Player bare = onFloor(helper, 0.5, 0.5);
        DamageSource blow = player.damageSources().generic();
        helper.assertFalse(lethal(bare, blow), "no trophy, no second life");
        TrophyService.award(player, Trophy.DEAD_KING);
        player.setHealth(1f);
        helper.assertFalse(lethal(player, player.damageSources().genericKill()), "/kill still kills");
        helper.assertTrue(lethal(player, blow), "the lethal hit is cancelled");
        helper.assertTrue(player.getHealth() == PowerMath.reviveHealth(player.getMaxHealth()),
                "a quarter of the health is left, got " + player.getHealth());
        long ready = player.getData(TrophyContent.DATA).readyAt().getOrDefault(TrophyPassives.REVIVE_KEY, 0L);
        helper.assertTrue(ready >= player.level().getGameTime() + TrophyPassives.REVIVE_COOLDOWN_TICKS - 1,
                "ten minutes until the next one");
        player.setHealth(1f);
        helper.assertFalse(lethal(player, blow), "not twice in a row");
        TrophyContent.Data data = player.getData(TrophyContent.DATA);
        player.setData(TrophyContent.DATA, new TrophyContent.Data(data.owned(), Map.of(TrophyPassives.REVIVE_KEY, 0L)));
        helper.assertTrue(lethal(player, blow), "ready again once the time has passed");
        helper.succeed();
    }

    private static boolean lethal(Player player, DamageSource source) {
        LivingDeathEvent event = new LivingDeathEvent(player, source);
        TrophyEvents.onLethalHit(event);
        return event.isCanceled();
    }

    @GameTest(template = "empty", batch = "tp_theFlamebearerAddsSpellPowerWhenSpellbooksAreInstalled")
    public static void theFlamebearerAddsSpellPowerWhenSpellbooksAreInstalled(GameTestHelper helper) {
        Player player = onFloor(helper, 2.5, 2.5);
        TrophyService.award(player, Trophy.FIRE_BOSS);
        TrophyService.tickPassives(player);
        if (!TrophyPassives.spellbooksPresent()) {
            helper.assertFalse(TrophyPassives.hasSpellPowerBonus(player), "nothing to boost without the mod");
            helper.succeed();
            return;
        }
        helper.assertTrue(TrophyPassives.hasSpellPowerBonus(player), "the bonus is on");
        player.setData(TrophyContent.DATA, new TrophyContent.Data(List.of(), Map.of()));
        TrophyService.tickPassives(player);
        helper.assertFalse(TrophyPassives.hasSpellPowerBonus(player), "and off again without the trophy");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "tp_theArchevokersFangsSpareThemAndPlayersAlike")
    public static void theArchevokersFangsSpareThemAndPlayersAlike(GameTestHelper helper) {
        Player owner = onFloor(helper, 2.5, 2.5);
        Player friend = onFloor(helper, 0.5, 2.5);
        TrophyService.award(owner, Trophy.ARCHEVOKER);
        Husk caster = husk(helper, 4.5, 4.5);
        Vec3 at = owner.position();
        // an enemy's fangs cannot hurt the Archevoker owner
        EvokerFangs hostile = new EvokerFangs(helper.getLevel(), at.x, at.y, at.z, 0f, 0, caster);
        helper.assertTrue(cancelled(owner, owner.damageSources().indirectMagic(hostile, caster)), "own passive");
        // the owner's fangs hurt hostile mobs, but not other players
        EvokerFangs mine = new EvokerFangs(helper.getLevel(), at.x, at.y, at.z, 0f, 0, owner);
        mine.addTag(SpellbookPowers.FANG_TAG);
        helper.assertTrue(cancelled(friend, friend.damageSources().indirectMagic(mine, owner)), "a friend is spared");
        var event = new net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent(caster,
                new net.neoforged.neoforge.common.damagesource.DamageContainer(
                        caster.damageSources().indirectMagic(mine, owner), 6f));
        TrophyEvents.onPlayerHurt(event);
        helper.assertFalse(event.isCanceled(), "a hostile mob is not");
        helper.succeed();
    }

    // ---------------------------------------------------------------- actives

    @GameTest(template = "empty", batch = "tp_targetedPowersNeedSomeoneToHit")
    public static void targetedPowersNeedSomeoneToHit(GameTestHelper helper) {
        Player player = onFloor(helper, 2.5, 2.5);
        faceAlongZ(helper, player);
        for (Trophy t : new Trophy[] {Trophy.UMVUTHI, Trophy.SCULPTOR, Trophy.VALKYRIE_QUEEN, Trophy.SUN_SPIRIT,
                Trophy.FIRE_BOSS, Trophy.ARCHEVOKER}) {
            TrophyService.award(player, t);
            helper.assertFalse(TrophyService.castTrophy(player, t), t.key() + " with nobody around does nothing");
            helper.assertTrue(player.getData(TrophyContent.DATA).readyAt().getOrDefault(t.key(), 0L) == 0L,
                    t.key() + " keeps its cooldown when it did not fire");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "tp_icyBreathFreezesWhatIsInTheCone", timeoutTicks = 80)
    public static void icyBreathFreezesWhatIsInTheCone(GameTestHelper helper) {
        Player player = onFloor(helper, 2.5, 0.5);
        faceAlongZ(helper, player);
        TrophyService.award(player, Trophy.FROSTMAW);
        Husk ahead = husk(helper, 2.5, 3.5);
        Husk behind = husk(helper, 2.5, 0.5);
        behind.moveTo(behind.getX(), behind.getY(), player.getZ() - 3);
        Player friend = player(helper, 3.5, 3.5);
        float aheadHp = ahead.getHealth(), behindHp = behind.getHealth(), friendHp = friend.getHealth();
        helper.assertTrue(TrophyService.castTrophy(player, Trophy.FROSTMAW), "the breath is cast");
        helper.assertFalse(TrophyService.castTrophy(player, Trophy.FROSTMAW), "then it recharges");
        helper.runAfterDelay(MowziePowers.BREATH_WINDUP + 12, () -> {
            helper.assertTrue(ahead.getHealth() < aheadHp, "the husk ahead is hurt");
            MobEffectInstance slow = ahead.getEffect(MobEffects.MOVEMENT_SLOWDOWN);
            helper.assertTrue(slow != null && slow.getAmplifier() == 3, "with Slowness IV");
            helper.assertTrue(ahead.getTicksFrozen() > 0, "and freezing");
            helper.assertTrue(behind.getHealth() == behindHp, "the one behind is not");
            helper.assertTrue(friend.getHealth() == friendHp, "neither is the friend in the cone");
            helper.succeed();
        });
    }

    @GameTest(template = "empty", batch = "tp_theSolarBeamChargesThenBurns", timeoutTicks = 100)
    public static void theSolarBeamChargesThenBurns(GameTestHelper helper) {
        Player player = onFloor(helper, 2.5, 0.5);
        faceAlongZ(helper, player);
        TrophyService.award(player, Trophy.UMVUTHI);
        Husk husk = husk(helper, 2.5, 4.0);
        float before = husk.getHealth();
        helper.assertTrue(TrophyService.castTrophy(player, Trophy.UMVUTHI), "beam cast");
        helper.runAfterDelay(MowziePowers.BEAM_CHARGE_TICKS - 3, () ->
                helper.assertTrue(husk.getHealth() == before, "still charging: the husk is untouched"));
        helper.runAfterDelay(MowziePowers.BEAM_CHARGE_TICKS + 12, () -> {
            helper.assertTrue(husk.isDeadOrDying() || husk.getHealth() < before, "burnt by the beam");
            helper.assertTrue(husk.isOnFire(), "and set on fire");
            helper.assertFalse(fireNear(helper, 0, 0, 4, 6), "no block is ever set alight");
            int shown = helper.getLevel().getEntitiesOfClass(Display.BlockDisplay.class,
                    player.getBoundingBox().inflate(20)).size();
            helper.assertTrue(shown >= 3, "the beam is made of block displays, found " + shown);
        });
        helper.runAfterDelay(MowziePowers.BEAM_CHARGE_TICKS + MowziePowers.BEAM_BURN_TICKS + 12, () -> {
            helper.assertTrue(helper.getLevel().getEntitiesOfClass(Display.BlockDisplay.class,
                    player.getBoundingBox().inflate(20)).isEmpty(), "and none are left once it is over");
            helper.succeed();
        });
    }

    @GameTest(template = "empty", batch = "tp_stonePillarsThrowEnemiesUpAndLeaveNoTrace", timeoutTicks = 120)
    public static void stonePillarsThrowEnemiesUpAndLeaveNoTrace(GameTestHelper helper) {
        Player player = onFloor(helper, 2.5, 0.5);
        faceAlongZ(helper, player);
        TrophyService.award(player, Trophy.SCULPTOR);
        Husk husk = husk(helper, 2.5, 3.5);
        var villager = helper.spawn(EntityType.VILLAGER, new BlockPos(0, (int) FLOOR, 4));
        villager.setNoAi(true);
        float before = husk.getHealth(), villagerHp = villager.getHealth();
        helper.assertTrue(TrophyService.castTrophy(player, Trophy.SCULPTOR), "pillars called");
        double[] peak = {0};
        helper.runAfterDelay(MowziePowers.PILLAR_WARN_TICKS + 2, () -> {
            helper.assertTrue(husk.getHealth() < before, "the pillar hurts the husk");
            peak[0] = husk.getDeltaMovement().y;
            helper.assertTrue(peak[0] > 0.5, "and throws it up, vy " + peak[0]);
            List<Display.BlockDisplay> shards = helper.getLevel().getEntitiesOfClass(Display.BlockDisplay.class,
                    player.getBoundingBox().inflate(10));
            helper.assertTrue(shards.size() >= 3, "pillar shards are standing there, found " + shards.size());
            helper.assertTrue(villager.getHealth() == villagerHp, "the villager is spared");
        });
        helper.runAfterDelay(MowziePowers.PILLAR_WARN_TICKS + MowziePowers.PILLAR_HOLD_TICKS + 15, () -> {
            helper.assertTrue(helper.getLevel().getEntitiesOfClass(Display.BlockDisplay.class,
                    player.getBoundingBox().inflate(10)).isEmpty(), "the shards have sunk away");
            helper.assertBlockPresent(Blocks.STONE, new BlockPos(2, 1, 3));
            helper.succeed();
        });
    }

    @GameTest(template = "empty", batch = "tp_theRamBowlsEnemiesOver", timeoutTicks = 60)
    public static void theRamBowlsEnemiesOver(GameTestHelper helper) {
        Player player = onFloor(helper, 2.5, 0.5);
        faceAlongZ(helper, player);
        TrophyService.award(player, Trophy.SLIDER);
        Husk husk = husk(helper, 2.5, 3.5);
        float before = husk.getHealth();
        helper.assertTrue(TrophyService.castTrophy(player, Trophy.SLIDER), "the ram is cast");
        helper.runAfterDelay(AetherPowers.RAM_WINDUP_TICKS + 1, () -> {
            helper.assertTrue(player.getDeltaMovement().z > 1.0, "the dash is under way, vz " + player.getDeltaMovement().z);
            helper.assertTrue(player.getDeltaMovement().x == 0.0 || Math.abs(player.getDeltaMovement().x) < 0.01,
                    "straight ahead");
        });
        helper.runAfterDelay(AetherPowers.RAM_WINDUP_TICKS + 6, () -> {
            helper.assertTrue(husk.getHealth() < before, "the husk is hit");
            helper.assertTrue(husk.getDeltaMovement().z > 0.5 || husk.getZ() > helper.absoluteVec(new Vec3(0, 0, 3.6)).z,
                    "and thrown ahead");
            helper.succeed();
        });
    }

    @GameTest(template = "empty", batch = "tp_theRamStopsAtAWall", timeoutTicks = 60)
    public static void theRamStopsAtAWall(GameTestHelper helper) {
        Player player = onFloor(helper, 2.5, 0.5);
        faceAlongZ(helper, player);
        TrophyService.award(player, Trophy.SLIDER);
        for (int x = 0; x < 5; x++) {
            for (int y = 2; y <= 3; y++) {
                helper.setBlock(new BlockPos(x, y, 2), Blocks.STONE_BRICKS);
            }
        }
        Husk husk = husk(helper, 2.5, 4.0);
        float before = husk.getHealth();
        helper.assertTrue(TrophyService.castTrophy(player, Trophy.SLIDER), "cast");
        helper.runAfterDelay(AetherPowers.RAM_WINDUP_TICKS + 8, () -> {
            helper.assertTrue(Math.abs(player.getDeltaMovement().z) < 0.01, "the dash ended at the wall");
            helper.assertTrue(husk.getHealth() == before, "nothing behind the wall is hit");
            helper.succeed();
        });
    }

    @GameTest(template = "empty", batch = "tp_valkyrieLightningStrikesTheTargetOnly", timeoutTicks = 80)
    public static void valkyrieLightningStrikesTheTargetOnly(GameTestHelper helper) {
        Player player = onFloor(helper, 2.5, 0.5);
        faceAlongZ(helper, player);
        TrophyService.award(player, Trophy.VALKYRIE_QUEEN);
        Husk husk = husk(helper, 2.5, 4.0);
        Player friend = player(helper, 3.5, 4.0);
        float before = husk.getHealth(), friendHp = friend.getHealth(), ownHp = player.getHealth();
        helper.assertTrue(TrophyService.castTrophy(player, Trophy.VALKYRIE_QUEEN), "the bolt is called");
        helper.assertTrue(husk.getHealth() == before, "it takes a moment to gather");
        helper.runAfterDelay(AetherPowers.LIGHTNING_WINDUP_TICKS + 6, () -> {
            helper.assertTrue(husk.isDeadOrDying() || husk.getHealth() <= before - AetherPowers.LIGHTNING_DAMAGE + 0.01,
                    "struck for the full damage, health " + husk.getHealth());
            helper.assertTrue(friend.getHealth() == friendHp && player.getHealth() == ownHp, "no player is hurt");
            helper.assertFalse(husk.isOnFire() || friend.isOnFire(), "nothing burns");
            helper.assertFalse(fireNear(helper, 0, 0, 4, 6), "and the ground is not set alight");
            helper.succeed();
        });
    }

    @GameTest(template = "empty", batch = "tp_theSunSpiritIgnitesHostilesInRange", timeoutTicks = 80)
    public static void theSunSpiritIgnitesHostilesInRange(GameTestHelper helper) {
        Player player = onFloor(helper, 2.5, 2.5);
        TrophyService.award(player, Trophy.SUN_SPIRIT);
        Husk near = husk(helper, 4.5, 2.5);
        Husk far = husk(helper, 2.5, 2.5);
        Vec3 away = player.position().add(11, 0, 0);
        far.moveTo(away.x, away.y, away.z);
        far.setNoGravity(true);
        Player friend = player(helper, 0.5, 2.5);
        var cow = helper.spawn(EntityType.COW, new BlockPos(2, (int) FLOOR, 4));
        cow.setNoAi(true);
        helper.assertTrue(TrophyService.castTrophy(player, Trophy.SUN_SPIRIT), "cast");
        helper.assertFalse(near.isOnFire(), "the wave has not arrived yet");
        helper.runAfterDelay(AetherPowers.IGNITE_WINDUP_TICKS + 8, () -> {
            helper.assertTrue(near.isOnFire(), "the husk in range burns");
            helper.assertTrue(near.getRemainingFireTicks() > 100, "for a long while, ticks " + near.getRemainingFireTicks());
            helper.assertFalse(far.isOnFire(), "the one out of range does not");
            helper.assertFalse(friend.isOnFire() || cow.isOnFire() || player.isOnFire(), "players and animals do not");
            helper.assertFalse(fireNear(helper, 0, 0, 4, 6), "no fire on the ground");
            helper.succeed();
        });
    }

    @GameTest(template = "empty", batch = "tp_theDeadKingRaisesThreeLoyalSkeletons", timeoutTicks = 200)
    public static void theDeadKingRaisesThreeLoyalSkeletons(GameTestHelper helper) {
        Player player = onFloor(helper, 2.5, 0.5);
        faceAlongZ(helper, player);
        TrophyService.award(player, Trophy.DEAD_KING);
        Husk husk = husk(helper, 2.5, 4.5);
        Player friend = player(helper, 0.5, 4.5);
        helper.assertTrue(TrophyService.castTrophy(player, Trophy.DEAD_KING), "the dead are called");
        helper.runAfterDelay(SpellbookPowers.SKELETON_RISE_TICKS + 3, () -> {
            List<Skeleton> bones = summons(helper, player);
            helper.assertTrue(bones.size() == 3, "three skeletons rise, got " + bones.size());
            for (Skeleton s : bones) {
                helper.assertTrue(s.getItemBySlot(EquipmentSlot.HEAD).has(net.minecraft.core.component.DataComponents.UNBREAKABLE),
                        "an unbreakable helmet keeps the sun off");
                helper.assertTrue(s.isPersistenceRequired(), "they do not despawn on their own");
            }
        });
        // they pick the hostile husk as their target and never a player
        boolean[] sawHusk = {false};
        helper.onEachTick(() -> {
            for (Skeleton s : summons(helper, player)) {
                helper.assertFalse(s.getTarget() instanceof Player, "a summon must never target a player, but targets " + s.getTarget() + " goals "
                        + s.targetSelector.getAvailableGoals().stream().map(w -> w.getGoal().getClass().getSimpleName()).toList());
                sawHusk[0] |= s.getTarget() == husk;
                helper.assertFalse(s.isOnFire(), "skeletons do not burn");
            }
            if (sawHusk[0] && helper.getTick() > SpellbookPowers.SKELETON_RISE_TICKS + 5) {
                helper.assertTrue(friend.isAlive(), "the friend lives");
            }
        });
        helper.runAfterDelay(SpellbookPowers.SKELETON_RISE_TICKS + 120, () -> {
            helper.assertTrue(sawHusk[0], "the skeletons went for the husk");
            helper.succeed();
        });
    }

    private static List<Skeleton> summons(GameTestHelper helper, Player near) {
        return helper.getLevel().getEntitiesOfClass(Skeleton.class, near.getBoundingBox().inflate(40),
                s -> SpellbookPowers.isSummon(s) && s.isAlive() && near.getUUID().equals(SpellbookPowers.summonOwner(s)));
    }

    @GameTest(template = "empty", batch = "tp_summonedSkeletonsDropNothingAndVanishInSmoke", timeoutTicks = 60)
    public static void summonedSkeletonsDropNothingAndVanishInSmoke(GameTestHelper helper) {
        Player player = onFloor(helper, 2.5, 0.5);
        faceAlongZ(helper, player);
        TrophyService.award(player, Trophy.DEAD_KING);
        helper.assertTrue(TrophyService.castTrophy(player, Trophy.DEAD_KING), "cast");
        helper.runAfterDelay(SpellbookPowers.SKELETON_RISE_TICKS + 3, () -> {
            List<Skeleton> bones = summons(helper, player);
            helper.assertTrue(bones.size() == 3, "three skeletons");
            int itemsBefore = helper.getLevel().getEntitiesOfClass(ItemEntity.class, player.getBoundingBox().inflate(30)).size();
            bones.get(0).kill();
            SpellbookPowers.vanish(helper.getLevel(), bones.get(1));
            helper.assertTrue(bones.get(1).isRemoved(), "the dismissed one is gone");
            helper.assertTrue(helper.getLevel().getEntitiesOfClass(ItemEntity.class, player.getBoundingBox().inflate(30))
                    .size() == itemsBefore, "no bones, no arrows, no bow on the ground");
            helper.succeed();
        });
    }

    @GameTest(template = "empty", batch = "tp_theFirestormRainsFireOnEnemiesWithoutBurningTheWorld", timeoutTicks = 100)
    public static void theFirestormRainsFireOnEnemiesWithoutBurningTheWorld(GameTestHelper helper) {
        Player player = onFloor(helper, 2.5, 0.5);
        faceAlongZ(helper, player);
        TrophyService.award(player, Trophy.FIRE_BOSS);
        Husk husk = husk(helper, 2.5, 4.0);
        Player friend = player(helper, 0.5, 4.0);
        float before = husk.getHealth(), friendHp = friend.getHealth(), ownHp = player.getHealth();
        helper.assertTrue(TrophyService.castTrophy(player, Trophy.FIRE_BOSS), "the storm is called");
        helper.runAfterDelay(SpellbookPowers.STORM_WINDUP_TICKS + 40, () -> {
            helper.assertTrue(husk.isDeadOrDying() || husk.getHealth() < before, "fireballs hit the husk");
            helper.assertTrue(friend.getHealth() == friendHp && player.getHealth() == ownHp, "no player is hurt");
            helper.assertFalse(fireNear(helper, 0, 0, 4, 6), "no block is set alight");
            helper.assertBlockPresent(Blocks.STONE, new BlockPos(2, 1, 3));
            helper.succeed();
        });
    }

    @GameTest(template = "empty", batch = "tp_archevokerFangsBiteEnemiesNotPlayers", timeoutTicks = 80)
    public static void archevokerFangsBiteEnemiesNotPlayers(GameTestHelper helper) {
        Player player = onFloor(helper, 2.5, 0.5);
        faceAlongZ(helper, player);
        TrophyService.award(player, Trophy.ARCHEVOKER);
        Husk husk = husk(helper, 2.5, 4.0);
        Player friend = player(helper, 3.5, 4.0);
        var villager = helper.spawn(EntityType.VILLAGER, new BlockPos(1, (int) FLOOR, 4));
        villager.setNoAi(true);
        float before = husk.getHealth(), friendHp = friend.getHealth(), ownHp = player.getHealth(),
                villagerHp = villager.getHealth();
        helper.assertTrue(TrophyService.castTrophy(player, Trophy.ARCHEVOKER), "fangs summoned");
        helper.runAfterDelay(SpellbookPowers.FANG_WINDUP_TICKS + 2, () -> helper.assertTrue(
                !helper.getLevel().getEntitiesOfClass(EvokerFangs.class, player.getBoundingBox().inflate(12)).isEmpty(),
                "the fangs are there"));
        helper.runAfterDelay(SpellbookPowers.FANG_WINDUP_TICKS + 30, () -> {
            helper.assertTrue(husk.isDeadOrDying() || husk.getHealth() < before, "the husk is bitten");
            helper.assertTrue(friend.getHealth() == friendHp && player.getHealth() == ownHp, "no player is");
            helper.assertTrue(villager.getHealth() == villagerHp, "nor the villager");
            helper.succeed();
        });
    }

    @GameTest(template = "empty", batch = "tp_newPowersNeedTheFullSet")
    public static void newPowersNeedTheFullSet(GameTestHelper helper) {
        Player bare = player(helper, 2.5, 2.5);
        Husk husk = husk(helper, 3.5, 2.5);
        for (Trophy t : new Trophy[] {Trophy.FROSTMAW, Trophy.UMVUTHI, Trophy.SCULPTOR, Trophy.SLIDER,
                Trophy.VALKYRIE_QUEEN, Trophy.SUN_SPIRIT, Trophy.DEAD_KING, Trophy.FIRE_BOSS, Trophy.ARCHEVOKER}) {
            TrophyService.award(bare, t);
            helper.assertFalse(TrophyService.castTrophy(bare, t), t.key() + " needs the armor");
        }
        helper.assertTrue(husk.isAlive(), "nothing happened");
        helper.succeed();
    }
}
