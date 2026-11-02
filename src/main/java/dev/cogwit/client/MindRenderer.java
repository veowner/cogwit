package dev.cogwit.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.cogwit.CogwitMod;
import dev.cogwit.mind.Mind;
import dev.cogwit.mind.MindClientState;
import dev.cogwit.mind.MindContent;
import dev.cogwit.mind.Threats;
import dev.cogwit.network.MarkPayload;
import dev.cogwit.speech.SpeechClientState;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/** Draws the scan outlines and the shared marks in the world, through walls. */
@EventBusSubscriber(modid = CogwitMod.MODID, value = Dist.CLIENT)
public final class MindRenderer {
    private static final float[] GOLD = {1f, 0.82f, 0.35f}, VIOLET = {0.78f, 0.49f, 1f}, ORANGE = {0.85f, 0.47f, 0.34f};
    private static final double BEAM_HEIGHT = 48;

    private MindRenderer() {}

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }
        boolean scan = MindClientState.scanVisible();
        var marks = MindClientState.marks();
        var threats = ThreatSense.current();
        if (!scan && marks.isEmpty() && threats.isEmpty()) {
            return;
        }
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        Minecraft mc = Minecraft.getInstance();
        Camera camera = event.getCamera();
        Vec3 cam = camera.getPosition();
        PoseStack pose = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        pose.pushPose();
        pose.translate(-cam.x, -cam.y, -cam.z);
        VertexConsumer lines = buffers.getBuffer(CogwitRenderTypes.SEE_THROUGH_LINES);
        float pulse = 0.65f + 0.35f * (float) Math.sin(System.currentTimeMillis() / 180.0);
        if (scan) {
            drawScan(pose, lines, pulse);
        }
        for (MindClientState.Mark mark : marks) {
            BlockPos pos = mark.pos();
            LevelRenderer.renderLineBox(pose, lines, new AABB(pos).inflate(0.05), ORANGE[0], ORANGE[1], ORANGE[2], 1f);
            PoseStack.Pose p = pose.last();
            float x = pos.getX() + 0.5f, y = pos.getY() + 1f, z = pos.getZ() + 0.5f;
            lines.addVertex(p, x, y, z).setColor(ORANGE[0], ORANGE[1], ORANGE[2], pulse).setNormal(p, 0, 1, 0);
            lines.addVertex(p, x, (float) (y + BEAM_HEIGHT), z).setColor(ORANGE[0], ORANGE[1], ORANGE[2], 0f)
                    .setNormal(p, 0, 1, 0);
        }
        long nowMillis = System.currentTimeMillis();
        for (ThreatSense.Threat threat : threats) {
            brackets(pose, camera, lines, threat, partial, nowMillis);
        }
        // the "no depth test" render state only works when depth testing is already off, and here it is on
        RenderSystem.disableDepthTest();
        buffers.endBatch(CogwitRenderTypes.SEE_THROUGH_LINES);
        RenderSystem.enableDepthTest();
        for (MindClientState.Mark mark : marks) {
            label(pose, camera, buffers, mc.font, mark, cam);
        }
        for (ThreatSense.Threat threat : threats) {
            threatLabel(pose, camera, buffers, mc.font, threat, partial, nowMillis);
        }
        buffers.endBatch();
        pose.popPose();
    }

    /**
     * The scan: a ring runs out from where Cogwit scanned, and each find lights up as the ring passes it, flashing
     * white for a moment before settling into its ore colour.
     */
    private static void drawScan(PoseStack pose, VertexConsumer lines, float pulse) {
        BlockPos origin = MindClientState.scanOrigin();
        long age = MindClientState.scanAge();
        double wave = age / 1000.0 * Mind.SCAN_WAVE_SPEED;
        if (wave < Mind.SCAN_RADIUS + 6) {
            float fade = (float) Math.max(0, 1 - wave / (Mind.SCAN_RADIUS + 6));
            ring(pose, lines, Vec3.atCenterOf(origin), wave, ORANGE, fade);
            ring(pose, lines, Vec3.atCenterOf(origin), wave * 0.97, GOLD, fade * 0.5f);
        }
        for (MindContent.Find find : MindClientState.ores()) {
            long since = age - Mind.waveArrival(Math.sqrt(find.pos().distSqr(origin)));
            if (since < 0) {
                continue;
            }
            int rgb = find.ore().colour;
            float flash = Math.max(0, 1 - since / 300f);
            float r = ((rgb >> 16 & 255) / 255f) * (1 - flash) + flash;
            float g = ((rgb >> 8 & 255) / 255f) * (1 - flash) + flash;
            float b = ((rgb & 255) / 255f) * (1 - flash) + flash;
            LevelRenderer.renderLineBox(pose, lines, new AABB(find.pos()).inflate(0.02 + 0.08 * flash), r, g, b,
                    Math.max(pulse, flash));
        }
        for (BlockPos pos : MindClientState.containers()) {
            if (age >= Mind.waveArrival(Math.sqrt(pos.distSqr(origin)))) {
                LevelRenderer.renderLineBox(pose, lines, new AABB(pos).inflate(0.02), VIOLET[0], VIOLET[1], VIOLET[2],
                        pulse);
            }
        }
    }

    /** A flat circle of see-through lines. */
    private static void ring(PoseStack pose, VertexConsumer lines, Vec3 centre, double radius, float[] c, float alpha) {
        PoseStack.Pose p = pose.last();
        int segments = 96;
        for (int i = 0; i < segments; i++) {
            double a0 = i * Math.PI * 2 / segments, a1 = (i + 1) * Math.PI * 2 / segments;
            float x0 = (float) (centre.x + Math.cos(a0) * radius), z0 = (float) (centre.z + Math.sin(a0) * radius);
            float x1 = (float) (centre.x + Math.cos(a1) * radius), z1 = (float) (centre.z + Math.sin(a1) * radius);
            float nx = x1 - x0, nz = z1 - z0, len = (float) Math.sqrt(nx * nx + nz * nz);
            lines.addVertex(p, x0, (float) centre.y, z0).setColor(c[0], c[1], c[2], alpha)
                    .setNormal(p, nx / len, 0, nz / len);
            lines.addVertex(p, x1, (float) centre.y, z1).setColor(c[0], c[1], c[2], alpha)
                    .setNormal(p, nx / len, 0, nz / len);
        }
    }

    private static void label(PoseStack pose, Camera camera, MultiBufferSource buffers, Font font,
            MindClientState.Mark mark, Vec3 cam) {
        Vec3 at = Vec3.atCenterOf(mark.pos()).add(0, 1.4, 0);
        int distance = (int) Math.round(at.distanceTo(cam));
        String text = mark.label() + " · " + distance + " м · " + mark.author();
        float scale = 0.025f * Math.max(1f, distance / 12f);
        pose.pushPose();
        pose.translate(at.x, at.y, at.z);
        pose.mulPose(camera.rotation());
        pose.scale(scale, -scale, scale);
        float x = -font.width(text) / 2f;
        // like vanilla name tags: a faint copy that shows through walls, then the crisp one on top
        font.drawInBatch(text, x, 0, 0x80FFD9B0, false, pose.last().pose(), buffers, Font.DisplayMode.SEE_THROUGH,
                0x99201418, LightTexture.FULL_BRIGHT);
        font.drawInBatch(text, x, 0, 0xFFFFD9B0, false, pose.last().pose(), buffers, Font.DisplayMode.NORMAL, 0,
                LightTexture.FULL_BRIGHT);
        pose.popPose();
    }

    /** Half-size of the lock-on frame around an entity, in blocks, before the lock-on animation. */
    private static float halfWidth(ThreatSense.Threat t) {
        return t.entity().getBbWidth() * 0.5f + 0.15f;
    }

    private static float halfHeight(ThreatSense.Threat t) {
        return t.entity().getBbHeight() * 0.5f + 0.15f;
    }

    private static Vec3 centre(ThreatSense.Threat t, float partial) {
        return t.entity().getPosition(partial).add(0, t.entity().getBbHeight() / 2, 0);
    }

    /** Four corner brackets facing the camera, snapping in when the target is first seen. */
    private static void brackets(PoseStack pose, Camera camera, VertexConsumer lines, ThreatSense.Threat t,
            float partial, long now) {
        float lock = Threats.lockOn(now - t.firstSeenMillis());
        float hw = halfWidth(t) * lock, hh = halfHeight(t) * lock, corner = Math.min(hw, hh) * 0.45f;
        float[] c = ThreatSense.colour(t.level());
        float alpha = t.level() == Threats.Level.DANGER ? (now / 120 % 2 == 0 ? 1f : 0.45f)
                : t.level() == Threats.Level.INFO ? 0.6f : 0.95f;
        Vec3 at = centre(t, partial);
        pose.pushPose();
        pose.translate(at.x, at.y, at.z);
        pose.mulPose(camera.rotation());
        PoseStack.Pose p = pose.last();
        for (int sx = -1; sx <= 1; sx += 2) {
            for (int sy = -1; sy <= 1; sy += 2) {
                float x = sx * hw, y = sy * hh;
                lines.addVertex(p, x, y, 0).setColor(c[0], c[1], c[2], alpha).setNormal(p, 1, 0, 0);
                lines.addVertex(p, x - sx * corner, y, 0).setColor(c[0], c[1], c[2], alpha).setNormal(p, 1, 0, 0);
                lines.addVertex(p, x, y, 0).setColor(c[0], c[1], c[2], alpha).setNormal(p, 0, 1, 0);
                lines.addVertex(p, x, y - sy * corner, 0).setColor(c[0], c[1], c[2], alpha).setNormal(p, 0, 1, 0);
            }
        }
        pose.popPose();
    }

    private static void threatLabel(PoseStack pose, Camera camera, MultiBufferSource buffers, Font font,
            ThreatSense.Threat t, float partial, long now) {
        if (t.level() == Threats.Level.INFO && t.distance() > 6) {
            return; // far, ordinary mobs only get brackets, to keep the screen calm
        }
        Vec3 at = centre(t, partial);
        float hh = halfHeight(t) * Threats.lockOn(now - t.firstSeenMillis());
        float[] c = ThreatSense.colour(t.level());
        int rgb = (int) (c[0] * 255) << 16 | (int) (c[1] * 255) << 8 | (int) (c[2] * 255);
        // grows with distance so the label keeps roughly the same size on screen
        float scale = Math.max(0.008f, 0.0032f * (float) t.distance());
        pose.pushPose();
        pose.translate(at.x, at.y, at.z);
        pose.mulPose(camera.rotation());
        pose.translate(0, hh + 0.08f, 0);
        pose.scale(scale, -scale, scale);
        String text = t.label().getString();
        float x = -font.width(text) / 2f;
        font.drawInBatch(text, x, -9, 0x80000000 | rgb, false, pose.last().pose(), buffers,
                Font.DisplayMode.SEE_THROUGH, 0x99101418, LightTexture.FULL_BRIGHT);
        font.drawInBatch(text, x, -9, 0xFF000000 | rgb, false, pose.last().pose(), buffers, Font.DisplayMode.NORMAL,
                0, LightTexture.FULL_BRIGHT);
        pose.popPose();
    }

    /** The mark key: find the mob or block under the crosshair far away and ask the server to share it. */
    public static void markWhatPlayerLooksAt() {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null) {
            return;
        }
        float partial = mc.getTimer().getGameTimeDeltaPartialTick(true);
        double reach = Mind.MARK_REACH;
        Vec3 eye = player.getEyePosition(partial);
        Vec3 look = player.getViewVector(partial);
        Vec3 end = eye.add(look.scale(reach));
        HitResult block = player.pick(reach, partial, false);
        double blockDistance = block.getType() == HitResult.Type.MISS ? reach * reach
                : block.getLocation().distanceToSqr(eye);
        AABB sweep = player.getBoundingBox().expandTowards(look.scale(reach)).inflate(1);
        EntityHitResult entity = ProjectileUtil.getEntityHitResult(player, eye, end, sweep,
                e -> !e.isSpectator() && e.isPickable(), blockDistance);
        if (entity != null) {
            PacketDistributor.sendToServer(new MarkPayload(entity.getEntity().getId(), entity.getEntity().blockPosition()));
        } else if (block instanceof BlockHitResult hit && block.getType() == HitResult.Type.BLOCK) {
            PacketDistributor.sendToServer(new MarkPayload(-1, hit.getBlockPos()));
        } else {
            SpeechClientState.add(Component.translatable("cogwit.mind.mark_nothing"), true);
        }
    }
}
