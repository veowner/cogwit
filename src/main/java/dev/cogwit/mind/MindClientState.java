package dev.cogwit.mind;

import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.List;

/** What the screen should outline: the last scan and the live marks. Plain data, safe to load on a server. */
public final class MindClientState {
    public record Mark(BlockPos pos, String author, String label, long expiresMillis) {}

    private static volatile List<MindContent.Find> ores = List.of();
    private static volatile List<BlockPos> containers = List.of();
    private static volatile BlockPos scanOrigin = BlockPos.ZERO;
    private static volatile long scanStartMillis, scanExpiresMillis;
    private static final List<Mark> MARKS = new ArrayList<>();

    private MindClientState() {}

    public static void showScan(BlockPos origin, List<MindContent.Find> foundOres, List<BlockPos> foundContainers) {
        ores = List.copyOf(foundOres);
        containers = List.copyOf(foundContainers);
        scanOrigin = origin;
        scanStartMillis = System.currentTimeMillis();
        scanExpiresMillis = scanStartMillis + Mind.SCAN_SHOW_TICKS * 50;
    }

    public static BlockPos scanOrigin() {
        return scanOrigin;
    }

    /** Ms since the last scan started, for the wave. */
    public static long scanAge() {
        return System.currentTimeMillis() - scanStartMillis;
    }

    public static boolean scanVisible() {
        return System.currentTimeMillis() < scanExpiresMillis;
    }

    public static List<MindContent.Find> ores() {
        return ores;
    }

    public static List<BlockPos> containers() {
        return containers;
    }

    public static synchronized void addMark(BlockPos pos, String author, String label) {
        MARKS.removeIf(m -> m.pos().equals(pos));
        MARKS.add(new Mark(pos, author, label, System.currentTimeMillis() + Mind.MARK_SHOW_TICKS * 50));
    }

    public static synchronized List<Mark> marks() {
        long now = System.currentTimeMillis();
        MARKS.removeIf(m -> m.expiresMillis() < now);
        return List.copyOf(MARKS);
    }
}
