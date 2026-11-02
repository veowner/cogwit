package dev.cogwit.guide.client;

import dev.cogwit.brain.Growth;
import dev.cogwit.cells.CellBank;
import dev.cogwit.client.menu.MenuTabs;
import dev.cogwit.guide.GuideClientState;
import dev.cogwit.guide.GuideProgress;
import dev.cogwit.guide.Milestone;
import dev.cogwit.guide.net.GuideTargetPayload;
import dev.cogwit.horizon.HorizonArmorItem;
import dev.cogwit.item.CogwitTalismanItem;
import dev.cogwit.trophy.Trophy;
import dev.cogwit.trophy.TrophyClientState;
import dev.cogwit.trophy.TrophyHunt;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * The path: chapters of milestones on the left, the picked one in full on the right (its hint, how far along
 * you are, where Cogwit is leading). It opens on the current step; finished chapters fold into one line and
 * open on a click. Mouse, wheel and the arrow keys all move the pick.
 */
public final class RoadmapScreen extends Screen {
    private static final int PAD = 8, GAP = 6, ROW_H = 11, CHAPTER_H = 14, HEADER_H = 26, FOOTER_H = 14;
    /** The panel grows with the screen up to this size, and never below the other tabs' size. */
    private static final int MAX_W = 420, MIN_W = 316, MAX_H = 260, MIN_H = 210;
    private static final int ORANGE = 0xFFC8803F, BACKGROUND = 0xE0141416, GREEN = 0xFF7CE07C, GREY = 0xFF8A8A8F,
            TEXT = 0xFFE8E8EA, TEXT_DIM = 0xFFB0B0B5, TRACK = 0xFF2A2A2E, PICKED = 0x40C8803F, HOVER = 0x20FFFFFF;

    /** One line of the list: a chapter heading or a milestone. */
    private record Row(int chapter, Milestone milestone) {
        boolean heading() {
            return milestone == null;
        }
    }

    private final boolean[] open = new boolean[Milestone.CHAPTERS + 1];
    private Milestone picked;
    private int scroll;
    private int panelW = MIN_W, panelH = MIN_H, listW = 160;

    public RoadmapScreen() {
        super(Component.translatable("cogwit.guide.roadmap.title"));
    }

