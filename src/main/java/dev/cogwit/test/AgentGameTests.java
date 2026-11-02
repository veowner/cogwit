package dev.cogwit.test;

import dev.cogwit.CogwitMod;
import dev.cogwit.agent.Agent;
import dev.cogwit.agent.AgentService;
import dev.cogwit.agent.AgentState;
import dev.cogwit.brain.Brain;
import dev.cogwit.brain.BrainState;
import dev.cogwit.brain.Growth;
import dev.cogwit.crew.CogwitCreature;
import dev.cogwit.crew.Crew;
import dev.cogwit.crew.CrewService;
import dev.cogwit.horizon.HorizonContent;
import dev.cogwit.item.CogwitTalismanItem;
import dev.cogwit.network.AgentSettingsPayload;
import dev.cogwit.trophy.Trophy;
import dev.cogwit.trophy.TrophyContent;
import dev.cogwit.trophy.TrophyService;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ThrownEnderpearl;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import top.theillusivec4.curios.api.CuriosApi;

import java.util.List;

/** In-game checks for Cogwit the agent: permissions, questions and each action. */
@GameTestHolder(CogwitMod.MODID)
@PrefixGameTestTemplate(false)
public final class AgentGameTests {
    private AgentGameTests() {}

    private static Player player(GameTestHelper helper) {
        Player player = TestPlayers.mock(helper);
        Vec3 spot = helper.absoluteVec(new Vec3(2.5, 1, 2.5));
        player.moveTo(spot.x, spot.y, spot.z, 180f, 0f);
        helper.getLevel().addFreshEntity(player);
        player.setHealth(player.getMaxHealth());
        player.getInventory().clearContent();
        return player;
    }

    private static ItemStack wearCogwit(Player player, BrainState brain) {
        ItemStack stack = new ItemStack(CogwitMod.COGWIT.get());
        CogwitTalismanItem.setBrain(stack, brain);
        CuriosApi.getCuriosInventory(player).flatMap(inv -> inv.getStacksHandler("charm")).orElseThrow()
                .getStacks().setStackInSlot(0, stack);
        return stack;
    }

    private static BrainState fed() {
        return BrainState.EMPTY.withTokens(Brain.MAX_TOKENS);
    }

    private static boolean run(Player player, Agent.Action action) {
        return AgentService.attempt(player, action, AgentService.plan(player, action));
    }

