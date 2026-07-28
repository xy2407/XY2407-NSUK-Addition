package com.xy2407.nsukaddition.client.gui;

import com.xy2407.nsukaddition.common.menu.CitizenEquipmentMenu;
import common.cn.kafei.simukraft.entity.CitizenEntity;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;

/** 市民装备 GUI 屏幕，绘制背景、槽位底框并在右侧渲染正面朝前的 NPC 模型。 */
@SuppressWarnings("null")
public final class CitizenEquipmentScreen extends AbstractContainerScreen<CitizenEquipmentMenu> {

    private static final int SLOT_SIZE = 18;
    private static final int ARMOR_SLOT_X = 8;
    private static final int[] ARMOR_SLOT_Y = {26, 44, 62, 80};
    private static final int INVENTORY_START_X = 8;
    private static final int INVENTORY_START_Y = 110;
    private static final int HOTBAR_Y = 168;

    private static final int ENTITY_BOX_X1 = 40;
    private static final int ENTITY_BOX_Y1 = 12;
    private static final int ENTITY_BOX_X2 = 120;
    private static final int ENTITY_BOX_Y2 = 92;
    private static final int ENTITY_SCALE = 30;
    private static final float ENTITY_Y_OFFSET = 0.0625F;

    public CitizenEquipmentScreen(CitizenEquipmentMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = 176;
        this.imageHeight = 192;
        this.inventoryLabelY = 100;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int x = this.leftPos;
        int y = this.topPos;
        graphics.fill(x, y, x + this.imageWidth, y + this.imageHeight, 0xFFC6C6C6);

        for (int slotY : ARMOR_SLOT_Y) {
            drawSlot(graphics, x + ARMOR_SLOT_X, y + slotY);
        }

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                drawSlot(graphics, x + INVENTORY_START_X + col * SLOT_SIZE, y + INVENTORY_START_Y + row * SLOT_SIZE);
            }
        }

        for (int col = 0; col < 9; col++) {
            drawSlot(graphics, x + INVENTORY_START_X + col * SLOT_SIZE, y + HOTBAR_Y);
        }

        LivingEntity citizen = findCitizen();
        if (citizen == null) return;
        InventoryScreen.renderEntityInInventoryFollowsAngle(
                graphics,
                x + ENTITY_BOX_X1,
                y + ENTITY_BOX_Y1,
                x + ENTITY_BOX_X2,
                y + ENTITY_BOX_Y2,
                ENTITY_SCALE,
                ENTITY_Y_OFFSET,
                0.0F,
                0.0F,
                citizen
        );
    }

    private LivingEntity findCitizen() {
        int id = this.menu.citizenId();
        if (id < 0 || this.minecraft == null || this.minecraft.level == null) return null;
        net.minecraft.world.entity.Entity entity = this.minecraft.level.getEntity(id);
        return entity instanceof LivingEntity living ? living : null;
    }

    private static void drawSlot(GuiGraphics graphics, int x, int y) {
        graphics.fill(x, y, x + SLOT_SIZE, y + SLOT_SIZE, 0xFF8B8B8B);
        graphics.hLine(x, x + SLOT_SIZE - 1, y, 0xFF373737);
        graphics.hLine(x, x + SLOT_SIZE - 1, y + SLOT_SIZE - 1, 0xFFFFFFFF);
        graphics.vLine(x, y, y + SLOT_SIZE - 1, 0xFF373737);
        graphics.vLine(x + SLOT_SIZE - 1, y, y + SLOT_SIZE - 1, 0xFFFFFFFF);
    }
}
