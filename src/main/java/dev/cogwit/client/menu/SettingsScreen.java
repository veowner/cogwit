package dev.cogwit.client.menu;

import dev.cogwit.ClientConfig;
import dev.cogwit.network.CogwitPrefsPayload;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** The settings tab: Cogwit's display preferences ({@link ClientConfig}) and a reset for his key bindings. */
public final class SettingsScreen extends Screen {
    private static final String KEY_CATEGORY = "key.categories.cogwit";
    private static final int W = 316, H = 220, PAD = 8;
    private static final int ROWS_Y = 24, BUTTON_H = 14, PITCH = 25, DESC_OFFSET = BUTTON_H + 2;
    private static final int BACKGROUND = 0xE0141416, ORANGE = 0xFFC8803F, TEXT_DIM = 0xFF8A8A8F, GREEN = 0xFF7CE07C,
            TRACK = 0xFF2C2C31;
    private static final long NOTICE_MILLIS = 3000;

    /** The description line under one option. */
    private record Desc(int y, Component text, boolean isReset) {}

    private final List<Desc> descriptions = new ArrayList<>();
    private int hintY;
    private long resetNoticeUntil;

    public SettingsScreen() {
        super(Component.translatable("cogwit.menu.settings.title"));
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

    // widgets

    @Override
    protected void init() {
        descriptions.clear();
        int x = left() + PAD, w = panelW() - 2 * PAD;
        int y = top() + ROWS_Y;

        row(addRenderableWidget(CycleButton.onOffBuilder(ClientConfig.HUD_ENABLED.get())
                .create(x, y, w, BUTTON_H, Component.translatable("cogwit.settings.hud"), (b, on) -> {
                    ClientConfig.HUD_ENABLED.set(on);
                    ClientConfig.SPEC.save();
                })), "cogwit.settings.hud.desc", false);
        y += PITCH;

        row(addRenderableWidget(CycleButton.onOffBuilder(ClientConfig.detailed())
                .create(x, y, w, BUTTON_H, Component.translatable("cogwit.settings.detailed"), (b, on) -> {
                    ClientConfig.HUD_DETAILED.set(on);
                    ClientConfig.SPEC.save();
                })), "cogwit.settings.detailed.desc", false);
        y += PITCH;

        row(addRenderableWidget(CycleButton.<ClientConfig.Side>builder(s -> Component.translatable(
                        "cogwit.settings.side." + s.name().toLowerCase(Locale.ROOT)))
                .withValues(ClientConfig.Side.values())
                .withInitialValue(ClientConfig.HUD_SIDE.get())
                .create(x, y, w, BUTTON_H, Component.translatable("cogwit.settings.side"), (b, side) -> {
                    ClientConfig.HUD_SIDE.set(side);
                    ClientConfig.SPEC.save();
                })), "cogwit.settings.side.desc", false);
        y += PITCH;

        row(addRenderableWidget(CycleButton.onOffBuilder(ClientConfig.WIDGET_ENABLED.get())
                .create(x, y, w, BUTTON_H, Component.translatable("cogwit.settings.widget"), (b, on) -> {
                    ClientConfig.WIDGET_ENABLED.set(on);
                    ClientConfig.SPEC.save();
                })), "cogwit.settings.widget.desc", false);
        y += PITCH;

        row(addRenderableWidget(CycleButton.onOffBuilder(ClientConfig.SPEECH_TO_CHAT.get())
                .create(x, y, w, BUTTON_H, Component.translatable("cogwit.settings.chat"), (b, on) -> {
                    ClientConfig.SPEECH_TO_CHAT.set(on);
                    ClientConfig.SPEC.save();
                })), "cogwit.settings.chat.desc", false);
        y += PITCH;

        row(addRenderableWidget(CycleButton.<ClientConfig.Chatter>builder(c -> Component.translatable(
                        "cogwit.settings.chatter." + c.name().toLowerCase(Locale.ROOT)))
                .withValues(ClientConfig.Chatter.values())
                .withInitialValue(ClientConfig.chatter())
                .create(x, y, w, BUTTON_H, Component.translatable("cogwit.settings.chatter"), (b, chatter) -> {
                    ClientConfig.CHATTER.set(chatter);
                    ClientConfig.SPEC.save();
                    if (Minecraft.getInstance().getConnection() != null) {
                        CogwitPrefsPayload.sendFromClient();
                    }
                })), "cogwit.settings.chatter.desc", false);
        y += PITCH;

        row(addRenderableWidget(Button.builder(Component.translatable("cogwit.settings.pack_keys"), b -> {
                    dev.cogwit.client.PackKeyLayout.apply();
                    resetNoticeUntil = System.currentTimeMillis() + NOTICE_MILLIS;
                })
                .bounds(x, y, w, BUTTON_H).build()), "cogwit.settings.pack_keys.desc", true);
        y += PITCH;

        hintY = y + 1;
    }

    private void row(AbstractWidget widget, String descKey, boolean isReset) {
        descriptions.add(new Desc(widget.getY() + DESC_OFFSET, Component.translatable(descKey), isReset));
    }

    // input

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (MenuTabs.keyPressed(this, MenuTabs.Tab.SETTINGS, keyCode, scanCode, modifiers)) {
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (MenuTabs.mouseClicked(font, MenuTabs.Tab.SETTINGS, width, top(), mouseX, mouseY, button)) {
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
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
        MenuTabs.render(g, font, MenuTabs.Tab.SETTINGS, width, top(), mouseX, mouseY);

        int x = left() + PAD, right = left() + panelW() - PAD, w = right - x;
        g.drawString(font, title, x, top() + 7, ORANGE, true);
        g.fill(x, top() + 19, right, top() + 20, TRACK);

        boolean notice = System.currentTimeMillis() < resetNoticeUntil;
        for (Desc desc : descriptions) {
            Component text = desc.isReset() && notice ? Component.translatable("cogwit.settings.pack_keys.done") : desc.text();
            g.drawString(font, trim(text, w), x + 2, desc.y(), desc.isReset() && notice ? GREEN : TEXT_DIM, false);
        }

        int y = hintY;
        for (FormattedCharSequence line : font.split(Component.translatable("cogwit.settings.keys_hint"), w)) {
            g.drawString(font, line, x + 2, y, TEXT_DIM, false);
            y += font.lineHeight;
        }
    }

    private String trim(Component text, int maxWidth) {
        String s = text.getString();
        if (font.width(s) <= maxWidth) {
            return s;
        }
        return font.plainSubstrByWidth(s, maxWidth - font.width("...")) + "...";
    }
}
