package dev.cogwit.horizon;

import dev.cogwit.Speech;
import dev.cogwit.item.CogwitTalismanItem;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

/** Explains new armor features once. If Cogwit is worn he does the talking. */
public final class HorizonTips {
    private HorizonTips() {}

    public static void say(Player player, Component message) {
        if (CogwitTalismanItem.worn(player).isPresent()) {
            Speech.say(player, message);
        } else {
            player.sendSystemMessage(Component.translatable("cogwit.horizon.prefix")
                    .withStyle(ChatFormatting.DARK_PURPLE).append(" ").append(message));
        }
    }

    /** Shows a tip the first time only and returns the state that remembers it. */
    static HorizonState once(Player player, HorizonState state, String tip) {
        if (state.hasSeenTip(tip)) {
            return state;
        }
        say(player, Component.translatable("cogwit.tip." + tip));
        return state.withTip(tip);
    }
}
