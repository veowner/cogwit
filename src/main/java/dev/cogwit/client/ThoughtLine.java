package dev.cogwit.client;

import dev.cogwit.item.CogwitTalismanItem;
import dev.cogwit.speech.SpeechClientState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.FormattedCharSequence;

/**
 * Cogwit's current thought, one line above the hotbar and the action bar: an orange tick on the left, the text in
 * warm white on a dark strip. It rises into place, holds and fades.
 */
public final class ThoughtLine {
    private static final int ABOVE_BOTTOM = 84, PAD = 4, MAX_WIDTH = 260;
    private static final long IN_MILLIS = 160, OUT_MILLIS = 450;

    private ThoughtLine() {}

    public static void render(GuiGraphics g) {
        Minecraft mc = Minecraft.getInstance();
        SpeechClientState.Thought thought = SpeechClientState.thought();
        if (thought == null || mc.options.hideGui || mc.screen != null) {
            return;
        }
        long age = System.currentTimeMillis() - thought.startMillis();
        float in = Math.min(1f, age / (float) IN_MILLIS);
        float out = Math.min(1f, (thought.lifeMillis() - age) / (float) OUT_MILLIS);
        float alpha = Math.max(0f, Math.min(in, out));
        if (alpha <= 0.02f) {
            return;
        }
        Font font = mc.font;
        FormattedCharSequence text = font.split(thought.text(), MAX_WIDTH).get(0);
        int width = font.width(text) + PAD * 2 + 5;
        int x = (g.guiWidth() - width) / 2;
        int y = g.guiHeight() - ABOVE_BOTTOM + Math.round((1 - in) * 4);
        int a = Math.round(alpha * 255);
        g.fill(x, y, x + width, y + 13, (Math.round(alpha * 0xA0)) << 24 | 0x100C0A);
        g.fill(x, y, x + 2, y + 13, a << 24 | CogwitTalismanItem.COGWIT_ORANGE);
        g.drawString(font, text, x + PAD + 3, y + 3, a << 24 | 0xF2E6DA, false);
    }
}
