package dev.cogwit.cells;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CellsTest {
    @Test
    void socketsPerPieceAddUpToEight() {
        assertEquals(8, Cells.SOCKETS_HEAD + Cells.SOCKETS_CHEST + Cells.SOCKETS_LEGS + Cells.SOCKETS_FEET);
    }

    @Test
    void insertFillsFreeSocketsOnly() {
        List<Integer> one = Cells.insert(List.of(), 1, 900).orElseThrow();
        assertEquals(List.of(900), one);
        assertTrue(Cells.insert(one, 1, 100).isEmpty());
    }

    @Test
    void popTakesTheLastCellOut() {
        Cells.Popped p = Cells.pop(List.of(10, 20, 30)).orElseThrow();
        assertEquals(30, p.charge());
        assertEquals(List.of(10, 20), p.remaining());
        assertTrue(Cells.pop(List.of()).isEmpty());
    }

    @Test
    void chargeFillsCellsInOrderAndReportsLeftover() {
        Cells.Charged c = Cells.charge(List.of(2400, 0), 300);
        assertEquals(List.of(2500, 200), c.cells());
        assertEquals(0, c.leftover());
        Cells.Charged full = Cells.charge(List.of(2500, 2490), 50);
        assertEquals(List.of(2500, 2500), full.cells());
        assertEquals(40, full.leftover());
    }

    @Test
    void drainTakesWhatItCan() {
        Cells.Drained d = Cells.drain(List.of(100, 500), 300);
        assertEquals(300, d.taken());
        assertEquals(List.of(0, 300), d.cells());
        assertEquals(600, Cells.drain(List.of(100, 500), 10_000).taken());
    }

    @Test
    void topUpRefillsCogwitFromCells() {
        Cells.TopUp t = Cells.topUp(1500, 2000, List.of(2500, 2500));
        assertEquals(2000, t.ownTokens());
        assertEquals(4500, Cells.total(t.cells()));
        Cells.TopUp none = Cells.topUp(2000, 2000, List.of(100));
        assertEquals(List.of(100), none.cells());
    }

    @Test
    void feedingFillsCogwitFirstThenCells() {
        Cells.Fed f = Cells.feed(1990, 2000, List.of(0, 0), 60);
        assertEquals(2000, f.ownTokens());
        assertEquals(List.of(50, 0), f.cells());
    }

    @Test
    void capacityGrowsWithInsertedCells() {
        assertEquals(2000, Cells.capacity(2000, 0));
        assertEquals(22_000, Cells.capacity(2000, 8));
    }

    @Test
    void lightningFillsEveryCell() {
        assertEquals(List.of(2500, 2500), Cells.lightning(List.of(0, 1234)));
    }

    @Test
    void massOverflowBecomesTokens() {
        assertEquals(15, Cells.tokensFromMass(3f));
        assertEquals(0, Cells.tokensFromMass(0f));
    }

    @Test
    void fuelValues() {
        assertEquals(10, Cells.REDSTONE_TOKENS);
        assertEquals(40, Cells.AMETHYST_TOKENS);
    }
}
