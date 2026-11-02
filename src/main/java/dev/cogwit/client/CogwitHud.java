package dev.cogwit.client;

import dev.cogwit.ClientConfig;
import dev.cogwit.DamageCategories;
import dev.cogwit.brain.Brain;
import dev.cogwit.brain.HudView;
import dev.cogwit.cells.CellBank;
import dev.cogwit.cells.Cells;
import dev.cogwit.crew.CrewService;
import dev.cogwit.mind.Mind;
import dev.cogwit.mind.MindService;
import dev.cogwit.ultra.UltraService;
import dev.cogwit.ultra.UltraState;
import dev.cogwit.ultra.Overdrive;
import dev.cogwit.item.CogwitTalismanItem;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/** Draws Cogwit's status panel: tokens, mood, the context window and cooldowns. */
public final class CogwitHud {
    public static final int WIDTH = 132;

    private static final int PAD = 4;
    private static final int ROW_HEIGHT = 12;
    private static final int ORANGE = 0xFFC8803F;
    private static final int BACKGROUND = 0xC0141416;
    private static final int TRACK = 0xFF2C2C31;
    private static final int TEXT_DIM = 0xFF8A8A8F;
    private static final int TEXT = 0xFFE8E8EA;
    private static final int GREEN = 0xFF7CE07C;
    private static final int YELLOW = 0xFFFFD27A;
    private static final int BLUE = 0xFF8CC8FF;
    private static final int AQUA = 0xFF66E0E0;
    private static final int RED = 0xFFE05555;

    private CogwitHud() {}

    /** The compact panel: name, mood, level and the token bar (plus cells and the sleep timer). */
    private static final int COMPACT_HEIGHT = 46;

    public static int height() {
        int cells = hasArmorSockets(cellSlots()) ? CELL_ROW : 0;
        if (!ClientConfig.detailed()) {
            HudView v = localView();
            return COMPACT_HEIGHT + cells + (v != null && v.mood() == HudView.Mood.ASLEEP ? CELL_ROW + 2 : 0);
        }
        return 58 + Brain.CONTEXT_SIZE * ROW_HEIGHT + 34 + cells + (hasExtraRow(localView()) ? CELL_ROW : 0);
    }

    private static final int PURPLE = 0xFFB98CFF;

    /** The local player's Cogwit as the panel sees it, or null without one. */
    private static HudView localView() {
        Player player = Minecraft.getInstance().player;
        if (player == null) {
            return null;
        }
        long now = player.level().getGameTime();
        return CogwitTalismanItem.worn(player).map(stack -> HudView.of(CogwitTalismanItem.brain(stack), now))
                .orElse(null);
    }

    /** A last row for the Copper Apple wake timer while asleep, or the back shield once it is learned. */
    private static boolean hasExtraRow(HudView v) {
        return v != null && (v.mood() == HudView.Mood.ASLEEP || v.backShieldUnlocked());
    }

