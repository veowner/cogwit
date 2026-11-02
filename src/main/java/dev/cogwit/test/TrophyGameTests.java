package dev.cogwit.test;

import dev.cogwit.CogwitMod;
import dev.cogwit.horizon.HorizonContent;
import dev.cogwit.trophy.CataclysmStrikes;
import dev.cogwit.trophy.DragonBreathCloud;
import dev.cogwit.trophy.Trophy;
import dev.cogwit.trophy.TrophyContent;
import dev.cogwit.trophy.TrophyEvents;
import dev.cogwit.trophy.TrophyService;
import dev.cogwit.trophy.TrophySkull;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.monster.ElderGuardian;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** In-game checks for trophies: earning them in co-op, their passives and the four vanilla actives. */
@GameTestHolder(CogwitMod.MODID)
@PrefixGameTestTemplate(false)
public final class TrophyGameTests {
    private TrophyGameTests() {}

    static Player player(GameTestHelper helper, double x, double z) {
        Player player = TestPlayers.mock(helper);
        Vec3 spot = helper.absoluteVec(new Vec3(x, 1, z));
        player.moveTo(spot.x, spot.y, spot.z, 0f, 0f);
        helper.getLevel().addFreshEntity(player);
        return player;
    }

    static Player suited(GameTestHelper helper, double x, double z) {
        Player player = player(helper, x, z);
        player.setItemSlot(EquipmentSlot.HEAD, new ItemStack(HorizonContent.HELMET.get()));
        player.setItemSlot(EquipmentSlot.CHEST, new ItemStack(HorizonContent.CHESTPLATE.get()));
        player.setItemSlot(EquipmentSlot.LEGS, new ItemStack(HorizonContent.LEGGINGS.get()));
        player.setItemSlot(EquipmentSlot.FEET, new ItemStack(HorizonContent.BOOTS.get()));
        return player;
    }

    /** Faces the player along +z of the test area, so things placed at higher z are in front of them. */
    static void faceAlongZ(GameTestHelper helper, Player player) {
        Vec3 a = helper.absoluteVec(new Vec3(0, 0, 0)), b = helper.absoluteVec(new Vec3(0, 0, 1));
        Vec3 d = b.subtract(a);
        player.setYRot((float) Math.toDegrees(Math.atan2(-d.x, d.z)));
        player.setXRot(0);
        player.setYHeadRot(player.getYRot());
    }

