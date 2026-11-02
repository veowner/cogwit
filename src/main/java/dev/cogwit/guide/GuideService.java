package dev.cogwit.guide;

import dev.cogwit.ClientConfig;
import dev.cogwit.Speech;
import dev.cogwit.guide.net.GuideProgressPayload;
import dev.cogwit.guide.net.GuideTargetPayload;
import dev.cogwit.item.CogwitTalismanItem;
import dev.cogwit.trophy.Trophy;
import dev.cogwit.trophy.TrophyHunt;
import dev.cogwit.trophy.TrophyService;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TextColor;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Server side of the guide: decides when to speak, what to say and what the client should show. */
public final class GuideService {
    /** What Cogwit says for one milestone, and where the compass should point (if anywhere). */
    public record Hint(List<Component> lines, Optional<BlockPos> target, String label) {}

    private record Active(Milestone milestone, String dimension, BlockPos pos, String label) {}

    /** Side tips Cogwit mixes into reminders, so a quiet stretch still teaches something. */
    public static final int TIP_COUNT = 8;

    private static final Map<UUID, Integer> SENT_MASK = new HashMap<>();
    private static final Map<UUID, Active> ACTIVE = new HashMap<>();
    private static final Map<UUID, ClientConfig.Chatter> CHATTER = new HashMap<>();

    /** Reminder gaps per chatter setting: quiet never nags, normal every 10 minutes, chatty every 5 with tips. */
    public static long remindTicks(ClientConfig.Chatter chatter) {
        return switch (chatter) {
            case QUIET -> Long.MAX_VALUE;
            case NORMAL -> GuideTiming.REMIND_TICKS * 2;
            case CHATTY -> GuideTiming.REMIND_TICKS;
        };
    }

    public static void setChatter(Player player, ClientConfig.Chatter chatter) {
        CHATTER.put(player.getUUID(), chatter);
    }

    public static ClientConfig.Chatter chatter(Player player) {
        return CHATTER.getOrDefault(player.getUUID(), ClientConfig.Chatter.NORMAL);
    }

    private GuideService() {}

    /** The player's progress: what they have now, plus what they did before, remembered on the player. */
    public static int progress(Player player) {
        GuideState state = GuideContent.state(player);
        int mask = GuideProgress.progress(GuideFactsReader.read(player), state.reached());
        int keep = GuideProgress.toKeep(mask);
        if (keep != state.reached()) {
            GuideContent.setState(player, state.withReached(keep));
        }
        return mask;
    }

    public static void tick(Player player) {
        tick(player, player.level().getGameTime());
    }

    /** One look at the player: sync progress to the client and speak up if it is time. */
    public static void tick(Player player, long now) {
        int mask = progress(player);
        syncMask(player, mask);
        Optional<Milestone> next = GuideProgress.next(mask);
        dropStaleTarget(player, next);

        GuideState state = GuideContent.state(player);
        int completed = GuideProgress.firstPending(mask) - 1;
        ClientConfig.Chatter chatter = chatter(player);
        switch (GuideTiming.decide(state, now, completed, next.isEmpty(), remindTicks(chatter))) {
            case CONGRATULATE -> {
                GuideContent.setState(player, state.congratulated(now, completed));
                announce(player, Optional.of(Milestone.values()[completed]), next);
            }
            case REMIND -> {
                GuideContent.setState(player, state.spoke(now));
                announce(player, Optional.empty(), next);
                if (chatter == ClientConfig.Chatter.CHATTY) {
                    tip(player, next, now).ifPresent(line -> say(player, line));
                }
            }
            case NONE -> { }
        }
    }

    /** The /cogwit guide command: repeat the current hint right now, whatever the timers say. */
    public static void showNow(Player player) {
        int mask = progress(player);
        syncMask(player, mask);
        GuideContent.setState(player, GuideContent.state(player).spoke(player.level().getGameTime()));
        announce(player, Optional.empty(), GuideProgress.next(mask));
    }

    public static void setEnabled(Player player, boolean enabled) {
        GuideContent.setState(player, GuideContent.state(player).withEnabled(enabled));
        if (!enabled) {
            clearTarget(player);
        }
        say(player, Component.translatable(enabled ? "cogwit.guide.enabled" : "cogwit.guide.disabled"));
    }

