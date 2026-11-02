package dev.cogwit.client;

import dev.cogwit.agent.Agent;
import dev.cogwit.agent.AgentService;
import dev.cogwit.agent.AgentState;
import dev.cogwit.client.menu.MenuTabs;
import dev.cogwit.item.CogwitTalismanItem;
import dev.cogwit.network.AgentSettingsPayload;
import dev.cogwit.speech.SpeechClientState;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/** Cogwit's agent settings: a behaviour mode, a permission (ask / always / never) for each action, and his journal. */
public class AgentScreen extends Screen {
    private static final int W = 316, H = 210, PAD = 8, GAP = 4;
    private static final int LEFT_W = 166, RIGHT_W = W - 2 * PAD - LEFT_W - 2 * GAP;
    private static final int MODE_Y = 18, MODE_H = 16, DESC_Y = 38;
    private static final int LIST_Y = 52, ROW_H = 11, PERM_W = 54, PERM_H = 10;
    private static final int FOOT_BUTTONS_Y = 176, FOOT_BUTTONS_H = 14, FOOTER_Y = 196;
    private static final int JOURNAL_TOP = 64, JOURNAL_BOTTOM = 190, ENTRY_H = 20, JOURNAL_MAX = 12;

    private static final int BACKGROUND = 0xC0141416, ORANGE = 0xFFC8803F, TEXT = 0xFFE8E8EA, TEXT_DIM = 0xFF8A8A8F,
            GREEN = 0xFF7CE07C, YELLOW = 0xFFFFD27A, RED = 0xFFE05555, TRACK = 0xFF2C2C31, STRIPE = 0x18FFFFFF;

    /** What the screen was last built from; a different state from the server rebuilds the buttons. */
    private AgentState shown;

    public AgentScreen() {
        super(Component.translatable("cogwit.agent.title"));
    }

    private static AgentState read() {
        var player = net.minecraft.client.Minecraft.getInstance().player;
        if (player == null) {
            return null;
        }
        return CogwitTalismanItem.worn(player).map(AgentService::state).orElse(null);
    }

    private int left() {
        return (width - W) / 2;
    }

    private int top() {
        return MenuTabs.panelTop(height, H);
    }

    @Override
    protected void init() {
        shown = read();
        if (shown == null) {
            return;
        }
        int x = left() + PAD, y = top();
        int modeW = (W - 2 * PAD - 2 * GAP) / 3;
        Agent.Mode[] modes = Agent.Mode.values();
        for (int i = 0; i < modes.length; i++) {
            Agent.Mode mode = modes[i];
            Component name = Component.translatable("cogwit.agent.mode." + mode.key());
            boolean selected = mode == shown.mode();
            Component label = selected
                    ? Component.literal("> ").append(name).append(" <").withColor(ORANGE & 0xFFFFFF)
                    : name;
            Button button = addRenderableWidget(Button.builder(label, b -> send(mode.key(), List.of(), false))
                    .bounds(x + i * (modeW + GAP), y + MODE_Y, modeW, MODE_H).build());
            button.active = !selected;
        }

        for (int i = 0; i < Agent.Action.values().length; i++) {
            Agent.Action action = Agent.Action.values()[i];
            Agent.Permission permission = shown.permission(action);
            Button button = addRenderableWidget(Button.builder(permissionLabel(permission), b -> cycle(action))
                    .bounds(x + LEFT_W - PERM_W, y + LIST_Y + i * ROW_H + (ROW_H - PERM_H) / 2, PERM_W, PERM_H)
                    .build());
            button.setTooltip(Tooltip.create(actionName(action)));
        }

        int resetW = 100;
        Button reset = addRenderableWidget(Button.builder(Component.translatable("cogwit.agent.reset"),
                        b -> send(shown.mode().key(), List.of(), false))
                .bounds(x, y + FOOT_BUTTONS_Y, resetW, FOOT_BUTTONS_H).build());
        reset.active = !shown.overrides().isEmpty();
        addRenderableWidget(Button.builder(Component.translatable("cogwit.agent.vault"),
                        b -> send("", overrideList(shown, null, null), true))
                .bounds(x + resetW + GAP, y + FOOT_BUTTONS_Y, LEFT_W - resetW - GAP, FOOT_BUTTONS_H).build());
    }

