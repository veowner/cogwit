package dev.cogwit;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.cogwit.brain.Brain;
import dev.cogwit.brain.BrainState;
import dev.cogwit.brain.CompactResult;
import dev.cogwit.brain.Growth;
import dev.cogwit.brain.Skill;
import dev.cogwit.item.CogwitTalismanItem;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.Optional;

/** /compact and /cogwit work for everyone, cheats or not. */
public final class CogwitCommands {
    private CogwitCommands() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("compact").executes(CogwitCommands::compact));
        dispatcher.register(Commands.literal("cogwit")
                .executes(CogwitCommands::status)
                .then(Commands.literal("think").executes(ctx -> {
                    CogwitActions.think(ctx.getSource().getPlayerOrException());
                    return 1;
                })));
    }

    private static int compact(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        Optional<ItemStack> worn = CogwitTalismanItem.worn(player);
        if (worn.isEmpty()) {
            ctx.getSource().sendFailure(Component.translatable("cogwit.command.not_worn"));
            return 0;
        }
        long now = player.level().getGameTime();
        CompactResult result = Brain.compact(CogwitTalismanItem.brain(worn.get()), now);
        switch (result.status()) {
            case OK -> {
                CogwitTalismanItem.setBrain(worn.get(), result.state());
                Speech.say(player, Component.translatable("cogwit.say.compacted",
                        DamageCategories.name(result.kept()), DamageCategories.name(result.merged())));
            }
            case COOLDOWN -> Speech.say(player, Component.translatable("cogwit.say.compact_cooldown",
                    Brain.clock(CogwitTalismanItem.brain(worn.get()).compactReadyAt() - now)));
            case NOT_ENOUGH -> Speech.say(player, Component.translatable("cogwit.say.compact_not_enough"));
        }
        return result.status() == CompactResult.Status.OK ? 1 : 0;
    }

    private static int status(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        Optional<ItemStack> worn = CogwitTalismanItem.worn(player);
        if (worn.isEmpty()) {
            ctx.getSource().sendFailure(Component.translatable("cogwit.command.not_worn"));
            return 0;
        }
        BrainState s = CogwitTalismanItem.brain(worn.get());
        long now = player.level().getGameTime();
        Speech.say(player, Component.translatable("cogwit.say.status", s.tokens(), Brain.MAX_TOKENS,
                s.skills().size(), Brain.CONTEXT_SIZE));
        int level = Growth.level(s.xp());
        player.sendSystemMessage(level >= Growth.MAX_LEVEL
                ? Component.translatable("cogwit.status.level_max", level, Math.round(s.xp()))
                : Component.translatable("cogwit.status.level", level, (int) s.xp(),
                        Math.round(Growth.threshold(level + 1))));
        if (Brain.isAsleep(s, now)) {
            Speech.say(player, Component.translatable("cogwit.say.still_sleeping", Brain.clock(s.sleepUntil() - now)));
        }
        for (Skill skill : s.skills()) {
            Component line = skill.compacted()
                    ? Component.translatable("cogwit.tooltip.skill_compacted",
                    DamageCategories.name(skill.category()), Math.round(skill.reductionFor(skill.category()) * 100),
                    DamageCategories.name(skill.merged()), Math.round(skill.reductionFor(skill.merged()) * 100))
                    : Component.translatable("cogwit.tooltip.skill", DamageCategories.name(skill.category()),
                    Math.round(skill.reductionFor(skill.category()) * 100),
                    Brain.xpToNextLevel(skill.xp()) == 0
                            ? Component.translatable("cogwit.tooltip.maxed")
                            : Component.translatable("cogwit.tooltip.to_next", Brain.xpToNextLevel(skill.xp())));
            player.sendSystemMessage(line);
        }
        return 1;
    }
}
