package dev.cogwit.client;

import dev.cogwit.brain.Brain;
import dev.cogwit.horizon.Horizon;
import dev.cogwit.horizon.HorizonClientState;
import dev.cogwit.network.HorizonSyncPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/** Event Horizon panel: mass meter and ability cooldowns; an ability whose part is missing reads "no part". */
public final class HorizonHud {
    public static final int WIDTH = 132;
    public static final int HEIGHT = 74;

    private static final int PAD = 4;
    private static final int VIOLET = 0xFFA970FF;
    private static final int GOLD = 0xFFE9A23B;
    private static final int TEXT_DIM = 0xFF8A8A8F;
    private static final int GREEN = 0xFF7CE07C;
    private static final int YELLOW = 0xFFFFD27A;

    private HorizonHud() {}

    /** Out of combat (no mass, nothing recharging) the armor panel hides unless the detailed panel is on. */
    public static boolean worthShowing() {
        HorizonSyncPayload s = HorizonClientState.latest();
        return dev.cogwit.ClientConfig.detailed() || s.mass() > 0 || s.blinkLeft() > 0 || s.wellLeft() > 0;
    }

    public static void render(GuiGraphics g, Font font, int x, int y) {
        HorizonSyncPayload s = HorizonClientState.latest();
        int right = x + WIDTH;
        g.fill(x, y, right, y + HEIGHT, 0xC0141416);
        g.fill(x, y, right, y + 1, VIOLET);

        g.drawString(font, Component.translatable("cogwit.hud.horizon").withStyle(ChatFormatting.BOLD), x + PAD, y + 5,
                VIOLET, true);

        boolean full = s.mass() >= Horizon.MASS_MAX;
        int my = y + 18;
        g.drawString(font, Component.translatable("cogwit.hud.mass"), x + PAD, my, TEXT_DIM, false);
        String amount = Math.round(s.mass()) + "/" + Math.round(Horizon.MASS_MAX);
        g.drawString(font, amount, right - PAD - font.width(amount), my, full ? GOLD : 0xFFE8E8EA, false);
        int barWidth = WIDTH - 2 * PAD;
        g.fill(x + PAD, my + 10, x + PAD + barWidth, my + 14, 0xFF2C2C31);
        g.fill(x + PAD, my + 10, x + PAD + Math.round(barWidth * s.mass() / Horizon.MASS_MAX), my + 14,
                full ? GOLD : VIOLET);

        int ry = y + 37;
        row(g, font, CogwitClient.BLINK_KEY, Component.translatable("cogwit.hud.blink"),
                HorizonClientState.ticksLeft(s.blinkLeft()), x, ry, !s.has(Horizon.Feature.THRUSTERS));
        if (!s.has(Horizon.Feature.CORE)) {
            row(g, font, CogwitClient.GRAVITY_KEY, Component.translatable("cogwit.hud.well"), 0, x, ry + 11, true);
        } else if (full) {
            Component collapse = Component.translatable("cogwit.hud.collapse_ready");
            g.drawString(font, CogwitClient.GRAVITY_KEY.isUnbound() ? collapse
                            : keyLabel(CogwitClient.GRAVITY_KEY).copy().append(" ").append(collapse), x + PAD, ry + 11,
                    GOLD, false);
        } else {
            row(g, font, CogwitClient.GRAVITY_KEY, Component.translatable("cogwit.hud.well"),
                    HorizonClientState.ticksLeft(s.wellLeft()), x, ry + 11, false);
        }
        int voidLeft = HorizonClientState.ticksLeft(s.voidRescueLeft());
        g.drawString(font, Component.translatable("cogwit.hud.void"), x + PAD, ry + 22, TEXT_DIM, false);
        boolean sealed = s.has(Horizon.Feature.SEALED);
        Component state = !sealed ? Component.translatable("cogwit.hud.missing")
                : voidLeft == 0 ? Component.translatable("cogwit.hud.ready") : Component.literal(Brain.clock(voidLeft));
        g.drawString(font, state, right - PAD - font.width(state), ry + 22,
                !sealed ? TEXT_DIM : voidLeft == 0 ? GREEN : YELLOW, false);
    }

    private static void row(GuiGraphics g, Font font, KeyMapping key, Component name, int ticksLeft, int x, int y,
            boolean missing) {
        g.drawString(font, key.isUnbound() ? name : keyLabel(key).copy().append(" ").append(name), x + PAD, y, TEXT_DIM,
                false);
        Component state = missing ? Component.translatable("cogwit.hud.missing")
                : ticksLeft == 0 ? Component.translatable("cogwit.hud.ready") : Component.literal(Brain.clock(ticksLeft));
        g.drawString(font, state, x + WIDTH - PAD - font.width(state), y,
                missing ? TEXT_DIM : ticksLeft == 0 ? GREEN : YELLOW, false);
    }

    /** "[Z]" using the key's untranslated name so a Russian keyboard layout doesn't turn Z into Я. */
    private static Component keyLabel(KeyMapping key) {
        if (key.isUnbound()) {
            return Component.empty();
        }
        String name = key.getKey().getName();
        String shortName = name.substring(name.lastIndexOf('.') + 1).toUpperCase();
        return Component.literal("[" + shortName + "]");
    }
}
