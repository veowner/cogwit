package dev.cogwit.trophy;

import dev.cogwit.CogwitMod;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Runs pieces of an ability a few ticks apart, so a wave of spikes or a volley unfolds instead of popping at once. */
@EventBusSubscriber(modid = CogwitMod.MODID)
public final class TrophyScheduler {
    private record Task(long at, Runnable run) {}

    private static final Map<ServerLevel, List<Task>> TASKS = new ConcurrentHashMap<>();

    private TrophyScheduler() {}

    public static void later(ServerLevel level, int delayTicks, Runnable run) {
        TASKS.computeIfAbsent(level, l -> new ArrayList<>()).add(new Task(level.getGameTime() + delayTicks, run));
    }

    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        List<Task> tasks = TASKS.get(level);
        if (tasks == null || tasks.isEmpty()) {
            return;
        }
        long now = level.getGameTime();
        List<Task> due = new ArrayList<>();
        tasks.removeIf(t -> {
            if (t.at() <= now) {
                due.add(t);
                return true;
            }
            return false;
        });
        for (Task t : due) {
            t.run().run();
        }
    }
}
