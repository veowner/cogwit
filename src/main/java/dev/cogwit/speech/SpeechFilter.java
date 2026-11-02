package dev.cogwit.speech;

import dev.cogwit.ClientConfig;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;

import java.util.List;

/** Which of Cogwit's spoken lines survive the quiet setting: danger, achievements, answers and what you asked for. */
public final class SpeechFilter {
    /** Translation key prefixes Cogwit always says, whatever the chatter setting. */
    static final List<String> IMPORTANT = List.of(
            "cogwit.say.rescued", "cogwit.say.hungry", "cogwit.say.limit", "cogwit.say.woke_up", "cogwit.say.apple_woke",
            "cogwit.say.still_sleeping", "cogwit.say.status", "cogwit.say.grew", "cogwit.say.hello", "cogwit.perk.",
            "cogwit.trophy.gained", "cogwit.ultra.", "cogwit.crew.brother_fell", "cogwit.crew.knocked_out",
            "cogwit.death.", "cogwit.agent.vaulted", "cogwit.guide.done", "cogwit.guide.next", "cogwit.guide.locate",
            "cogwit.guide.hunt.", "cogwit.guide.all_done", "cogwit.guide.enabled", "cogwit.guide.disabled",
            "cogwit.mind.", "cogwit.answer.");

    private SpeechFilter() {}

    public static boolean shows(Component text, ClientConfig.Chatter chatter) {
        if (chatter != ClientConfig.Chatter.QUIET) {
            return true;
        }
        if (!(text.getContents() instanceof TranslatableContents translatable)) {
            return true;
        }
        return isImportant(translatable.getKey());
    }

    /** Whether a line with this translation key is one Cogwit says even when set to quiet. */
    public static boolean isImportant(String key) {
        return IMPORTANT.stream().anyMatch(key::startsWith);
    }
}
