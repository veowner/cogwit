package dev.cogwit;

import dev.cogwit.trophy.Trophy;
import dev.cogwit.trophy.WheelPages;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/** Per-player display preferences (config/cogwit-client.toml). */
public final class ClientConfig {
    public enum Side { LEFT, RIGHT }

    /** How much Cogwit talks: only what matters, the usual, or every tip he knows. */
    public enum Chatter { QUIET, NORMAL, CHATTY }

    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue HUD_ENABLED = BUILDER
            .comment("Show Cogwit's panel on screen while playing (toggle in game with J)")
            .define("hudEnabled", true);

    public static final ModConfigSpec.BooleanValue HUD_DETAILED = BUILDER
            .comment("Full panel: every skill, cooldowns and the armor panel even out of combat. Off: tokens and level only")
            .define("hudDetailed", false);

    public static final ModConfigSpec.EnumValue<Side> HUD_SIDE = BUILDER
            .comment("Which edge of the screen the panel sits on")
            .defineEnum("hudSide", Side.LEFT);

    public static final ModConfigSpec.BooleanValue SPEECH_TO_CHAT = BUILDER
            .comment("Also copy what Cogwit says into the chat, so it can be read again later")
            .define("speechToChat", false);

    public static final ModConfigSpec.BooleanValue WIDGET_ENABLED = BUILDER
            .comment("Show Cogwit's face, state and speech bubble in the top left corner")
            .define("widgetEnabled", true);

    public static final ModConfigSpec.EnumValue<Chatter> CHATTER = BUILDER
            .comment("QUIET: only danger, achievements and answers. NORMAL: plus a reminder every 10 minutes."
                    + " CHATTY: a reminder and a tip every 5 minutes")
            .defineEnum("chatter", Chatter.NORMAL);

    public static final ModConfigSpec.BooleanValue PACK_KEYS_APPLIED = BUILDER
            .comment("Set once Cogwit has laid out the modpack's keys so nothing clashes; set to false to do it again")
            .define("packKeysApplied", false);

    public static final ModConfigSpec.ConfigValue<List<? extends String>> WHEEL_FAVOURITES = BUILDER
            .comment("Trophy actives starred in the codex (right click or F on a tile): the ability wheel opens on them")
            .defineListAllowEmpty("wheelFavourites", List.of(), () -> "", o -> o instanceof String);

    public static final ModConfigSpec SPEC = BUILDER.build();

    /** The starred trophies, or none before the config has loaded. */
    public static Set<Trophy> favourites() {
        Set<Trophy> out = EnumSet.noneOf(Trophy.class);
        try {
            for (String key : WHEEL_FAVOURITES.get()) {
                Trophy.byKey(key).ifPresent(out::add);
            }
        } catch (IllegalStateException e) {
            // not loaded yet
        }
        return out;
    }

    /** Stars or unstars a trophy; returns false when it would be one more than the wheel's page holds. */
    public static boolean toggleFavourite(Trophy trophy) {
        Set<Trophy> now = favourites();
        if (!now.remove(trophy)) {
            if (now.size() >= WheelPages.MAX_FAVOURITES) {
                return false;
            }
            now.add(trophy);
        }
        List<String> keys = new ArrayList<>();
        now.forEach(t -> keys.add(t.key()));
        try {
            WHEEL_FAVOURITES.set(keys);
            SPEC.save();
        } catch (IllegalStateException e) {
            return false;
        }
        return true;
    }

    public static boolean packKeysApplied() {
        try {
            return PACK_KEYS_APPLIED.get();
        } catch (IllegalStateException e) {
            return true;
        }
    }

    public static boolean detailed() {
        try {
            return HUD_DETAILED.get();
        } catch (IllegalStateException e) {
            return false;
        }
    }

    /** The chatter setting, or NORMAL before the config has loaded. */
    public static Chatter chatter() {
        try {
            return CHATTER.get();
        } catch (IllegalStateException e) {
            return Chatter.NORMAL;
        }
    }

    private ClientConfig() {}
}
