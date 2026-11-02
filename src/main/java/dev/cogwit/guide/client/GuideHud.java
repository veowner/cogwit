package dev.cogwit.guide.client;

import com.mojang.math.Axis;
import dev.cogwit.guide.GuideClientState;
import dev.cogwit.guide.GuideDirection;
import dev.cogwit.guide.net.GuideTargetPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/** The compass panel at the top of the screen: an arrow toward the guide's target, its name and distance. */
public final class GuideHud {
    public static final int WIDTH = 132;
    public static final int HEIGHT = 30;

    private static final int PAD = 4;
    private static final int ORANGE = 0xFFC8803F;
    private static final int BACKGROUND = 0xC0141416;
    private static final int TEXT = 0xFFE8E8EA;
    private static final int TEXT_DIM = 0xFF8A8A8F;
    private static final int GREEN = 0xFF7CE07C;
    private static final int ARRIVED_BLOCKS = 16;

    private GuideHud() {}

    public static void render(GuiGraphics g) {
        Minecraft mc = Minecraft.getInstance();
        GuideTargetPayload target = GuideClientState.target();
        if (target == null || mc.player == null || mc.options.hideGui || mc.screen != null) {
            return;
        }
        double dx = target.x() + 0.5 - mc.player.getX();
        double dz = target.z() + 0.5 - mc.player.getZ();
        long distance = Math.round(Math.hypot(dx, dz));
        boolean arrived = distance < ARRIVED_BLOCKS;

        Font font = mc.font;
        Component label = Component.translatable(target.labelKey());
        Component detail = arrived
                ? Component.translatable("cogwit.guide.hud.arrived")
                : Component.translatable("cogwit.guide.hud.distance", distance);
        // grow to fit long structure names instead of cutting them off
        int width = Math.max(WIDTH, 32 + Math.max(font.width(label), font.width(detail)) + PAD);
        int x = (g.guiWidth() - width) / 2;
        int y = 4;
        g.fill(x, y, x + width, y + HEIGHT, BACKGROUND);
        g.fill(x, y, x + width, y + 1, ORANGE);
        drawArrow(g, x + 16, y + 16, GuideDirection.arrowDegrees(dx, dz, mc.player.getYRot()),
                arrived ? GREEN : ORANGE);

        int textX = x + 32;
        g.drawString(font, label, textX, y + 7, TEXT, true);
        g.drawString(font, detail, textX, y + 18, arrived ? GREEN : TEXT_DIM, false);
    }

    /** An up-pointing arrow of plain rectangles, turned clockwise by {@code degrees} around (cx, cy). */
    private static void drawArrow(GuiGraphics g, int cx, int cy, float degrees, int color) {
        g.pose().pushPose();
        g.pose().translate(cx, cy, 0);
        g.pose().mulPose(Axis.ZP.rotationDegrees(degrees));
        for (int row = 0; row < 6; row++) {
            g.fill(-row, -9 + row, row + 1, -8 + row, color);
        }
        g.fill(-1, -3, 2, 7, color);
        g.pose().popPose();
    }
}
