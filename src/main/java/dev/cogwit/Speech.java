package dev.cogwit;

import dev.cogwit.item.CogwitTalismanItem;
import dev.cogwit.network.SpeechPayload;
import dev.cogwit.speech.CogwitVoice;
import dev.cogwit.speech.SpeechClientState;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.PacketDistributor;

/** Cogwit talks to his owner through the speech bubble next to his face (optionally mirrored to chat). */
public final class Speech {
    private Speech() {}

    public static void say(Player player, Component message) {
        send(player, message, false);
        CogwitVoice.beep(player, CogwitVoice.Beep.CHIRP);
    }

    /** Short status line for things that happen often. */
    public static void hint(Player player, Component message) {
        send(player, message, true);
    }

    /**
     * What Cogwit noticed or is thinking, in a few words: one line above the hotbar that replaces the previous one.
     * For warnings that matter right now (a creeper behind, lava behind the block you're digging).
     */
    public static void think(Player player, Component message) {
        if (player instanceof ServerPlayer serverPlayer) {
            PacketDistributor.sendToPlayer(serverPlayer, new SpeechPayload(message, 3));
        } else if (player.level().isClientSide) {
            SpeechClientState.think(message);
        }
    }

    /** An agent action: a short bubble and a line in the agent journal. */
    public static void action(Player player, Component message) {
        CogwitVoice.beep(player, CogwitVoice.Beep.YES);
        if (player instanceof ServerPlayer serverPlayer) {
            PacketDistributor.sendToPlayer(serverPlayer, new SpeechPayload(message, 2));
        } else if (player.level().isClientSide) {
            SpeechClientState.action(message);
        }
    }

    private static void send(Player player, Component message, boolean hint) {
        if (player instanceof ServerPlayer serverPlayer) {
            PacketDistributor.sendToPlayer(serverPlayer, new SpeechPayload(message, hint ? 1 : 0));
        } else if (player.level().isClientSide) {
            SpeechClientState.add(message, hint);
        }
    }

    /** How a line looks when mirrored into chat: "[Cogwit] message" with an orange name. */
    public static Component chatLine(Component message) {
        Component name = Component.translatable("cogwit.name")
                .withStyle(style -> style.withColor(TextColor.fromRgb(CogwitTalismanItem.COGWIT_ORANGE)).withBold(true));
        return Component.translatable("cogwit.say", name, message);
    }
}
