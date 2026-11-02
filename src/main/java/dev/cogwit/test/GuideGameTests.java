package dev.cogwit.test;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.ParseResults;
import dev.cogwit.CogwitCommands;
import dev.cogwit.CogwitMod;
import dev.cogwit.brain.BrainState;
import dev.cogwit.guide.GuideCommands;
import dev.cogwit.guide.GuideContent;
import dev.cogwit.guide.GuideCoop;
import dev.cogwit.guide.GuideFacts;
import dev.cogwit.guide.GuideFactsReader;
import dev.cogwit.guide.GuideLocator;
import dev.cogwit.guide.GuideProgress;
import dev.cogwit.guide.GuideService;
import dev.cogwit.guide.GuideState;
import dev.cogwit.guide.GuideTiming;
import dev.cogwit.guide.Milestone;
import dev.cogwit.horizon.HorizonContent;
import dev.cogwit.item.CogwitTalismanItem;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import top.theillusivec4.curios.api.CuriosApi;

import java.util.List;
import java.util.Optional;

/** In-game checks for Cogwit the guide, run with {@code ./gradlew runGameTestServer}. */
@GameTestHolder(CogwitMod.MODID)
@PrefixGameTestTemplate(false)
public final class GuideGameTests {
    /** A fixed point in game time, far from zero, so "N ticks ago" never goes negative. */
    private static final long T0 = 1_000_000L;

    private GuideGameTests() {}

    private static Player player(GameTestHelper helper) {
        Player player = TestPlayers.mock(helper);
        Vec3 spot = helper.absoluteVec(new Vec3(2.5, 1, 2.5));
        player.moveTo(spot.x, spot.y, spot.z);
        helper.getLevel().addFreshEntity(player);
        return player;
    }

    private static void wear(Player player, int tokens) {
        ItemStack stack = new ItemStack(CogwitMod.COGWIT.get());
        CogwitTalismanItem.setBrain(stack, BrainState.EMPTY.withTokens(tokens));
        CuriosApi.getCuriosInventory(player)
                .orElseThrow(() -> new IllegalStateException("player has no curios inventory"))
                .setEquippedCurio("charm", 0, stack);
    }

    private static void quiet(Player player, long lastHint, int lastCompleted) {
        GuideContent.setState(player, new GuideState(true, lastHint, lastCompleted));
    }

    private static Optional<Milestone> next(Player player) {
        return GuideProgress.next(GuideFactsReader.read(player));
    }