    @GameTest(template = "empty", batch = "trophies")
    public static void aBossKillCountsForEveryoneWhoFought(GameTestHelper helper) {
        Player a = player(helper, 1.5, 1.5), b = player(helper, 3.5, 1.5);
        ElderGuardian boss = helper.spawn(EntityType.ELDER_GUARDIAN, 2, 2, 4);
        TrophyService.noteHit(boss, a);
        TrophyService.noteHit(boss, b);
        TrophyService.bossDied(boss, a);
        helper.assertTrue(TrophyService.has(a, Trophy.ELDER_GUARDIAN), "the killer gets it");
        helper.assertTrue(TrophyService.has(b, Trophy.ELDER_GUARDIAN), "and so does the friend who fought");
        helper.assertFalse(TrophyService.award(a, Trophy.ELDER_GUARDIAN), "only once");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "trophies")
    public static void passivesNeedTheSetAndAreShared(GameTestHelper helper) {
        Player owner = suited(helper, 1.5, 1.5), friend = suited(helper, 3.5, 1.5), bare = player(helper, 2.5, 3.5);
        TrophyService.award(owner, Trophy.WITHER);
        helper.assertTrue(TrophyService.hasPassive(owner, Trophy.WITHER), "own passive in the full set");
        helper.assertTrue(TrophyService.hasPassive(friend, Trophy.WITHER), "a suited friend nearby shares it");
        helper.assertFalse(TrophyService.hasPassive(bare, Trophy.WITHER), "no set, no powers");
        MobEffectEvent.Applicable event = new MobEffectEvent.Applicable(friend,
                new MobEffectInstance(MobEffects.WITHER, 100), null);
        TrophyEvents.onEffect(event);
        helper.assertTrue(event.getResult() == MobEffectEvent.Applicable.Result.DO_NOT_APPLY,
                "wither can't touch them");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "trophies")
    public static void trophyPowersNeedTheFullSet(GameTestHelper helper) {
        Player player = player(helper, 2.5, 2.5);
        TrophyService.award(player, Trophy.WITHER);
        helper.assertFalse(TrophyService.castTrophy(player, Trophy.WITHER), "no armor, no skulls");
        helper.assertFalse(TrophyService.castTrophy(suited(helper, 1.5, 1.5), Trophy.WARDEN), "not owned");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "trophies")
    public static void theSonicBoomGoesThroughWalls(GameTestHelper helper) {
        Player player = suited(helper, 2.5, 0.5);
        faceAlongZ(helper, player);
        TrophyService.award(player, Trophy.WARDEN);
        for (int x = 0; x < 5; x++) {
            for (int y = 1; y < 4; y++) {
                helper.setBlock(new BlockPos(x, y, 3), Blocks.STONE);
            }
        }
        Zombie zombie = helper.spawn(EntityType.ZOMBIE, new BlockPos(2, 1, 6));
        zombie.setNoAi(true);
        float before = zombie.getHealth();
        helper.assertTrue(TrophyService.castTrophy(player, Trophy.WARDEN), "boom");
        helper.assertTrue(zombie.getHealth() < before, "hit behind the wall");
        helper.assertFalse(TrophyService.castTrophy(player, Trophy.WARDEN), "then it recharges");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "trophies")
    public static void theWitherVolleyIsFiveSkulls(GameTestHelper helper) {
        Player player = suited(helper, 2.5, 0.5);
        faceAlongZ(helper, player);
        TrophyService.award(player, Trophy.WITHER);
        helper.assertTrue(TrophyService.castTrophy(player, Trophy.WITHER), "fired");
        int skulls = helper.getLevel().getEntitiesOfClass(TrophySkull.class, player.getBoundingBox().inflate(4)).size();
        helper.assertTrue(skulls == 5, "five skulls in a fan, got " + skulls);
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "trophies", timeoutTicks = 60)
    public static void dragonBreathBurnsMobsNotPlayers(GameTestHelper helper) {
        Player player = suited(helper, 0.5, 0.5);
        Zombie zombie = helper.spawn(EntityType.ZOMBIE, new BlockPos(3, 1, 3));
        zombie.setNoAi(true);
        Player friend = player(helper, 3.5, 4.5);
        float zombieHealth = zombie.getHealth(), friendHealth = friend.getHealth();
        DragonBreathCloud.breathe(player, zombie.position());
        helper.runAfterDelay(25, () -> {
            helper.assertTrue(zombie.getHealth() < zombieHealth, "the zombie burns");
            helper.assertTrue(friend.getHealth() == friendHealth, "the friend standing in it doesn't");
            helper.succeed();
        });
    }

    @GameTest(template = "empty", batch = "trophies", timeoutTicks = 60)
    public static void theLaserChargesThenStrikes(GameTestHelper helper) {
        Player player = suited(helper, 2.5, 0.5);
        faceAlongZ(helper, player);
        TrophyService.award(player, Trophy.ELDER_GUARDIAN);
        Zombie zombie = helper.spawn(EntityType.ZOMBIE, new BlockPos(2, 0, 5));
        zombie.setNoAi(true);
        zombie.moveTo(zombie.getX(), player.getY(), zombie.getZ());
        float before = zombie.getHealth();
        helper.assertTrue(TrophyService.castTrophy(player, Trophy.ELDER_GUARDIAN), "locked on");
        helper.assertTrue(zombie.getHealth() == before, "charging, not hit yet");
        helper.runAfterDelay(TrophyService.LASER_CHARGE_TICKS + 3, () -> {
            helper.assertTrue(zombie.isDeadOrDying() || zombie.getHealth() < before, "struck after the charge");
            helper.succeed();
        });
    }

