package com.xy2407.nsukaddition.client.hud;

import com.xy2407.nsukaddition.client.data.SidebarDataSnapshot;
import com.xy2407.nsukaddition.common.material.MaterialCategoryRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** 侧边栏建造任务追踪区域渲染，展示进度条和材料需求。 */
final class QuestSectionRenderer {
    private static final Map<String, ItemStack> ICON_CACHE = new HashMap<>();
    private static final Cache CACHE = new Cache();

    private QuestSectionRenderer() {}

    static int render(GuiGraphics gg, PopupLayoutManager.Rect r, Minecraft mc, Matrix4f matrix, int startY,
                      BatchTextRenderer textRenderer, BatchRectRenderer rectRenderer) {
        CACHE.refresh(mc, textRenderer);

        int padX = SidebarLayout.PAD_X;
        int leftX = r.x() + padX;
        int titleH = SidebarLayout.TITLE_LINE_H;
        textRenderer.drawText(mc.font, CACHE.title, leftX, startY, SidebarHudLayer.TEXT_PRIMARY, false, matrix);

        if (!CACHE.hasTask) {
            textRenderer.drawText(mc.font, CACHE.emptyText,
                    leftX, startY + titleH + SidebarLayout.TITLE_CONTENT_GAP,
                    SidebarHudLayer.TEXT_MUTED, false, matrix);
            return startY + titleH + SidebarLayout.TITLE_CONTENT_GAP + mc.font.lineHeight + SidebarLayout.SECTION_GAP;
        }

        int contentY = startY + titleH + SidebarLayout.TITLE_CONTENT_GAP;
        int barW = r.width() - padX * 2;
        textRenderer.drawText(mc.font, CACHE.headerText, leftX, contentY,
                SidebarHudLayer.TEXT_PRIMARY, false, matrix);

        int barY = contentY + mc.font.lineHeight + 6;
        rectRenderer.fill(matrix, leftX, barY, barW, SidebarLayout.BAR_H, 0xFF444444);
        if (CACHE.fillWidth > 0) {
            rectRenderer.fill(matrix, leftX, barY, CACHE.fillWidth, SidebarLayout.BAR_H, SidebarHudLayer.COLOR_SUCCESS);
        }

        int statusY = barY + SidebarLayout.BAR_H + 6;
        textRenderer.drawText(mc.font, CACHE.statusText, leftX, statusY,
                SidebarHudLayer.TEXT_SECONDARY, false, matrix);

        int materialY = statusY + mc.font.lineHeight + 6;
        int colGap = 8;
        int colWidth = (barW - colGap) / 2;
        int rightColX = leftX + colWidth + colGap;
        int maxPerCol = 6;
        int rowH = mc.font.lineHeight + 4;

        for (int i = 0; i < CACHE.materialRows.size(); i++) {
            MaterialRowCache row = CACHE.materialRows.get(i);
            int rowIndex = i % maxPerCol;
            int colX = i < maxPerCol ? leftX : rightColX;
            renderMaterialRow(gg, mc, matrix, textRenderer, row, colX, materialY + rowIndex * rowH);
        }

        int materialRows = Math.min(maxPerCol, (CACHE.materialRows.size() + 1) / 2);
        return materialY + materialRows * rowH + SidebarLayout.SECTION_GAP;
    }

    private static void renderMaterialRow(GuiGraphics gg, Minecraft mc, Matrix4f matrix,
                                          BatchTextRenderer textRenderer, MaterialRowCache row, int x, int y) {
        if (!row.icon.isEmpty()) {
            gg.pose().pushPose();
            gg.pose().scale(0.7F, 0.7F, 1.0F);
            gg.renderFakeItem(row.icon, (int) ((x + 2) / 0.7F), (int) ((y - 2) / 0.7F));
            gg.pose().popPose();
        }
        textRenderer.drawText(mc.font, row.text, x + 18, y, row.color, false, matrix);
    }

    private static SidebarDataSnapshot.BuildTask findTrackedTask(SidebarDataSnapshot data) {
        for (SidebarDataSnapshot.BuildTask task : data.buildTasks()) {
            if (task.tracked()) return task;
        }
        return null;
    }

    private static final class Cache {
        private String language = "";
        private SidebarDataSnapshot snapshot;
        private String title = "";
        private String emptyText = "";
        private boolean hasTask;
        private String headerText = "";
        private String statusText = "";
        private int fillWidth;
        private final List<MaterialRowCache> materialRows = new ArrayList<>();

        void refresh(Minecraft mc, BatchTextRenderer textRenderer) {
            SidebarDataSnapshot current = SidebarDataSnapshot.get();
            String currentLanguage = mc.getLanguageManager().getSelected();
            if (Objects.equals(language, currentLanguage) && snapshot == current) {
                return;
            }
            language = currentLanguage;
            snapshot = current;
            title = Component.translatable("hud.xy2407_nsuk_addition.quest.title").getString();
            emptyText = Component.translatable("hud.xy2407_nsuk_addition.quest.empty").getString();
            materialRows.clear();

            if (!current.hasBuildTask()) {
                hasTask = false;
                headerText = "";
                statusText = "";
                fillWidth = 0;
                return;
            }

            hasTask = true;
            SidebarDataSnapshot.BuildTask task = findTrackedTask(current);
            if (task == null) {
                task = current.buildTasks().get(0);
            }
            headerText = task.displayName() + "  " + task.progressPercent() + "%";
            statusText = Component.translatable("hud.xy2407_nsuk_addition.quest.status." + task.statusKey()).getString();
            fillWidth = (int) ((task.progressPercent() / 100.0) * 408.0);

            int shown = 0;
            for (SidebarDataSnapshot.BuildTaskMaterial material : task.materials()) {
                if (shown >= 12) break;
                materialRows.add(new MaterialRowCache(material));
                shown++;
            }
        }
    }

    private static final class MaterialRowCache {
        final ItemStack icon;
        final String text;
        final int color;

        MaterialRowCache(SidebarDataSnapshot.BuildTaskMaterial material) {
            boolean complete = material.available() >= material.required();
            this.color = complete ? SidebarHudLayer.COLOR_SUCCESS : SidebarHudLayer.COLOR_WARNING;
            this.icon = ICON_CACHE.computeIfAbsent(material.categoryKey(), MaterialCategoryRegistry::getDisplayStack);
            this.text = Component.translatable("hud.xy2407_nsuk_addition.material." + material.categoryKey()).getString()
                    + "  " + material.available() + "/" + material.required();
        }
    }
}
