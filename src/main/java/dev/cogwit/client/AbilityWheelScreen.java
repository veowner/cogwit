package dev.cogwit.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.Window;
import dev.cogwit.ClientConfig;
import dev.cogwit.brain.Brain;
import dev.cogwit.trophy.Trophy;
import dev.cogwit.trophy.WheelPages;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The ability wheel: hold the key, point at a slice, let go to use it. A click works too. It offers everything
 * the player has unlocked: Cogwit's actions, the horizon armor's and the trophy actives. Up to eight entries
 * share one ring; more split into an inner ring (Cogwit and armor) and an outer ring (trophies). When the trophies
 * are too many for one ring, the outer ring turns pages ({@link WheelPages}): favourites, then each land, flipped with
 * the mouse wheel, Tab or the pad's shoulder buttons. Rings are painted row by row, so every slice is a true sector
 * with a clock sweep for its recharge.
 */
public class AbilityWheelScreen extends Screen {
    private static final int OUTER = 92, INNER = 34, MAX_CENTRE_LINES = 5;
    /** Entries above this count switch to two rings. */
    private static final int SINGLE_RING_MAX = 8;
    /** Crowded layout: the hub, the inner ring's outer edge, the outer ring's start and its edge. */
    private static final int CROWD_HUB = 28, CROWD_MID = 66, CROWD_GAP = 3, CROWD_OUTER = 104;
    /** Text scale and line count for the labels inside slices. */
    private static final float CROWD_TEXT = 0.75f;
    private static final double SEAM = 2.0, EDGE = 1.2;
    /** Height of the page bar under the wheel: name, dots and the hint. */
    private static final int PAGE_BAR = 32;

    private static final int PANEL = 0x141416, ORANGE = 0xC8803F, VIOLET = 0xC77DFF, TEXT = 0xE8E8EA,
            TEXT_DIM = 0x8A8A8F, GREEN = 0x7CE07C, YELLOW = 0xFFD27A, OPAQUE = 0xFF000000;

    /** A band of slices: entries {@code first} to {@code first + count - 1}, between two radii. */
    private record Ring(int first, int count, float inner, float outer) {
        double step() {
            return Math.PI * 2 / Math.max(1, count);
        }
    }

    /** The page the wheel was last left on, so it opens there again while that page still exists. */
    private static WheelPages.Kind lastKind = WheelPages.Kind.FAVOURITES;
    private static int lastPart = 1;

    private final KeyMapping holdKey;
    private List<TrophyUi.WheelEntry> entries = List.of();
    private List<WheelPages.Page> pages = List.of();
    private int page;
    private boolean leftShoulder, rightShoulder;
    private List<Ring> rings = List.of();
    private boolean crowded;
    private int cx, cy;
    /** The radius of the wheel's edge and of its hub, in screen pixels. */
    private float outer = OUTER, hub = INNER;

    public AbilityWheelScreen(KeyMapping holdKey) {
        super(Component.translatable("cogwit.wheel.title"));
        this.holdKey = holdKey;
    }

    @Override
    protected void init() {
        refresh();
    }

    /** Reads the entries again and rebuilds the pages and rings, since the set can change while the wheel is open. */
    private void refresh() {
        List<TrophyUi.WheelEntry> shown = new ArrayList<>();
        Map<Trophy, TrophyUi.WheelEntry> trophies = new LinkedHashMap<>();
        for (TrophyUi.WheelEntry e : TrophyUi.wheel()) {
            Trophy t = e.trophy() ? Trophy.byKey(e.id()).orElse(null) : null;
            if (t == null) {
                shown.add(e);
            } else {
                trophies.put(t, e);
            }
        }
        pages = WheelPages.pages(List.copyOf(trophies.keySet()), ClientConfig.favourites());
        page = 0;
        for (int i = 0; i < pages.size(); i++) {
            WheelPages.Page p = pages.get(i);
            if (p.kind() == lastKind && p.part() == Math.min(lastPart, p.parts())) {
                page = i;
            }
        }
        for (Trophy t : pages.get(page).trophies()) {
            shown.add(trophies.get(t));
        }
        entries = shown;
        layout();
    }

