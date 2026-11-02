package dev.cogwit.test;

import dev.cogwit.CogwitEvents;
import dev.cogwit.CogwitMod;
import dev.cogwit.brain.Brain;
import dev.cogwit.brain.BrainState;
import dev.cogwit.brain.Growth;
import dev.cogwit.brain.Skill;
import dev.cogwit.crew.CogwitCreature;
import dev.cogwit.crew.Crew;
import dev.cogwit.crew.CrewContent;
import dev.cogwit.crew.CrewGear;
import dev.cogwit.crew.CrewService;
import dev.cogwit.growth.GrowthContent;
import dev.cogwit.horizon.HorizonContent;
import dev.cogwit.horizon.HorizonEvents;
import dev.cogwit.item.CogwitTalismanItem;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.damagesource.DamageContainer;
import net.neoforged.neoforge.event.entity.living.LivingEquipmentChangeEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import software.bernie.geckolib.animatable.GeoItem;
import top.theillusivec4.curios.api.CuriosApi;

import java.util.List;

/** In-game checks for Cogwit off the shoulder and his brothers. */
@GameTestHolder(CogwitMod.MODID)
@PrefixGameTestTemplate(false)
public final class CrewGameTests {
    private CrewGameTests() {}

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

    private static ItemStack talisman(Player player) {
        return CogwitTalismanItem.worn(player).orElseThrow();
    }

    private static BrainState fed(float xp) {
        return BrainState.EMPTY.withTokens(Brain.MAX_TOKENS).withXp(xp);
    }

    private static final float BROTHERS_XP = Growth.threshold(Growth.Perk.BROTHERS.level());

    private static CogwitCreature member(Player player, Crew.Kind kind) {
        return CrewService.crewOf(player).stream().filter(c -> c.kind() == kind).findFirst().orElseThrow();
    }

