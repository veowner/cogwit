package dev.cogwit.trophy;

import java.util.Locale;
import java.util.Optional;

/** The 35 bosses of the pack. Each one, once beaten, leaves a passive and maybe an active. */
public enum Trophy {
    ENDER_DRAGON("minecraft:ender_dragon", 0xB24BF3, true, 400, true, "minecraft:stronghold", "minecraft:overworld", 8),
    WITHER("minecraft:wither", 0x3A3A44, true, 240, true, null, "minecraft:overworld", 6),
    WARDEN("minecraft:warden", 0x1F6F78, true, 300, true, "minecraft:ancient_city", "minecraft:overworld", 7),
    ELDER_GUARDIAN("minecraft:elder_guardian", 0x7BC6B7, true, 200, true, "minecraft:monument", "minecraft:overworld", 3),
    IGNIS("cataclysm:ignis", 0xFF7A1A, true, 160, true, "cataclysm:burning_arena", "minecraft:the_nether", 14),
    NETHERITE_MONSTROSITY("cataclysm:netherite_monstrosity", 0x5C3B33, true, 300, true, "cataclysm:soul_black_smith", "minecraft:the_nether", 11),
    ENDER_GUARDIAN("cataclysm:ender_guardian", 0x6E2BA8, true, 260, true, "cataclysm:ruined_citadel", "minecraft:the_end", 15),
    HARBINGER("cataclysm:the_harbinger", 0xC23B3B, true, 280, true, "cataclysm:ancient_factory", "minecraft:overworld", 13),
    LEVIATHAN("cataclysm:the_leviathan", 0x1B3F8F, true, 300, true, "cataclysm:sunken_city", "minecraft:overworld", 16),
    ANCIENT_REMNANT("cataclysm:ancient_remnant", 0xD8B26A, true, 300, true, "cataclysm:cursed_pyramid", "minecraft:overworld", 12),
    MALEDICTUS("cataclysm:maledictus", 0x8FA6C9, true, 220, true, "cataclysm:frosted_prison", "minecraft:overworld", 17),
    SCYLLA("cataclysm:scylla", 0x2FB6D6, true, 320, true, "cataclysm:acropolis", "minecraft:overworld", 18),
    FERROUS_WROUGHTNAUT("mowziesmobs:ferrous_wroughtnaut", 0x8C8C92, false, 0, true, "mowziesmobs:wrought_chamber", "minecraft:overworld", 1),
    FROSTMAW("mowziesmobs:frostmaw", 0xBFEFFF, true, 240, true, "mowziesmobs:frostmaw_spawn", "minecraft:overworld", 4),
    UMVUTHI("mowziesmobs:umvuthi", 0xFFD447, true, 260, true, "mowziesmobs:umvuthana_grove", "minecraft:overworld", 5),
    SCULPTOR("mowziesmobs:sculptor", 0xA59A86, true, 240, true, "mowziesmobs:monastery", "minecraft:overworld", 9),
    SLIDER("aether:slider", 0x9DA7B0, true, 160, true, "aether:bronze_dungeon", "aether:the_aether", 2),
    VALKYRIE_QUEEN("aether:valkyrie_queen", 0xF4F0D2, true, 220, true, "aether:silver_dungeon", "aether:the_aether", 10),
    SUN_SPIRIT("aether:sun_spirit", 0xFFB12E, true, 260, true, "aether:gold_dungeon", "aether:the_aether", 19),
    DEAD_KING("irons_spellbooks:dead_king", 0x6B7F5B, true, 400, true, "irons_spellbooks:catacombs", "minecraft:overworld", 12),
    FIRE_BOSS("irons_spellbooks:fire_boss", 0xE5522B, true, 300, true, null, "minecraft:overworld", 20),
    ARCHEVOKER("irons_spellbooks:archevoker", 0x5F6E8C, true, 260, true, "irons_spellbooks:evoker_fort", "minecraft:overworld", 9),
    NAGA("twilightforest:naga", 0x3F8F3F, true, 200, true, "twilightforest:naga_courtyard", "twilightforest:twilight_forest", 3),
    LICH("twilightforest:lich", 0x7A5BD0, true, 240, true, "twilightforest:lich_tower", "twilightforest:twilight_forest", 5),
    MINOSHROOM("twilightforest:minoshroom", 0xB5473A, true, 260, true, "twilightforest:labyrinth", "twilightforest:twilight_forest", 7),
    HYDRA("twilightforest:hydra", 0x4FA36B, true, 260, true, "twilightforest:hydra_lair", "twilightforest:twilight_forest", 10),
    KNIGHT_PHANTOM("twilightforest:knight_phantom", 0x9FD9E8, true, 260, true, "twilightforest:knight_stronghold", "twilightforest:twilight_forest", 12),
    UR_GHAST("twilightforest:ur_ghast", 0xE9E9F0, true, 320, true, "twilightforest:dark_tower", "twilightforest:twilight_forest", 15),
    ALPHA_YETI("twilightforest:alpha_yeti", 0xDCE4EC, true, 240, true, "twilightforest:yeti_cave", "twilightforest:twilight_forest", 16),
    SNOW_QUEEN("twilightforest:snow_queen", 0x6FB7E8, true, 300, true, "twilightforest:aurora_palace", "twilightforest:twilight_forest", 18),
    YETI("block_factorys_bosses:yeti", 0x7FA6C4, true, 280, true, "block_factorys_bosses:yeti_hideout", "minecraft:overworld", 6),
    SANDWORM("block_factorys_bosses:sandworm", 0xB8863B, true, 280, true, "block_factorys_bosses:sandworm_nest", "minecraft:overworld", 9),
    INFERNAL_DRAGON("block_factorys_bosses:infernal_dragon", 0xB32D12, true, 280, true, "block_factorys_bosses:dragon_tower", "minecraft:overworld", 13),
    KRAKEN("block_factorys_bosses:kraken", 0x2C7A6E, true, 300, true, "block_factorys_bosses:kraken_ship", "minecraft:overworld", 14),
    UNDERWORLD_KNIGHT("block_factorys_bosses:underworld_knight", 0x3AA7B8, true, 260, true, "block_factorys_bosses:underworld_arena", "minecraft:the_nether", 17);

