package dev.cogwit.cells;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Token cell rules, free of Minecraft classes. A piece of armor holds a list of cell charges,
 * one entry per inserted cell; Cogwit's own tank is topped up from them and overflow fills them.
 */
public final class Cells {
    public static final int CAPACITY = 2500;
    public static final int SOCKETS_HEAD = 1;
    public static final int SOCKETS_CHEST = 4;
    public static final int SOCKETS_LEGS = 2;
    public static final int SOCKETS_FEET = 1;

    public static final int REDSTONE_TOKENS = 10;
    public static final int AMETHYST_TOKENS = 40;
    public static final float TOKENS_PER_MASS = 5f;

    public record Popped(List<Integer> remaining, int charge) {}

    public record Charged(List<Integer> cells, int leftover) {}

    public record Drained(List<Integer> cells, int taken) {}

    public record TopUp(int ownTokens, List<Integer> cells) {}

    public record Fed(int ownTokens, List<Integer> cells) {}

    private Cells() {}

    public static Optional<List<Integer>> insert(List<Integer> cells, int sockets, int charge) {
        if (cells.size() >= sockets) {
            return Optional.empty();
        }
        List<Integer> out = new ArrayList<>(cells);
        out.add(Math.max(0, Math.min(CAPACITY, charge)));
        return Optional.of(List.copyOf(out));
    }

    public static Optional<Popped> pop(List<Integer> cells) {
        if (cells.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new Popped(List.copyOf(cells.subList(0, cells.size() - 1)), cells.get(cells.size() - 1)));
    }

    public static int total(List<Integer> cells) {
        return cells.stream().mapToInt(Integer::intValue).sum();
    }

    public static int capacity(int ownMax, int cellCount) {
        return ownMax + cellCount * CAPACITY;
    }

    /** Fills cells in order; whatever doesn't fit comes back as leftover. */
    public static Charged charge(List<Integer> cells, int amount) {
        List<Integer> out = new ArrayList<>(cells);
        int left = amount;
        for (int i = 0; i < out.size() && left > 0; i++) {
            int add = Math.min(CAPACITY - out.get(i), left);
            out.set(i, out.get(i) + add);
            left -= add;
        }
        return new Charged(List.copyOf(out), left);
    }

    /** Takes up to {@code amount} tokens from the cells, in order. */
    public static Drained drain(List<Integer> cells, int amount) {
        List<Integer> out = new ArrayList<>(cells);
        int taken = 0;
        for (int i = 0; i < out.size() && taken < amount; i++) {
            int take = Math.min(out.get(i), amount - taken);
            out.set(i, out.get(i) - take);
            taken += take;
        }
        return new Drained(List.copyOf(out), taken);
    }

    /** Refills Cogwit's own tank from the cells. */
    public static TopUp topUp(int ownTokens, int ownMax, List<Integer> cells) {
        if (ownTokens >= ownMax) {
            return new TopUp(ownTokens, cells);
        }
        Drained d = drain(cells, ownMax - ownTokens);
        return new TopUp(ownTokens + d.taken(), d.cells());
    }

    /** Food goes to Cogwit first; once he's full the rest charges the cells. */
    public static Fed feed(int ownTokens, int ownMax, List<Integer> cells, int amount) {
        int toCogwit = Math.min(ownMax - ownTokens, amount);
        Charged c = charge(cells, amount - Math.max(0, toCogwit));
        return new Fed(ownTokens + Math.max(0, toCogwit), c.cells());
    }

    public static List<Integer> lightning(List<Integer> cells) {
        return cells.stream().map(c -> CAPACITY).toList();
    }

    public static int tokensFromMass(float overflow) {
        return Math.round(overflow * TOKENS_PER_MASS);
    }
}
