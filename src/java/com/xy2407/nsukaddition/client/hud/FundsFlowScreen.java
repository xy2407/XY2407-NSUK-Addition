package com.xy2407.nsukaddition.client.hud;

import com.xy2407.nsukaddition.client.data.SidebarDataSnapshot;
import com.xy2407.nsukaddition.client.gui.ScrollablePanel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.List;

/** 城市财务流水全屏界面，以卡片列表展示收支记录和余额变化。 */
@OnlyIn(Dist.CLIENT)
public final class FundsFlowScreen extends Screen {

    private static final int BG_COLOR = 0xFF444444;
    private static final int TITLE_BAR_BG = 0xFF303030;
    private static final int DIVIDER = 0xFF555555;
    private static final int TEXT_PRIMARY = 0xFFFFFFFF;
    private static final int TEXT_SECONDARY = 0xFFE6E6E6;
    private static final int TEXT_MUTED = 0xFFBDBDBD;
    private static final int CARD_BG = 0xFF3A3A3A;
    private static final int CARD_BG_ALT = 0xFF363636;
    private static final int COLOR_INCOME = 0xFF66FF66;
    private static final int COLOR_EXPENSE = 0xFFFF6666;
    private static final int COLOR_BALANCE = 0xFFE6B800;

    private static final int WINDOW_W = 420;
    private static final int WINDOW_H = 320;
    private static final int PADDING = 12;
    private static final int TITLE_H = 24;
    private static final int CARD_H = 48;
    private static final int CARD_GAP = 3;

    private final ScrollablePanel scrollPanel = new ScrollablePanel();
    private List<SidebarDataSnapshot.FinanceRecord> records;

    public FundsFlowScreen() {
        super(Component.translatable("gui.xy2407_nsuk_addition.funds_flow.title"));
    }

    @Override
    protected void init() {
        super.init();
        records = SidebarDataSnapshot.get().financeRecords();
        recalcScroll();
    }

    private void recalcScroll() {
        int contentH = WINDOW_H - TITLE_H - PADDING * 2;
        int totalH = records.size() * (CARD_H + CARD_GAP);
        scrollPanel.setContent(totalH, contentH);
    }

    @Override
    public void render(GuiGraphics gg, int mouseX, int mouseY, float partialTick) {

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

        int contentY = winY + TITLE_H + PADDING;
        int contentH = WINDOW_H - TITLE_H - PADDING * 2;
        int cardW = scrollPanel.viewportWidth(WINDOW_W, PADDING);

        gg.enableScissor(winX + PADDING, contentY, winX + PADDING + cardW, contentY + contentH);

        if (records.isEmpty()) {
            Component emptyText = Component.translatable("gui.xy2407_nsuk_addition.funds_flow.empty");
            int emptyW = font.width(emptyText);
            gg.drawString(font, emptyText, winX + (WINDOW_W - emptyW) / 2,
                    contentY + contentH / 2 - font.lineHeight / 2, TEXT_MUTED, false);
        } else {
            int cardY = contentY - scrollPanel.scrollOffset();
            for (int i = 0; i < records.size(); i++) {
                SidebarDataSnapshot.FinanceRecord record = records.get(i);
                if (cardY + CARD_H > contentY && cardY < contentY + contentH) {
                    renderRecordCard(gg, winX + PADDING, cardY, cardW, record, i);
                }
                cardY += CARD_H + CARD_GAP;
            }
        }

        gg.disableScissor();

        if (scrollPanel.isScrollbarVisible()) {
            int barX = scrollPanel.scrollbarX(winX + WINDOW_W, PADDING);
            scrollPanel.renderScrollbar(gg, barX, contentY, contentH, mouseX, mouseY);
        }
    }

    private void renderRecordCard(GuiGraphics gg, int x, int y, int cardW,
                                   SidebarDataSnapshot.FinanceRecord record, int index) {

        int bg = (index % 2 == 0) ? CARD_BG : CARD_BG_ALT;
        gg.fill(x, y, x + cardW, y + CARD_H, bg);

        boolean isIncome = "INCOME".equals(record.type());
        String prefix = isIncome ? "+" : "-";
        int prefixColor = isIncome ? COLOR_INCOME : COLOR_EXPENSE;

        String amountStr = prefix + String.format("%.1f", Math.abs(record.amount()));
        gg.drawString(font, Component.literal(amountStr), x + 10, y + 6, prefixColor, false);

        String timeStr = formatGameTime(record.time());
        int timeW = font.width(timeStr);
        gg.drawString(font, Component.literal(timeStr), x + cardW - timeW - 10, y + 6, TEXT_MUTED, false);

        String reason = record.reason();
        if (reason.length() > 28) {
            reason = reason.substring(0, 26) + "..";
        }
        gg.drawString(font, Component.literal(reason), x + 10, y + 24, TEXT_SECONDARY, false);

        String balanceStr = "余额: " + String.format("%.1f", record.balanceAfter());
        int balanceW = font.width(balanceStr);
        gg.drawString(font, Component.literal(balanceStr), x + cardW - balanceW - 10, y + 24, COLOR_BALANCE, false);
    }

    private static String formatGameTime(long tick) {
        long day = tick / 24000 + 1;
        return "第" + day + "天";
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        return scrollPanel.onMouseScrolled(scrollY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (scrollPanel.onMouseClicked(mouseX, mouseY, button)) return true;
        return super.mouseClicked(mouseX, mouseY, button);
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

    public static void open() {
        Minecraft.getInstance().setScreen(new FundsFlowScreen());
    }
}
