package dev.cogwit.trophy;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * How the ability wheel's outer ring is split when there are too many trophy actives for one ring: a page of the
 * player's favourites first, then one page per land (the bosses of chapter two, the Twilight Forest, the world bosses),
 * a land with more than {@link #PER_PAGE} actives taking several pages of even size. Few actives stay on one page.
 */
public final class WheelPages {
    /** The most slices the outer ring holds before its labels stop being readable. */
    public static final int PER_PAGE = 12;
    /** The most trophies that can be marked as favourites. */
    public static final int MAX_FAVOURITES = PER_PAGE;
    public static final String TWILIGHT = "twilightforest", WORLD = "block_factorys_bosses";

    public enum Kind {
        ALL, FAVOURITES, CHAPTER_TWO, TWILIGHT, WORLD;

        public String key() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    /** One page: what it holds, which part of its land it is (1-based) and how many parts the land has. */
    public record Page(Kind kind, int part, int parts, List<Trophy> trophies) {}

    private WheelPages() {}

    /** The land a boss belongs to on the wheel. */
    public static Kind land(Trophy t) {
        return switch (t.mod()) {
            case TWILIGHT -> Kind.TWILIGHT;
            case WORLD -> Kind.WORLD;
            default -> Kind.CHAPTER_TWO;
        };
    }

    /**
     * The pages for the trophy actives the player has, in the order given. Favourites the player does not own are
     * skipped. Always at least one page, which may be empty.
     */
    public static List<Page> pages(List<Trophy> actives, Collection<Trophy> favourites) {
        List<Page> out = new ArrayList<>();
        if (actives.size() <= PER_PAGE) {
            out.add(new Page(Kind.ALL, 1, 1, List.copyOf(actives)));
            return out;
        }
        Set<Trophy> fav = Set.copyOf(favourites);
        List<Trophy> picked = actives.stream().filter(fav::contains).limit(MAX_FAVOURITES).toList();
        if (!picked.isEmpty()) {
            out.add(new Page(Kind.FAVOURITES, 1, 1, picked));
        }
        for (Kind kind : new Kind[] {Kind.CHAPTER_TWO, Kind.TWILIGHT, Kind.WORLD}) {
            List<Trophy> land = actives.stream().filter(t -> land(t) == kind).toList();
            int parts = (land.size() + PER_PAGE - 1) / PER_PAGE;
            for (int p = 0; p < parts; p++) {
                // even split: 21 actives make pages of 11 and 10, not 12 and 9
                int from = (land.size() * p + parts - 1) / parts, to = (land.size() * (p + 1) + parts - 1) / parts;
                out.add(new Page(kind, p + 1, parts, land.subList(from, to)));
            }
        }
        return out;
    }

    /** The page a step of {@code delta} away from {@code current}, wrapping around. */
    public static int turn(int current, int delta, int pages) {
        return pages <= 0 ? 0 : Math.floorMod(current + delta, pages);
    }
}