    @Override
    public void tick() {
        if (!Objects.equals(shown, read())) {
            rebuildWidgets();
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (MenuTabs.keyPressed(this, MenuTabs.Tab.AGENT, keyCode, scanCode, modifiers)) {
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (MenuTabs.mouseClicked(font, MenuTabs.Tab.AGENT, width, top(), mouseX, mouseY, button)) {
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private static Component actionName(Agent.Action action) {
        return Component.translatable("cogwit.agent.action." + action.key());
    }

    private static Component permissionLabel(Agent.Permission permission) {
        int colour = switch (permission) {
            case ASK -> YELLOW;
            case ALWAYS -> GREEN;
            case NEVER -> RED;
        };
        return Component.translatable("cogwit.agent.permission." + permission.name().toLowerCase(Locale.ROOT))
                .withColor(colour & 0xFFFFFF);
    }

    private static Agent.Permission next(Agent.Permission permission) {
        return switch (permission) {
            case ASK -> Agent.Permission.ALWAYS;
            case ALWAYS -> Agent.Permission.NEVER;
            case NEVER -> Agent.Permission.ASK;
        };
    }

    private void cycle(Agent.Action action) {
        AgentState state = read();
        if (state == null) {
            return;
        }
        send("", overrideList(state, action, next(state.permission(action))), false);
    }

    /** Every action whose permission differs from the mode default, with {@code changed} set to {@code to}. */
    private static List<String> overrideList(AgentState state, Agent.Action changed, Agent.Permission to) {
        List<String> list = new ArrayList<>();
        for (Agent.Action a : Agent.Action.values()) {
            Agent.Permission p = a == changed ? to : state.permission(a);
            if (p != Agent.permission(state.mode(), Map.of(), a)) {
                list.add(a.key() + "=" + p.name().toLowerCase(Locale.ROOT));
            }
        }
        return list;
    }

    private static void send(String mode, List<String> overrides, boolean openVault) {
        PacketDistributor.sendToServer(new AgentSettingsPayload(mode, overrides, openVault));
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderTransparentBackground(g);
        int x = left(), y = top();
        g.fill(x, y, x + W, y + H, BACKGROUND);
        g.fill(x, y, x + W, y + 1, ORANGE);
        if (shown != null) {
            for (int i = 0; i < Agent.Action.values().length; i += 2) {
                int rowY = y + LIST_Y + i * ROW_H;
                g.fill(x + PAD, rowY, x + PAD + LEFT_W, rowY + ROW_H, STRIPE);
            }
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        MenuTabs.render(g, font, MenuTabs.Tab.AGENT, width, top(), mouseX, mouseY);
        int x = left() + PAD, y = top();
        g.drawString(font, title, x, y + 6, ORANGE, true);
        if (shown == null) {
            g.drawCenteredString(font, Component.translatable("cogwit.agent.not_worn"), left() + W / 2,
                    y + H / 2 - font.lineHeight / 2, TEXT_DIM);
            return;
        }

        String mode = Component.translatable("cogwit.agent.mode").getString() + ": "
                + Component.translatable("cogwit.agent.mode." + shown.mode().key() + ".desc").getString();
        g.drawString(font, font.plainSubstrByWidth(mode, W - 2 * PAD), x, y + DESC_Y, TEXT_DIM, false);

        Agent.Action[] actions = Agent.Action.values();
        for (int i = 0; i < actions.length; i++) {
            int rowY = y + LIST_Y + i * ROW_H;
            String name = trim(actionName(actions[i]).getString(), LEFT_W - PERM_W - 8);
            g.drawString(font, name, x + 3, rowY + (ROW_H - font.lineHeight) / 2 + 1, TEXT, false);
        }

        int rx = x + LEFT_W + 2 * GAP;
        g.fill(rx - GAP - 1, y + LIST_Y, rx - GAP, y + FOOT_BUTTONS_Y + FOOT_BUTTONS_H, TRACK);
        g.drawString(font, Component.translatable("cogwit.agent.journal"), rx, y + LIST_Y + 1, TEXT, false);
        journal(g, rx, y);

        g.drawCenteredString(font, Component.translatable("cogwit.agent.footer"), left() + W / 2, y + FOOTER_Y,
                TEXT_DIM);
    }

    private void journal(GuiGraphics g, int rx, int y) {
        List<SpeechClientState.JournalEntry> entries = SpeechClientState.journal();
        if (entries.isEmpty()) {
            g.drawString(font, trim(Component.translatable("cogwit.agent.journal.empty").getString(), RIGHT_W), rx,
                    y + JOURNAL_TOP, TEXT_DIM, false);
            return;
        }
        long now = System.currentTimeMillis();
        int shownCount = Math.min(JOURNAL_MAX, Math.min(entries.size(), (JOURNAL_BOTTOM - JOURNAL_TOP) / ENTRY_H));
        for (int i = 0; i < shownCount; i++) {
            SpeechClientState.JournalEntry entry = entries.get(i);
            int ey = y + JOURNAL_TOP + i * ENTRY_H;
            g.drawString(font, Component.translatable("cogwit.agent.ago", age(now - entry.millis())), rx, ey,
                    TEXT_DIM, false);
            g.drawString(font, trim(entry.text().getString(), RIGHT_W), rx, ey + font.lineHeight + 1, TEXT, false);
        }
    }

    /** "12 с", "3 мин" or "2 ч". */
    private static String age(long millis) {
        long seconds = Math.max(0, millis / 1000);
        if (seconds < 60) {
            return seconds + " с";
        }
        if (seconds < 3600) {
            return seconds / 60 + " мин";
        }
        return seconds / 3600 + " ч";
    }

    private String trim(String text, int maxWidth) {
        if (font.width(text) <= maxWidth) {
            return text;
        }
        return font.plainSubstrByWidth(text, maxWidth - font.width("...")) + "...";
    }
}
