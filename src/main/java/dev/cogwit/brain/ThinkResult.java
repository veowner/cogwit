package dev.cogwit.brain;

public record ThinkResult(Status status, BrainState state) {
    public enum Status { OK, COOLDOWN, NO_TOKENS, ASLEEP }
}
