package dev.cogwit.guide;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Finds a teammate who already holds what the current step needs. */
public final class GuideCoop {
    public record Offer(Component player, Component itemName, int count) {}

    private GuideCoop() {}

    public static Optional<Offer> find(Player asker, Milestone milestone) {
        return find(asker, milestone, onlinePlayers(asker));
    }

    public static Optional<Offer> find(Player asker, Milestone milestone, List<? extends Player> candidates) {
        Optional<Item> wanted = milestone.coopItem().map(id -> BuiltInRegistries.ITEM.get(ResourceLocation.parse(id)));
        if (wanted.isEmpty() || wanted.get() == Items.AIR) {
            return Optional.empty();
        }
        Offer best = null;
        for (Player other : candidates) {
            if (other == asker || other.getUUID().equals(asker.getUUID()) || other.isSpectator()) {
                continue;
            }
            int count = GuideFactsReader.count(other, wanted.get());
            if (count > 0 && (best == null || count > best.count())) {
                best = new Offer(other.getName(), wanted.get().getDescription(), count);
            }
        }
        return Optional.ofNullable(best);
    }

    private static List<Player> onlinePlayers(Player asker) {
        MinecraftServer server = asker.getServer();
        return server == null ? new ArrayList<>() : new ArrayList<>(server.getPlayerList().getPlayers());
    }
}
