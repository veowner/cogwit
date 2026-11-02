package dev.cogwit.trophy;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class WheelPagesTest {
    private static List<Trophy> allActives() {
        return Arrays.stream(Trophy.values()).filter(t -> t.hasActive).toList();
    }

    @Test
    void fewActivesStayOnOnePage() {
        List<Trophy> few = allActives().subList(0, WheelPages.PER_PAGE);
        List<WheelPages.Page> pages = WheelPages.pages(few, Set.of(Trophy.WITHER));
        assertEquals(1, pages.size());
        assertEquals(WheelPages.Kind.ALL, pages.get(0).kind());
        assertEquals(few, pages.get(0).trophies());
        assertEquals(1, WheelPages.pages(List.of(), Set.of()).size(), "an empty wheel still has its one page");
    }

    @Test
    void everyActiveIsReachableAndNoPageIsCrowded() {
        List<Trophy> all = allActives();
        List<WheelPages.Page> pages = WheelPages.pages(all, Set.of());
        assertEquals(34, all.size());
        List<Trophy> seen = pages.stream().flatMap(p -> p.trophies().stream()).toList();
        assertEquals(all.size(), seen.size(), "without favourites every active shows once");
        assertEquals(Set.copyOf(all), Set.copyOf(seen));
        for (WheelPages.Page p : pages) {
            assertTrue(p.trophies().size() <= WheelPages.PER_PAGE, p.toString());
            assertFalse(p.trophies().isEmpty(), p.toString());
        }
    }

    @Test
    void landsSplitEvenlyInOrder() {
        List<WheelPages.Page> pages = WheelPages.pages(allActives(), Set.of());
        assertEquals(List.of(WheelPages.Kind.CHAPTER_TWO, WheelPages.Kind.CHAPTER_TWO, WheelPages.Kind.TWILIGHT,
                WheelPages.Kind.WORLD), pages.stream().map(WheelPages.Page::kind).toList());
        assertEquals(11, pages.get(0).trophies().size());
        assertEquals(10, pages.get(1).trophies().size());
        assertEquals(1, pages.get(0).part());
        assertEquals(2, pages.get(1).parts());
        assertEquals(8, pages.get(2).trophies().size());
        assertEquals(5, pages.get(3).trophies().size());
        assertEquals(Trophy.NAGA, pages.get(2).trophies().get(0));
    }

    @Test
    void favouritesComeFirstCappedAndOnlyIfOwned() {
        List<Trophy> owned = allActives().stream().filter(t -> t != Trophy.SCYLLA).toList();
        List<WheelPages.Page> pages = WheelPages.pages(owned, Set.of(Trophy.HYDRA, Trophy.WARDEN, Trophy.SCYLLA));
        WheelPages.Page first = pages.get(0);
        assertEquals(WheelPages.Kind.FAVOURITES, first.kind());
        assertEquals(List.of(Trophy.WARDEN, Trophy.HYDRA), first.trophies(), "kept in wheel order, unowned skipped");
        assertTrue(pages.get(1).kind() != WheelPages.Kind.FAVOURITES);

        List<WheelPages.Page> capped = WheelPages.pages(allActives(), Set.copyOf(allActives()));
        assertEquals(WheelPages.MAX_FAVOURITES, capped.get(0).trophies().size());
    }

    @Test
    void turningWraps() {
        assertEquals(1, WheelPages.turn(0, 1, 4));
        assertEquals(3, WheelPages.turn(0, -1, 4));
        assertEquals(0, WheelPages.turn(3, 1, 4));
        assertEquals(0, WheelPages.turn(2, 1, 0));
    }
}
