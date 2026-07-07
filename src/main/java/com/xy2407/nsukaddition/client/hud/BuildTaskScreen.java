package com.xy2407.nsukaddition.client.hud;

import com.xy2407.nsukaddition.client.data.SidebarDataSnapshot;
import com.xy2407.nsukaddition.client.gui.ScrollablePanel;
import com.xy2407.nsukaddition.common.network.building.BuildTaskActionPacket;
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

/** 建造任务全屏界面，展示任务卡片、进度条、材料需求和操作按钮。 */
@OnlyIn(Dist.CLIENT)
public final class BuildTaskScreen extends Screen {

    private static final int BG_COLOR = 0xFF444444;
    private static final int TITLE_BAR_BG = 0xFF303030;
    private static final int DIVIDER = 0xFF555555;
    private static final int TEXT_PRIMARY = 0xFFFFFFFF;
    private static final int TEXT_SECONDARY = 0xFFE6E6E6;
    private static final int TEXT_MUTED = 0xFFBDBDBD;
    private static final int CARD_BG = 0xFF3A3A3A;
    private static final int PROGRESS_BG = 0xFF555555;
    private static final int PROGRESS_FILL = 0xFFE6B800;
    private static final int COLOR_SUCCESS = 0xFF66FF66;
    private static final int COLOR_WARNING = 0xFFFFFF55;
    private static final int BTN_BG = 0xFFE0E0E0;
    private static final int BTN_BG_HOVER = 0xFFFFFFFF;
    private static final int BTN_BORDER = 0xFFAAAAAA;
    private static final int BTN_TEXT = 0xFF333333;
    private static final int BTN_TRACKED_BG = 0xFF71A4F4;

    private static final int WINDOW_W = 420;
    private static final int WINDOW_H = 320;
    private static final int PADDING = 12;
    private static final int TITLE_H = 24;
    private static final int CARD_H = 78;
    private static final int CARD_GAP = 4;

    private final ScrollablePanel scrollPanel = new ScrollablePanel();
    private List<SidebarDataSnapshot.BuildTask> tasks;
    private List<Button> buttons = new ArrayList<>();

    public BuildTaskScreen() {
        super(Component.translatable("gui.xy2407_nsuk_addition.build_tasks.title"));
    }

    @Override
    protected void init() {
        super.init();
        tasks = SidebarDataSnapshot.get().buildTasks();
        recalcScroll();
    }

    private void recalcScroll() {
        int contentH = tasks.size() * (CARD_H + CARD_GAP);
        int viewportH = WINDOW_H - TITLE_H - PADDING * 2;
        scrollPanel.setContent(contentH, viewportH);
    }