    @GameTest(template = "empty")
    public static void cogwitFeedsAHungryOwner(GameTestHelper helper) {
        Player player = player(helper);
        ItemStack talisman = wearCogwit(player, fed());
        player.getFoodData().setFoodLevel(10);
        player.getInventory().add(new ItemStack(Items.BREAD, 3));
        helper.assertTrue(run(player, Agent.Action.EAT), "a careful Cogwit feeds you without asking");
        helper.assertTrue(player.getFoodData().getFoodLevel() > 10, "fed");
        helper.assertTrue(player.getInventory().countItem(Items.BREAD) == 2, "one bread eaten");
        helper.assertTrue(CogwitTalismanItem.brain(talisman).tokens() == Brain.MAX_TOKENS
                - Agent.Action.EAT.tokenCost, "and it cost a little");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void goldenApplesAreNotSnacks(GameTestHelper helper) {
        Player player = player(helper);
        wearCogwit(player, fed());
        player.getFoodData().setFoodLevel(10);
        player.getInventory().add(new ItemStack(Items.GOLDEN_APPLE));
        helper.assertFalse(run(player, Agent.Action.EAT), "the golden apple waits for a boss");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void neverMeansNever(GameTestHelper helper) {
        Player player = player(helper);
        ItemStack talisman = wearCogwit(player, fed());
        AgentService.setState(talisman, AgentState.DEFAULT.with(Agent.Action.EAT, Agent.Permission.NEVER));
        player.getFoodData().setFoodLevel(5);
        player.getInventory().add(new ItemStack(Items.BREAD));
        helper.assertFalse(run(player, Agent.Action.EAT), "forbidden");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void askingAndAlwaysAllowing(GameTestHelper helper) {
        Player player = player(helper);
        ItemStack talisman = wearCogwit(player, fed());
        helper.spawn(EntityType.ZOMBIE, 2, 1, 6).setNoAi(true);
        player.getInventory().items.set(3, new ItemStack(Items.DIAMOND_SWORD));
        player.getInventory().selected = 0;
        helper.assertFalse(run(player, Agent.Action.WEAPON), "careful mode asks about weapons out of a fight");
        int id = AgentService.lastAsk(player);
        helper.assertTrue(id > 0, "a question is waiting");
        AgentService.answer(player, id, true);
        helper.assertTrue(player.getInventory().selected == 3, "yes: the sword is in hand");
        helper.assertTrue(AgentService.state(talisman).permission(Agent.Action.WEAPON) == Agent.Permission.ALWAYS,
                "Shift+Y: never ask again");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void inAFightCogwitDoesNotAsk(GameTestHelper helper) {
        Player player = player(helper);
        wearCogwit(player, fed());
        Zombie zombie = helper.spawn(EntityType.ZOMBIE, 2, 1, 6);
        zombie.setNoAi(true);
        player.getInventory().items.set(5, new ItemStack(Items.IRON_AXE));
        player.getInventory().selected = 0;
        AgentService.noteFight(player);
        helper.assertTrue(run(player, Agent.Action.WEAPON), "mid-fight, ask turns into act");
        helper.assertTrue(player.getInventory().selected == 5, "the axe is in hand");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void cogwitLeavesYourChoiceInHand(GameTestHelper helper) {
        Player player = player(helper);
        wearCogwit(player, fed());
        helper.spawn(EntityType.ZOMBIE, 2, 1, 4).setNoAi(true);
        var inv = player.getInventory();
        inv.items.set(3, new ItemStack(Items.NETHERITE_SWORD));
        inv.selected = 0;
        AgentService.noteFight(player);
        inv.items.set(0, new ItemStack(Items.SHIELD));
        helper.assertFalse(run(player, Agent.Action.WEAPON), "a shield stays in hand");
        inv.items.set(0, new ItemStack(Items.COBBLESTONE, 32));
        helper.assertFalse(run(player, Agent.Action.WEAPON), "blocks stay in hand");
        inv.items.set(0, new ItemStack(Items.IRON_SWORD));
        helper.assertFalse(run(player, Agent.Action.WEAPON), "an iron sword is close enough to netherite");
        inv.items.set(0, new ItemStack(Items.WOODEN_SWORD));
        helper.assertTrue(run(player, Agent.Action.WEAPON), "a wooden sword gives way to netherite");
        helper.assertTrue(inv.selected == 3, "the netherite sword is in hand");
        inv.selected = 0;
        inv.items.set(0, new ItemStack(Items.IRON_PICKAXE));
        helper.assertTrue(run(player, Agent.Action.WEAPON), "a pickaxe gives way with a zombie at arm's length");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void cogwitCastsATrophyAtToughFoesNotAtAZombie(GameTestHelper helper) {
        Player player = player(helper);
        player.setItemSlot(EquipmentSlot.HEAD, new ItemStack(HorizonContent.HELMET.get()));
        player.setItemSlot(EquipmentSlot.CHEST, new ItemStack(HorizonContent.CHESTPLATE.get()));
        player.setItemSlot(EquipmentSlot.LEGS, new ItemStack(HorizonContent.LEGGINGS.get()));
        player.setItemSlot(EquipmentSlot.FEET, new ItemStack(HorizonContent.BOOTS.get()));
        wearCogwit(player, fed());
        TrophyService.award(player, Trophy.HARBINGER);
        Zombie zombie = helper.spawn(EntityType.ZOMBIE, 2, 1, 4);
        zombie.setNoAi(true);
        AgentService.noteFight(player);
        helper.assertFalse(run(player, Agent.Action.TROPHIES), "a lone zombie is left to the sword");
        zombie.discard();
        helper.spawn(EntityType.RAVAGER, 2, 1, 4).setNoAi(true);
        helper.assertTrue(run(player, Agent.Action.TROPHIES), "a ravager is worth the missiles");
        helper.assertTrue(player.getData(TrophyContent.DATA).readyAt().containsKey(Trophy.HARBINGER.key()),
                "the missiles were fired and are recharging");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void refillsComeFromABackpackToo(GameTestHelper helper) {
        Player player = player(helper);
        wearCogwit(player, fed());
        var inv = player.getInventory();
        ItemStack box = new ItemStack(Items.SHULKER_BOX);
        box.set(net.minecraft.core.component.DataComponents.CONTAINER,
                net.minecraft.world.item.component.ItemContainerContents.fromItems(List.of(ItemStack.EMPTY,
                        new ItemStack(Items.COOKED_BEEF, 20))));
        inv.items.set(15, box);
        inv.items.set(0, new ItemStack(Items.COOKED_BEEF));
        AgentService.tick(player);
        inv.items.set(0, ItemStack.EMPTY); // eaten
        AgentService.tick(player);
        helper.assertTrue(inv.items.get(0).is(Items.COOKED_BEEF) && inv.items.get(0).getCount() == 20,
                "the beef came out of the shulker box, got " + inv.items.get(0));
        var left = inv.items.get(15).get(net.minecraft.core.component.DataComponents.CONTAINER);
        helper.assertTrue(left != null && left.nonEmptyStream().findAny().isEmpty(), "and the box is empty now");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void usedUpStacksRefillMovedOnesDoNot(GameTestHelper helper) {
        Player player = player(helper);
        wearCogwit(player, fed());
        var inv = player.getInventory();
        inv.items.set(0, new ItemStack(Items.BREAD));
        inv.items.set(20, new ItemStack(Items.BREAD, 16));
        inv.items.set(1, new ItemStack(Items.TORCH));
        inv.items.set(21, new ItemStack(Items.TORCH, 10));
        ItemStack worn = new ItemStack(Items.IRON_PICKAXE), fresh = new ItemStack(Items.IRON_PICKAXE);
        worn.setDamageValue(200);
        fresh.setDamageValue(10);
        inv.items.set(2, new ItemStack(Items.IRON_PICKAXE));
        inv.items.set(22, worn);
        inv.items.set(23, fresh);
        AgentService.tick(player);
        inv.items.set(0, ItemStack.EMPTY); // eaten
        inv.items.set(30, inv.items.get(1)); // moved to the backpack
        inv.items.set(1, ItemStack.EMPTY);
        inv.items.set(2, ItemStack.EMPTY); // broke
        AgentService.tick(player);
        helper.assertTrue(inv.items.get(0).is(Items.BREAD) && inv.items.get(0).getCount() == 16, "the eaten bread is replaced");
        helper.assertTrue(inv.items.get(1).isEmpty(), "a moved torch is not");
        helper.assertTrue(inv.items.get(2).is(Items.IRON_PICKAXE) && inv.items.get(2).getDamageValue() == 10,
                "a broken pickaxe is replaced by the freshest one");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void breakingArmorGoesToTheBackpackOrTheVault(GameTestHelper helper) {
        Player player = player(helper);
        ItemStack talisman = wearCogwit(player, fed());
        ItemStack helmet = new ItemStack(Items.IRON_HELMET);
        helmet.setDamageValue(helmet.getMaxDamage() - 3);
        player.setItemSlot(EquipmentSlot.HEAD, helmet);
        helper.assertTrue(run(player, Agent.Action.ARMOR_SAVE), "taken off");
        helper.assertTrue(player.getItemBySlot(EquipmentSlot.HEAD).isEmpty(), "head bare");
        helper.assertTrue(player.getInventory().countItem(Items.IRON_HELMET) == 1, "helmet in the backpack");
        player.getInventory().clearContent();
        for (int i = 0; i < player.getInventory().items.size(); i++) {
            player.getInventory().items.set(i, new ItemStack(Items.DIRT, 64));
        }
        ItemStack boots = new ItemStack(Items.IRON_BOOTS);
        boots.setDamageValue(boots.getMaxDamage() - 2);
        player.setItemSlot(EquipmentSlot.FEET, boots);
        run(player, Agent.Action.ARMOR_SAVE);
        helper.assertTrue(AgentService.vault(talisman).stream().anyMatch(s -> s.is(Items.IRON_BOOTS)),
                "full backpack: the boots go into Cogwit's vault");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void aPearlWhenItGetsCritical(GameTestHelper helper) {
        Player player = player(helper);
        wearCogwit(player, fed());
        Zombie zombie = helper.spawn(EntityType.ZOMBIE, 2, 1, 4);
        zombie.setNoAi(true);
        player.getInventory().add(new ItemStack(Items.ENDER_PEARL, 2));
        player.setHealth(3f);
        AgentService.noteFight(player);
        helper.assertTrue(run(player, Agent.Action.PEARL), "pearl thrown");
        helper.assertTrue(player.getInventory().countItem(Items.ENDER_PEARL) == 1, "one pearl used");
        helper.assertTrue(!helper.getLevel().getEntitiesOfClass(ThrownEnderpearl.class,
                player.getBoundingBox().inflate(4)).isEmpty(), "and it is flying");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void waterBreaksAFall(GameTestHelper helper) {
        Player player = player(helper);
        wearCogwit(player, fed());
        player.getInventory().add(new ItemStack(Items.WATER_BUCKET));
        Vec3 high = helper.absoluteVec(new Vec3(2.5, 4, 2.5));
        player.moveTo(high.x, high.y, high.z);
        player.setOnGround(false);
        player.fallDistance = 9;
        player.setDeltaMovement(0, -1, 0);
        AgentService.tick(player);
        boolean water = false;
        for (int y = 0; y <= 4; y++) {
            water |= helper.getLevel().getBlockState(BlockPos.containing(helper.absoluteVec(new Vec3(2.5, y, 2.5))))
                    .is(Blocks.WATER);
        }
        helper.assertTrue(water, "water placed under the falling player");
        helper.assertTrue(player.getInventory().countItem(Items.BUCKET) == 1, "the bucket is empty now");
        helper.succeed();
    }

    // own batch: another test's player standing nearby could pull the same diamond their way
    @GameTest(template = "empty", batch = "magnet")
    public static void theMagnetSkipsJunk(GameTestHelper helper) {
        Player player = player(helper);
        ItemStack talisman = wearCogwit(player, fed());
        AgentService.setState(talisman, AgentState.DEFAULT.withMode(Agent.Mode.MINER));
        Vec3 at = helper.absoluteVec(new Vec3(6.5, 1.2, 6.5));
        ItemEntity diamond = new ItemEntity(helper.getLevel(), at.x, at.y, at.z, new ItemStack(Items.DIAMOND));
        ItemEntity cobble = new ItemEntity(helper.getLevel(), at.x, at.y, at.z, new ItemStack(Items.COBBLESTONE));
        diamond.setNoPickUpDelay();
        cobble.setNoPickUpDelay();
        diamond.setDeltaMovement(Vec3.ZERO);
        cobble.setDeltaMovement(Vec3.ZERO);
        helper.getLevel().addFreshEntity(diamond);
        helper.getLevel().addFreshEntity(cobble);
        AgentService.pull(player);
        Vec3 toPlayer = player.position().subtract(diamond.position());
        helper.assertTrue(diamond.getDeltaMovement().dot(toPlayer) > 0, "the diamond flies to you");
        helper.assertTrue(cobble.getDeltaMovement().lengthSqr() < 1e-6, "cobblestone stays");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void harvestedWheatIsReplanted(GameTestHelper helper) {
        Player player = player(helper);
        wearCogwit(player, fed());
        BlockPos soil = new BlockPos(4, 1, 4);
        helper.setBlock(soil, Blocks.FARMLAND);
        BlockPos crop = soil.above();
        var grown = ((CropBlock) Blocks.WHEAT).getStateForAge(7);
        player.getInventory().add(new ItemStack(Items.WHEAT_SEEDS, 3));
        BlockPos abs = helper.absolutePos(crop);
        AgentService.harvested(player, abs, grown);
        helper.setBlock(crop, Blocks.AIR);
        helper.runAfterDelay(3, () -> {
            helper.assertBlockPresent(Blocks.WHEAT, crop);
            helper.assertTrue(player.getInventory().countItem(Items.WHEAT_SEEDS) == 2, "one seed used");
            helper.succeed();
        });
    }

    @GameTest(template = "empty")
    public static void settingsFromTheScreenApply(GameTestHelper helper) {
        Player player = player(helper);
        ItemStack talisman = wearCogwit(player, fed());
        AgentService.applySettings(player, new AgentSettingsPayload("combat", List.of(), false));
        helper.assertTrue(AgentService.state(talisman).mode() == Agent.Mode.COMBAT, "mode set");
        AgentService.applySettings(player, new AgentSettingsPayload("", List.of("magnet=never", "junk", "eat=ask"),
                false));
        AgentState s = AgentService.state(talisman);
        helper.assertTrue(s.permission(Agent.Action.MAGNET) == Agent.Permission.NEVER
                && s.permission(Agent.Action.EAT) == Agent.Permission.ASK, "overrides set, junk ignored");
        AgentService.applySettings(player, new AgentSettingsPayload("", List.of(), true));
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void sparkSlowsAndAnvilFinishes(GameTestHelper helper) {
        Player player = player(helper);
        wearCogwit(player, fed().withXp(Growth.threshold(Growth.Perk.BROTHERS.level())));
        CrewService.deploy(player, CrewService.Reason.KEY);
        CogwitCreature spark = CrewService.crewOf(player).stream().filter(c -> c.kind() == Crew.Kind.SPARK)
                .findFirst().orElseThrow();
        CogwitCreature anvil = CrewService.crewOf(player).stream().filter(c -> c.kind() == Crew.Kind.ANVIL)
                .findFirst().orElseThrow();
        Zombie zombie = helper.spawn(EntityType.ZOMBIE, 4, 1, 4);
        zombie.setNoAi(true);
        spark.doHurtTarget(zombie);
        helper.assertTrue(zombie.hasEffect(MobEffects.MOVEMENT_SLOWDOWN), "Spark slows it");
        float before = zombie.getHealth();
        zombie.invulnerableTime = 0;
        anvil.doHurtTarget(zombie);
        float dealt = before - zombie.getHealth();
        helper.assertTrue(dealt > (float) Crew.Kind.ANVIL.attack + 0.5f, "Anvil hits a slowed target harder, dealt " + dealt);
        helper.succeed();
    }
}
