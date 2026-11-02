package dev.cogwit.guide;

/** Everything the progression rules need to know about a player, already boiled down to numbers. */
public record GuideFacts(boolean hasCogwit, boolean wornCogwit, int wornTokens, boolean netheriteArmor,
                         boolean heavyCore, int echoShards, boolean singularityCore, boolean horizonTemplate,
                         boolean horizonPiece, boolean fullSet, boolean tokenCell,
                         int cellsInserted, int cogwitLevel, int trophies, int trophiesAvailable,
                         boolean twilightVisited, int twilightTrophies, int twilightAvailable,
                         int worldTrophies, int worldAvailable) {
    public static final GuideFacts NOTHING = new GuideFacts(false, false, 0, false, false, 0, false, false, false, false, false,
            0, 0, 0, 0, false, 0, 0, 0, 0);

    public GuideFacts withCogwit(boolean worn, int tokens) {
        return new GuideFacts(true, worn, tokens, netheriteArmor, heavyCore, echoShards, singularityCore,
                horizonTemplate, horizonPiece, fullSet, tokenCell,
                cellsInserted, cogwitLevel, trophies, trophiesAvailable, twilightVisited, twilightTrophies, twilightAvailable, worldTrophies, worldAvailable);
    }

    public GuideFacts withNetherite() {
        return new GuideFacts(hasCogwit, wornCogwit, wornTokens, true, heavyCore, echoShards, singularityCore,
                horizonTemplate, horizonPiece, fullSet, tokenCell,
                cellsInserted, cogwitLevel, trophies, trophiesAvailable, twilightVisited, twilightTrophies, twilightAvailable, worldTrophies, worldAvailable);
    }

    public GuideFacts withHeavyCore() {
        return new GuideFacts(hasCogwit, wornCogwit, wornTokens, netheriteArmor, true, echoShards, singularityCore,
                horizonTemplate, horizonPiece, fullSet, tokenCell,
                cellsInserted, cogwitLevel, trophies, trophiesAvailable, twilightVisited, twilightTrophies, twilightAvailable, worldTrophies, worldAvailable);
    }

    public GuideFacts withEchoShards(int count) {
        return new GuideFacts(hasCogwit, wornCogwit, wornTokens, netheriteArmor, heavyCore, count, singularityCore,
                horizonTemplate, horizonPiece, fullSet, tokenCell,
                cellsInserted, cogwitLevel, trophies, trophiesAvailable, twilightVisited, twilightTrophies, twilightAvailable, worldTrophies, worldAvailable);
    }

    public GuideFacts withSingularityCore() {
        return new GuideFacts(hasCogwit, wornCogwit, wornTokens, netheriteArmor, heavyCore, echoShards, true,
                horizonTemplate, horizonPiece, fullSet, tokenCell,
                cellsInserted, cogwitLevel, trophies, trophiesAvailable, twilightVisited, twilightTrophies, twilightAvailable, worldTrophies, worldAvailable);
    }

    public GuideFacts withTemplate() {
        return new GuideFacts(hasCogwit, wornCogwit, wornTokens, netheriteArmor, heavyCore, echoShards,
                singularityCore, true, horizonPiece, fullSet, tokenCell,
                cellsInserted, cogwitLevel, trophies, trophiesAvailable, twilightVisited, twilightTrophies, twilightAvailable, worldTrophies, worldAvailable);
    }

    public GuideFacts withHorizonPiece() {
        return new GuideFacts(hasCogwit, wornCogwit, wornTokens, netheriteArmor, heavyCore, echoShards,
                singularityCore, horizonTemplate, true, fullSet, tokenCell,
                cellsInserted, cogwitLevel, trophies, trophiesAvailable, twilightVisited, twilightTrophies, twilightAvailable, worldTrophies, worldAvailable);
    }

    public GuideFacts withCells(boolean carriesCell, int inserted) {
        return new GuideFacts(hasCogwit, wornCogwit, wornTokens, netheriteArmor, heavyCore, echoShards,
                singularityCore, horizonTemplate, horizonPiece, fullSet, carriesCell, inserted,
                cogwitLevel, trophies, trophiesAvailable, twilightVisited, twilightTrophies, twilightAvailable, worldTrophies, worldAvailable);
    }

    public GuideFacts withFullSet() {
        return new GuideFacts(hasCogwit, wornCogwit, wornTokens, netheriteArmor, heavyCore, echoShards,
                singularityCore, horizonTemplate, true, true, tokenCell,
                cellsInserted, cogwitLevel, trophies, trophiesAvailable, twilightVisited, twilightTrophies, twilightAvailable, worldTrophies, worldAvailable);
    }

    public GuideFacts withLevel(int level) {
        return new GuideFacts(hasCogwit, wornCogwit, wornTokens, netheriteArmor, heavyCore, echoShards,
                singularityCore, horizonTemplate, horizonPiece, fullSet, tokenCell,
                cellsInserted, level, trophies, trophiesAvailable, twilightVisited, twilightTrophies, twilightAvailable, worldTrophies, worldAvailable);
    }

    public GuideFacts withTrophies(int owned, int available) {
        return new GuideFacts(hasCogwit, wornCogwit, wornTokens, netheriteArmor, heavyCore, echoShards,
                singularityCore, horizonTemplate, horizonPiece, fullSet, tokenCell,
                cellsInserted, cogwitLevel, owned, available, twilightVisited, twilightTrophies, twilightAvailable, worldTrophies, worldAvailable);
    }

    public GuideFacts withTwilight(boolean visited, int owned, int available) {
        return new GuideFacts(hasCogwit, wornCogwit, wornTokens, netheriteArmor, heavyCore, echoShards,
                singularityCore, horizonTemplate, horizonPiece, fullSet, tokenCell,
                cellsInserted, cogwitLevel, trophies, trophiesAvailable, visited, owned, available, worldTrophies,
                worldAvailable);
    }

    public GuideFacts withWorldBosses(int owned, int available) {
        return new GuideFacts(hasCogwit, wornCogwit, wornTokens, netheriteArmor, heavyCore, echoShards,
                singularityCore, horizonTemplate, horizonPiece, fullSet, tokenCell,
                cellsInserted, cogwitLevel, trophies, trophiesAvailable, twilightVisited, twilightTrophies,
                twilightAvailable, owned, available);
    }
}
