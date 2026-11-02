package dev.cogwit.client;

import com.mojang.math.Axis;
import dev.cogwit.mind.Threats;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/** Arrows around the crosshair pointing at threats you can't see, Jarvis style. */
public final class ThreatHud {
    private static final int RADIUS = 40, ARROW = 9;

    private ThreatHud() {}

    public static void render(GuiGraphics g) {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null || mc.options.hideGui || mc.screen != null) {
            return;
        }
        int cx = g.guiWidth() / 2, cy = g.guiHeight() / 2;
        Vec3 look = player.getLookAngle();
        long now = System.currentTimeMillis();
        for (ThreatSense.Threat t : ThreatSense.current()) {
            if (t.level() == Threats.Level.INFO || t.level() == Threats.Level.LOOT) {
                continue;
            }
            double angle = Threats.relativeAngle(look.x, look.z, t.entity().getX() - player.getX(),
                    t.entity().getZ() - player.getZ());
            if (!Threats.needsArrow(angle)) {
                continue;
            }
            float[] c = ThreatSense.colour(t.level());
            float alpha = t.level() == Threats.Level.DANGER && now / 150 % 2 == 1 ? 0.5f : 1f;
            int colour = (int) (alpha * 255) << 24 | (int) (c[0] * 255) << 16 | (int) (c[1] * 255) << 8
                    | (int) (c[2] * 255);
            g.pose().pushPose();
            g.pose().translate(cx, cy, 0);
            g.pose().mulPose(Axis.ZP.rotationDegrees((float) angle));
            g.pose().translate(0, -RADIUS, 0);
            // a chevron pointing outwards, two pixels thick
            for (int i = 0; i < ARROW; i++) {
                g.fill(-i - 2, i, -i + 1, i + 2, colour);
                g.fill(i - 1, i, i + 2, i + 2, colour);
            }
            g.pose().popPose();
            double rad = Math.toRadians(angle);
            int tx = (int) Math.round(cx + Math.sin(rad) * (RADIUS + 18));
            int ty = (int) Math.round(cy - Math.cos(rad) * (RADIUS + 18));
            String distance = Math.round(t.distance()) + "м";
            g.drawString(mc.font, distance, tx - mc.font.width(distance) / 2, ty - 4, colour, true);
        }
    }
}
