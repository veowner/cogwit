package dev.cogwit.client;

import dev.cogwit.crew.CrewGear;
import dev.cogwit.crew.CrewGearMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Inventory;

/** Helmet, chestplate and weapon slots next to a live view of the crew member wearing them. */
public class CrewGearScreen extends AbstractContainerScreen<CrewGearMenu> {
    private static final int PANEL = 0xFFC6C6C6, LIGHT = 0xFFFFFFFF, SHADOW = 0xFF555555, SLOT = 0xFF8B8B8B,
            SLOT_DARK = 0xFF373737, VIEW = 0xFF1A1A20, ORANGE = 0xFFC8803F;
    private static final String[] LABELS = {"cogwit.crew.slot.helmet", "cogwit.crew.slot.chest", "cogwit.crew.slot.weapon"};

    public CrewGearScreen(CrewGearMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 176;
        imageHeight = 166;
        inventoryLabelY = CrewGearMenu.INVENTORY_Y - 11;
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int x = leftPos, y = topPos;
        g.fill(x, y, x + imageWidth, y + imageHeight, PANEL);
        g.fill(x, y, x + imageWidth - 1, y + 1, LIGHT);
        g.fill(x, y, x + 1, y + imageHeight - 1, LIGHT);
        g.fill(x + 1, y + imageHeight - 1, x + imageWidth, y + imageHeight, SHADOW);
        g.fill(x + imageWidth - 1, y + 1, x + imageWidth, y + imageHeight, SHADOW);
        g.fill(x, y, x + imageWidth, y + 2, ORANGE);
        for (var slot : menu.slots) {
            int sx = x + slot.x - 1, sy = y + slot.y - 1;
            g.fill(sx, sy, sx + 18, sy + 18, SLOT);
            g.fill(sx, sy, sx + 17, sy + 1, SLOT_DARK);
            g.fill(sx, sy, sx + 1, sy + 17, SLOT_DARK);
            g.fill(sx + 1, sy + 17, sx + 18, sy + 18, LIGHT);
            g.fill(sx + 17, sy + 1, sx + 18, sy + 18, LIGHT);
        }
        int vx0 = x + 98, vy0 = y + 14, vx1 = x + 168, vy1 = y + 74;
        g.fill(vx0, vy0, vx1, vy1, VIEW);
        if (minecraft != null && minecraft.level != null
                && minecraft.level.getEntity(menu.creatureId) instanceof LivingEntity creature) {
            InventoryScreen.renderEntityInInventoryFollowsMouse(g, vx0, vy0, vx1, vy1, 34, 0.0625f, mouseX, mouseY,
                    creature);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        super.renderLabels(g, mouseX, mouseY);
        for (int i = 0; i < CrewGear.SLOTS.length; i++) {
            g.drawString(font, Component.translatable(LABELS[i]), CrewGearMenu.GEAR_X + 22,
                    CrewGearMenu.GEAR_Y + i * 18 + 5, 0xFF404040, false);
        }
        if (minecraft != null && minecraft.level != null
                && minecraft.level.getEntity(menu.creatureId) instanceof LivingEntity creature) {
            String stats = Math.round(creature.getHealth()) + "/" + Math.round(creature.getMaxHealth()) + " ❤  "
                    + Math.round(creature.getAttributeValue(Attributes.ARMOR)) + " ⛨";
            g.drawString(font, stats, 98, 76 - 1, 0xFF404040, false);
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        renderTooltip(g, mouseX, mouseY);
    }
}
