package dev.cogwit.brain;

/** Things Cogwit wants to tell the player about. */
public sealed interface BrainEvent {
    record Learned(String category) implements BrainEvent {}

    record LevelUp(String category, int level) implements BrainEvent {}

    record Forgot(String category) implements BrainEvent {}

    record FellAsleep() implements BrainEvent {}

    /** Cogwit himself reached a new level (see {@link Growth}). */
    record Grew(int level) implements BrainEvent {}
}
