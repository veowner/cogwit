package dev.cogwit.guide;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;

/** /cogwit guide [on|off]; the "cogwit" literal merges with the one in CogwitCommands. */
public final class GuideCommands {
    private GuideCommands() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("cogwit").then(Commands.literal("guide")
                .executes(ctx -> {
                    GuideService.showNow(ctx.getSource().getPlayerOrException());
                    return 1;
                })
                .then(Commands.literal("on").executes(ctx -> toggle(ctx, true)))
                .then(Commands.literal("off").executes(ctx -> toggle(ctx, false)))));
    }

    private static int toggle(CommandContext<CommandSourceStack> ctx, boolean enabled) throws CommandSyntaxException {
        GuideService.setEnabled(ctx.getSource().getPlayerOrException(), enabled);
        return 1;
    }
}