    /** Flips the outer ring by {@code delta} pages and remembers where it was left. */
    private void turn(int delta) {
        if (pages.size() < 2) {
            return;
        }
        WheelPages.Page p = pages.get(WheelPages.turn(page, delta, pages.size()));
        lastKind = p.kind();
        lastPart = p.part();
        refresh();
        Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance
                .forUI(net.minecraft.sounds.SoundEvents.BOOK_PAGE_TURN, 1.4f, 0.6f));
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (scrollY != 0) {
            turn(scrollY < 0 ? 1 : -1);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_TAB) {
            turn(hasShiftDown() ? -1 : 1);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    /** The pad's shoulder buttons flip pages on the press, not while held. */
    private void padPages() {
        boolean left = GamepadWheel.buttonDown("left_shoulder", GLFW.GLFW_GAMEPAD_BUTTON_LEFT_BUMPER);
        boolean right = GamepadWheel.buttonDown("right_shoulder", GLFW.GLFW_GAMEPAD_BUTTON_RIGHT_BUMPER);
        if (left && !leftShoulder) {
            turn(-1);
        }
        if (right && !rightShoulder) {
            turn(1);
        }
        leftShoulder = left;
        rightShoulder = right;
    }

    /**
     * Centres the wheel, picks one or two rings and scales it down on a small screen, leaving room for the title and,
     * when there are pages, for the page bar underneath.
     */
    private void layout() {
        int n = entries.size();
        int cogwit = (int) entries.stream().filter(e -> !e.trophy()).count();
        boolean split = n > SINGLE_RING_MAX && cogwit > 0 && cogwit < n;
        crowded = n > SINGLE_RING_MAX;
        int extent = crowded ? CROWD_OUTER : OUTER;
        int bar = pages.size() > 1 ? PAGE_BAR : 0;
        cx = width / 2;
        cy = height / 2 + 6 - bar / 2;
        float scale = Math.max(0.5f, Math.min(1f, Math.min(width / 2f - 4, height / 2f - 10 - bar / 2f) / extent));
        outer = extent * scale;
        if (split) {
            hub = CROWD_HUB * scale;
            rings = List.of(new Ring(0, cogwit, hub, CROWD_MID * scale),
                    new Ring(cogwit, n - cogwit, (CROWD_MID + CROWD_GAP) * scale, outer));
        } else if (crowded) {
            hub = CROWD_MID * scale;
            rings = List.of(new Ring(0, n, hub, outer));
        } else {
            hub = INNER * scale;
            rings = List.of(new Ring(0, n, hub, outer));
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void tick() {
        refresh();
        padPages();
        if (isHeld()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        Window window = mc.getWindow();
        double mx = mc.mouseHandler.xpos() * window.getGuiScaledWidth() / window.getScreenWidth();
        double my = mc.mouseHandler.ypos() * window.getGuiScaledHeight() / window.getScreenHeight();
        chooseAt(mx, my);
    }

    /** Whether the key that opened the wheel is still down, or a gamepad button when it was opened from a pad. */
    private boolean isHeld() {
        if (GamepadWheel.anyButtonHeld()) {
            return true;
        }
        InputConstants.Key key = holdKey.getKey();
        int code = key.getValue();
        if (code < 0) {
            return false;
        }
        long window = Minecraft.getInstance().getWindow().getWindow();
        if (key.getType() == InputConstants.Type.MOUSE) {
            return GLFW.glfwGetMouseButton(window, code) == GLFW.GLFW_PRESS;
        }
        return InputConstants.isKeyDown(window, code);
    }

    /** Closes the wheel, then uses the ready entry under the pointer; closing first lets an entry open a screen. */
    private void chooseAt(double mx, double my) {
        int hot = hoverAt(mx, my);
        String pick = hot >= 0 && hot < entries.size() && entries.get(hot).ready() ? entries.get(hot).id() : null;
        onClose();
        if (pick != null) {
            TrophyUi.cast(pick);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            chooseAt(mouseX, mouseY);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    /** The slice the pointer is aiming at, or -1 while it is still inside the hub. */
    private int hoverAt(double mx, double my) {
        if (entries.isEmpty()) {
            return -1;
        }
        double dx = mx - cx, dy = my - cy;
        double d = Math.sqrt(dx * dx + dy * dy);
        if (d < hub) {
            return -1;
        }
        Ring ring = rings.get(0);
        if (rings.size() > 1 && d >= (ring.outer() + rings.get(1).inner()) / 2) {
            ring = rings.get(1);
        }
        double step = ring.step();
        return ring.first() + (int) ((angle(dx, dy) + step / 2) / step) % ring.count();
    }

    /** Clockwise from straight up, 0 to 2 pi. */
    private static double angle(double dx, double dy) {
        double a = Math.atan2(dx, -dy);
        return a < 0 ? a + Math.PI * 2 : a;
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, width, height, 0x50000000);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        GamepadWheel.steer(cx, cy, hub + 4, outer - 4);
        super.render(g, mouseX, mouseY, partialTick);
        int hot = hoverAt(mouseX, mouseY);
        g.drawCenteredString(font, title, cx, cy - Math.round(outer) - 14, ORANGE | OPAQUE);
        paintRings(g, hot);
        hub(g, hot);
        labels(g, hot);
        pageBar(g);
    }

    /** Under the wheel: the page's name between arrows, a dot per page and how to flip. Nothing when there is one page. */
    private void pageBar(GuiGraphics g) {
        if (pages.size() < 2) {
            return;
        }
        WheelPages.Page p = pages.get(page);
        Component name = Component.translatable("cogwit.wheel.page." + p.kind().key());
        if (p.parts() > 1) {
            name = Component.translatable("cogwit.wheel.page.part", name, p.part(), p.parts());
        }
        int y = cy + Math.round(outer) + 6;
        String arrows = "‹  " + name.getString() + "  ›";
        g.drawCenteredString(font, arrows, cx, y, VIOLET | OPAQUE);
        int dots = pages.size(), gap = 7, x0 = cx - (dots - 1) * gap / 2;
        for (int i = 0; i < dots; i++) {
            int colour = i == page ? ORANGE : TEXT_DIM;
            int r = i == page ? 2 : 1;
            g.fill(x0 + i * gap - r, y + 13 - r, x0 + i * gap + r + 1, y + 13 + r + 1, colour | OPAQUE);
        }
        g.drawCenteredString(font, Component.translatable("cogwit.wheel.page.hint"), cx, y + 19, TEXT_DIM | OPAQUE);
    }

    // ring

    /** Paints the whole ring row by row, merging neighbouring cells of one kind into a single fill. */
    private void paintRings(GuiGraphics g, int hot) {
        int r = (int) Math.ceil(outer);
        for (int dy = -r; dy < r; dy++) {
            int start = -r, kind = -1;
            for (int dx = -r; dx <= r; dx++) {
                int now = dx == r ? -1 : cell(dx + 0.5, dy + 0.5, hot);
                if (now != kind) {
                    if (kind >= 0) {
                        g.fill(cx + start, cy + dy, cx + dx, cy + dy + 1, cellColour(kind, hot));
                    }
                    start = dx;
                    kind = now;
                }
            }
        }
    }

    /** Which slice a point of the wheel belongs to and how to paint it, or -1 for the seams, the gap and outside. */
    private int cell(double px, double py, int hot) {
        double d = Math.sqrt(px * px + py * py);
        Ring ring = null;
        for (Ring r : rings) {
            if (d >= r.inner() && d <= r.outer()) {
                ring = r;
            }
        }
        if (ring == null) {
            return -1;
        }
        int n = Math.max(1, ring.count());
        double step = ring.step();
        double shifted = angle(px, py) + step / 2;
        int slice = (int) (shifted / step) % n;
        double within = shifted - Math.floor(shifted / step) * step;
        boolean edge = false;
        if (n > 1) {
            double toSeam = Math.min(within, step - within) * d;
            if (toSeam < SEAM / 2) {
                return -1;
            }
            edge = toSeam < SEAM / 2 + EDGE;
        }
        if (entries.isEmpty()) {
            return 0;
        }
        int index = ring.first() + slice;
        if (index == hot && (edge || d < ring.inner() + 1.5 || d > ring.outer() - 1.5)) {
            return index * 3 + 2;
        }
        boolean cooling = within / step >= 1 - entries.get(index).cooldown();
        return index * 3 + (cooling ? 1 : 0);
    }

    private int cellColour(int kind, int hot) {
        if (entries.isEmpty()) {
            return mix(PANEL, TEXT_DIM, 0.12f) | 0x90000000;
        }
        TrophyUi.WheelEntry e = entries.get(kind / 3);
        boolean lit = kind / 3 == hot;
        return switch (kind % 3) {
            case 2 -> e.colour() | OPAQUE;
            case 1 -> mix(PANEL, e.colour(), lit ? 0.10f : 0.04f) | 0xD0000000;
            default -> mix(PANEL, e.colour(), lit ? (e.ready() ? 0.50f : 0.30f) : 0.22f) | 0xC0000000;
        };
    }

    // hub and labels

    private void hub(GuiGraphics g, int hot) {
        int accent = hot >= 0 && hot < entries.size() ? entries.get(hot).colour() : VIOLET;
        annulus(g, hub - 3, 0, 0xD0000000 | PANEL);
        annulus(g, hub - 2, hub - 3, 0xB0000000 | accent);

        int wrap = Math.max(40, Math.round(hub * 2 - 8));
        List<FormattedCharSequence> lines = new ArrayList<>();
        List<Integer> colours = new ArrayList<>();
        if (entries.isEmpty()) {
            add(lines, colours, Component.translatable("cogwit.wheel.empty"), wrap, TEXT_DIM);
        } else if (hot >= 0 && hot < entries.size()) {
            TrophyUi.WheelEntry e = entries.get(hot);
            add(lines, colours, e.name(), wrap, TEXT);
            Component state = e.ready() ? Component.translatable("cogwit.hud.ready")
                    : Component.literal(Brain.clock(e.ticksLeft()));
            add(lines, colours, state, wrap, e.ready() ? GREEN : YELLOW);
        } else {
            add(lines, colours, Component.translatable("cogwit.wheel.hint"), wrap, TEXT_DIM);
        }
        int count = Math.min(lines.size(), MAX_CENTRE_LINES);
        int y = cy - count * font.lineHeight / 2;
        for (int i = 0; i < count; i++) {
            FormattedCharSequence line = lines.get(i);
            g.drawString(font, line, cx - font.width(line) / 2, y + i * font.lineHeight, colours.get(i) | OPAQUE,
                    false);
        }
    }

    private void add(List<FormattedCharSequence> lines, List<Integer> colours, Component text, int width, int colour) {
        for (FormattedCharSequence line : font.split(text, width)) {
            lines.add(line);
            colours.add(colour);
        }
    }

    /** A flat disc or ring around the centre, from {@code from} down to {@code to} pixels. */
    private void annulus(GuiGraphics g, float from, float to, int colour) {
        int r = (int) Math.ceil(from);
        for (int dy = -r; dy < r; dy++) {
            double y = dy + 0.5;
            if (Math.abs(y) >= from) {
                continue;
            }
            int hi = (int) Math.round(Math.sqrt(from * from - y * y));
            if (Math.abs(y) < to) {
                int lo = (int) Math.round(Math.sqrt(to * to - y * y));
                g.fill(cx - hi, cy + dy, cx - lo, cy + dy + 1, colour);
                g.fill(cx + lo, cy + dy, cx + hi, cy + dy + 1, colour);
            } else {
                g.fill(cx - hi, cy + dy, cx + hi, cy + dy + 1, colour);
            }
        }
    }

    private void labels(GuiGraphics g, int hot) {
        for (Ring ring : rings) {
            if (ring.count() == 0) {
                continue;
            }
            double step = ring.step();
            double mid = (ring.inner() + ring.outer()) / 2;
            float textScale = crowded ? CROWD_TEXT : 1f;
            // a label is as wide as the slice is across and as the ring is thick, minus a little air
            double across = ring.count() == 1 ? 64 : 2 * mid * Math.sin(Math.min(step / 2, Math.PI / 2));
            int budget = (int) Math.max(20, Math.min(64, Math.min(across, ring.outer() - ring.inner() + 2) - 4));
            for (int k = 0; k < ring.count(); k++) {
                int i = ring.first() + k;
                int x = cx + (int) Math.round(Math.sin(k * step) * mid);
                int y = cy - (int) Math.round(Math.cos(k * step) * mid);
                label(g, entries.get(i), x, y, budget, textScale, i == hot);
            }
        }
    }

    /** The name over the clock when it is recharging; the name takes a second line when it is ready. */
    private void label(GuiGraphics g, TrophyUi.WheelEntry e, int x, int y, int budget, float scale, boolean hot) {
        boolean cooling = !e.ready();
        List<String> lines = wrap(e.name().getString(), Math.round(budget / scale), cooling ? 1 : 2);
        if (cooling) {
            lines.add(Brain.clock(e.ticksLeft()));
        }
        float lineHeight = (font.lineHeight + 1) * scale;
        float top = y - lines.size() * lineHeight / 2;
        int nameColour = (cooling && !hot ? TEXT_DIM : TEXT) | OPAQUE;
        g.pose().pushPose();
        g.pose().translate(x, top, 0);
        g.pose().scale(scale, scale, 1f);
        for (int i = 0; i < lines.size(); i++) {
            boolean clock = cooling && i == lines.size() - 1;
            g.drawCenteredString(font, lines.get(i), 0, i * (font.lineHeight + 1),
                    clock ? YELLOW | OPAQUE : nameColour);
        }
        g.pose().popPose();
    }

    /** Breaks {@code text} at spaces into at most {@code maxLines} lines of {@code width} pixels, ending in "..." if cut. */
    private List<String> wrap(String text, int width, int maxLines) {
        String[] words = text.split(" ");
        List<String> out = new ArrayList<>();
        int start = 0;
        while (start < words.length) {
            if (out.size() == maxLines - 1) {
                out.add(trim(String.join(" ", java.util.Arrays.copyOfRange(words, start, words.length)), width));
                break;
            }
            int end = start + 1;
            String line = words[start];
            while (end < words.length && font.width(line + " " + words[end]) <= width) {
                line += " " + words[end++];
            }
            out.add(trim(line, width));
            start = end;
        }
        return out;
    }

    private String trim(String text, int maxWidth) {
        if (font.width(text) <= maxWidth) {
            return text;
        }
        return font.plainSubstrByWidth(text, maxWidth - font.width("...")) + "...";
    }

    private static int mix(int from, int to, float t) {
        int r = Math.round((from >> 16 & 0xFF) * (1 - t) + (to >> 16 & 0xFF) * t);
        int gr = Math.round((from >> 8 & 0xFF) * (1 - t) + (to >> 8 & 0xFF) * t);
        int b = Math.round((from & 0xFF) * (1 - t) + (to & 0xFF) * t);
        return r << 16 | gr << 8 | b;
    }
}