    @GameTest(template = "empty")
    public static void aNetheriteIngotIsEnoughForTheCore(GameTestHelper helper) {
        Player player = player(helper);
        wear(player, 1000);
        player.getInventory().add(new ItemStack(Items.NETHERITE_CHESTPLATE));
        player.getInventory().add(new ItemStack(Items.NETHERITE_INGOT));
        helper.assertTrue(next(player).equals(Optional.of(Milestone.ECHO_SHARDS)),
                "an ingot replaces the rare heavy core, got " + next(player));
        var recipes = helper.getLevel().getRecipeManager();
        var access = helper.getLevel().registryAccess();
        helper.assertTrue(recipes.byKey(dev.cogwit.CogwitMod.id("singularity_core")).orElseThrow().value()
                .getResultItem(access).getCount() == 1, "ingot recipe makes one core");
        helper.assertTrue(recipes.byKey(dev.cogwit.CogwitMod.id("singularity_core_from_heavy_core")).orElseThrow().value()
                .getResultItem(access).getCount() == 2, "a heavy core makes two");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void playerHoldingHeavyCoreGetsEchoShardsNext(GameTestHelper helper) {
        Player player = player(helper);
        wear(player, 1000);
        player.getInventory().add(new ItemStack(Items.NETHERITE_CHESTPLATE));
        helper.assertTrue(next(player).equals(Optional.of(Milestone.HEAVY_CORE)),
                "expected heavy_core next, got " + next(player));
        player.getInventory().add(new ItemStack(Items.HEAVY_CORE));
        helper.assertTrue(next(player).equals(Optional.of(Milestone.ECHO_SHARDS)),
                "expected echo_shards next, got " + next(player));
        player.getInventory().add(new ItemStack(Items.ECHO_SHARD, 4));
        helper.assertTrue(next(player).equals(Optional.of(Milestone.SINGULARITY_CORE)),
                "expected singularity_core next, got " + next(player));
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void factsReaderSeesWornCogwitTokensAndHorizonPieces(GameTestHelper helper) {
        Player player = player(helper);
        wear(player, 1234);
        player.getInventory().add(new ItemStack(HorizonContent.BOOTS.get()));
        GuideFacts facts = GuideFactsReader.read(player);
        helper.assertTrue(facts.wornCogwit() && facts.hasCogwit() && facts.wornTokens() == 1234, "worn Cogwit: " + facts);
        helper.assertTrue(facts.horizonPiece() && !facts.fullSet(), "one Horizon piece: " + facts);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void completingAMilestoneSpeaksImmediately(GameTestHelper helper) {
        Player player = player(helper);
        long now = T0;
        quiet(player, now - GuideTiming.MIN_GAP_TICKS - 1, -1);

        player.getInventory().add(new ItemStack(CogwitMod.COGWIT.get()));
        GuideService.tick(player, now);

        GuideState state = GuideContent.state(player);
        helper.assertTrue(state.lastCompleted() == Milestone.GET_COGWIT.ordinal(),
                "get_cogwit should be congratulated, state is " + state);
        helper.assertTrue(state.lastHintTick() == now, "hint tick should be now, state is " + state);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void guideNeverSpeaksTwiceWithinAMinute(GameTestHelper helper) {
        Player player = player(helper);
        long now = T0;
        wear(player, 1000);
        quiet(player, now - GuideTiming.MIN_GAP_TICKS - 1, -1);
        GuideService.tick(player, now);
        helper.assertTrue(GuideContent.state(player).lastCompleted() == Milestone.FEED_COGWIT.ordinal(),
                "worn and fed Cogwit completes three steps at once, state is " + GuideContent.state(player));

        player.getInventory().add(new ItemStack(Items.NETHERITE_BOOTS));
        GuideService.tick(player, now + 100);
        helper.assertTrue(GuideContent.state(player).lastCompleted() == Milestone.FEED_COGWIT.ordinal(),
                "too soon to congratulate again");
        GuideService.tick(player, now + GuideTiming.MIN_GAP_TICKS);
        helper.assertTrue(GuideContent.state(player).lastCompleted() == Milestone.NETHERITE.ordinal(),
                "congratulation should come once the minute is up, state " + GuideContent.state(player));
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void hintsRepeatOnTheChatterInterval(GameTestHelper helper) {
        Player player = player(helper);
        long now = T0;
        long gap = GuideService.remindTicks(GuideService.chatter(player));
        quiet(player, now - gap + 1, -1);
        GuideService.tick(player, now);
        helper.assertTrue(GuideContent.state(player).lastHintTick() == now - gap + 1, "no reminder before the gap");
        GuideService.tick(player, now + 1);
        helper.assertTrue(GuideContent.state(player).lastHintTick() == now + 1, "a reminder once the gap is over");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void guideOffStopsHints(GameTestHelper helper) {
        Player player = player(helper);
        long now = T0;
        quiet(player, now - 100_000, -1);
        GuideService.setEnabled(player, false);
        GuideState off = GuideContent.state(player);
        helper.assertTrue(!off.enabled(), "guide should be off");

        player.getInventory().add(new ItemStack(CogwitMod.COGWIT.get()));
        GuideService.tick(player, now);
        GuideService.tick(player, now + 50_000);
        GuideState after = GuideContent.state(player);
        helper.assertTrue(after.lastHintTick() == off.lastHintTick() && after.lastCompleted() == off.lastCompleted(),
                "no hint or congratulation while off");
        helper.assertTrue(GuideProgress.isDone(Milestone.GET_COGWIT, after.reached()), "progress is still noted");

        GuideService.setEnabled(player, true);
        GuideService.tick(player, now + 50_000);
        helper.assertTrue(GuideContent.state(player).lastHintTick() == now + 50_000, "hints resume after /cogwit guide on");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void showNowIgnoresTimersButCountsAsAMessage(GameTestHelper helper) {
        Player player = player(helper);
        long now = player.level().getGameTime();
        quiet(player, now, 3);
        GuideService.showNow(player);
        helper.assertTrue(GuideContent.state(player).lastCompleted() == 3, "showNow does not congratulate");
        helper.assertTrue(GuideContent.state(player).lastHintTick() == now, "showNow records the message");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void guideCommandsMergeIntoTheCogwitLiteral(GameTestHelper helper) {
        CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
        GuideCommands.register(dispatcher);
        CogwitCommands.register(dispatcher);
        CommandSourceStack source = helper.getLevel().getServer().createCommandSourceStack();
        for (String command : List.of("cogwit", "cogwit think", "cogwit guide", "cogwit guide on", "cogwit guide off")) {
            ParseResults<CommandSourceStack> parsed = dispatcher.parse(command, source);
            helper.assertTrue(!parsed.getReader().canRead() && parsed.getExceptions().isEmpty()
                            && parsed.getContext().getCommand() != null,
                    "'/" + command + "' should parse to a runnable command");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void teammateWithEchoShardsIsMentioned(GameTestHelper helper) {
        Player player = player(helper);
        Player friend = player(helper);
        helper.assertTrue(GuideCoop.find(player, Milestone.ECHO_SHARDS, List.of(player, friend)).isEmpty(),
                "nobody has shards yet");
        friend.getInventory().add(new ItemStack(Items.ECHO_SHARD, 2));
        Optional<GuideCoop.Offer> offer = GuideCoop.find(player, Milestone.ECHO_SHARDS, List.of(player, friend));
        helper.assertTrue(offer.isPresent() && offer.get().count() == 2, "friend's 2 shards should be offered");
        helper.assertTrue(GuideCoop.find(friend, Milestone.ECHO_SHARDS, List.of(player, friend)).isEmpty(),
                "you are not your own teammate");
        helper.assertTrue(GuideCoop.find(player, Milestone.WEAR_COGWIT, List.of(friend)).isEmpty(),
                "wearing Cogwit is not something to borrow");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void netheriteHintSendsOverworldPlayersToTheNether(GameTestHelper helper) {
        Player player = player(helper);
        GuideService.Hint hint = GuideService.hint(player, Milestone.NETHERITE);
        TranslatableContents next = (TranslatableContents) hint.lines().get(0).getContents();
        Component body = (Component) next.getArgs()[1];
        helper.assertTrue(((TranslatableContents) body.getContents()).getKey().equals("cogwit.guide.hint.netherite.travel"),
                "overworld player should be told to go to the Nether");
        helper.assertTrue(hint.target().isEmpty(), "no structure search outside the Nether");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 600)
    public static void locatorCachesEvenWhenNothingIsFound(GameTestHelper helper) {
        Player player = player(helper);
        helper.assertTrue(!GuideLocator.isCached(player.getUUID()), "nothing cached yet");
        Optional<net.minecraft.core.BlockPos> first = GuideLocator.find(player, Milestone.ECHO_SHARDS);
        helper.assertTrue(GuideLocator.isCached(player.getUUID()), "answer should be cached for ten minutes");
        helper.assertTrue(first.equals(GuideLocator.find(player, Milestone.ECHO_SHARDS)), "cached answer should match");
        helper.assertTrue(GuideLocator.find(player, Milestone.WEAR_COGWIT).isEmpty(), "no structure for this step");
        GuideLocator.forget(player.getUUID());
        helper.assertTrue(!GuideLocator.isCached(player.getUUID()), "forgotten on logout");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 600)
    public static void structureStepsAddASearchLineAndSurviveEmptyWorlds(GameTestHelper helper) {
        Player player = player(helper);
        GuideService.Hint hint = GuideService.hint(player, Milestone.ECHO_SHARDS);
        helper.assertTrue(hint.lines().size() >= 2, "hint plus a locate line, got " + hint.lines().size());
        helper.assertTrue(hint.label().equals("cogwit.guide.target.ancient_city"), "label is " + hint.label());
        wear(player, 1000);
        player.getInventory().add(new ItemStack(Items.NETHERITE_HELMET));
        player.getInventory().add(new ItemStack(Items.HEAVY_CORE));
        GuideService.showNow(player);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void everyGuideStructureExistsInTheRegistry(GameTestHelper helper) {
        for (Milestone m : Milestone.values()) {
            m.target().ifPresent(target -> helper.assertTrue(GuideLocator.structure(helper.getLevel(), target).isPresent(),
                    "unknown structure " + target.structure()));
        }
        helper.succeed();
    }
}
