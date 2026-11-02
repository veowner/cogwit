package dev.cogwit;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Groups Minecraft's many damage types into the lessons Cogwit can learn. */
public final class DamageCategories {
    private static final Set<String> KNOWN = Set.of("fire", "lava", "explosion", "fall", "drowning", "freezing",
            "lightning", "projectile", "magic", "wither", "sonic", "contact", "suffocation", "mob");

    private static final List<ResourceKey<DamageType>> MAGIC = List.of(DamageTypes.MAGIC, DamageTypes.INDIRECT_MAGIC,
            DamageTypes.DRAGON_BREATH);
    private static final List<ResourceKey<DamageType>> WITHER = List.of(DamageTypes.WITHER, DamageTypes.WITHER_SKULL);
    private static final List<ResourceKey<DamageType>> CONTACT = List.of(DamageTypes.CACTUS,
            DamageTypes.SWEET_BERRY_BUSH, DamageTypes.STALAGMITE, DamageTypes.FALLING_STALACTITE,
            DamageTypes.HOT_FLOOR, DamageTypes.FALLING_BLOCK, DamageTypes.FALLING_ANVIL);
    private static final List<ResourceKey<DamageType>> SUFFOCATION = List.of(DamageTypes.IN_WALL,
            DamageTypes.CRAMMING, DamageTypes.FLY_INTO_WALL);

    private DamageCategories() {}

    /** Empty for damage Cogwit refuses to deal with at all (void, /kill, starving). */
    public static Optional<String> categorize(DamageSource source) {
        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY) || source.is(DamageTypes.STARVE)) {
            return Optional.empty();
        }
        if (source.is(DamageTypes.LAVA)) return Optional.of("lava");
        if (source.is(DamageTypeTags.IS_EXPLOSION)) return Optional.of("explosion");
        if (source.is(DamageTypeTags.IS_FIRE)) return Optional.of("fire");
        if (source.is(DamageTypeTags.IS_FALL)) return Optional.of("fall");
        if (source.is(DamageTypeTags.IS_DROWNING)) return Optional.of("drowning");
        if (source.is(DamageTypeTags.IS_FREEZING)) return Optional.of("freezing");
        if (source.is(DamageTypeTags.IS_LIGHTNING)) return Optional.of("lightning");
        if (source.is(DamageTypes.SONIC_BOOM)) return Optional.of("sonic");
        if (source.is(DamageTypeTags.IS_PROJECTILE)) return Optional.of("projectile");
        if (isAny(source, MAGIC)) return Optional.of("magic");
        if (isAny(source, WITHER)) return Optional.of("wither");
        if (isAny(source, CONTACT)) return Optional.of("contact");
        if (isAny(source, SUFFOCATION)) return Optional.of("suffocation");
        if (source.getEntity() instanceof LivingEntity attacker && !(attacker instanceof Player)) {
            return Optional.of("mob");
        }
        // Modded damage (boss attacks, spells…): learn it under its own name.
        return source.typeHolder().unwrapKey().map(key -> key.location().toString());
    }

    public static Component name(String category) {
        if (KNOWN.contains(category)) {
            return Component.translatable("cogwit.category." + category);
        }
        String path = category.contains(":") ? category.substring(category.indexOf(':') + 1) : category;
        return Component.literal(path.replace('_', ' '));
    }

    private static boolean isAny(DamageSource source, List<ResourceKey<DamageType>> types) {
        for (ResourceKey<DamageType> type : types) {
            if (source.is(type)) {
                return true;
            }
        }
        return false;
    }
}