    /** Text lines for one milestone; may run a structure search the first time it is asked for. */
    public static Hint hint(Player player, Milestone milestone) {
        String dimension = player.level().dimension().location().toString();
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable("cogwit.guide.next", Component.translatable(milestone.titleKey()),
                Component.translatable(milestone.hintKey(dimension))));

        Optional<Milestone.Target> here = milestone.target().filter(t -> t.dimension().equals(dimension));
        if (milestone.isHunt()) {
            Optional<Trophy> prey = huntTarget(player, milestone);
            prey.ifPresent(t -> lines.add(preyLine(t)));
            here = prey.flatMap(GuideService::lair);
            prey.filter(t -> !TrophyHunt.dimensionOf(t).equals(dimension)).ifPresent(t -> lines.add(Component
                    .translatable("cogwit.guide.hunt.travel", Component.translatable(dimensionKey(TrophyHunt.dimensionOf(t))))));
            here = here.filter(t -> t.dimension().equals(dimension));
        }
        String label = here.map(Milestone.Target::labelKey).orElse("");
        Optional<BlockPos> found = Optional.empty();
        if (here.isPresent()) {
            found = GuideLocator.find(player, here.get());
            lines.add(found.map(pos -> locateLine(player, label, pos))
                    .orElseGet(() -> Component.translatable("cogwit.guide.locate_none", Component.translatable(label))));
        }
        GuideCoop.find(player, milestone).ifPresent(offer -> lines.add(
                Component.translatable("cogwit.guide.coop", offer.player(), offer.itemName(), offer.count())));
        return new Hint(lines, found, label);
    }

    /** The easiest boss whose trophy this player still lacks and whose mod is in the pack. */
    public static Optional<Trophy> huntTarget(Player player) {
        return huntTarget(player, Milestone.ALL_TROPHIES);
    }

    /** The same, among the bosses a hunt step is about. */
    public static Optional<Trophy> huntTarget(Player player, Milestone step) {
        return TrophyHunt.next(TrophyService.owned(player), t -> TrophyHunt.loaded(t) && step.hunts(t.mod()),
                dimensionOf(player));
    }

    /** The boss's structure as a compass target, or empty for summoned and wandering bosses. */
    public static Optional<Milestone.Target> lair(Trophy trophy) {
        return Optional.ofNullable(trophy.structure)
                .map(s -> new Milestone.Target(s, TrophyHunt.dimensionOf(trophy), false));
    }

    private static Component preyLine(Trophy trophy) {
        String stars = "★".repeat(TrophyHunt.stars(trophy)) + "☆".repeat(5 - TrophyHunt.stars(trophy));
        return Component.translatable("cogwit.guide.hunt.target",
                Component.translatable("cogwit.trophy." + trophy.key() + ".name"), stars,
                Component.translatable("cogwit.trophy." + trophy.key() + ".passive"),
                Component.translatable("cogwit.trophy." + trophy.key() + ".hint"));
    }

    private static String dimensionKey(String dimension) {
        return "cogwit.guide.dim." + dimension.substring(dimension.indexOf(':') + 1);
    }

    /**
     * A tip for a quiet reminder, picked by the clock so it rotates. Tip 0 points at the next boss when the
     * player has not reached the hunt steps yet; the rest are general advice on growing Cogwit.
     */
    static Optional<Component> tip(Player player, Optional<Milestone> next, long now) {
        int index = (int) Math.floorMod(now / GuideTiming.REMIND_TICKS, TIP_COUNT);
        if (index == 0) {
            if (next.isPresent() && next.get().ordinal() < Milestone.FIRST_TROPHY.ordinal()) {
                return huntTarget(player).map(t -> Component.translatable("cogwit.guide.tip.boss",
                        Component.translatable("cogwit.trophy." + t.key() + ".name"),
                        Component.translatable("cogwit.trophy." + t.key() + ".hint")));
            }
            return Optional.empty();
        }
        return Optional.of(Component.translatable("cogwit.guide.tip." + index));
    }

    /** Sends the login snapshot: progress now, no compass target. */
    public static void sync(ServerPlayer player) {
        SENT_MASK.remove(player.getUUID());
        ACTIVE.remove(player.getUUID());
        syncMask(player, progress(player));
        send(player, GuideTargetPayload.none());
    }

    public static void forget(UUID player) {
        CHATTER.remove(player);
        SENT_MASK.remove(player);
        ACTIVE.remove(player);
        GuideLocator.forget(player);
    }

    public static void clear() {
        SENT_MASK.clear();
        ACTIVE.clear();
        GuideLocator.clear();
    }

    private static void announce(Player player, Optional<Milestone> done, Optional<Milestone> next) {
        List<Component> lines = new ArrayList<>();
        done.ifPresent(m -> lines.add(Component.translatable("cogwit.guide.done", Component.translatable(m.titleKey()))));
        if (next.isPresent()) {
            Hint hint = hint(player, next.get());
            lines.addAll(hint.lines());
            if (hint.target().isPresent()) {
                setTarget(player, next.get(), hint.target().get(), hint.label());
            } else {
                clearTarget(player);
            }
        } else {
            lines.add(Component.translatable("cogwit.guide.all_done"));
            clearTarget(player);
        }
        lines.forEach(line -> say(player, line));
    }

    private static Component locateLine(Player player, String label, BlockPos pos) {
        double dx = pos.getX() + 0.5 - player.getX();
        double dz = pos.getZ() + 0.5 - player.getZ();
        return Component.translatable("cogwit.guide.locate", Component.translatable(label),
                Component.translatable(GuideDirection.compass(dx, dz).key()),
                GuideDirection.roughDistance(Math.hypot(dx, dz)));
    }

    /** Cogwit talks if worn; otherwise the hint arrives as a plain tagged system message. */
    private static void say(Player player, Component message) {
        if (CogwitTalismanItem.worn(player).isPresent()) {
            Speech.say(player, message);
            return;
        }
        MutableComponent prefix = Component.translatable("cogwit.guide.prefix")
                .withStyle(style -> style.withColor(TextColor.fromRgb(CogwitTalismanItem.COGWIT_ORANGE)));
        player.sendSystemMessage(prefix.append(" ").append(message));
    }

    private static void syncMask(Player player, int mask) {
        Integer previous = SENT_MASK.put(player.getUUID(), mask);
        if (previous == null || previous != mask) {
            send(player, new GuideProgressPayload(mask));
        }
    }

    private static void setTarget(Player player, Milestone milestone, BlockPos pos, String label) {
        ACTIVE.put(player.getUUID(), new Active(milestone, dimensionOf(player), pos, label));
        // the way back to your dropped things comes first; this target waits until that trip is over
        if (!dev.cogwit.mind.DeathCompass.active(player)) {
            send(player, GuideTargetPayload.at(pos.getX(), pos.getZ(), label));
        }
    }

    /** Puts the guide's target back on the compass, if it still has one. */
    public static void resendTarget(Player player) {
        Active active = ACTIVE.get(player.getUUID());
        if (active != null && active.dimension().equals(dimensionOf(player))) {
            send(player, GuideTargetPayload.at(active.pos().getX(), active.pos().getZ(), active.label()));
        }
    }

    private static void clearTarget(Player player) {
        if (ACTIVE.remove(player.getUUID()) != null && !dev.cogwit.mind.DeathCompass.active(player)) {
            send(player, GuideTargetPayload.none());
        }
    }

    private static void dropStaleTarget(Player player, Optional<Milestone> next) {
        Active active = ACTIVE.get(player.getUUID());
        if (active != null && (next.isEmpty() || next.get() != active.milestone()
                || !active.dimension().equals(dimensionOf(player)))) {
            clearTarget(player);
        }
    }

    private static String dimensionOf(Player player) {
        return player.level().dimension().location().toString();
    }

    private static void send(Player player, CustomPacketPayload payload) {
        if (player instanceof ServerPlayer serverPlayer) {
            PacketDistributor.sendToPlayer(serverPlayer, payload);
        }
    }
}
