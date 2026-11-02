package dev.cogwit;

import dev.cogwit.brain.Brain;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Balance knobs for the whole world (serverconfig/cogwit-server.toml, synced to players). Defaults keep the
 * Horizon set and Cogwit strong without making the pack's own bosses trivial.
 */
public final class BalanceConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.DoubleValue ARMOR_REDUCTION = BUILDER
            .comment("Full Horizon set: share of incoming damage taken off with no mass stored (old value 0.5)")
            .defineInRange("armorReduction", 0.30, 0.0, 0.9);

    public static final ModConfigSpec.DoubleValue ARMOR_MASS_REDUCTION = BUILDER
            .comment("Extra share taken off at full mass, scaling with the mass meter (0 = mass doesn't protect)")
            .defineInRange("armorMassReduction", 0.20, 0.0, 0.9);

    public static final ModConfigSpec.IntValue FLIGHT_TOKENS_PER_SECOND = BUILDER
            .comment("Tokens the Horizon set's flight burns per second, from cells first, then Cogwit (0 = free)")
            .defineInRange("flightTokensPerSecond", 2, 0, 200);

    public static final ModConfigSpec.DoubleValue COGWIT_BLOCK_SCALE = BUILDER
            .comment("Scales Cogwit's skill blocking: 1.0 = up to 60% (old), 0.67 = up to 40%")
            .defineInRange("cogwitBlockScale", 0.67, 0.0, 1.5);

    public static final ModConfigSpec SPEC = BUILDER.build();

    private BalanceConfig() {}

    public static float armorReduction() {
        return (float) get(ARMOR_REDUCTION, 0.30);
    }

    public static float armorMassReduction() {
        return (float) get(ARMOR_MASS_REDUCTION, 0.20);
    }

    public static int flightTokensPerSecond() {
        try {
            return FLIGHT_TOKENS_PER_SECOND.get();
        } catch (IllegalStateException e) {
            return 2;
        }
    }

    private static double get(ModConfigSpec.DoubleValue value, double fallback) {
        try {
            return value.get();
        } catch (IllegalStateException e) {
            return fallback;
        }
    }

    /** Pushes the blocking scale into the pure logic whenever the config (re)loads, on either side. */
    @EventBusSubscriber(modid = CogwitMod.MODID, bus = EventBusSubscriber.Bus.MOD)
    public static final class Events {
        private Events() {}

        @SubscribeEvent
        public static void onLoad(ModConfigEvent.Loading event) {
            apply(event);
        }

        @SubscribeEvent
        public static void onReload(ModConfigEvent.Reloading event) {
            apply(event);
        }

        private static void apply(ModConfigEvent event) {
            if (event.getConfig().getSpec() == SPEC) {
                Brain.setBlockScale((float) get(COGWIT_BLOCK_SCALE, 0.67));
            }
        }
    }
}
