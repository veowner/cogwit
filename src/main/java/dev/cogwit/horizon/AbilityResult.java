package dev.cogwit.horizon;

public record AbilityResult(Status status, HorizonState state) {
    public enum Status { OK, COOLDOWN, NOT_READY }
}
