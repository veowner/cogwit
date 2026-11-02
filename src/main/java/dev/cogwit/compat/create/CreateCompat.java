package dev.cogwit.compat.create;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;

/**
 * Optional Create integration, wired from {@code CogwitMod}. This class must never reference Create types:
 * everything that does lives behind {@link ChargerContent}, which is only loaded after {@link #isLoaded()}.
 */
public final class CreateCompat {
    public static final String CREATE_ID = "create";

    private CreateCompat() {}

    public static boolean isLoaded() {
        return ModList.get().isLoaded(CREATE_ID);
    }

    public static void init(IEventBus modBus) {
        if (isLoaded()) {
            ChargerContent.register(modBus);
        }
    }
}