    @Override
    public void render(GuiGraphics gg, int mouseX, int mouseY, float partialTick) {
        buttons.clear();
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

        if (tasks.isEmpty()) {
            Component emptyText = Component.translatable("hud.xy2407_nsuk_addition.quest.empty");
            int emptyW = font.width(emptyText);
            gg.drawString(font, emptyText, winX + (WINDOW_W - emptyW) / 2,
                    contentY + contentH / 2 - font.lineHeight / 2, TEXT_MUTED, false);
        } else {
            int cardY = contentY - scrollPanel.scrollOffset();
            for (int i = 0; i < tasks.size(); i++) {
                if (cardY + CARD_H > contentY && cardY < contentY + contentH) {
                    renderTaskCard(gg, winX + PADDING, cardY, cardW, tasks.get(i));
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

    private void renderTaskCard(GuiGraphics gg, int x, int y, int cardW, SidebarDataSnapshot.BuildTask task) {
        gg.fill(x, y, x + cardW, y + CARD_H, CARD_BG);

        gg.drawString(font, Component.literal(task.displayName()), x + 8, y + 4, TEXT_PRIMARY, false);

        String statusLabel = getStatusLabel(task.statusKey());
        int statusColor = isPausedOrWaiting(task.statusKey()) ? COLOR_WARNING : COLOR_SUCCESS;
        int statusW = font.width(statusLabel);
        gg.drawString(font, Component.literal(statusLabel), x + cardW - statusW - 8, y + 4, statusColor, false);

        int barY = y + 18;
        int barH = 10;
        gg.fill(x + 6, barY, x + cardW - 6, barY + barH, PROGRESS_BG);
        int fillW = (int) ((cardW - 12) * (task.progressPercent() / 100.0f));
        if (fillW > 0) {
            gg.fill(x + 6, barY, x + 6 + fillW, barY + barH, PROGRESS_FILL);
        }
        String progText = task.progressPercent() + "%";
        int progW = font.width(progText);
        gg.drawString(font, Component.literal(progText), x + cardW - progW - 8, barY, TEXT_PRIMARY, false);

        int matY = barY + barH + 4;
        List<SidebarDataSnapshot.BuildTaskMaterial> materials = task.materials();
        if (!materials.isEmpty()) {
            StringBuilder matLine = new StringBuilder();
            int maxChars = (cardW - 16) / (font.width("M") + 1);
            int charCount = 0;
            for (int i = 0; i < materials.size() && charCount < maxChars; i++) {
                SidebarDataSnapshot.BuildTaskMaterial mat = materials.get(i);
                String label = Component.translatable("hud.xy2407_nsuk_addition.material." + mat.categoryKey()).getString();
                String entry = label + " " + mat.available() + "/" + mat.required();
                if (i > 0) entry = "  " + entry;
                if (charCount + entry.length() > maxChars) {
                    matLine.append("...");
                    break;
                }
                matLine.append(entry);
                charCount += entry.length();
            }
            gg.drawString(font, Component.literal(matLine.toString()), x + 8, matY, TEXT_SECONDARY, false);
        }

        int btnH = 16;
        int btnGap = 4;
        int btnTop = y + CARD_H - btnH - 6;
        int btnAreaW = cardW - 16;
        int btnW = (btnAreaW - btnGap * 2) / 3;
        boolean paused = isPaused(task.statusKey());

        drawActionButton(gg, x + 8, btnTop, btnW, btnH, paused ? "恢复" : "暂停",
                paused ? BuildTaskActionPacket.Action.RESUME : BuildTaskActionPacket.Action.PAUSE, task.citizenId(), false);
        drawActionButton(gg, x + 8 + btnW + btnGap, btnTop, btnW, btnH, "追踪",
                BuildTaskActionPacket.Action.TRACK, task.citizenId(), task.tracked());
    }

    private void drawActionButton(GuiGraphics gg, int x, int y, int w, int h,
                                   String label, BuildTaskActionPacket.Action action,
                                   String citizenId, boolean active) {
        boolean hovered = mouseIn(x, y, w, h);
        int bg = active ? BTN_TRACKED_BG : (hovered ? BTN_BG_HOVER : BTN_BG);
        gg.fill(x, y, x + w, y + h, bg);
        gg.renderOutline(x, y, w, h, active ? 0xFFFFFFFF : BTN_BORDER);
        int tw = font.width(label);
        gg.drawString(font, Component.literal(label), x + (w - tw) / 2,
                y + (h - font.lineHeight) / 2, BTN_TEXT, false);
        buttons.add(new Button(x, y, w, h, citizenId, action));
    }

    private static boolean mouseIn(int x, int y, int w, int h) {
        Minecraft mc = Minecraft.getInstance();
        double scale = mc.getWindow().getGuiScale();
        int mx = (int) (mc.mouseHandler.xpos() / scale);
        int my = (int) (mc.mouseHandler.ypos() / scale);
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }

    private static String getStatusLabel(String statusKey) {
        return switch (statusKey) {
            case "building" -> "建造中";
            case "queued" -> "排队中";
            case "waiting_materials" -> "等待材料";
            case "paused_manual" -> Component.translatable("gui.xy2407_nsuk_addition.build_tasks.status.paused_manual").getString();
            case "paused_resting" -> Component.translatable("hud.xy2407_nsuk_addition.quest.status.paused_resting").getString();
            case "paused_offline" -> Component.translatable("hud.xy2407_nsuk_addition.quest.status.paused_offline").getString();
            case "completed" -> "已完成";
            default -> statusKey;
        };
    }

    private static boolean isPaused(String statusKey) {

        return "paused_manual".equals(statusKey);
    }

    private static boolean isPausedOrWaiting(String statusKey) {
        return "paused_manual".equals(statusKey)
                || "paused_resting".equals(statusKey)
                || "paused_offline".equals(statusKey)
                || "waiting_materials".equals(statusKey);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        return scrollPanel.onMouseScrolled(scrollY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (scrollPanel.onMouseClicked(mouseX, mouseY, button)) return true;
        if (button == 0) {
            for (Button btn : buttons) {
                if (mouseX >= btn.x && mouseX <= btn.x + btn.w && mouseY >= btn.y && mouseY <= btn.y + btn.h) {
                    try {
                        PacketDistributor.sendToServer(new BuildTaskActionPacket(UUID.fromString(btn.citizenId), btn.action));
                    } catch (Exception ignored) {
                    }
                    return true;
                }
            }
        }
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
        Minecraft.getInstance().setScreen(new BuildTaskScreen());
    }

    private record Button(int x, int y, int w, int h, String citizenId, BuildTaskActionPacket.Action action) {}
}
