package dev.cogwit.test;

import dev.cogwit.CogwitEvents;
import dev.cogwit.CogwitMod;
import dev.cogwit.brain.Brain;
import dev.cogwit.brain.BrainState;
import dev.cogwit.brain.Growth;
import dev.cogwit.brain.Skill;
import dev.cogwit.crew.CogwitCreature;
import dev.cogwit.crew.Crew;
import dev.cogwit.crew.CrewService;
import dev.cogwit.item.CogwitTalismanItem;
import dev.cogwit.ultra.UltraService;
import dev.cogwit.ultra.UltraState;
import dev.cogwit.ultra.Overdrive;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.ElderGuardian;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.damagesource.DamageContainer;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import top.theillusivec4.curios.api.CuriosApi;

import java.util.List;

/** In-game checks for Overdrive, the rage mode against bosses. */
@GameTestHolder(CogwitMod.MODID)
@PrefixGameTestTemplate(false)
public final class UltraGameTests {
    private static final float MAX_XP = Growth.threshold(Growth.Perk.OVERDRIVE.level());

    private UltraGameTests() {}

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

    private static ElderGuardian bossHunting(GameTestHelper helper, Player player) {
        ElderGuardian boss = helper.spawn(EntityType.ELDER_GUARDIAN, 5, 2, 5);
        boss.setNoAi(true);
        boss.setTarget(player);
        return boss;
    }

    private static UltraState raging(long now) {
        return new UltraState(true, now, now, 0, false, 1f);
    }

