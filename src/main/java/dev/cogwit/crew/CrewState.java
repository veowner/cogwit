package dev.cogwit.crew;

/**
 * What the talisman remembers about the crew. Gear lives in a separate component because it holds item stacks.
 *
 * @param deployed     Cogwit is off the shoulder
 * @param downUntil    per brother (Spark, Rivet, Anvil), when he can fight again
 * @param griefUntil   when Cogwit stops grieving a fallen brother
 */
public record CrewState(boolean deployed, long sparkDownUntil, long rivetDownUntil, long anvilDownUntil,
                        long griefUntil) {
    public static final CrewState EMPTY = new CrewState(false, 0, 0, 0, 0);

    public boolean isDown(Crew.Kind kind, long now) {
        return now < downUntil(kind);
    }

    public long downUntil(Crew.Kind kind) {
        return switch (kind) {
            case COGWIT -> 0;
            case SPARK -> sparkDownUntil;
            case RIVET -> rivetDownUntil;
            case ANVIL -> anvilDownUntil;
        };
    }

    public CrewState withDeployed(boolean v) {
        return new CrewState(v, sparkDownUntil, rivetDownUntil, anvilDownUntil, griefUntil);
    }

    public CrewState withDownUntil(Crew.Kind kind, long v) {
        return switch (kind) {
            case COGWIT -> this;
            case SPARK -> new CrewState(deployed, v, rivetDownUntil, anvilDownUntil, griefUntil);
            case RIVET -> new CrewState(deployed, sparkDownUntil, v, anvilDownUntil, griefUntil);
            case ANVIL -> new CrewState(deployed, sparkDownUntil, rivetDownUntil, v, griefUntil);
        };
    }

    public CrewState withGriefUntil(long v) {
        return new CrewState(deployed, sparkDownUntil, rivetDownUntil, anvilDownUntil, v);
    }
}
