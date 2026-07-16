package com.xy2407.nsukaddition.client.hud;

import com.xy2407.nsukaddition.client.data.SidebarDataSnapshot;
import com.xy2407.nsukaddition.client.gui.ScrollablePanel;
import com.xy2407.nsukaddition.common.city.ImmigrantData;
import com.xy2407.nsukaddition.common.network.city.ImmigrationActionPacket;
import com.xy2407.nsukaddition.common.network.city.ImmigrationListRequestPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** 移民申请全屏界面，展示移民列表并提供批准/拒绝操作。 */
@OnlyIn(Dist.CLIENT)
public final class ImmigrationScreen extends Screen {

    private static final int BG_COLOR = 0xFF444444;
    private static final int TITLE_BAR_BG = 0xFF303030;
    private static final int DIVIDER = 0xFF555555;
    private static final int TEXT_PRIMARY = 0xFFFFFFFF;
    private static final int TEXT_SECONDARY = 0xFFE6E6E6;
    private static final int TEXT_MUTED = 0xFFBDBDBD;
    private static final int CARD_BG = 0xFF3A3A3A;
    private static final int CARD_BG_ALT = 0xFF363636;
    private static final int BTN_APPROVE_BG = 0xFF4CAF50;
    private static final int BTN_APPROVE_HOVER = 0xFF66BB6A;
    private static final int BTN_REJECT_BG = 0xFFF44336;
    private static final int BTN_REJECT_HOVER = 0xFFEF5350;
    private static final int BTN_TEXT = 0xFFFFFFFF;

    private static final int WINDOW_W = 420;
    private static final int WINDOW_H = 320;
    private static final int PADDING = 12;
    private static final int TITLE_H = 24;
    private static final int ITEM_H = 44;
    private static final int ITEM_GAP = 2;
    private static final int BUTTON_W = 44;
    private static final int BUTTON_H = 18;
    private static final int BUTTON_GAP = 8;

    private static UUID currentCityId = null;
    private static List<ImmigrantData> currentImmigrants = List.of();

    private final ScrollablePanel scrollPanel = new ScrollablePanel();
    private final List<ButtonRect> buttonRects = new ArrayList<>();
    private int mouseX, mouseY;

    public ImmigrationScreen() {
        super(Component.translatable("gui.xy2407_nsuk_addition.immigration.title"));
    }

    @Override
    protected void init() {
        super.init();
        requestRefresh();
        recalcScroll();
    }

    public static void refresh(UUID cityId, List<ImmigrantData> immigrants) {
        currentCityId = cityId;
        currentImmigrants = immigrants == null ? List.of() : immigrants;
        if (Minecraft.getInstance().screen instanceof ImmigrationScreen screen) {
            screen.recalcScroll();
        }
    }

    public static void open() {
        Minecraft mc = Minecraft.getInstance();
        currentCityId = SidebarDataSnapshot.get().cityId();
        mc.setScreen(new ImmigrationScreen());
    }

    private static void requestRefresh() {
        if (currentCityId != null) {
            PacketDistributor.sendToServer(new ImmigrationListRequestPacket(currentCityId));
        }
    }

    private void recalcScroll() {
        int contentH = computeContentH();
        int totalH = currentImmigrants.size() * (ITEM_H + ITEM_GAP);
        scrollPanel.setContent(totalH, contentH);
        buttonRects.clear();
    }

    private int computeContentH() {
        int winY = (height - WINDOW_H) / 2;
        int contentY = winY + TITLE_H + PADDING + 18;
        return winY + WINDOW_H - contentY - PADDING;
    }

