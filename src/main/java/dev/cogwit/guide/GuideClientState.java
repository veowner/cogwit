package dev.cogwit.guide;

import dev.cogwit.guide.net.GuideTargetPayload;

/** What the server last told the local player about the guide; read by the HUD and the roadmap. Safe on servers. */
public final class GuideClientState {
    private static volatile int doneMask;
    private static volatile GuideTargetPayload target = GuideTargetPayload.none();

    private GuideClientState() {}

    public static void updateProgress(int mask) {
        doneMask = mask;
    }

    public static void updateTarget(GuideTargetPayload payload) {
        target = payload;
    }

    public static int doneMask() {
        return doneMask;
    }

    /** The compass target, or null when there is none. */
    public static GuideTargetPayload target() {
        GuideTargetPayload t = target;
        return t.present() ? t : null;
    }

    public static void reset() {
        doneMask = 0;
        target = GuideTargetPayload.none();
    }
}
