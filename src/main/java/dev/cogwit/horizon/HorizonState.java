package dev.cogwit.horizon;

import java.util.List;

/** Per-player state of the Event Horizon armor. Immutable: every change returns a new state. */
public record HorizonState(float mass, long lastCombat, long blinkReadyAt, long wellReadyAt, long wellUntil,
                           long voidRescueReadyAt, List<String> tipsSeen) {
    public static final HorizonState EMPTY = new HorizonState(0f, 0, 0, 0, 0, 0, List.of());

    public HorizonState {
        tipsSeen = List.copyOf(tipsSeen);
    }

    public boolean hasSeenTip(String tip) {
        return tipsSeen.contains(tip);
    }

    public HorizonState withTip(String tip) {
        if (hasSeenTip(tip)) {
            return this;
        }
        List<String> tips = new java.util.ArrayList<>(tipsSeen);
        tips.add(tip);
        return new HorizonState(mass, lastCombat, blinkReadyAt, wellReadyAt, wellUntil, voidRescueReadyAt, tips);
    }

    public HorizonState withMass(float v) {
        return new HorizonState(v, lastCombat, blinkReadyAt, wellReadyAt, wellUntil, voidRescueReadyAt, tipsSeen);
    }

    public HorizonState withLastCombat(long v) {
        return new HorizonState(mass, v, blinkReadyAt, wellReadyAt, wellUntil, voidRescueReadyAt, tipsSeen);
    }

    public HorizonState withBlinkReadyAt(long v) {
        return new HorizonState(mass, lastCombat, v, wellReadyAt, wellUntil, voidRescueReadyAt, tipsSeen);
    }

    public HorizonState withWell(long readyAt, long until) {
        return new HorizonState(mass, lastCombat, blinkReadyAt, readyAt, until, voidRescueReadyAt, tipsSeen);
    }

    public HorizonState withVoidRescueReadyAt(long v) {
        return new HorizonState(mass, lastCombat, blinkReadyAt, wellReadyAt, wellUntil, v, tipsSeen);
    }
}