    @GameTest(template = "empty")
    public static void aBossFightWakesOverdrive(GameTestHelper helper) {
        Player player = player(helper);
        ItemStack talisman = wearCogwit(player, fed(MAX_XP));
        bossHunting(helper, player);
        long now = helper.getLevel().getGameTime();
        UltraService.tick(player, talisman, now);
        UltraState u = UltraService.state(talisman);
        helper.assertTrue(u.active(), "a boss hunting a level 5 Cogwit's owner starts Overdrive");
        helper.assertTrue(player.hasEffect(MobEffects.DAMAGE_BOOST), "the owner gets Strength");
        helper.assertTrue(player.hasEffect(MobEffects.REGENERATION), "and Regeneration");
        List<CogwitCreature> crew = CrewService.crewOf(player);
        helper.assertTrue(crew.size() == 4, "the whole crew jumps down, got " + crew.size());
        helper.assertTrue(crew.stream().allMatch(CogwitCreature::rage), "everyone is in rage");
        helper.assertTrue(u.crewByUltra(), "Overdrive will also call them back");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void notBeforeLevelFive(GameTestHelper helper) {
        Player player = player(helper);
        ItemStack talisman = wearCogwit(player, fed(MAX_XP - 1));
        bossHunting(helper, player);
        UltraService.tick(player, talisman, helper.getLevel().getGameTime());
        helper.assertFalse(UltraService.isActive(talisman), "level 4 Cogwit does not rage");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void aBossThatIgnoresYouDoesNotCount(GameTestHelper helper) {
        Player player = player(helper);
        ItemStack talisman = wearCogwit(player, fed(MAX_XP));
        ElderGuardian boss = helper.spawn(EntityType.ELDER_GUARDIAN, 5, 2, 5);
        boss.setNoAi(true);
        UltraService.tick(player, talisman, helper.getLevel().getGameTime());
        helper.assertFalse(UltraService.isActive(talisman), "a boss just floating nearby is not a fight");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void overdriveEndsWithAHangoverAndCallsTheCrewBack(GameTestHelper helper) {
        Player player = player(helper);
        ItemStack talisman = wearCogwit(player, fed(MAX_XP));
        ElderGuardian boss = bossHunting(helper, player);
        long now = helper.getLevel().getGameTime();
        UltraService.tick(player, talisman, now);
        boss.discard();
        UltraService.tick(player, talisman, now + 20);
        helper.assertTrue(UltraService.isActive(talisman), "lingers a few seconds after the boss");
        UltraService.tick(player, talisman, now + Overdrive.LINGER_TICKS);
        UltraState u = UltraService.state(talisman);
        helper.assertFalse(u.active(), "over");
        helper.assertTrue(Overdrive.inHangover(u, now + Overdrive.LINGER_TICKS + 1), "hangover starts");
        helper.assertTrue(CrewService.crewOf(player).isEmpty(), "the crew Overdrive sent goes home");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void rageBlocksEvenUnlearnedHitsAtTripleCost(GameTestHelper helper) {
        Player player = player(helper);
        ItemStack talisman = wearCogwit(player, fed(MAX_XP));
        long now = helper.getLevel().getGameTime();
        UltraService.setState(talisman, raging(now));
        LivingIncomingDamageEvent event = new LivingIncomingDamageEvent(player,
                new DamageContainer(player.damageSources().onFire(), 8f));
        CogwitEvents.onIncomingDamage(event);
        helper.assertTrue(Math.abs(event.getAmount() - 6f) < 0.01f, "25% blocked although fire is new, got "
                + event.getAmount());
        int spent = Brain.MAX_TOKENS - CogwitTalismanItem.brain(talisman).tokens();
        helper.assertTrue(spent == 24, "8 x 25% x 4 tokens = 8, tripled = 24, spent " + spent);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void theHangoverHalvesProtection(GameTestHelper helper) {
        Player player = player(helper);
        ItemStack talisman = wearCogwit(player, new BrainState(List.of(new Skill("fire", 50, 0, 0, "", 0)),
                Brain.MAX_TOKENS, 0, 0, 0, 0, 0));
        long now = helper.getLevel().getGameTime();
        UltraService.setState(talisman, new UltraState(false, 0, 0, now + 1000, false, 1f));
        LivingIncomingDamageEvent event = new LivingIncomingDamageEvent(player,
                new DamageContainer(player.damageSources().onFire(), 10f));
        CogwitEvents.onIncomingDamage(event);
        float expected = 10f * (1f - 0.6f * dev.cogwit.brain.Brain.blockScale() / 2f);
        helper.assertTrue(Math.abs(event.getAmount() - expected) < 0.01f, "the skill halved, got " + event.getAmount());
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void theCrewCostsTripleInRage(GameTestHelper helper) {
        Player player = player(helper);
        ItemStack talisman = wearCogwit(player, BrainState.EMPTY.withTokens(100));
        CrewService.deploy(player, CrewService.Reason.KEY);
        long now = helper.getLevel().getGameTime();
        UltraService.setState(talisman, raging(now));
        CrewService.tick(player, talisman, now);
        helper.assertTrue(CogwitTalismanItem.brain(talisman).tokens() == 97, "1 token x3, got "
                + CogwitTalismanItem.brain(talisman).tokens());
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void aSleepingCogwitDropsOutOfRage(GameTestHelper helper) {
        Player player = player(helper);
        long now = helper.getLevel().getGameTime();
        ItemStack talisman = wearCogwit(player, fed(MAX_XP).asleep(now, now + 500));
        UltraService.setState(talisman, raging(now));
        UltraService.tick(player, talisman, now + 1);
        helper.assertFalse(UltraService.isActive(talisman), "rage collapses when Cogwit falls asleep");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void bossPhasesAreTracked(GameTestHelper helper) {
        Player player = player(helper);
        ItemStack talisman = wearCogwit(player, fed(MAX_XP));
        ElderGuardian boss = bossHunting(helper, player);
        long now = helper.getLevel().getGameTime();
        UltraService.tick(player, talisman, now);
        boss.setHealth(boss.getMaxHealth() * 0.6f);
        UltraService.tick(player, talisman, now + 20);
        float seen = UltraService.state(talisman).bossHealth();
        helper.assertTrue(Math.abs(seen - 0.6f) < 0.01f, "boss health share remembered, got " + seen);
        helper.assertTrue(CrewService.crewOf(player).stream().filter(c -> c.kind() == Crew.Kind.ANVIL)
                .allMatch(c -> c.getTarget() == boss), "the crew goes for the boss");
        helper.succeed();
    }
}