    @Override
    public void render(GuiGraphics gg, int mouseX, int mouseY, float partialTick) {
        this.mouseX = mouseX;
        this.mouseY = mouseY;
        gg.fill(0, 0, width, height, 0xCC000000);

        int winX = (width - WINDOW_W) / 2;
        int winY = (height - WINDOW_H) / 2;

        gg.fill(winX, winY, winX + WINDOW_W, winY + WINDOW_H, BG_COLOR);
        gg.renderOutline(winX, winY, WINDOW_W, WINDOW_H, DIVIDER);

        gg.fill(winX + 1, winY + 1, winX + WINDOW_W - 1, winY + TITLE_H, TITLE_BAR_BG);
        gg.fill(winX + 1, winY + TITLE_H, winX + WINDOW_W - 1, winY + TITLE_H + 1, DIVIDER);
        Component titleText = getTitle();
        int titleW = font.width(titleText);
        gg.drawString(font, titleText, winX + (WINDOW_W - titleW) / 2, winY + 6, TEXT_PRIMARY, false);

        Component hint = Component.translatable("gui.xy2407_nsuk_addition.immigration.hint");
        gg.drawString(font, hint, winX + PADDING, winY + TITLE_H + 10, TEXT_MUTED, false);

        int contentY = winY + TITLE_H + PADDING + 18;
        int contentH = winY + WINDOW_H - contentY - PADDING;
        int cardW = scrollPanel.viewportWidth(WINDOW_W, PADDING);

        buttonRects.clear();
        gg.enableScissor(winX + PADDING, contentY, winX + PADDING + cardW, contentY + contentH);

        if (currentImmigrants.isEmpty()) {
            Component emptyText = currentCityId == null
                    ? Component.translatable("gui.xy2407_nsuk_addition.immigration.no_city")
                    : Component.translatable("gui.xy2407_nsuk_addition.immigration.empty");
            int emptyW = font.width(emptyText);
            gg.drawString(font, emptyText, winX + (WINDOW_W - emptyW) / 2,
                    contentY + contentH / 2 - font.lineHeight / 2, TEXT_MUTED, false);
        } else {
            int itemY = contentY - scrollPanel.scrollOffset();
            for (int i = 0; i < currentImmigrants.size(); i++) {
                if (itemY + ITEM_H > contentY && itemY < contentY + contentH) {
                    renderImmigrantRow(gg, winX + PADDING, itemY, cardW, currentImmigrants.get(i), i);
                }
                itemY += ITEM_H + ITEM_GAP;
            }
        }

        gg.disableScissor();

        if (scrollPanel.isScrollbarVisible()) {
            int barX = scrollPanel.scrollbarX(winX + WINDOW_W, PADDING);
            scrollPanel.renderScrollbar(gg, barX, contentY, contentH, mouseX, mouseY);
        }
    }

    private void renderImmigrantRow(GuiGraphics gg, int x, int y, int cardW, ImmigrantData immigrant, int index) {
        int bg = (index % 2 == 0) ? CARD_BG : CARD_BG_ALT;
        gg.fill(x, y, x + cardW, y + ITEM_H, bg);

        gg.drawString(font, Component.literal(immigrant.name()), x + 10, y + 5, TEXT_PRIMARY, false);

        String grantStr = String.format("+%.0f", immigrant.grantFunds());
        int grantColor = 0xFFFFD700;
        gg.drawString(font, Component.literal(grantStr), x + 10, y + 23, grantColor, false);

        int btnY = y + (ITEM_H - BUTTON_H) / 2;
        int approveX = x + cardW - BUTTON_W * 2 - BUTTON_GAP - 10;
        int rejectX = x + cardW - BUTTON_W - 10;

        boolean approveHovered = isMouseOver(approveX, btnY, BUTTON_W, BUTTON_H);
        boolean rejectHovered = isMouseOver(rejectX, btnY, BUTTON_W, BUTTON_H);

        drawButton(gg, approveX, btnY, BUTTON_W, BUTTON_H,
                Component.translatable("gui.xy2407_nsuk_addition.immigration.approve"),
                approveHovered ? BTN_APPROVE_HOVER : BTN_APPROVE_BG);
        drawButton(gg, rejectX, btnY, BUTTON_W, BUTTON_H,
                Component.translatable("gui.xy2407_nsuk_addition.immigration.reject"),
                rejectHovered ? BTN_REJECT_HOVER : BTN_REJECT_BG);

        buttonRects.add(new ButtonRect(approveX, btnY, BUTTON_W, BUTTON_H, immigrant.requestId(), true));
        buttonRects.add(new ButtonRect(rejectX, btnY, BUTTON_W, BUTTON_H, immigrant.requestId(), false));
    }

    private void drawButton(GuiGraphics gg, int x, int y, int w, int h, Component label, int bg) {
        gg.fill(x, y, x + w, y + h, bg);
        gg.renderOutline(x, y, w, h, 0xFF777777);
        int textW = font.width(label);
        gg.drawString(font, label, x + (w - textW) / 2, y + (h - font.lineHeight) / 2, BTN_TEXT, false);
    }

    private boolean isMouseOver(int x, int y, int w, int h) {
        return mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        return scrollPanel.onMouseScrolled(scrollY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) return true;
        if (scrollPanel.onMouseClicked(mouseX, mouseY, button)) return true;

        if (button == 0 && currentCityId != null) {
            for (ButtonRect br : buttonRects) {
                if (mouseX >= br.x && mouseX <= br.x + br.w && mouseY >= br.y && mouseY <= br.y + br.h) {
                    PacketDistributor.sendToServer(new ImmigrationActionPacket(currentCityId, br.requestId, br.approve));
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (scrollPanel.onMouseReleased(mouseX, mouseY, button)) return true;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (scrollPanel.onMouseDragged(mouseX, mouseY)) return true;
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256) {
            onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private record ButtonRect(int x, int y, int w, int h, UUID requestId, boolean approve) {}
}
