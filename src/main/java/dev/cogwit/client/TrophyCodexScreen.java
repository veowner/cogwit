package dev.cogwit.client;

import dev.cogwit.ClientConfig;
import dev.cogwit.client.menu.MenuTabs;
import dev.cogwit.trophy.WheelPages;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * The trophy codex: every boss of the pack as a tile, lit once beaten, with what each one grants and where to find it.
 * Right click or F stars an owned active for the ability wheel's favourites page.
 */
public class TrophyCodexScreen extends Screen {
    private static final int W = 330, H = 220, PAD = 8, COLS = 6, TILE_GAP = 3, SEPARATION = 12, MIN_TILE = 12;
    private static final int HEADER_BAR_Y = 19, BODY_Y = 30, SCROLL_STEP = 12;

    private static final int BACKGROUND = 0xC0141416, ORANGE = 0xFFC8803F, VIOLET = 0xFFC77DFF, VIOLET_DEEP = 0xFF9B4DDB,
            TEXT = 0xFFE8E8EA, TEXT_DIM = 0xFF8A8A8F, YELLOW = 0xFFFFD27A, TRACK = 0xFF2C2C31, TILE_EMPTY = 0xFF1E1E22,
            TILE_OFF = 0xFF18181A, CROSS = 0xFF55555A, FINAL = 0xFF8A6FB3;

    /** One wrapped line of the detail pane. */
    private record Row(FormattedCharSequence text, int colour, int gapBefore) {}

    private List<TrophyUi.CodexEntry> entries = List.of();
    private int selected;
    private int scroll, maxScroll;
    /** The footer text wrapped for {@link #footerWrap} pixels, since the tile size asks for it on every tile. */
    private List<FormattedCharSequence> footer;
    private int footerWrap;
    private Set<dev.cogwit.trophy.Trophy> favourites = Set.of();
    /** Ticks left to show that the favourites are full after a star was refused. */
    private int fullFlash;

    public TrophyCodexScreen() {
        super(Component.translatable("cogwit.codex.title"));
    }

    @Override
    protected void init() {
        entries = TrophyUi.codex();
        selected = Math.min(selected, Math.max(0, entries.size() - 1));
        favourites = ClientConfig.favourites();
    }

    @Override
    public void tick() {
        fullFlash = Math.max(0, fullFlash - 1);
    }

    /** Whether a boss's active can sit on the wheel at all: beaten, with an active that works. */
    private static boolean starrable(TrophyUi.CodexEntry e) {
        return e.owned() && e.trophy().hasActive && e.implemented();
    }

