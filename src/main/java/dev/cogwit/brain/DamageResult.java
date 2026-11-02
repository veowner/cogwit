package dev.cogwit.brain;

import java.util.List;

/** Outcome of one hit: the new memory, how much of the damage Cogwit blocked, and what to say. */
public record DamageResult(BrainState state, float reduction, List<BrainEvent> events) {}
