package dev.cogwit.horizon;

import dev.cogwit.CogwitMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** The armor's own sounds (Kenney's CC0 Sci-Fi Sounds, see assets/cogwit/sounds/credits.txt). */
public final class HorizonSounds {
    private static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, CogwitMod.MODID);

    public static final DeferredHolder<SoundEvent, SoundEvent> VISOR_OPEN = sound("horizon.visor_open");
    public static final DeferredHolder<SoundEvent, SoundEvent> VISOR_CLOSE = sound("horizon.visor_close");
    /** A plate locking onto the body when one piece goes on. */
    public static final DeferredHolder<SoundEvent, SoundEvent> PLATE = sound("horizon.plate");
    /** The whole suit coming alive once the set is complete. */
    public static final DeferredHolder<SoundEvent, SoundEvent> SUIT_UP = sound("horizon.suit_up");

    private HorizonSounds() {}

    private static DeferredHolder<SoundEvent, SoundEvent> sound(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(CogwitMod.id(name)));
    }

    public static void register(IEventBus modBus) {
        SOUNDS.register(modBus);
    }
}
