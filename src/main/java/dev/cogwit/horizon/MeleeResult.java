package dev.cogwit.horizon;

/** Extra damage a melee hit gets from stored mass, and the state after the hit. */
public record MeleeResult(HorizonState state, float bonus) {}
