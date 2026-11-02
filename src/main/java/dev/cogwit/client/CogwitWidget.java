package dev.cogwit.client;

import dev.cogwit.ClientConfig;
import dev.cogwit.Speech;
import dev.cogwit.brain.HudView;
import dev.cogwit.item.CogwitTalismanItem;
import dev.cogwit.speech.SpeechClientState;
import dev.cogwit.speech.SpeechTiming;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

/** Cogwit's face and state in the top left corner, with his words in speech bubbles underneath instead of the chat. */
public final class CogwitWidget {
    private static final int X = 4, Y = 4, FACE = 28, BUBBLE_WIDTH = 210, PAD = 4, GAP = 3;
    private static final int PANEL = 0x141416, ORANGE = 0xC8803F, TEXT = 0xE8E8EA, HINT = 0xF0B49A, ACTION = 0x8CE0FF,
            ASK = 0xFFD27A, DIM = 0x8A8A8F;
    /** Lines already chirped about and mirrored to chat. */
    private static final Set<SpeechClientState.Line> SEEN = Collections.newSetFromMap(new IdentityHashMap<>());

    private CogwitWidget() {}

    public static void render(GuiGraphics g) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui || mc.player == null || !ClientConfig.WIDGET_ENABLED.get()) {
            return;
        }
        List<SpeechClientState.Line> lines = SpeechClientState.lines();
        announceNew(mc, lines);
        ItemStack cogwit = CogwitTalismanItem.worn(mc.player).orElse(null);
        int y = Y;
        if (cogwit != null) {
            face(g, mc.font, cogwit);
            y += FACE + GAP + 2;
        } else if (lines.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        int limit = bottomLimit(g, cogwit);
        boolean first = true;
        for (SpeechClientState.Line line : lines) {
            int bottom = bubble(g, mc.font, line, now, y, first && cogwit != null, limit);
            if (bottom < 0) {
                break; // no room left above the big panel; older lines wait off screen
            }
            y = bottom + GAP;
            first = false;
        }
    }

    private static void face(GuiGraphics g, Font font, ItemStack cogwit) {
        Minecraft mc = Minecraft.getInstance();
        long tick = mc.level != null ? mc.level.getGameTime() : 0;
        HudView view = HudView.of(CogwitTalismanItem.brain(cogwit), tick);
        CogwitHud.Status status = CogwitHud.status(cogwit, view, tick);
        Component name = Component.translatable("cogwit.name").withStyle(ChatFormatting.BOLD);
        int textWidth = Math.max(font.width(name), font.width(status.text()));
        int right = X + FACE + PAD + textWidth + PAD + 2;
        g.fill(X, Y, right, Y + FACE, argb(0.78f, PANEL));
        g.fill(X, Y, right, Y + 1, status.colour() | 0xFF000000);
        g.pose().pushPose();
        g.pose().translate(X + 2, Y + 2, 0);
        g.pose().scale(1.5f, 1.5f, 1f);
        g.renderItem(cogwit, 0, 0);
        g.pose().popPose();
        g.drawString(font, name, X + FACE + PAD, Y + 5, ORANGE | 0xFF000000, true);
        g.drawString(font, status.text(), X + FACE + PAD, Y + 16, status.colour() | 0xFF000000, true);
    }

    /** Bubbles stop above Cogwit's big panel when it sits on the same side of the screen. */
    private static int bottomLimit(GuiGraphics g, ItemStack cogwit) {
        if (cogwit != null && ClientConfig.HUD_ENABLED.get() && ClientConfig.HUD_SIDE.get() == ClientConfig.Side.LEFT) {
            return Math.max(4, (g.guiHeight() - CogwitHud.height()) / 2) - GAP;
        }
        return g.guiHeight() - 60;
    }

    /** Draws one bubble and returns its bottom edge, or -1 when not even one row fits above {@code limit}. */
    private static int bubble(GuiGraphics g, Font font, SpeechClientState.Line line, long now, int y, boolean tail,
            int limit) {
        long age = now - line.startMillis();
        float alpha = SpeechTiming.alpha(age, line.lifeMillis());
        if (alpha <= 0.02f) {
            return y - GAP;
        }
        String full = line.text().getString();
        boolean instant = line.kind() != SpeechClientState.Kind.SAY;
        String shown = full.substring(0, SpeechTiming.visibleChars(age, full.length(), instant));
        if (line.kind() == SpeechClientState.Kind.ACTION) {
            shown = "\u2713 " + shown;
        }
        List<FormattedCharSequence> rows = new java.util.ArrayList<>(
                font.split(Component.literal(shown), BUBBLE_WIDTH - 2 * PAD - 3));
        if (line.kind() == SpeechClientState.Kind.ASK) {
            long left = Math.max(0, (line.lifeMillis() - age) / 1000);
            rows.addAll(font.split(Component.translatable("cogwit.agent.ask_keys", left)
                    .withColor(DIM), BUBBLE_WIDTH - 2 * PAD - 3));
        }
        int fits = (limit - y - 2 * PAD + 2) / 10;
        if (fits < 1) {
            return -1;
        }
        if (rows.size() > fits) {
            // a long line scrolls: show its newest rows while it is being typed
            rows = rows.subList(rows.size() - fits, rows.size());
        }
        int width = 0;
        for (FormattedCharSequence row : rows) {
            width = Math.max(width, font.width(row));
        }
        int w = width + 2 * PAD + 3, h = rows.size() * 10 + 2 * PAD - 2;
        if (tail) {
            // a little notch pointing up at Cogwit's face
            for (int i = 0; i < 4; i++) {
                g.fill(X + 10 - i, y - 4 + i, X + 11 + i, y - 3 + i, argb(0.82f * alpha, PANEL));
            }
        }
        g.fill(X, y, X + w, y + h, argb(0.82f * alpha, PANEL));
        int accent = switch (line.kind()) {
            case HINT -> HINT;
            case ACTION -> ACTION;
            case ASK -> ASK;
            default -> ORANGE;
        };
        g.fill(X, y, X + 2, y + h, argb(alpha, accent));
        int ty = y + PAD;
        int colour = argb(Math.max(0.1f, alpha), line.kind() == SpeechClientState.Kind.SAY ? TEXT : accent);
        for (FormattedCharSequence row : rows) {
            g.drawString(font, row, X + PAD + 3, ty, colour, false);
            ty += 10;
        }
        return y + h;
    }

    /** A soft chirp for each new line, and a copy in chat if the player wants one. */
    private static void announceNew(Minecraft mc, List<SpeechClientState.Line> lines) {
        SEEN.retainAll(lines);
        for (SpeechClientState.Line line : lines) {
            if (SEEN.add(line) && (line.kind() == SpeechClientState.Kind.SAY
                    || line.kind() == SpeechClientState.Kind.ASK)) {
                if (mc.player != null) {
                    mc.player.playSound(SoundEvents.AMETHYST_BLOCK_CHIME, 0.25f, 1.8f);
                }
                if (ClientConfig.SPEECH_TO_CHAT.get()) {
                    mc.gui.getChat().addMessage(Speech.chatLine(line.text()));
                }
            }
        }
    }

    private static int argb(float alpha, int rgb) {
        int a = Math.max(0, Math.min(255, Math.round(alpha * 255)));
        return a << 24 | rgb & 0xFFFFFF;
    }
}
