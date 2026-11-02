package dev.cogwit.guide;

import java.util.Optional;

/** The ordered steps of mod progression Cogwit walks the player through. */
public enum Milestone {
    GET_COGWIT("get_cogwit", null, null),
    WEAR_COGWIT("wear_cogwit", null, null),
    FEED_COGWIT("feed_cogwit", null, null),
    NETHERITE("netherite", new Target("minecraft:bastion_remnant", "minecraft:the_nether", true), null),
    /** The core material: a netherite ingot, or the rarer heavy core (which makes two cores). Id kept for saves. */
    HEAVY_CORE("heavy_core", null, "minecraft:netherite_ingot"),
    ECHO_SHARDS("echo_shards", new Target("minecraft:ancient_city", "minecraft:overworld", false), "minecraft:echo_shard"),
    SINGULARITY_CORE("singularity_core", null, "cogwit:singularity_core"),
    HORIZON_TEMPLATE("horizon_template", null, "cogwit:horizon_template"),
    FIRST_PIECE("first_piece", null, null),
    FULL_SET("full_set", null, null),
    TOKEN_CELL("token_cell", null, "cogwit:token_cell"),
    CELLS_IN_ARMOR("cells_in_armor", null, null),
    // Chapter two: Cogwit grows and the two of you hunt bosses. Appended so saved milestone indices stay put.
    COGWIT_LEVEL_2("cogwit_level_2", null, null),
    FIRST_TROPHY("first_trophy", null, null),
    COGWIT_LEVEL_4("cogwit_level_4", null, null),
    COGWIT_LEVEL_5("cogwit_level_5", null, null),
    ALL_TROPHIES("all_trophies", null, null),
    // Chapter three: the lands the pack grew later, each with bosses of its own.
    TWILIGHT_PORTAL("twilight_portal", null, null),
    TWILIGHT_BOSSES("twilight_bosses", null, null),
    WORLD_BOSSES("world_bosses", null, null);

    /** The first milestone of chapter two, where the roadmap draws its second heading. */
    public static final Milestone CHAPTER_TWO = COGWIT_LEVEL_2;
    public static final Milestone CHAPTER_THREE = TWILIGHT_PORTAL;
    /** How many chapters the roadmap has; {@link #chapter} runs from 1 to this. */
    public static final int CHAPTERS = 3;
    /** Mods whose bosses belong to chapter three rather than to the hunt of chapter two. */
    public static final String TWILIGHT = "twilightforest", WORLD = "block_factorys_bosses";

    /** A structure worth walking to, its dimension, and whether the hint text changes when elsewhere. */
    public record Target(String structure, String dimension, boolean travelHint) {
        /** Short HUD label key, e.g. {@code cogwit.guide.target.trial_chambers}. */
        public String labelKey() {
            return "cogwit.guide.target." + structure.substring(structure.indexOf(':') + 1);
        }
    }

    private final String id;
    private final Target target;
    private final String coopItem;

    Milestone(String id, Target target, String coopItem) {
        this.id = id;
        this.target = target;
        this.coopItem = coopItem;
    }

    public String id() {
        return id;
    }

    /** The roadmap chapter this step belongs to, from 1. */
    public int chapter() {
        return ordinal() >= CHAPTER_THREE.ordinal() ? 3 : ordinal() >= CHAPTER_TWO.ordinal() ? 2 : 1;
    }

    public String titleKey() {
        return "cogwit.guide.milestone." + id;
    }

    /** Steps whose compass target is the next boss to hunt, picked per player at hint time. */
    public boolean isHunt() {
        return this == FIRST_TROPHY || this == ALL_TROPHIES || this == TWILIGHT_BOSSES || this == WORLD_BOSSES;
    }

    /** Whether a boss from {@code mod} is this step's prey: chapter three's lands have steps of their own. */
    public boolean hunts(String mod) {
        return switch (this) {
            case TWILIGHT_BOSSES -> mod.equals(TWILIGHT);
            case WORLD_BOSSES -> mod.equals(WORLD);
            default -> !mod.equals(TWILIGHT) && !mod.equals(WORLD);
        };
    }

    public Optional<Target> target() {
        return Optional.ofNullable(target);
    }

    /** Item id another player may hold that would finish this step for the asker, if any. */
    public Optional<String> coopItem() {
        return Optional.ofNullable(coopItem);
    }

    public String hintKey(String dimension) {
        boolean travel = target != null && target.travelHint() && !target.dimension().equals(dimension);
        return travel ? "cogwit.guide.hint." + id + ".travel" : "cogwit.guide.hint." + id;
    }
}