    private static final int CELL_ROW = 10;
    private static final EquipmentSlot[] ARMOR_ORDER = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS,
            EquipmentSlot.FEET};
    private static final int[] SOCKETS_PER_SLOT = {Cells.SOCKETS_HEAD, Cells.SOCKETS_CHEST, Cells.SOCKETS_LEGS,
            Cells.SOCKETS_FEET};
    /** Cell slot states for the HUD: a charge, or one of these. */
    private static final int NO_ARMOR = -2, EMPTY_SOCKET = -1;

    /** The 8 sockets of the local player's armor, head to feet. */
    static int[] cellSlots() {
        Player player = Minecraft.getInstance().player;
        int[] slots = new int[8];
        int i = 0;
        for (int piece = 0; piece < ARMOR_ORDER.length; piece++) {
            ItemStack armor = player == null ? ItemStack.EMPTY : player.getItemBySlot(ARMOR_ORDER[piece]);
            List<Integer> cells = CellBank.sockets(armor) > 0 ? CellBank.cells(armor) : null;
            for (int k = 0; k < SOCKETS_PER_SLOT[piece]; k++) {
                slots[i++] = cells == null ? NO_ARMOR : k < cells.size() ? cells.get(k) : EMPTY_SOCKET;
            }
        }
        return slots;
    }

    private static boolean hasArmorSockets(int[] slots) {
        for (int s : slots) {
            if (s != NO_ARMOR) return true;
        }
        return false;
    }

    public static void render(GuiGraphics g, Font font, ItemStack stack, HudView v, int x, int y) {
        int right = x + WIDTH;
        g.fill(x, y, right, y + height(), BACKGROUND);
        g.fill(x, y, right, y + 1, ORANGE);

        // header: icon, name, mood
        g.renderItem(stack, x + PAD, y + 5);
        g.drawString(font, Component.translatable("cogwit.name").withStyle(ChatFormatting.BOLD), x + 24, y + 5,
                ORANGE, true);
        long now = Minecraft.getInstance().level != null ? Minecraft.getInstance().level.getGameTime() : 0;
        Status status = status(stack, v, now);
        if (UltraService.isActive(stack)) {
            g.fill(x, y, right, y + 1, status.colour());
        }
        g.drawString(font, status.text(), x + 24, y + 15, status.colour(), true);
        Component level = Component.translatable("cogwit.hud.level", v.level());
        g.drawString(font, level, right - PAD - font.width(level), y + 5, PURPLE, true);
        bar(g, x + 24, y + 25, WIDTH - 24 - PAD, 1, v.levelProgress(), PURPLE);

        // tokens
        int ty = y + 28;
        int[] slots = cellSlots();
        int stored = 0, inserted = 0;
        for (int charge : slots) {
            if (charge >= 0) {
                stored += charge;
                inserted++;
            }
        }
        int total = v.tokens() + stored;
        int capacity = Cells.capacity(Brain.MAX_TOKENS, inserted);
        g.drawString(font, Component.translatable("cogwit.hud.tokens"), x + PAD, ty, TEXT_DIM, false);
        String amount = total + "/" + capacity;
        g.drawString(font, amount, right - PAD - font.width(amount), ty, TEXT, false);
        bar(g, x + PAD, ty + 10, WIDTH - 2 * PAD, 4, (float) total / capacity, total < 200 ? RED : ORANGE);
        if (hasArmorSockets(slots)) {
            cells(g, slots, x, ty + 17);
            y += CELL_ROW;
        }
        if (!ClientConfig.detailed()) {
            // skills, cooldowns and passives live in the detailed panel; only the wake timer matters here
            if (v.mood() == HudView.Mood.ASLEEP) {
                cooldown(g, font, Component.translatable("cogwit.hud.apple"), v.wakeTicksLeft(), x, y + 46);
            }
            return;
        }

        // context window
        int cy = y + 46;
        g.drawString(font, Component.translatable("cogwit.hud.context", v.rows().size(), Brain.CONTEXT_SIZE),
                x + PAD, cy, TEXT_DIM, false);
        int ry = cy + 11;
        for (int i = 0; i < Brain.CONTEXT_SIZE; i++, ry += ROW_HEIGHT) {
            if (i < v.rows().size()) {
                row(g, font, v.rows().get(i), x, ry);
            } else {
                g.drawString(font, Component.translatable("cogwit.hud.empty"), x + PAD + 2, ry + 1, 0xFF4A4A50, false);
            }
        }

        // cooldowns and passives
        int fy = ry + 3;
        g.fill(x + PAD, fy - 2, right - PAD, fy - 1, 0x30FFFFFF);
        cooldown(g, font, Component.translatable("cogwit.hud.think"), v.thinkTicksLeft(), x, fy);
        cooldown(g, font, Component.literal("/compact"), v.compactTicksLeft(), x, fy + 10);
        g.drawString(font, Component.translatable("cogwit.hud.regen"), x + PAD, fy + 20,
                v.regenActive() ? GREEN : 0xFF505056, false);
        Component shield = Component.translatable("cogwit.hud.immunity");
        g.drawString(font, shield, right - PAD - font.width(shield), fy + 20,
                v.shieldReady() ? GREEN : 0xFF505056, false);
        if (v.mood() == HudView.Mood.ASLEEP) {
            cooldown(g, font, Component.translatable("cogwit.hud.apple"), v.wakeTicksLeft(), x, fy + 30);
        } else if (v.backShieldUnlocked()) {
            cooldown(g, font, Component.translatable("cogwit.hud.back_shield"), v.backShieldTicksLeft(), x, fy + 30);
        }
    }

    /** Eight mini batteries: filled by charge, outlined when empty, dim when that armor piece isn't worn. */
    private static void cells(GuiGraphics g, int[] slots, int x, int y) {
        int box = (WIDTH - 2 * PAD - 7 * 2) / 8;
        for (int i = 0; i < slots.length; i++) {
            int bx = x + PAD + i * (box + 2);
            if (slots[i] == NO_ARMOR) {
                g.fill(bx, y, bx + box, y + 6, 0xFF1E1E22);
                continue;
            }
            g.fill(bx, y, bx + box, y + 6, TRACK);
            g.fill(bx, y, bx + box, y + 1, 0xFF4A4A50);
            if (slots[i] > 0) {
                int filled = Math.max(1, Math.round(box * (float) slots[i] / Cells.CAPACITY));
                g.fill(bx, y + 1, bx + filled, y + 6, ORANGE);
            }
        }
    }

    private static void row(GuiGraphics g, Font font, HudView.Row row, int x, int y) {
        int right = x + WIDTH;
        if (row.recentlyUsed()) {
            g.fill(x + 1, y - 1, right - 1, y + ROW_HEIGHT - 1, 0x55C8803F);
        }
        String value = row.compacted() ? row.percent() + "·" + row.mergedPercent() + "%" : row.percent() + "%";
        int valueColor = row.compacted() ? AQUA : row.percent() > 0 ? GREEN : TEXT_DIM;
        g.drawString(font, value, right - PAD - font.width(value), y + 1, valueColor, false);

        Component name = row.compacted()
                ? DamageCategories.name(row.category()).copy().append("+").append(DamageCategories.name(row.merged()))
                : DamageCategories.name(row.category());
        int nameWidth = WIDTH - 2 * PAD - font.width(value) - 4;
        String shown = font.plainSubstrByWidth(name.getString(), nameWidth);
        g.drawString(font, shown, x + PAD, y + 1, row.compacted() ? AQUA : TEXT, false);

        bar(g, x + PAD, y + 10, WIDTH - 2 * PAD, 1, row.levelProgress(), row.compacted() ? AQUA : 0xFF5FBF5F);
    }

    private static void cooldown(GuiGraphics g, Font font, Component label, long ticksLeft, int x, int y) {
        g.drawString(font, label, x + PAD, y, TEXT_DIM, false);
        Component state = ticksLeft == 0
                ? Component.translatable("cogwit.hud.ready")
                : Component.literal(Brain.clock(ticksLeft));
        g.drawString(font, state, x + WIDTH - PAD - font.width(state), y, ticksLeft == 0 ? GREEN : YELLOW, false);
    }

    private static void bar(GuiGraphics g, int x, int y, int width, int height, float fill, int color) {
        g.fill(x, y, x + width, y + height, TRACK);
        int filled = Math.round(width * Math.max(0f, Math.min(1f, fill)));
        if (filled > 0) {
            g.fill(x, y, x + filled, y + height, color);
        }
    }

    /** One line saying how Cogwit is, and its colour: rage, hangover, fighting, an emotion, or his plain mood. */
    public record Status(Component text, int colour) {}

    public static Status status(ItemStack stack, HudView v, long now) {
        UltraState ultra = UltraService.state(stack);
        if (ultra.active()) {
            return new Status(Component.translatable("cogwit.hud.overdrive"), now % 6 < 3 ? 0xFFC77DFF : 0xFFFF5A7A);
        }
        if (Overdrive.inHangover(ultra, now) && v.mood() != HudView.Mood.ASLEEP) {
            return new Status(Component.translatable("cogwit.hud.hangover", Brain.clock(ultra.hangoverUntil() - now)),
                    YELLOW);
        }
        if (CrewService.isDeployed(stack) && v.mood() != HudView.Mood.ASLEEP) {
            return new Status(Component.translatable("cogwit.hud.fighting"), ORANGE);
        }
        Mind.Emotion emotion = Mind.emotion(MindService.state(stack), now);
        if (v.mood() == HudView.Mood.AWAKE && emotion != Mind.Emotion.CALM) {
            boolean scared = emotion == Mind.Emotion.SCARED;
            return new Status(Component.translatable(scared ? "cogwit.hud.scared" : "cogwit.hud.happy"),
                    scared ? RED : GREEN);
        }
        return new Status(mood(v), moodColor(v));
    }

    private static Component mood(HudView v) {
        return switch (v.mood()) {
            case AWAKE -> Component.translatable("cogwit.hud.awake");
            case THINKING -> Component.translatable("cogwit.hud.thinking", Brain.clock(v.moodTicksLeft()));
            case ASLEEP -> Component.translatable("cogwit.hud.asleep", Brain.clock(v.moodTicksLeft()));
        };
    }

    private static int moodColor(HudView v) {
        return switch (v.mood()) {
            case AWAKE -> GREEN;
            case THINKING -> YELLOW;
            case ASLEEP -> BLUE;
        };
    }
}
