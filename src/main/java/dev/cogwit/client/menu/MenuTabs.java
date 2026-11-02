package dev.cogwit.client.menu;

import dev.cogwit.client.AgentScreen;
import dev.cogwit.client.TrophyCodexScreen;
import dev.cogwit.guide.client.GuideClient;
import dev.cogwit.guide.client.RoadmapScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.function.Supplier;

/**
 * The tab bar of Cogwit's menu. Every tab is its own {@link Screen}; each of the five draws this bar just above its
 * panel and forwards clicks and keys to it, which swaps in the screen of the chosen tab.
 *
 * <p>Wiring for a screen: position the panel at {@code top = MenuTabs.panelTop(height, panelHeight)}, call
 * {@link #render} from {@code render}, and call {@link #mouseClicked} / {@link #keyPressed} first in the matching
 * overrides. Esc closes the menu from any tab (vanilla behaviour); so does the roadmap key.
 */
public final class MenuTabs {
    /** Height of the bar; it sits directly above the panel. */
    public static final int BAR_H = 16;
    /** The panel height every tab is centred as, so the bar does not jump between tabs. */
    public static final int PANEL_H = 220;

    private static final int MIN_BAR_W = 220;
    private static final int TAB_PAD = 8;
    private static final int ORANGE = 0xFFC8803F, GREY = 0xFF8A8A8F, TEXT = 0xFFE8E8EA;
    private static final int ACTIVE_BG = 0xE0141416, INACTIVE_BG = 0xB00E0E10, HOVER_BG = 0xC01E1E22;

    public enum Tab {
        PATH("cogwit.menu.tab.path", RoadmapScreen::new),
        TROPHIES("cogwit.menu.tab.trophies", TrophyCodexScreen::new),
        AGENT("cogwit.menu.tab.agent", AgentScreen::new),
        HELP("cogwit.menu.tab.help", HelpScreen::new),
        SETTINGS("cogwit.menu.tab.settings", SettingsScreen::new);

        private final String key;
        private final Supplier<Screen> factory;

        Tab(String key, Supplier<Screen> factory) {
            this.key = key;
            this.factory = factory;
        }

        public Component label() {
            return Component.translatable(key);
        }

        public Screen create() {
            return factory.get();
        }
    }

    private MenuTabs() {}

    /** Open the menu on the given tab (replaces whatever screen is showing). */
    public static void open(Tab tab) {
        Minecraft.getInstance().setScreen(tab.create());
    }

    /** Top edge of the panel, so that panel plus tab bar are centred on the screen (never above the bar). */
    public static int panelTop(int screenHeight, int panelHeight) {
        int h = Math.max(panelHeight, PANEL_H);
        return Math.max(BAR_H + 2, (screenHeight - h - BAR_H) / 2 + BAR_H);
    }

    // geometry

    private static int[] widths(Font font, int screenWidth) {
        Tab[] tabs = Tab.values();
        int[] w = new int[tabs.length];
        int natural = 0;
        for (int i = 0; i < tabs.length; i++) {
            w[i] = font.width(tabs[i].label()) + 2 * TAB_PAD;
            natural += w[i];
        }
        int bar = Math.min(Math.max(natural, MIN_BAR_W), Math.max(40, screenWidth - 4));
        int extra = bar - natural;
        int given = 0;
        for (int i = 0; i < w.length; i++) {
            int add = i == w.length - 1 ? extra - given : extra / w.length;
            w[i] += add;
            given += add;
            w[i] = Math.max(8, w[i]);
        }
        return w;
    }

    private static int barLeft(int[] widths, int screenWidth) {
        int total = 0;
        for (int w : widths) {
            total += w;
        }
        return (screenWidth - total) / 2;
    }

    private static int indexAt(Font font, int screenWidth, int panelTop, double mx, double my) {
        int y = panelTop - BAR_H;
        if (my < y || my >= panelTop) {
            return -1;
        }
        int[] widths = widths(font, screenWidth);
        int x = barLeft(widths, screenWidth);
        for (int i = 0; i < widths.length; i++) {
            if (mx >= x && mx < x + widths[i]) {
                return i;
            }
            x += widths[i];
        }
        return -1;
    }

    // drawing

    public static void render(GuiGraphics g, Font font, Tab current, int screenWidth, int panelTop, int mouseX,
            int mouseY) {
        Tab[] tabs = Tab.values();
        int[] widths = widths(font, screenWidth);
        int x = barLeft(widths, screenWidth);
        int y = panelTop - BAR_H;
        int hover = indexAt(font, screenWidth, panelTop, mouseX, mouseY);
        for (int i = 0; i < tabs.length; i++) {
            boolean active = tabs[i] == current;
            int w = widths[i];
            int right = x + w - (i == tabs.length - 1 ? 0 : 1);
            g.fill(x, y, right, panelTop, active ? ACTIVE_BG : i == hover ? HOVER_BG : INACTIVE_BG);
            if (active) {
                g.fill(x, y, right, y + 1, ORANGE);
            }
            int colour = active ? ORANGE : i == hover ? TEXT : GREY;
            Component label = tabs[i].label();
            g.drawString(font, label, x + (w - font.width(label)) / 2, y + (BAR_H - font.lineHeight) / 2 + 1, colour,
                    false);
            x += w;
        }
    }

    // input

    /** A click on a tab other than the current one switches to it. Returns true when the click hit the bar. */
    public static boolean mouseClicked(Font font, Tab current, int screenWidth, int panelTop, double mx, double my,
            int button) {
        if (button != 0) {
            return false;
        }
        int hit = indexAt(font, screenWidth, panelTop, mx, my);
        if (hit < 0) {
            return false;
        }
        switchTo(current, Tab.values()[hit]);
        return true;
    }

    /**
     * Tab / Shift+Tab step through the tabs, 1-5 jump to one, and the roadmap key closes the menu.
     * Returns true when the key was consumed.
     */
    public static boolean keyPressed(Screen screen, Tab current, int keyCode, int scanCode, int modifiers) {
        Tab[] tabs = Tab.values();
        if (keyCode == GLFW.GLFW_KEY_TAB) {
            int step = (modifiers & GLFW.GLFW_MOD_SHIFT) != 0 ? tabs.length - 1 : 1;
            switchTo(current, tabs[(current.ordinal() + step) % tabs.length]);
            return true;
        }
        if ((modifiers & (GLFW.GLFW_MOD_CONTROL | GLFW.GLFW_MOD_ALT | GLFW.GLFW_MOD_SUPER)) == 0) {
            int number = keyCode >= GLFW.GLFW_KEY_1 && keyCode < GLFW.GLFW_KEY_1 + tabs.length
                    ? keyCode - GLFW.GLFW_KEY_1
                    : keyCode >= GLFW.GLFW_KEY_KP_1 && keyCode < GLFW.GLFW_KEY_KP_1 + tabs.length
                            ? keyCode - GLFW.GLFW_KEY_KP_1
                            : -1;
            if (number >= 0) {
                switchTo(current, tabs[number]);
                return true;
            }
        }
        if (GuideClient.ROADMAP_KEY.matches(keyCode, scanCode)) {
            screen.onClose();
            return true;
        }
        return false;
    }

    private static void switchTo(Tab current, Tab target) {
        if (target != current) {
            open(target);
        }
    }
}
