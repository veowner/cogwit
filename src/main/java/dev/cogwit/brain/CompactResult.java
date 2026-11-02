package dev.cogwit.brain;

public record CompactResult(Status status, BrainState state, String kept, String merged) {
    public enum Status { OK, COOLDOWN, NOT_ENOUGH }
}
