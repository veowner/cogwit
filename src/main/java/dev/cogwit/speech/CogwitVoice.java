package dev.cogwit.speech;

import dev.cogwit.CogwitMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Cogwit's voice: short synthesized beeps and trills (tools/synth_beeps.py), one meaning per sound event. Heard by
 * everyone nearby, so a co-op friend hears your Cogwit too. Never more than one beep at a time per Cogwit.
 */
public final class CogwitVoice {
    public enum Beep {
        HELLO, YES, NO, ALERT, HAPPY, THINK, SAD, CHIRP;

        String key() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    /** Ticks between two beeps of the same Cogwit; an alert may cut in sooner. */
    public static final long GAP_TICKS = 8, ALERT_GAP_TICKS = 3;

    private static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, CogwitMod.MODID);
    private static final Map<Beep, DeferredHolder<SoundEvent, SoundEvent>> EVENTS = new EnumMap<>(Beep.class);
    private static final Map<UUID, Long> LAST = new ConcurrentHashMap<>();

    static {
        for (Beep beep : Beep.values()) {
            String name = "beep." + beep.key();
            EVENTS.put(beep, SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(CogwitMod.id(name))));
        }
    }

    private CogwitVoice() {}

    public static void register(IEventBus modBus) {
        SOUNDS.register(modBus);
    }

    /** Whether a beep may play now, given the last one. Pure logic. */
    public static boolean ready(long last, long now, Beep beep) {
        return last == 0 || now - last >= (beep == Beep.ALERT ? ALERT_GAP_TICKS : GAP_TICKS);
    }

    /** Cogwit beeps from his owner's shoulder (server side; on a client this does nothing). */
    public static void beep(Player player, Beep beep) {
        if (player.level().isClientSide) {
            return;
        }
        long now = player.level().getGameTime();
        if (!ready(LAST.getOrDefault(player.getUUID(), 0L), now, beep)) {
            return;
        }
        LAST.put(player.getUUID(), now);
        float pitch = 0.94f + player.getRandom().nextFloat() * 0.12f;
        player.level().playSound(null, player.getX(), player.getEyeY(), player.getZ(), EVENTS.get(beep).get(),
                SoundSource.PLAYERS, beep == Beep.ALERT ? 0.6f : 0.4f, pitch);
    }

    public static void forget(UUID player) {
        LAST.remove(player);
    }
}
