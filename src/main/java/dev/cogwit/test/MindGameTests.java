package dev.cogwit.test;

import dev.cogwit.CogwitMod;
import dev.cogwit.brain.Brain;
import dev.cogwit.brain.BrainState;
import dev.cogwit.brain.Growth;
import dev.cogwit.item.CogwitTalismanItem;
import dev.cogwit.mind.Mind;
import dev.cogwit.mind.MindContent;
import dev.cogwit.mind.MindEvents;
import dev.cogwit.mind.MindService;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import top.theillusivec4.curios.api.CuriosApi;

/** In-game checks for Cogwit's brain: scanning, hints, moods, marks and death comments. */
@GameTestHolder(CogwitMod.MODID)
@PrefixGameTestTemplate(false)
public final class MindGameTests {
    private static final float SCAN_XP = Growth.threshold(Growth.Perk.HIGHLIGHT.level());

    private MindGameTests() {}

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

    private static BrainState fed(float xp) {
        return BrainState.EMPTY.withTokens(Brain.MAX_TOKENS).withXp(xp);
    }

    private static Mind.Emotion mood(GameTestHelper helper, ItemStack talisman) {
        return Mind.emotion(MindService.state(talisman), helper.getLevel().getGameTime());
    }

    @GameTest(template = "empty")
    public static void theScanFindsOresAndChests(GameTestHelper helper) {
        Player player = player(helper);
        ItemStack talisman = wearCogwit(player, fed(SCAN_XP));
        helper.setBlock(new BlockPos(0, 1, 0), Blocks.DEEPSLATE_DIAMOND_ORE);
        helper.setBlock(new BlockPos(2, 1, 0), Blocks.IRON_ORE);
        helper.setBlock(new BlockPos(3, 1, 0), Blocks.COAL_ORE);
        helper.setBlock(new BlockPos(4, 1, 4), Blocks.CHEST);
        MindService.Found found = MindService.scanAround(helper.getLevel(), player.blockPosition());
        helper.assertTrue(found.ores().contains(new MindContent.Find(helper.absolutePos(new BlockPos(0, 1, 0)),
                Mind.Ore.DIAMOND)), "diamond ore found as a diamond: " + found.ores());
        helper.assertTrue(found.ores().contains(new MindContent.Find(helper.absolutePos(new BlockPos(2, 1, 0)),
                Mind.Ore.IRON)), "iron counts too, not only diamonds");
        helper.assertTrue(found.ores().contains(new MindContent.Find(helper.absolutePos(new BlockPos(3, 1, 0)),
                Mind.Ore.COAL)), "and coal");
        helper.assertTrue(Mind.pickFinds(found.ores(), MindContent.Find::ore, f -> 0, 1).get(0).ore()
                == Mind.Ore.DIAMOND, "with room for one outline, the diamond wins");
        helper.assertTrue(found.containers().contains(helper.absolutePos(new BlockPos(4, 1, 4))), "chest found");
        helper.assertTrue(MindService.scan(player) == Mind.Scan.OK, "a level 2 Cogwit scans");
        helper.assertTrue(CogwitTalismanItem.brain(talisman).tokens() == Brain.MAX_TOKENS - Mind.SCAN_TOKEN_COST,
                "the scan costs 30 tokens");
        helper.assertTrue(MindService.scan(player) == Mind.Scan.COOLDOWN, "then it recharges");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void afterADeathCogwitStaysAndLeadsBackToTheLoot(GameTestHelper helper) {
        Player player = player(helper);
        ItemStack talisman = wearCogwit(player, fed(0));
        helper.assertTrue(((dev.cogwit.item.CogwitTalismanItem) talisman.getItem()).getDropRule(null,
                player.damageSources().generic(), false, talisman)
                == top.theillusivec4.curios.api.type.capability.ICurio.DropRule.ALWAYS_KEEP, "Cogwit is never dropped");
        net.minecraft.world.entity.monster.Zombie zombie = helper.spawn(net.minecraft.world.entity.EntityType.ZOMBIE, 1, 1, 1);
        MindService.recordDeath(player, player.damageSources().mobAttack(zombie));
        MindService.recordDeath(player, player.damageSources().mobAttack(zombie));
        MindContent.Deaths deaths = player.getData(MindContent.DEATHS);
        helper.assertTrue(deaths.streak() == 2 && deaths.killerType().equals("minecraft:zombie"),
                "two deaths in a row to zombies: " + deaths);
        helper.assertTrue(deaths.pos().equals(player.blockPosition()), "where it happened is remembered");
        // respawned somewhere else: the compass points back
        player.teleportTo(player.getX() + 40, player.getY(), player.getZ());
        MindService.commentOnDeath(player);
        helper.assertTrue(dev.cogwit.mind.DeathCompass.active(player), "the way back is on the compass");
        player.teleportTo(deaths.pos().getX() + 0.5, deaths.pos().getY(), deaths.pos().getZ() + 0.5);
        MindService.watch(player, talisman, helper.getLevel().getGameTime());
        helper.assertFalse(dev.cogwit.mind.DeathCompass.active(player), "and it ends once you're there");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void theScanOpensAtLevelTwo(GameTestHelper helper) {
        Player player = player(helper);
        wearCogwit(player, fed(SCAN_XP - 1));
        helper.assertTrue(MindService.scan(player) == Mind.Scan.LOCKED, "locked at level 1");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void aCreeperBehindYouScaresCogwit(GameTestHelper helper) {
        Player player = player(helper);
        ItemStack talisman = wearCogwit(player, fed(0));
        // the player faces north (-z), so +z is behind
        Creeper creeper = helper.spawn(EntityType.CREEPER, 2, 1, 5);
        creeper.setNoAi(true);
        MindService.watch(player, talisman, helper.getLevel().getGameTime());
        helper.assertTrue(mood(helper, talisman) == Mind.Emotion.SCARED, "Cogwit is scared");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void lowHealthScaresCogwitToo(GameTestHelper helper) {
        Player player = player(helper);
        ItemStack talisman = wearCogwit(player, fed(0));
        player.setHealth(4f);
        MindService.watch(player, talisman, helper.getLevel().getGameTime());
        helper.assertTrue(mood(helper, talisman) == Mind.Emotion.SCARED, "scared at 4 HP");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void foodAndVictoriesMakeCogwitHappy(GameTestHelper helper) {
        Player player = player(helper);
        ItemStack talisman = wearCogwit(player, BrainState.EMPTY.withTokens(100));
        CogwitTalismanItem.feed(player, talisman, new ItemStack(Items.COPPER_INGOT), 1);
        helper.assertTrue(mood(helper, talisman) == Mind.Emotion.HAPPY, "fed and happy");
        MindService.setState(talisman, dev.cogwit.mind.MindState.EMPTY);
        Zombie zombie = helper.spawn(EntityType.ZOMBIE, 4, 1, 4);
        MindEvents.onDeath(new LivingDeathEvent(zombie, player.damageSources().playerAttack(player)));
        helper.assertTrue(mood(helper, talisman) == Mind.Emotion.HAPPY, "a win makes him happy too");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void deathsAreRememberedForTheComment(GameTestHelper helper) {
        Player player = player(helper);
        wearCogwit(player, fed(0));
        MindService.recordDeath(player, player.damageSources().fall());
        MindContent.Deaths deaths = player.getData(MindContent.DEATHS);
        helper.assertTrue(deaths.kind().equals("fall") && deaths.count() == 1, "fall death recorded: " + deaths);
        Zombie zombie = helper.spawn(EntityType.ZOMBIE, 4, 1, 4);
        MindService.recordDeath(player, player.damageSources().mobAttack(zombie));
        deaths = player.getData(MindContent.DEATHS);
        helper.assertTrue(deaths.kind().equals("mob") && deaths.count() == 2 && !deaths.killer().isEmpty(),
                "mob death with the killer's name: " + deaths);
        MindService.commentOnDeath(player);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void markingAMobMakesItGlowForEveryone(GameTestHelper helper) {
        Player player = player(helper);
        wearCogwit(player, fed(0));
        Zombie zombie = helper.spawn(EntityType.ZOMBIE, 4, 1, 4);
        helper.assertTrue(MindService.mark(player, zombie.getId(), zombie.blockPosition()), "marked");
        helper.assertTrue(zombie.hasEffect(MobEffects.GLOWING), "it glows through walls");
        helper.assertFalse(MindService.mark(player, -1, player.blockPosition().offset(500, 0, 0)), "too far to mark");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void scanTagsAreLoaded(GameTestHelper helper) {
        helper.assertTrue(Blocks.DIAMOND_ORE.defaultBlockState().is(MindContent.SCAN_ORES), "ores tag");
        helper.assertTrue(Blocks.ANCIENT_DEBRIS.defaultBlockState().is(MindContent.SCAN_ORES), "debris counts");
        helper.assertTrue(Blocks.BARREL.defaultBlockState().is(MindContent.SCAN_CONTAINERS), "containers tag");
        helper.assertFalse(Blocks.STONE.defaultBlockState().is(MindContent.SCAN_ORES), "stone is not valuable");
        helper.succeed();
    }
}