    /** Registry id of the boss entity. */
    public final String entity;
    /** The boss's colour in the codex and on the ability wheel, RGB. */
    public final int colour;
    public final boolean hasActive;
    public final int cooldownTicks;
    /** Whether this trophy's powers are built yet; the rest only count for the codex for now. */
    public final boolean implemented;
    /** Structure where the boss lives (for Cogwit's compass), or null when it is summoned or roams. */
    public final String structure;
    public final String dimension;
    /** Rough difficulty for the suggested hunting order, lower first. */
    public final int difficulty;

    Trophy(String entity, int colour, boolean hasActive, int cooldownTicks, boolean implemented, String structure,
            String dimension, int difficulty) {
        this.entity = entity;
        this.colour = colour;
        this.hasActive = hasActive;
        this.cooldownTicks = cooldownTicks;
        this.implemented = implemented;
        this.structure = structure;
        this.dimension = dimension;
        this.difficulty = difficulty;
    }

    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** The mod a boss comes from, e.g. "cataclysm". */
    public String mod() {
        return entity.substring(0, entity.indexOf(':'));
    }

    public static Optional<Trophy> byKey(String key) {
        for (Trophy t : values()) {
            if (t.key().equals(key)) {
                return Optional.of(t);
            }
        }
        return Optional.empty();
    }

    public static Optional<Trophy> byEntity(String entityId) {
        for (Trophy t : values()) {
            if (t.entity.equals(entityId)) {
                return Optional.of(t);
            }
        }
        return Optional.empty();
    }
}
