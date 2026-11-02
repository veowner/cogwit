package dev.cogwit.client;

import dev.cogwit.brain.Growth;
import dev.cogwit.horizon.Horizon;
import dev.cogwit.horizon.HorizonClientState;
import dev.cogwit.item.CogwitTalismanItem;
import dev.cogwit.mind.Mind;
import dev.cogwit.mind.MindService;
import dev.cogwit.network.AbilityCastPayload;
import dev.cogwit.network.CrewPayload;
import dev.cogwit.network.ScanPayload;
import dev.cogwit.network.ThinkPayload;
import dev.cogwit.trophy.Trophy;
import dev.cogwit.trophy.TrophyClientState;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** What the ability wheel and the trophy codex show, read from the client's copies of the server state. */
public final class TrophyUi {
    /** Ids of the wheel's Cogwit actions; trophy and armor actives keep their own keys. */
    public static final String THINK = "cogwit.think", SCAN = "cogwit.scan", MARK = "cogwit.mark",
            CREW = "cogwit.crew", AGENT = "cogwit.agent", VISOR = "cogwit.visor";

    private static final int THINK_COLOUR = 0xC8803F, SCAN_COLOUR = 0xE8935F, MARK_COLOUR = 0xC9643F,
            CREW_COLOUR = 0xEBA67A, AGENT_COLOUR = 0xB5573A;

    /**
     * One slice of the wheel. {@code cooldown} is the share of the recharge still left, 0 when ready.
     * {@code trophy} entries go to the outer ring when the wheel is crowded.
     */
    public record WheelEntry(String id, Component name, int colour, float cooldown, long ticksLeft, boolean trophy) {
        public boolean ready() {
            return ticksLeft <= 0;
        }
    }

    /** One boss in the codex. */
    public record CodexEntry(Trophy trophy, boolean owned, Component name, Component passive, Component active,
                             Component hint, boolean modLoaded, boolean implemented) {}

    private TrophyUi() {}

    /**
     * Every action the player can use right now: Cogwit's, then the armor's, then the trophy actives.
     * Cogwit's entries need the talisman worn and open with his levels; the armor's need the full set.
     */
    public static List<WheelEntry> wheel() {
        Minecraft mc = Minecraft.getInstance();
        List<WheelEntry> out = new ArrayList<>();
        if (mc.player == null || mc.level == null) {
            return out;
        }
        long now = mc.level.getGameTime();
        CogwitTalismanItem.worn(mc.player).ifPresent(talisman -> cogwitEntries(out, talisman, now));
        var horizon = HorizonClientState.latest();
        if (horizon.has(Horizon.Feature.THRUSTERS)) {
            long blinkLeft = HorizonClientState.ticksLeft(horizon.blinkLeft());
            out.add(new WheelEntry("blink", Component.translatable("cogwit.wheel.blink"), 0xC77DFF,
                    share(blinkLeft, Horizon.BLINK_COOLDOWN_TICKS), blinkLeft, false));
        } else if (dev.cogwit.exoframe.ExoframeContent.wearsBoth(mc.player)) {
            out.add(plain("blink", "exo_shove", 0xD9824A));
        }
        if (horizon.has(Horizon.Feature.CORE)) {
            long wellLeft = HorizonClientState.ticksLeft(horizon.wellLeft());
            out.add(new WheelEntry("gravity", Component.translatable("cogwit.wheel.gravity"), 0x9B4DDB,
                    share(wellLeft, Horizon.WELL_COOLDOWN_TICKS), wellLeft, false));
        }
        if (mc.player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD).getItem()
                instanceof dev.cogwit.horizon.HorizonArmorItem) {
            out.add(plain(VISOR, "visor", 0x7A6FB3));
        }
        for (Trophy t : TrophyClientState.owned(mc.player.getId())) {
            if (t.hasActive && t.implemented) {
                long left = Math.max(0, TrophyClientState.readyAt(t) - now);
                out.add(new WheelEntry(t.key(), Component.translatable("cogwit.trophy." + t.key() + ".name"),
                        t.colour, share(left, t.cooldownTicks), left, true));
            }
        }
        return out;
    }

    /** Cogwit's own actions, the ones the old hotkeys gave; each opens with the level the server asks for. */
    private static void cogwitEntries(List<WheelEntry> out, ItemStack talisman, long now) {
        float xp = CogwitTalismanItem.brain(talisman).xp();
        out.add(plain(THINK, "think", THINK_COLOUR));
        if (Growth.has(xp, Growth.Perk.HIGHLIGHT)) {
            long scanLeft = Math.max(0, MindService.state(talisman).scanReadyAt() - now);
            out.add(new WheelEntry(SCAN, Component.translatable("cogwit.wheel.scan"), SCAN_COLOUR,
                    share(scanLeft, Mind.SCAN_COOLDOWN_TICKS), scanLeft, false));
        }
        out.add(plain(MARK, "mark", MARK_COLOUR));
        if (Growth.has(xp, Growth.Perk.BROTHERS)) {
            out.add(plain(CREW, "crew", CREW_COLOUR));
        }
        out.add(plain(AGENT, "agent", AGENT_COLOUR));
    }

    private static WheelEntry plain(String id, String key, int colour) {
        return new WheelEntry(id, Component.translatable("cogwit.wheel." + key), colour, 0, 0, false);
    }

    /** Runs a wheel entry: Cogwit's actions are local calls or his payloads, everything else is cast on the server. */
    public static void cast(String id) {
        switch (id) {
            case THINK -> PacketDistributor.sendToServer(new ThinkPayload());
            case SCAN -> PacketDistributor.sendToServer(new ScanPayload());
            case MARK -> MindRenderer.markWhatPlayerLooksAt();
            case CREW -> PacketDistributor.sendToServer(new CrewPayload());
            case VISOR -> PacketDistributor.sendToServer(new dev.cogwit.network.VisorPayload());
            case AGENT -> Minecraft.getInstance().setScreen(new AgentScreen());
            default -> PacketDistributor.sendToServer(new AbilityCastPayload(id));
        }
    }

    /** One entry per boss for the trophy codex, owned or not. */
    public static List<CodexEntry> codex() {
        Minecraft mc = Minecraft.getInstance();
        Set<Trophy> owned = mc.player == null ? Set.of() : TrophyClientState.owned(mc.player.getId());
        List<CodexEntry> out = new ArrayList<>();
        for (Trophy t : Trophy.values()) {
            String k = "cogwit.trophy." + t.key();
            boolean loaded = t.mod().equals("minecraft") || ModList.get().isLoaded(t.mod());
            out.add(new CodexEntry(t, owned.contains(t), Component.translatable(k + ".name"),
                    Component.translatable(k + ".passive"), Component.translatable(k + ".active"),
                    Component.translatable(k + ".hint"), loaded, t.implemented));
        }
        return out;
    }

    private static float share(long left, long total) {
        return total <= 0 ? 0 : Math.max(0, Math.min(1, (float) left / total));
    }
}