    /** Stars or unstars the boss at {@code index}; returns whether anything was done. */
    private boolean toggleStar(int index) {
        if (index < 0 || index >= entries.size() || !starrable(entries.get(index))) {
            return false;
        }
        if (!ClientConfig.toggleFavourite(entries.get(index).trophy())) {
            fullFlash = 40;
        }
        favourites = ClientConfig.favourites();
        return true;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // geometry

    private int panelW() {
        return Math.min(W, width - 10);
    }

    private int panelH() {
        return Math.max(100, Math.min(H, height - top() - 4));
    }

    private int left() {
        return (width - panelW()) / 2;
    }

    private int top() {
        return MenuTabs.panelTop(height, H);
    }

    /** The tile size: the usual 22 (20 on a narrow panel), shrunk when the rows of tiles would reach the footer. */
    private int tile() {
        int base = panelW() >= 320 ? 22 : 20;
        int rows = Math.max(1, (entries.size() + COLS - 1) / COLS);
        int room = footerTop() - 6 - (top() + BODY_Y + 2);
        int fit = (room + TILE_GAP) / rows - TILE_GAP;
        return Math.max(MIN_TILE, Math.min(base, fit));
    }

    private int gridW() {
        return COLS * tile() + (COLS - 1) * TILE_GAP;
    }

    private int tileX(int i) {
        return left() + PAD + 2 + i % COLS * (tile() + TILE_GAP);
    }

    private int tileY(int i) {
        return top() + BODY_Y + 2 + i / COLS * (tile() + TILE_GAP);
    }

    private int detailX() {
        return left() + PAD + 2 + gridW() + SEPARATION;
    }

    private int detailW() {
        return left() + panelW() - PAD - detailX();
    }

    /** The footer wraps across the whole panel and sits on the bottom edge. */
    private List<FormattedCharSequence> footerLines() {
        int wrap = panelW() - 2 * PAD;
        if (footer == null || footerWrap != wrap) {
            footer = font.split(Component.translatable("cogwit.codex.final"), wrap);
            footerWrap = wrap;
        }
        return footer;
    }

    private int footerTop() {
        return top() + panelH() - PAD + 2 - footerLines().size() * font.lineHeight;
    }

    private int tileAt(double mx, double my) {
        for (int i = 0; i < entries.size(); i++) {
            int x = tileX(i), y = tileY(i);
            if (mx >= x && mx < x + tile() && my >= y && my < y + tile()) {
                return i;
            }
        }
        return -1;
    }

    private void select(int index) {
        if (index >= 0 && index != selected) {
            selected = index;
            scroll = 0;
        }
    }

    // input

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (MenuTabs.keyPressed(this, MenuTabs.Tab.TROPHIES, keyCode, scanCode, modifiers)) {
            return true;
        }
        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_F && toggleStar(selected)) {
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (MenuTabs.mouseClicked(font, MenuTabs.Tab.TROPHIES, width, top(), mouseX, mouseY, button)) {
            return true;
        }
        int hit = tileAt(mouseX, mouseY);
        if (button == 0 && hit >= 0) {
            select(hit);
            return true;
        }
        if (button == 1 && hit >= 0) {
            select(hit);
            toggleStar(hit);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (maxScroll > 0) {
            scroll = Math.max(0, Math.min(maxScroll, scroll - (int) Math.signum(scrollY) * SCROLL_STEP));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    // drawing

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderTransparentBackground(g);
        int x = left(), y = top();
        g.fill(x, y, x + panelW(), y + panelH(), BACKGROUND);
        g.fill(x, y, x + panelW(), y + 1, ORANGE);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        MenuTabs.render(g, font, MenuTabs.Tab.TROPHIES, width, top(), mouseX, mouseY);
        select(tileAt(mouseX, mouseY));
        int x = left() + PAD, y = top();
        int owned = 0;
        for (TrophyUi.CodexEntry e : entries) {
            owned += e.owned() ? 1 : 0;
        }
        header(g, x, y, owned);
        for (int i = 0; i < entries.size(); i++) {
            tile(g, i, entries.get(i), i == selected);
        }
        if (!entries.isEmpty()) {
            details(g, entries.get(selected));
        }
        int fy = footerTop();
        g.fill(x, fy - 4, left() + panelW() - PAD, fy - 3, TRACK);
        for (FormattedCharSequence line : footerLines()) {
            g.drawString(font, line, x, fy, FINAL, false);
            fy += font.lineHeight;
        }
    }

    private void header(GuiGraphics g, int x, int y, int owned) {
        int right = left() + panelW() - PAD;
        g.drawString(font, title, x, y + 7, ORANGE, true);
        Component progress = Component.translatable("cogwit.codex.progress", owned, entries.size());
        g.drawString(font, progress, right - font.width(progress), y + 7, TEXT, false);
        g.fill(x, y + HEADER_BAR_Y, right, y + HEADER_BAR_Y + 3, TRACK);
        if (!entries.isEmpty() && owned > 0) {
            g.fill(x, y + HEADER_BAR_Y, x + (right - x) * owned / entries.size(), y + HEADER_BAR_Y + 3, VIOLET_DEEP);
        }
    }

    private void tile(GuiGraphics g, int i, TrophyUi.CodexEntry e, boolean selectedTile) {
        int x = tileX(i), y = tileY(i), t = tile();
        int rgb = e.trophy().colour & 0xFFFFFF;
        if (!e.modLoaded()) {
            g.fill(x, y, x + t, y + t, TILE_OFF);
            g.renderOutline(x, y, t, t, CROSS);
            for (int k = 3; k < t - 3; k++) {
                g.fill(x + k, y + k, x + k + 1, y + k + 1, CROSS);
                g.fill(x + t - 1 - k, y + k, x + t - k, y + k + 1, CROSS);
            }
        } else if (e.owned()) {
            g.fill(x - 2, y - 2, x + t + 2, y + t + 2, argb(0x30, rgb));
            g.fill(x - 1, y - 1, x + t + 1, y + t + 1, argb(0x90, rgb));
            g.fill(x, y, x + t, y + t, argb(0xFF, rgb));
            g.renderOutline(x, y, t, t, argb(0xFF, mix(rgb, 0xFFFFFF, 0.45f)));
        } else {
            g.fill(x, y, x + t, y + t, TILE_EMPTY);
            g.renderOutline(x, y, t, t, argb(0x80, 0x8A8A8F));
            g.drawCenteredString(font, "?", x + t / 2, y + (t - font.lineHeight) / 2 + 1, argb(0x90, 0x8A8A8F));
        }
        if (selectedTile) {
            g.renderOutline(x - 2, y - 2, t + 4, t + 4, ORANGE);
        }
        if (e.owned() && favourites.contains(e.trophy())) {
            star(g, x + t - 3, y + 2);
        }
    }

    /** A small five-pointed star, drawn as a plus and a cross, its centre at (x, y): the tile is on the wheel's favourites. */
    private static void star(GuiGraphics g, int x, int y) {
        g.fill(x - 3, y - 3, x + 4, y + 4, 0xC0141416);
        g.fill(x - 2, y, x + 3, y + 1, YELLOW);
        g.fill(x, y - 2, x + 1, y + 3, YELLOW);
        g.fill(x - 1, y - 1, x + 2, y + 2, YELLOW);
    }

    private void details(GuiGraphics g, TrophyUi.CodexEntry e) {
        int x = detailX(), w = detailW();
        int topY = top() + BODY_Y, bottomY = footerTop() - 6;
        g.fill(x - SEPARATION / 2 - 1, topY, x - SEPARATION / 2, bottomY, TRACK);

        List<Row> rows = rows(e, w - 4);
        int total = 0;
        for (Row row : rows) {
            total += row.gapBefore() + font.lineHeight;
        }
        maxScroll = Math.max(0, total - (bottomY - topY));
        scroll = Math.min(scroll, maxScroll);

        g.enableScissor(x, topY, x + w, bottomY);
        int y = topY - scroll;
        for (Row row : rows) {
            y += row.gapBefore();
            g.drawString(font, row.text(), x, y, row.colour(), false);
            y += font.lineHeight;
        }
        g.disableScissor();

        if (maxScroll > 0) {
            int track = bottomY - topY;
            int thumb = Math.max(8, track * track / total);
            int thumbY = topY + (track - thumb) * scroll / maxScroll;
            g.fill(x + w - 2, topY, x + w, bottomY, TRACK);
            g.fill(x + w - 2, thumbY, x + w, thumbY + thumb, VIOLET_DEEP);
        }
    }

    /** The selected boss laid out as wrapped lines: name, state, then passive, active and where to find it. */
    private List<Row> rows(TrophyUi.CodexEntry e, int width) {
        List<Row> rows = new ArrayList<>();
        int nameColour = e.owned() ? argb(0xFF, lift(e.trophy().colour)) : TEXT_DIM;
        add(rows, Component.empty().append(e.name()).withStyle(ChatFormatting.BOLD), width, nameColour, 0);
        add(rows, Component.translatable(e.owned() ? "cogwit.codex.owned" : "cogwit.codex.locked"), width,
                e.owned() ? VIOLET : TEXT_DIM, 1);
        if (!e.implemented()) {
            add(rows, Component.translatable("cogwit.codex.soon"), width, TEXT_DIM, 1);
        }
        if (!e.modLoaded()) {
            add(rows, Component.translatable("cogwit.codex.missing_mod", e.trophy().mod()), width, YELLOW, 1);
        }
        int body = e.owned() ? TEXT : TEXT_DIM;
        section(rows, "cogwit.codex.passive", VIOLET, e.passive(), body, width);
        if (e.trophy().hasActive) {
            section(rows, "cogwit.codex.active", ORANGE, e.active(), body, width);
        }
        if (starrable(e)) {
            boolean on = favourites.contains(e.trophy());
            Component star = fullFlash > 0 && !on
                    ? Component.translatable("cogwit.codex.star.full", WheelPages.MAX_FAVOURITES)
                    : Component.translatable(on ? "cogwit.codex.star.on" : "cogwit.codex.star.off", favourites.size(),
                            WheelPages.MAX_FAVOURITES);
            add(rows, star, width, on ? YELLOW : TEXT_DIM, 5);
        }
        section(rows, "cogwit.codex.where", TEXT_DIM, e.hint(), TEXT, width);
        return rows;
    }

    private void section(List<Row> rows, String labelKey, int labelColour, Component text, int colour, int width) {
        add(rows, Component.translatable(labelKey), width, labelColour, 5);
        add(rows, text, width, colour, 0);
    }

    private void add(List<Row> rows, Component text, int width, int colour, int gap) {
        boolean first = true;
        for (FormattedCharSequence line : font.split(text, width)) {
            rows.add(new Row(line, colour, first ? gap : 0));
            first = false;
        }
    }

    // colour helpers

    private static int argb(int alpha, int rgb) {
        return alpha << 24 | rgb & 0xFFFFFF;
    }

    private static int mix(int from, int to, float t) {
        int r = Math.round((from >> 16 & 0xFF) * (1 - t) + (to >> 16 & 0xFF) * t);
        int g = Math.round((from >> 8 & 0xFF) * (1 - t) + (to >> 8 & 0xFF) * t);
        int b = Math.round((from & 0xFF) * (1 - t) + (to & 0xFF) * t);
        return r << 16 | g << 8 | b;
    }

    /** A trophy colour readable on the dark panel: the near-black ones are pulled towards white. */
    private static int lift(int rgb) {
        int luma = ((rgb >> 16 & 0xFF) * 299 + (rgb >> 8 & 0xFF) * 587 + (rgb & 0xFF) * 114) / 1000;
        return luma < 110 ? mix(rgb, 0xFFFFFF, 0.55f) : rgb;
    }
}
