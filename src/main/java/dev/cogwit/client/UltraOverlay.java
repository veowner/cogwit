package dev.cogwit.client;

import dev.cogwit.ultra.UltraClientState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.Random;

/** The Overdrive flash: violet glitch bands, a chromatic "OVERDRIVE" title, and a softer flash when it ends. */
public final class UltraOverlay {
    private static final long START_MILLIS = 2200, END_MILLIS = 900;
    private static final int VIOLET = 0x9B4DDB, CYAN = 0x5CE1E6, RED = 0xFF5A7A;

    private UltraOverlay() {}

    public static void render(GuiGraphics g) {
        boolean start = UltraClientState.started();
        float t = UltraClientState.flash(start ? START_MILLIS : END_MILLIS);
        if (t < 0) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        int w = g.guiWidth(), h = g.guiHeight();
        float fade = (1 - t) * (1 - t);
        g.fill(0, 0, w, h, argb(start ? 0.35f * fade : 0.18f * fade, start ? VIOLET : CYAN));
        if (!start) {
            return;
        }
        // glitch bands: a new random set every 60 ms, fewer as the flash fades
        Random random = new Random(System.currentTimeMillis() / 60);
        int bands = (int) (10 * fade) + 2;
        for (int i = 0; i < bands; i++) {
            int y = random.nextInt(h), bh = 1 + random.nextInt(8), shift = random.nextInt(41) - 20;
            int colour = random.nextBoolean() ? CYAN : RED;
            g.fill(shift, y, w + shift, y + bh, argb(0.45f * fade, colour));
        }
        if (t > 0.85f) {
            return;
        }
        Font font = mc.font;
        Component title = Component.literal("OVERDRIVE");
        float scale = 4f;
        int jitter = t < 0.4f ? random.nextInt(5) - 2 : 0;
        int tw = font.width(title);
        g.pose().pushPose();
        g.pose().translate(w / 2f - tw * scale / 2f + jitter, h * 0.32f, 0);
        g.pose().scale(scale, scale, 1);
        float alpha = t < 0.7f ? 1f : (0.85f - t) / 0.15f;
        g.drawString(font, title, -1, 0, argb(0.8f * alpha, RED), false);
        g.drawString(font, title, 1, 0, argb(0.8f * alpha, CYAN), false);
        g.drawString(font, title, 0, 0, argb(alpha, 0xFFFFFF), false);
        g.pose().popPose();
        Component sub = Component.translatable("cogwit.ultra.subtitle");
        g.drawString(font, sub, w / 2 - font.width(sub) / 2, (int) (h * 0.32f) + 40, argb(alpha, 0xE7B6FF), true);
    }

    private static int argb(float alpha, int rgb) {
        int a = Math.max(0, Math.min(255, Math.round(alpha * 255)));
        return a << 24 | rgb & 0xFFFFFF;
    }
}
