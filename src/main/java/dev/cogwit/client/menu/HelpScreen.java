package dev.cogwit.client.menu;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * The help tab: a scrollable page built from lang keys. Each section {@code cogwit.help.<id>.title} is followed by
 * {@code cogwit.help.<id>.1}, {@code .2}, ... until a key is missing; a section with no keys at all is skipped.
 */
public final class HelpScreen extends Screen {
    private static final String[] SECTIONS = {"start", "tokens", "growth", "armor", "cells", "trophies", "wheel", "coop"};
    private static final int W = 330, H = 220, PAD = 8, BODY_Y = 26, SCROLL_STEP = 12;
    private static final int SECTION_GAP = 8, PARAGRAPH_GAP = 3;
    private static final int BACKGROUND = 0xE0141416, ORANGE = 0xFFC8803F, TEXT = 0xFFE8E8EA, TEXT_DIM = 0xFF8A8A8F,
            TRACK = 0xFF2C2C31;

    /** One wrapped line of the page. */
    private record Row(FormattedCharSequence text, int colour, int gapBefore) {}

    private List<Row> rows = List.of();
    private int contentHeight;
    private int scroll, maxScroll;

    public HelpScreen() {
        super(Component.translatable("cogwit.menu.help.title"));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // geometry

    private int panelW() {
        return Math.min(W, width - 10);
    }

    private int left() {
        return (width - panelW()) / 2;
    }

    private int top() {
        return MenuTabs.panelTop(height, H);
    }

    private int panelH() {
        return Math.max(60, Math.min(H, height - top() - 4));
    }

    private int bodyTop() {
        return top() + BODY_Y;
    }

    private int bodyBottom() {
        return top() + panelH() - PAD;
    }

    private int textW() {
        return panelW() - 2 * PAD - 6;
    }

    @Override
    protected void init() {
        List<Row> built = new ArrayList<>();
        for (String id : SECTIONS) {
            String titleKey = "cogwit.help." + id + ".title";
            boolean hasTitle = I18n.exists(titleKey);
            boolean hasLines = I18n.exists("cogwit.help." + id + ".1");
            if (!hasTitle && !hasLines) {
                continue;
            }
            int gap = built.isEmpty() ? 0 : SECTION_GAP;
            if (hasTitle) {
                add(built, Component.translatable(titleKey).withStyle(ChatFormatting.BOLD), ORANGE, gap);
                gap = PARAGRAPH_GAP;
            }
            for (int i = 1; I18n.exists("cogwit.help." + id + "." + i); i++) {
                add(built, Component.translatable("cogwit.help." + id + "." + i), TEXT, gap);
                gap = PARAGRAPH_GAP;
            }
        }
        if (built.isEmpty()) {
            add(built, Component.translatable("cogwit.menu.help.empty"), TEXT_DIM, 0);
        }
        rows = built;
        int total = 0;
        for (Row row : rows) {
            total += row.gapBefore() + font.lineHeight;
        }
        contentHeight = total;
        scroll = Math.min(scroll, Math.max(0, contentHeight - (bodyBottom() - bodyTop())));
    }

    /** Wrap {@code text} into rows; only the first row of the paragraph carries the gap. */
    private void add(List<Row> into, Component text, int colour, int gap) {
        boolean first = true;
        for (FormattedCharSequence line : font.split(text, textW())) {
            into.add(new Row(line, colour, first ? gap : 0));
            first = false;
        }
    }

    // input

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (MenuTabs.keyPressed(this, MenuTabs.Tab.HELP, keyCode, scanCode, modifiers)) {
            return true;
        }
        int page = Math.max(SCROLL_STEP, bodyBottom() - bodyTop() - SCROLL_STEP);
        switch (keyCode) {
            case GLFW.GLFW_KEY_DOWN -> scrollBy(SCROLL_STEP);
            case GLFW.GLFW_KEY_UP -> scrollBy(-SCROLL_STEP);
            case GLFW.GLFW_KEY_PAGE_DOWN -> scrollBy(page);
            case GLFW.GLFW_KEY_PAGE_UP -> scrollBy(-page);
            case GLFW.GLFW_KEY_HOME -> scroll = 0;
            case GLFW.GLFW_KEY_END -> scroll = Integer.MAX_VALUE;
            default -> {
                return super.keyPressed(keyCode, scanCode, modifiers);
            }
        }
        clampScroll();
        return true;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (MenuTabs.mouseClicked(font, MenuTabs.Tab.HELP, width, top(), mouseX, mouseY, button)) {
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        scrollBy(-(int) Math.signum(scrollY) * SCROLL_STEP);
        clampScroll();
        return true;
    }

    private void scrollBy(int delta) {
        scroll += delta;
    }

    private void clampScroll() {
        maxScroll = Math.max(0, contentHeight - (bodyBottom() - bodyTop()));
        scroll = Math.max(0, Math.min(maxScroll, scroll));
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
        MenuTabs.render(g, font, MenuTabs.Tab.HELP, width, top(), mouseX, mouseY);
        clampScroll();

        int x = left() + PAD, right = left() + panelW() - PAD;
        g.drawString(font, title, x, top() + 7, ORANGE, true);
        g.fill(x, top() + 19, right, top() + 20, TRACK);

        int topY = bodyTop(), bottomY = bodyBottom();
        g.enableScissor(x, topY, right, bottomY);
        int y = topY - scroll;
        for (Row row : rows) {
            y += row.gapBefore();
            if (y + font.lineHeight >= topY && y <= bottomY) {
                g.drawString(font, row.text(), x, y, row.colour(), false);
            }
            y += font.lineHeight;
        }
        g.disableScissor();

        if (maxScroll > 0) {
            int track = bottomY - topY;
            int thumb = Math.max(8, track * track / contentHeight);
            int thumbY = topY + (track - thumb) * scroll / maxScroll;
            g.fill(right - 2, topY, right, bottomY, TRACK);
            g.fill(right - 2, thumbY, right, thumbY + thumb, ORANGE);
        }
    }
}