    @GameTest(template = "empty", batch = "trophies")
    public static void trophiesSurviveInTheAttachment(GameTestHelper helper) {
        Player player = player(helper, 2.5, 2.5);
        TrophyService.award(player, Trophy.ENDER_DRAGON);
        TrophyContent.Data data = player.getData(TrophyContent.DATA);
        var saved = TrophyContent.Data.CODEC.encodeStart(com.mojang.serialization.JsonOps.INSTANCE, data)
                .flatMap(j -> TrophyContent.Data.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE, j))
                .getOrThrow();
        helper.assertTrue(saved.owned().contains("ender_dragon"), "saved and loaded");
        helper.succeed();
    }

    // ---------------------------------------------------------------- Cataclysm trophies

    /** The template's stone floor is its y = 1 layer, so these tests stand on y = 2. */
    static final double FLOOR = 2;

    static Player onFloor(GameTestHelper helper, double x, double z) {
        Player player = suited(helper, x, z);
        Vec3 spot = helper.absoluteVec(new Vec3(x, FLOOR, z));
        player.moveTo(spot.x, spot.y, spot.z, 0f, 0f);
        return player;
    }

    /** A husk: hostile, doesn't burn in daylight, so any damage it takes came from the trophy. */
    static net.minecraft.world.entity.monster.Husk husk(GameTestHelper helper, double x, double z) {
        var husk = helper.spawn(EntityType.HUSK, new BlockPos((int) x, (int) FLOOR, (int) z));
        husk.setNoAi(true);
        Vec3 at = helper.absoluteVec(new Vec3(x, FLOOR, z));
        husk.moveTo(at.x, at.y, at.z);
        return husk;
    }

    static boolean cancelled(Player player, net.minecraft.world.damagesource.DamageSource source) {
        var event = new net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent(player,
                new net.neoforged.neoforge.common.damagesource.DamageContainer(source, 5f));
        TrophyEvents.onPlayerHurt(event);
        return event.isCanceled();
    }

    @GameTest(template = "empty", batch = "trophies")
    public static void cataclysmPassivesTurnHitsAway(GameTestHelper helper) {
        Player player = onFloor(helper, 2.5, 2.5);
        for (Trophy t : new Trophy[] {Trophy.IGNIS, Trophy.NETHERITE_MONSTROSITY, Trophy.HARBINGER, Trophy.SCYLLA,
                Trophy.ANCIENT_REMNANT}) {
            TrophyService.award(player, t);
        }
        var sources = player.damageSources();
        helper.assertTrue(cancelled(player, sources.onFire()), "Ignis: fire");
        helper.assertTrue(cancelled(player, sources.explosion(null, null)), "Monstrosity: explosions");
        helper.assertTrue(cancelled(player, sources.source(net.minecraft.world.damagesource.DamageTypes.ARROW)),
                "Harbinger: projectiles");
        helper.assertTrue(cancelled(player, sources.lightningBolt()), "Scylla: lightning");
        helper.assertTrue(cancelled(player, sources.inWall()) && cancelled(player, sources.drown()),
                "Ancient Remnant: suffocation and drowning");
        helper.assertTrue(!cancelled(player, sources.fall()), "a fall still hurts");
        TrophyService.tickPassives(player);
        var kb = player.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.KNOCKBACK_RESISTANCE);
        helper.assertTrue(kb != null && kb.getValue() >= 1.0, "Monstrosity: no knockback");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "trophies")
    public static void ignisCoolsLavaUnderfoot(GameTestHelper helper) {
        Player player = onFloor(helper, 2.5, 2.5);
        TrophyService.award(player, Trophy.IGNIS);
        helper.setBlock(new BlockPos(2, 1, 2), Blocks.LAVA);
        TrophyService.walkOnLava(player);
        helper.assertBlockPresent(Blocks.MAGMA_BLOCK, new BlockPos(2, 1, 2));
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "trophies")
    public static void enderGuardianHitsPullTheTargetIn(GameTestHelper helper) {
        Player player = onFloor(helper, 0.5, 0.5);
        TrophyService.award(player, Trophy.ENDER_GUARDIAN);
        var husk = husk(helper, 4.5, 0.5);
        TrophyService.meleePull(player, husk);
        Vec3 towards = player.position().subtract(husk.position());
        helper.assertTrue(husk.getDeltaMovement().dot(towards) > 0, "pulled towards the player");
        helper.succeed();
    }

    /** Casts a trophy active at a husk in front of the player and checks the husk got hurt after a delay. */
    static void castHurts(GameTestHelper helper, Trophy trophy, double huskZ, int delay) {
        Player player = onFloor(helper, 2.5, 0.5);
        faceAlongZ(helper, player);
        TrophyService.award(player, trophy);
        var husk = husk(helper, 2.5, huskZ);
        float before = husk.getHealth();
        helper.assertTrue(TrophyService.castTrophy(player, trophy), trophy.key() + " cast");
        helper.runAfterDelay(delay, () -> {
            helper.assertTrue(husk.isDeadOrDying() || husk.getHealth() < before,
                    trophy.key() + " should hurt the husk, health " + husk.getHealth());
            helper.succeed();
        });
    }

    @GameTest(template = "empty", batch = "trophies", timeoutTicks = 60)
    public static void ignisDashBurnsWhatItPasses(GameTestHelper helper) {
        castHurts(helper, Trophy.IGNIS, 1.5, 12);
    }

    @GameTest(template = "empty", batch = "trophies", timeoutTicks = 60)
    public static void endSpikesRiseAhead(GameTestHelper helper) {
        castHurts(helper, Trophy.ENDER_GUARDIAN, 3.5, 20);
    }

    @GameTest(template = "empty", batch = "trophies", timeoutTicks = 80)
    public static void theAbyssBeamBurnsThrough(GameTestHelper helper) {
        castHurts(helper, Trophy.LEVIATHAN, 4.5, 45);
    }

    @GameTest(template = "empty", batch = "trophies", timeoutTicks = 60)
    public static void phantomBladesCutAhead(GameTestHelper helper) {
        castHurts(helper, Trophy.MALEDICTUS, 4.5, 15);
    }

    @GameTest(template = "empty", batch = "trophies", timeoutTicks = 60)
    public static void theStormStrikesEnemies(GameTestHelper helper) {
        castHurts(helper, Trophy.SCYLLA, 4.5, 20);
    }

    @GameTest(template = "empty", batch = "trophies_slam", timeoutTicks = 100)
    public static void theSlamRaisesMagmaGeysersAndLeavesNoTrace(GameTestHelper helper) {
        // a floor wider than the template, for the outer ring of geysers; taken away again at the end
        for (int x = -6; x <= 10; x++) {
            for (int z = -6; z <= 10; z++) {
                helper.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
            }
        }
        Player player = onFloor(helper, 2.5, 2.5);
        TrophyService.award(player, Trophy.NETHERITE_MONSTROSITY);
        // one husk on each of the outer ring's geysers, beyond the slam's own shove
        double ring = CataclysmStrikes.OUTER_RING;
        int count = CataclysmStrikes.OUTER_GEYSERS;
        java.util.List<net.minecraft.world.entity.monster.Husk> husks = new java.util.ArrayList<>();
        for (int i = 0; i < count; i++) {
            double a = i * 2 * Math.PI / count + ring;
            husks.add(husk(helper, 2.5 + Math.cos(a) * ring, 2.5 + Math.sin(a) * ring));
        }
        float full = husks.get(0).getHealth();
        Vec3 centre = player.position();
        helper.assertTrue(TrophyService.castTrophy(player, Trophy.NETHERITE_MONSTROSITY), "slammed");
        helper.runAfterDelay(8 + 2 * (count - 1) + CataclysmStrikes.GEYSER_WARN_TICKS + 2, () -> {
            long hurt = husks.stream().filter(h -> h.isDeadOrDying() || h.getHealth() < full && h.isOnFire()).count();
            String where = husks.stream().map(h -> String.format("%.1f", h.position().subtract(centre).horizontalDistance()))
                    .collect(java.util.stream.Collectors.joining(" "));
            helper.assertTrue(hurt >= count - 2, "the geysers burn the husks standing on them, " + hurt + " of " + count
                    + ", husks at " + where);
            int shown = helper.getLevel().getEntitiesOfClass(Display.class, player.getBoundingBox().inflate(12)).size();
            helper.assertTrue(shown >= 30, "cracks, plates and columns are block displays, found " + shown);
        });
        helper.runAfterDelay(CataclysmStrikes.SLAM_TICKS, () -> {
            helper.assertTrue(helper.getLevel().getEntitiesOfClass(Display.class, player.getBoundingBox().inflate(12))
                    .isEmpty(), "everything has sunk away");
            for (int x = -6; x <= 10; x++) {
                for (int z = -6; z <= 10; z++) {
                    helper.assertTrue(helper.getBlockState(new BlockPos(x, 1, z)).is(Blocks.STONE), "the floor is whole");
                    helper.assertFalse(helper.getBlockState(new BlockPos(x, 2, z)).is(Blocks.FIRE), "nothing set alight");
                    if (x < 0 || x > 4 || z < 0 || z > 4) {
                        helper.setBlock(new BlockPos(x, 1, z), Blocks.AIR);
                    }
                }
            }
            helper.succeed();
        });
    }

    @GameTest(template = "empty", batch = "trophies", timeoutTicks = 60)
    public static void theSlamThrowsMobsBack(GameTestHelper helper) {
        Player player = onFloor(helper, 2.5, 2.5);
        TrophyService.award(player, Trophy.NETHERITE_MONSTROSITY);
        var husk = husk(helper, 4.5, 2.5);
        helper.assertTrue(TrophyService.castTrophy(player, Trophy.NETHERITE_MONSTROSITY), "slammed");
        Vec3 away = husk.position().subtract(player.position());
        helper.assertTrue(husk.getDeltaMovement().dot(away) > 0 && husk.getDeltaMovement().y > 0, "thrown back and up");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "trophies", timeoutTicks = 60)
    public static void harbingerMissilesNeedEnemiesAndFly(GameTestHelper helper) {
        Player player = onFloor(helper, 2.5, 0.5);
        TrophyService.award(player, Trophy.HARBINGER);
        husk(helper, 2.5, 4.2);
        helper.assertTrue(TrophyService.castTrophy(player, Trophy.HARBINGER), "fired at the husk");
        // the area is only three blocks tall, so a missile may hit the barrier ceiling at once: seeing it is enough
        helper.succeedWhen(() -> helper.assertTrue(!helper.getLevel().getEntitiesOfClass(
                net.minecraft.world.entity.projectile.ShulkerBullet.class, player.getBoundingBox().inflate(10),
                b -> b.getTags().contains(dev.cogwit.trophy.CataclysmStrikes.MISSILE_TAG)).isEmpty(), "a missile in the air"));
    }

    @GameTest(template = "empty", batch = "trophies")
    public static void aMissileBurstsOnMobsNotPlayers(GameTestHelper helper) {
        Player player = onFloor(helper, 0.5, 0.5);
        var husk = husk(helper, 3.5, 3.5);
        Player friend = player(helper, 3.5, 2.5);
        float before = husk.getHealth(), friendBefore = friend.getHealth();
        var missile = new net.minecraft.world.entity.projectile.ShulkerBullet(helper.getLevel(), player, husk,
                net.minecraft.core.Direction.Axis.Y);
        dev.cogwit.trophy.CataclysmStrikes.missileHit(missile, husk.position());
        helper.assertTrue(husk.getHealth() < before, "the husk is hit");
        helper.assertTrue(friend.getHealth() == friendBefore, "the friend is not");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "trophies", timeoutTicks = 60)
    public static void theSandstormBlindsMobsAround(GameTestHelper helper) {
        Player player = onFloor(helper, 2.5, 2.5);
        TrophyService.award(player, Trophy.ANCIENT_REMNANT);
        var husk = husk(helper, 4.5, 2.5);
        helper.assertTrue(TrophyService.castTrophy(player, Trophy.ANCIENT_REMNANT), "storm raised");
        helper.assertTrue(!helper.getLevel().getEntitiesOfClass(dev.cogwit.trophy.SandstormEntity.class,
                player.getBoundingBox().inflate(3)).isEmpty(), "a sandstorm follows the player");
        helper.runAfterDelay(15, () -> {
            helper.assertTrue(husk.hasEffect(MobEffects.BLINDNESS), "the husk is blinded");
            helper.succeed();
        });
    }

    @GameTest(template = "empty", batch = "trophies")
    public static void cogwitHuntsTheEasiestMissingBoss(GameTestHelper helper) {
        Player player = player(helper, 2.5, 2.5);
        // the Wroughtnaut (1) and the Slider (2) are easier than anything else once they count
        helper.assertTrue(dev.cogwit.guide.GuideService.huntTarget(player).orElseThrow() == Trophy.FERROUS_WROUGHTNAUT,
                "the Wroughtnaut is the easiest trophy in the pack");
        TrophyService.award(player, Trophy.FERROUS_WROUGHTNAUT);
        helper.assertTrue(dev.cogwit.guide.GuideService.huntTarget(player).orElseThrow() == Trophy.SLIDER,
                "then the Slider");
        TrophyService.award(player, Trophy.SLIDER);
        helper.assertTrue(dev.cogwit.guide.GuideService.huntTarget(player).orElseThrow() == Trophy.ELDER_GUARDIAN,
                "then the elder guardian");
        TrophyService.award(player, Trophy.ELDER_GUARDIAN);
        TrophyService.award(player, Trophy.FROSTMAW);
        TrophyService.award(player, Trophy.UMVUTHI);
        // the Twilight Forest's first two bosses (difficulty 3 and 5) come before the wither when that mod is loaded
        TrophyService.award(player, Trophy.NAGA);
        TrophyService.award(player, Trophy.LICH);
        helper.assertTrue(dev.cogwit.guide.GuideService.huntTarget(player).orElseThrow() == Trophy.WITHER,
                "then the wither");
        helper.assertTrue(dev.cogwit.guide.GuideService.lair(Trophy.WARDEN).orElseThrow().structure()
                .equals("minecraft:ancient_city"), "the warden's compass points at an ancient city");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "trophies", timeoutTicks = 60)
    public static void areaPowersSpareVillagersAndAnimals(GameTestHelper helper) {
        Player player = onFloor(helper, 2.5, 0.5);
        faceAlongZ(helper, player);
        TrophyService.award(player, Trophy.NETHERITE_MONSTROSITY);
        var villager = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 2));
        var cow = helper.spawn(EntityType.COW, new BlockPos(1, 2, 2));
        villager.setNoAi(true);
        cow.setNoAi(true);
        var husk = husk(helper, 2.5, 3.5);
        float villagerHp = villager.getHealth(), cowHp = cow.getHealth(), huskHp = husk.getHealth();
        helper.assertTrue(TrophyService.castTrophy(player, Trophy.NETHERITE_MONSTROSITY), "slammed");
        helper.runAfterDelay(40, () -> {
            helper.assertTrue(villager.getHealth() == villagerHp && cow.getHealth() == cowHp,
                    "the villager and the cow are untouched");
            helper.assertTrue(husk.getHealth() < huskHp || husk.isDeadOrDying(), "the husk is not");
            helper.succeed();
        });
    }
}
