package dev.cogwit.mind;

import dev.cogwit.Speech;
import dev.cogwit.guide.GuideDirection;
import dev.cogwit.guide.GuideService;
import dev.cogwit.guide.net.GuideTargetPayload;
import dev.cogwit.speech.CogwitVoice;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameRules;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * After a death that dropped your things, Cogwit's compass points back to them until you get there or they would have
 * despawned. While it does, the guide's own target waits.
 */
public final class DeathCompass {
    /** Dropped items last five minutes in a loaded chunk; the compass gives up then. */
    public static final long LIFETIME_TICKS = 6000;
    public static final double ARRIVED = 4;
    public static final String LABEL = "cogwit.guide.target.death";

    private record Trip(String dimension, BlockPos pos, long until, boolean shown) {
        Trip shownNow() {
            return new Trip(dimension, pos, until, true);
        }
    }

    private static final Map<UUID, Trip> TRIPS = new ConcurrentHashMap<>();

    private DeathCompass() {}

    public static boolean active(Player player) {
        return TRIPS.containsKey(player.getUUID());
    }

    /** Called on respawn: tells you where your things are and points the compass there. */
    static void start(Player player, MindContent.Deaths deaths) {
        if (deaths.dimension().isEmpty() || player.level().getGameRules().getBoolean(GameRules.RULE_KEEPINVENTORY)) {
            return;
        }
        long now = player.level().getGameTime();
        TRIPS.put(player.getUUID(), new Trip(deaths.dimension(), deaths.pos(), now + LIFETIME_TICKS, false));
        if (here(player, deaths.dimension())) {
            double dx = deaths.pos().getX() + 0.5 - player.getX(), dz = deaths.pos().getZ() + 0.5 - player.getZ();
            Speech.say(player, Component.translatable("cogwit.death.loot", GuideDirection.roughDistance(Math.hypot(dx, dz)),
                    Component.translatable(GuideDirection.compass(dx, dz).key())));
        } else {
            Speech.say(player, Component.translatable("cogwit.death.loot_elsewhere"));
        }
        tick(player);
    }

    /** Twice a second while Cogwit is worn: show the way in the right dimension, end it on arrival or timeout. */
    static void tick(Player player) {
        Trip trip = TRIPS.get(player.getUUID());
        if (trip == null) {
            return;
        }
        long now = player.level().getGameTime();
        boolean here = here(player, trip.dimension());
        if (now > trip.until()) {
            end(player, trip, Component.translatable("cogwit.death.loot_gone"), false);
        } else if (here && player.blockPosition().distSqr(trip.pos()) <= ARRIVED * ARRIVED) {
            end(player, trip, Component.translatable("cogwit.death.loot_found"), true);
        } else if (here && !trip.shown()) {
            TRIPS.put(player.getUUID(), trip.shownNow());
            send(player, GuideTargetPayload.at(trip.pos().getX(), trip.pos().getZ(), LABEL));
        } else if (!here && trip.shown()) {
            TRIPS.put(player.getUUID(), new Trip(trip.dimension(), trip.pos(), trip.until(), false));
            send(player, GuideTargetPayload.none());
        }
    }

    private static void end(Player player, Trip trip, Component line, boolean found) {
        TRIPS.remove(player.getUUID());
        if (trip.shown()) {
            send(player, GuideTargetPayload.none());
        }
        Speech.say(player, line);
        CogwitVoice.beep(player, found ? CogwitVoice.Beep.HAPPY : CogwitVoice.Beep.SAD);
        GuideService.resendTarget(player);
    }

    private static boolean here(Player player, String dimension) {
        return player.level().dimension().location().toString().equals(dimension);
    }

    private static void send(Player player, GuideTargetPayload payload) {
        if (player instanceof ServerPlayer serverPlayer) {
            PacketDistributor.sendToPlayer(serverPlayer, payload);
        }
    }

    public static void forget(UUID player) {
        TRIPS.remove(player);
    }
}
