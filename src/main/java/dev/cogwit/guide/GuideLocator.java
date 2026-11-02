package dev.cogwit.guide;

import com.mojang.datafixers.util.Pair;
import dev.cogwit.CogwitMod;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.Structure;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Finds the nearest structure for a milestone with the server's structure search, caching answers. */
public final class GuideLocator {
    public static final int RADIUS_CHUNKS = 64;
    public static final long CACHE_TICKS = 12_000L;

    private record Key(UUID player, String structure, ResourceKey<Level> dimension) {}

    private record Entry(Optional<BlockPos> pos, long expiresAt) {}

    private static final Map<Key, Entry> CACHE = new HashMap<>();

    private GuideLocator() {}

    public static boolean inTargetDimension(Level level, Milestone.Target target) {
        return level.dimension().location().toString().equals(target.dimension());
    }

    /** Where the structure for this milestone is, or empty if there is none, it is not found, or wrong dimension. */
    public static Optional<BlockPos> find(Player player, Milestone milestone) {
        return milestone.target().flatMap(target -> find(player, target));
    }

    /** Same search for any structure target, such as the lair of the next boss to hunt. */
    public static Optional<BlockPos> find(Player player, Milestone.Target target) {
        if (!(player.level() instanceof ServerLevel level) || !inTargetDimension(level, target)) {
            return Optional.empty();
        }
        long now = level.getServer().getTickCount();
        Key key = new Key(player.getUUID(), target.structure(), level.dimension());
        Entry cached = CACHE.get(key);
        if (cached != null && now < cached.expiresAt()) {
            return cached.pos();
        }
        Optional<BlockPos> found = search(level, target, player.blockPosition());
        CACHE.put(key, new Entry(found, now + CACHE_TICKS));
        return found;
    }

    public static Optional<Holder<Structure>> structure(ServerLevel level, Milestone.Target target) {
        ResourceKey<Structure> key = ResourceKey.create(Registries.STRUCTURE, ResourceLocation.parse(target.structure()));
        return level.registryAccess().registryOrThrow(Registries.STRUCTURE).getHolder(key).map(h -> (Holder<Structure>) h);
    }

    private static Optional<BlockPos> search(ServerLevel level, Milestone.Target target, BlockPos from) {
        try {
            return structure(level, target).map(holder -> {
                Pair<BlockPos, Holder<Structure>> result = level.getChunkSource().getGenerator()
                        .findNearestMapStructure(level, HolderSet.direct(holder), from, RADIUS_CHUNKS, false);
                return result == null ? null : result.getFirst();
            });
        } catch (RuntimeException e) {
            CogwitMod.LOGGER.warn("Guide structure search for {} failed", target.structure(), e);
            return Optional.empty();
        }
    }

    public static boolean isCached(UUID player) {
        return CACHE.keySet().stream().anyMatch(key -> key.player().equals(player));
    }

    public static void forget(UUID player) {
        CACHE.keySet().removeIf(key -> key.player().equals(player));
    }

    public static void clear() {
        CACHE.clear();
    }
}