    @Override
    protected void init() {
        panelW = Math.max(MIN_W, Math.min(MAX_W, width - 16));
        panelH = Math.max(MIN_H, Math.min(MAX_H, height - MenuTabs.BAR_H - 12));
        listW = Math.round(panelW * 0.44f);
        int mask = GuideClientState.doneMask();
        int current = GuideProgress.firstPending(mask);
        if (picked == null) {
            picked = current < Milestone.values().length ? Milestone.values()[current] : Milestone.values()[0];
            for (int c = 1; c <= Milestone.CHAPTERS; c++) {
                open[c] = !chapterDone(c, mask) || picked.chapter() == c;
            }
        }
        keepPickedInView();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private int left() {
        return (width - panelW) / 2;
    }

    private int top() {
        return MenuTabs.panelTop(height, panelH);
    }

    private int listTop() {
        return top() + HEADER_H;
    }

    private int listHeight() {
        return panelH - HEADER_H - FOOTER_H;
    }

    private static boolean chapterDone(int chapter, int mask) {
        return Arrays.stream(Milestone.values()).filter(m -> m.chapter() == chapter)
                .allMatch(m -> GuideProgress.isDone(m, mask));
    }

    private List<Row> rows() {
        List<Row> rows = new ArrayList<>();
        for (int c = 1; c <= Milestone.CHAPTERS; c++) {
            rows.add(new Row(c, null));
            if (open[c]) {
                for (Milestone m : Milestone.values()) {
                    if (m.chapter() == c) {
                        rows.add(new Row(c, m));
                    }
                }
            }
        }
        return rows;
    }

    private static int rowHeight(Row row) {
        return row.heading() ? CHAPTER_H : ROW_H;
    }

    private int contentHeight() {
        return rows().stream().mapToInt(RoadmapScreen::rowHeight).sum();
    }

    private void clampScroll() {
        scroll = Math.max(0, Math.min(scroll, contentHeight() - listHeight()));
    }

    private void keepPickedInView() {
        int y = 0;
        for (Row row : rows()) {
            if (row.milestone() == picked) {
                if (y < scroll) {
                    scroll = y - CHAPTER_H;
                } else if (y + ROW_H > scroll + listHeight()) {
                    scroll = y + ROW_H - listHeight();
                }
                break;
            }
            y += rowHeight(row);
        }
        clampScroll();
    }

    /** The row under the pointer, or null. */
    private Row rowAt(double mx, double my) {
        int x = left() + PAD;
        if (mx < x || mx >= x + listW || my < listTop() || my >= listTop() + listHeight()) {
            return null;
        }
        int y = listTop() - scroll;
        for (Row row : rows()) {
            int h = rowHeight(row);
            if (my >= y && my < y + h) {
                return row;
            }
            y += h;
        }
        return null;
    }

    // ---------------------------------------------------------------- input

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (MenuTabs.mouseClicked(font, MenuTabs.Tab.PATH, width, top(), mouseX, mouseY, button)) {
            return true;
        }
        Row row = rowAt(mouseX, mouseY);
        if (row != null && button == 0) {
            if (row.heading()) {
                open[row.chapter()] = !open[row.chapter()];
                clampScroll();
            } else {
                picked = row.milestone();
            }
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        scroll -= (int) Math.round(scrollY * ROW_H * 2);
        clampScroll();
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (MenuTabs.keyPressed(this, MenuTabs.Tab.PATH, keyCode, scanCode, modifiers)) {
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_UP || keyCode == GLFW.GLFW_KEY_DOWN) {
            step(keyCode == GLFW.GLFW_KEY_DOWN ? 1 : -1);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    /** Moves the pick one milestone along, opening a folded chapter on the way. */
    private void step(int direction) {
        int next = Math.floorMod(picked.ordinal() + direction, Milestone.values().length);
        picked = Milestone.values()[next];
        open[picked.chapter()] = true;
        keepPickedInView();
    }

    // ---------------------------------------------------------------- drawing

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        int x = left(), y = top();
        MenuTabs.render(g, font, MenuTabs.Tab.PATH, width, y, mouseX, mouseY);
        g.fill(x, y, x + panelW, y + panelH, BACKGROUND);
        g.fill(x, y, x + panelW, y + 1, ORANGE);

        int mask = GuideClientState.doneMask();
        int done = Integer.bitCount(mask & ((1 << Milestone.values().length) - 1));
        int total = Milestone.values().length;
        g.drawString(font, title.copy().withStyle(ChatFormatting.BOLD), x + PAD, y + 6, ORANGE, true);
        String count = done + "/" + total;
        g.drawString(font, count, x + panelW - PAD - font.width(count), y + 6, GREY, false);
        bar(g, x + PAD, y + 17, panelW - 2 * PAD, 3, done / (float) total, ORANGE);

        list(g, mask, mouseX, mouseY);
        g.fill(x + PAD + listW + GAP / 2, listTop(), x + PAD + listW + GAP / 2 + 1, listTop() + listHeight(), TRACK);
        details(g, mask, x + PAD + listW + GAP, listTop(), panelW - 2 * PAD - listW - GAP);

        Component footer = Component.translatable("cogwit.guide.roadmap.nav", keyName());
        g.drawString(font, font.plainSubstrByWidth(footer.getString(), panelW - 2 * PAD), x + PAD, y + panelH - 11, GREY, false);
    }

    private void list(GuiGraphics g, int mask, int mouseX, int mouseY) {
        int x = left() + PAD, top = listTop(), bottom = top + listHeight();
        int current = GuideProgress.firstPending(mask);
        Row hovered = rowAt(mouseX, mouseY);
        g.enableScissor(x, top, x + listW, bottom);
        int y = top - scroll;
        for (Row row : rows()) {
            int h = rowHeight(row);
            if (y + h > top && y < bottom) {
                if (row.heading()) {
                    heading(g, row.chapter(), mask, x, y, row.equals(hovered));
                } else {
                    milestone(g, row.milestone(), mask, current, x, y, row.equals(hovered));
                }
            }
            y += h;
        }
        g.disableScissor();
        int content = contentHeight();
        if (content > listHeight()) {
            int thumb = Math.max(12, listHeight() * listHeight() / content);
            int at = top + (listHeight() - thumb) * scroll / (content - listHeight());
            g.fill(x + listW - 2, top, x + listW, bottom, TRACK);
            g.fill(x + listW - 2, at, x + listW, at + thumb, GREY);
        }
    }

    private void heading(GuiGraphics g, int chapter, int mask, int x, int y, boolean hovered) {
        long all = Arrays.stream(Milestone.values()).filter(m -> m.chapter() == chapter).count();
        long done = Arrays.stream(Milestone.values()).filter(m -> m.chapter() == chapter)
                .filter(m -> GuideProgress.isDone(m, mask)).count();
        boolean finished = done == all;
        if (hovered) {
            g.fill(x, y, x + listW - 3, y + CHAPTER_H - 1, HOVER);
        }
        String counter = finished ? "✔" : done + "/" + all;
        int colour = finished ? GREEN : ORANGE;
        triangle(g, x + 2, y + 4, open[chapter], colour);
        String name = Component.translatable("cogwit.guide.roadmap.chapter" + chapter).getString();
        int room = listW - 18 - font.width(counter);
        if (font.width(name) > room) {
            name = font.plainSubstrByWidth(name, room - font.width("…")) + "…";
        }
        g.drawString(font, name, x + 12, y + 3, colour, false);
        g.drawString(font, counter, x + listW - 4 - font.width(counter), y + 3, colour, false);
        g.fill(x, y + CHAPTER_H - 2, x + listW - 3, y + CHAPTER_H - 1, 0x40C8803F);
    }

    /** A small arrow: pointing down for an open chapter, right for a folded one. */
    private static void triangle(GuiGraphics g, int x, int y, boolean down, int colour) {
        for (int i = 0; i < 4; i++) {
            if (down) {
                g.fill(x + i, y + i, x + 7 - i, y + i + 1, colour);
            } else {
                g.fill(x + i, y + i, x + i + 1, y + 7 - i, colour);
            }
        }
    }

    private void milestone(GuiGraphics g, Milestone m, int mask, int current, int x, int y, boolean hovered) {
        boolean done = GuideProgress.isDone(m, mask);
        boolean isCurrent = m.ordinal() == current;
        if (m == picked) {
            g.fill(x, y, x + listW - 3, y + ROW_H, PICKED);
            g.fill(x, y, x + 1, y + ROW_H, ORANGE);
        } else if (hovered) {
            g.fill(x, y, x + listW - 3, y + ROW_H, HOVER);
        }
        int colour = done ? GREEN : isCurrent ? ORANGE : GREY;
        g.drawString(font, done ? "✔" : isCurrent ? "➤" : "○", x + 4, y + 2, colour, false);
        String name = font.plainSubstrByWidth(Component.translatable(m.titleKey()).getString(), listW - 22);
        g.drawString(font, name, x + 15, y + 2, m == picked || isCurrent ? TEXT : colour, false);
    }

    /** The picked milestone in full: name, state, progress bar, hint, Cogwit's compass target. */
    private void details(GuiGraphics g, int mask, int x, int y, int w) {
        Milestone m = picked;
        boolean done = GuideProgress.isDone(m, mask);
        boolean isCurrent = m.ordinal() == GuideProgress.firstPending(mask);
        int lineY = y + 2;
        for (FormattedCharSequence line : font.split(Component.translatable(m.titleKey())
                .withStyle(ChatFormatting.BOLD), w)) {
            g.drawString(font, line, x, lineY, TEXT, false);
            lineY += 10;
        }
        String state = done ? "done" : isCurrent ? "current" : "todo";
        g.drawString(font, Component.translatable("cogwit.guide.roadmap.state." + state), x, lineY,
                done ? GREEN : isCurrent ? ORANGE : GREY, false);
        lineY += 13;

        Progress p = done ? null : progress(m);
        if (p != null) {
            bar(g, x, lineY, w, 4, p.share(), isCurrent ? ORANGE : GREY);
            lineY += 7;
            g.drawString(font, p.label(), x, lineY, TEXT_DIM, false);
            lineY += 13;
        }

        int bottom = listTop() + listHeight();
        List<FormattedCharSequence> hint = font.split(Component.translatable(m.hintKey(dimension())), w);
        for (FormattedCharSequence line : hint) {
            if (lineY + 9 > bottom - 22) {
                break;
            }
            g.drawString(font, line, x, lineY, done ? GREY : TEXT_DIM, false);
            lineY += 10;
        }

        GuideTargetPayload target = GuideClientState.target();
        if (isCurrent && target != null) {
            List<FormattedCharSequence> lead = font.split(Component.translatable("cogwit.guide.roadmap.leading",
                    Component.translatable(target.labelKey())), w);
            int leadY = bottom - 10 * Math.min(2, lead.size());
            for (int i = 0; i < Math.min(2, lead.size()); i++) {
                g.drawString(font, lead.get(i), x, leadY + 10 * i, ORANGE, false);
            }
        }
    }

    private record Progress(float share, Component label) {}

    /** How far along an unfinished step is, from what this client can see, or null when there is no count. */
    private static Progress progress(Milestone m) {
        Player player = Minecraft.getInstance().player;
        if (player == null) {
            return null;
        }
        ItemStack worn = CogwitTalismanItem.worn(player).orElse(ItemStack.EMPTY);
        return switch (m) {
            case FEED_COGWIT -> {
                int tokens = worn.isEmpty() ? 0 : CogwitTalismanItem.brain(worn).tokens();
                yield count(tokens, GuideProgress.FEED_TOKENS, "cogwit.guide.roadmap.tokens");
            }
            case ECHO_SHARDS -> count(player.getInventory().countItem(Items.ECHO_SHARD), GuideProgress.ECHO_SHARDS_NEEDED,
                    "cogwit.guide.roadmap.shards");
            case FULL_SET -> {
                int pieces = 0;
                for (EquipmentSlot slot : new EquipmentSlot[] {EquipmentSlot.HEAD, EquipmentSlot.CHEST,
                        EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
                    pieces += player.getItemBySlot(slot).getItem() instanceof HorizonArmorItem ? 1 : 0;
                }
                yield count(pieces, 4, "cogwit.guide.roadmap.pieces");
            }
            case CELLS_IN_ARMOR -> count(CellBank.worn(player).all().size(), 1, "cogwit.guide.roadmap.cells");
            case COGWIT_LEVEL_2, COGWIT_LEVEL_4, COGWIT_LEVEL_5 -> {
                int level = m == Milestone.COGWIT_LEVEL_2 ? 2 : m == Milestone.COGWIT_LEVEL_4 ? 4 : 5;
                float xp = worn.isEmpty() ? 0 : CogwitTalismanItem.brain(worn).xp();
                yield count((int) xp, (int) Growth.threshold(level), "cogwit.guide.roadmap.xp");
            }
            case FIRST_TROPHY, ALL_TROPHIES, TWILIGHT_BOSSES, WORLD_BOSSES -> {
                int owned = (int) TrophyClientState.owned(player.getId()).stream()
                        .filter(t -> m.hunts(t.mod())).count();
                int all = m == Milestone.FIRST_TROPHY ? 1 : (int) Arrays.stream(Trophy.values())
                        .filter(t -> t.implemented && m.hunts(t.mod()) && TrophyHunt.loaded(t)).count();
                yield count(owned, all, "cogwit.guide.roadmap.trophies");
            }
            default -> null;
        };
    }

    private static Progress count(int have, int need, String key) {
        int shown = Math.min(have, need);
        return new Progress(need <= 0 ? 1 : shown / (float) need, Component.translatable(key, shown, need));
    }

    private static void bar(GuiGraphics g, int x, int y, int w, int h, float share, int colour) {
        g.fill(x, y, x + w, y + h, TRACK);
        g.fill(x, y, x + Math.round(w * Math.max(0, Math.min(1, share))), y + h, colour);
    }

    private static String dimension() {
        Minecraft mc = Minecraft.getInstance();
        return mc.level == null ? "minecraft:overworld" : mc.level.dimension().location().toString();
    }

    /** Key name without keyboard-layout translation, so a Russian layout doesn't show K as Л. */
    private static String keyName() {
        String name = GuideClient.ROADMAP_KEY.getKey().getName();
        return name.substring(name.lastIndexOf('.') + 1).toUpperCase();
    }
}