    @GameTest(template = "empty")
    public static void cogwitJumpsDownAndClimbsBack(GameTestHelper helper) {
        Player player = player(helper);
        wearCogwit(player, fed(0));
        helper.assertTrue(CrewService.deploy(player, CrewService.Reason.KEY), "a fed, awake Cogwit can jump down");
        helper.assertTrue(CrewService.crewOf(player).size() == 1, "only Cogwit before level 4");
        helper.assertTrue(CrewService.isDeployed(talisman(player)), "the talisman knows he is out");
        CrewService.toggle(player);
        helper.assertTrue(CrewService.crewOf(player).isEmpty(), "V again brings him back");
        helper.assertFalse(CrewService.isDeployed(talisman(player)), "and he is on the shoulder");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void sleepingOrHungryCogwitStaysOnTheShoulder(GameTestHelper helper) {
        Player player = player(helper);
        long now = helper.getLevel().getGameTime();
        wearCogwit(player, fed(0).asleep(now, now + 1000));
        helper.assertFalse(CrewService.deploy(player, CrewService.Reason.KEY), "asleep");
        CogwitTalismanItem.setBrain(talisman(player), BrainState.EMPTY.withTokens(Crew.MIN_TOKENS_TO_DEPLOY - 1));
        helper.assertFalse(CrewService.deploy(player, CrewService.Reason.KEY), "too hungry");
        helper.assertTrue(CrewService.crewOf(player).isEmpty(), "nobody came down");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void brothersComeAlongFromLevelFour(GameTestHelper helper) {
        Player player = player(helper);
        wearCogwit(player, fed(BROTHERS_XP));
        CrewService.deploy(player, CrewService.Reason.KEY);
        List<CogwitCreature> crew = CrewService.crewOf(player);
        helper.assertTrue(crew.size() == 4, "Cogwit and three brothers, got " + crew.size());
        CogwitCreature anvil = member(player, Crew.Kind.ANVIL);
        CogwitCreature spark = member(player, Crew.Kind.SPARK);
        helper.assertTrue(anvil.getMaxHealth() == (float) Crew.Kind.ANVIL.health, "Anvil is the tank");
        helper.assertTrue(anvil.getScale() > spark.getScale(), "Anvil is bigger than Spark");
        helper.assertTrue(crew.stream().allMatch(c -> c.isOwnedBy(player)), "they all belong to the player");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void gearGoesBackOntoTheTalismanWithItsWear(GameTestHelper helper) {
        Player player = player(helper);
        wearCogwit(player, fed(0));
        CrewService.deploy(player, CrewService.Reason.KEY);
        CogwitCreature cogwit = member(player, Crew.Kind.COGWIT);
        double bareArmor = cogwit.getAttributeValue(Attributes.ARMOR);
        ItemStack chest = new ItemStack(Items.DIAMOND_CHESTPLATE);
        chest.setDamageValue(10);
        cogwit.setItemSlot(EquipmentSlot.CHEST, chest);
        cogwit.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
        CrewService.toggle(player);
        ItemStack saved = CrewGear.of(talisman(player), Crew.Kind.COGWIT).get(1);
        helper.assertTrue(saved.is(Items.DIAMOND_CHESTPLATE) && saved.getDamageValue() == 10,
                "the chestplate is kept with its wear, got " + saved);
        CrewService.toggle(player);
        CogwitCreature again = member(player, Crew.Kind.COGWIT);
        helper.assertTrue(again.getItemBySlot(EquipmentSlot.CHEST).is(Items.DIAMOND_CHESTPLATE), "and worn again");
        helper.assertTrue(again.getMainHandItem().is(Items.IRON_SWORD), "sword in the claw");
        // equipment bonuses are applied on the creature's next tick
        helper.runAfterDelay(2, () -> {
            helper.assertTrue(again.getAttributeValue(Attributes.ARMOR) > bareArmor, "armor counts, got "
                    + again.getAttributeValue(Attributes.ARMOR));
            helper.succeed();
        });
    }

    @GameTest(template = "empty")
    public static void upkeepCostsTokensAndAnEmptyTankSendsHimHome(GameTestHelper helper) {
        Player player = player(helper);
        wearCogwit(player, BrainState.EMPTY.withTokens(25));
        CrewService.deploy(player, CrewService.Reason.KEY);
        long now = helper.getLevel().getGameTime();
        CrewService.tick(player, talisman(player), now);
        helper.assertTrue(CogwitTalismanItem.brain(talisman(player)).tokens() == 24, "one token per second");
        CogwitTalismanItem.setBrain(talisman(player), BrainState.EMPTY);
        CrewService.tick(player, talisman(player), now + 20);
        helper.assertTrue(CrewService.crewOf(player).isEmpty(), "out of tokens, back to the shoulder");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void knockedOutCogwitTakesEveryoneHome(GameTestHelper helper) {
        Player player = player(helper);
        wearCogwit(player, fed(BROTHERS_XP));
        CrewService.deploy(player, CrewService.Reason.KEY);
        CogwitCreature cogwit = member(player, Crew.Kind.COGWIT);
        CrewService.creatureDown(player, cogwit);
        cogwit.discard();
        long now = helper.getLevel().getGameTime();
        helper.assertTrue(Brain.isAsleep(CogwitTalismanItem.brain(talisman(player)), now), "Cogwit naps");
        helper.assertTrue(CrewService.crewOf(player).isEmpty(), "the brothers go home with him");
        helper.assertFalse(CrewService.isDeployed(talisman(player)), "back on the shoulder");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void aFallenBrotherSitsOutAndCogwitGrieves(GameTestHelper helper) {
        Player player = player(helper);
        BrainState trained = new BrainState(List.of(new Skill("fire", 50, 0, 0, "", 0)), Brain.MAX_TOKENS,
                0, 0, 0, 0, 0).withXp(BROTHERS_XP);
        wearCogwit(player, trained);
        CrewService.deploy(player, CrewService.Reason.KEY);
        CogwitCreature rivet = member(player, Crew.Kind.RIVET);
        CrewService.creatureDown(player, rivet);
        rivet.discard();
        long now = helper.getLevel().getGameTime();
        helper.assertTrue(CrewService.state(talisman(player)).isDown(Crew.Kind.RIVET, now), "Rivet sits out");
        CrewService.tick(player, talisman(player), now);
        helper.assertTrue(CrewService.crewOf(player).stream().noneMatch(c -> c.kind() == Crew.Kind.RIVET),
                "and does not come back right away");
        LivingIncomingDamageEvent event = new LivingIncomingDamageEvent(player,
                new DamageContainer(player.damageSources().onFire(), 10f));
        CogwitEvents.onIncomingDamage(event);
        float expected = 10f * (1f - 0.6f * dev.cogwit.brain.Brain.blockScale() * 0.7f);
        helper.assertTrue(Math.abs(event.getAmount() - expected) < 0.05f, "the skill x 0.7 grief, got "
                + event.getAmount());
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void theCrewSharesCogwitsSkills(GameTestHelper helper) {
        Player player = player(helper);
        wearCogwit(player, new BrainState(List.of(new Skill("fire", 50, 0, 0, "", 0)), Brain.MAX_TOKENS,
                0, 0, 0, 0, 0));
        CrewService.deploy(player, CrewService.Reason.KEY);
        CogwitCreature cogwit = member(player, Crew.Kind.COGWIT);
        cogwit.invulnerableTime = 0;
        cogwit.hurt(cogwit.damageSources().onFire(), 10f);
        float health = 20f - 10f * (1f - 0.6f * dev.cogwit.brain.Brain.blockScale());
        helper.assertTrue(Math.abs(cogwit.getHealth() - health) < 0.05f, "the fire skill blocks, health " + cogwit.getHealth());
        helper.assertFalse(cogwit.hurt(player.damageSources().playerAttack(player), 5f), "players can't hurt the crew");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void copperAppleHealsACrewMember(GameTestHelper helper) {
        Player player = player(helper);
        wearCogwit(player, fed(0));
        CrewService.deploy(player, CrewService.Reason.KEY);
        CogwitCreature cogwit = member(player, Crew.Kind.COGWIT);
        cogwit.setHealth(5f);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(GrowthContent.COPPER_APPLE.get(), 2));
        cogwit.mobInteract(player, InteractionHand.MAIN_HAND);
        helper.assertTrue(cogwit.getHealth() == 5f + CogwitCreature.APPLE_HEAL, "healed, got " + cogwit.getHealth());
        helper.assertTrue(player.getMainHandItem().getCount() == 1, "one apple used");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void rescueSendsCogwitDownToCoverThenToSleep(GameTestHelper helper) {
        Player player = player(helper);
        wearCogwit(player, fed(0));
        player.hurt(player.damageSources().generic(), 50f);
        helper.assertTrue(player.isAlive(), "rescued");
        helper.assertTrue(CrewService.crewOf(player).size() == 1, "Cogwit jumped down to cover");
        long now = helper.getLevel().getGameTime();
        CrewService.tick(player, talisman(player), now + 20);
        helper.assertTrue(CrewService.crewOf(player).size() == 1, "still covering although asleep");
        CrewService.tick(player, talisman(player), now + Crew.RESCUE_COVER_TICKS);
        helper.assertTrue(CrewService.crewOf(player).isEmpty(), "cover over: back up for the nap");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void takingTheTalismanOffCallsTheCrewHome(GameTestHelper helper) {
        Player player = player(helper);
        ItemStack stack = wearCogwit(player, fed(0));
        CrewService.deploy(player, CrewService.Reason.KEY);
        member(player, Crew.Kind.COGWIT).setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
        CrewService.recall(player, stack, null);
        helper.assertTrue(CrewService.crewOf(player).isEmpty(), "home");
        helper.assertTrue(CrewGear.of(stack, Crew.Kind.COGWIT).get(0).is(Items.IRON_HELMET), "helmet kept");
        helper.assertFalse(CrewContent.CREATURE.get().canSerialize(), "the crew is never saved into the world");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void theCrewLeavesPlayersAlone(GameTestHelper helper) {
        Player player = player(helper);
        Player friend = player(helper);
        wearCogwit(player, fed(0));
        CrewService.deploy(player, CrewService.Reason.KEY);
        CogwitCreature cogwit = member(player, Crew.Kind.COGWIT);
        helper.assertFalse(cogwit.wantsToAttack(friend, player), "no PvP for Cogwit, even on foot");
        helper.assertFalse(cogwit.canAttack(friend), "never targets players");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void puttingOnHorizonArmorPlaysTheSuitUp(GameTestHelper helper) {
        Player player = player(helper);
        ItemStack helmet = new ItemStack(HorizonContent.HELMET.get());
        player.setItemSlot(EquipmentSlot.HEAD, helmet);
        HorizonEvents.onEquipmentChange(new LivingEquipmentChangeEvent(player, EquipmentSlot.HEAD, ItemStack.EMPTY,
                helmet));
        helper.assertTrue(GeoItem.getId(helmet) != Long.MAX_VALUE, "the helmet got an animation id to play on");
        helper.succeed();
    }
}
