package com.xy2407.nsukaddition.client.hud;

import com.xy2407.nsukaddition.client.data.SidebarDataSnapshot;
import com.xy2407.nsukaddition.common.material.MaterialCategory;
import com.xy2407.nsukaddition.common.material.MaterialCategoryRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** 侧边栏建筑统计和材料储备双栏渲染。 */
final class BuildStatsSectionRenderer {
    private static final Cache CACHE = new Cache();

    private BuildStatsSectionRenderer() {}

    static int render(Minecraft mc, int startY, BatchTextRenderer textRenderer, BatchRectRenderer rectRenderer) {
        CACHE.refresh(mc, textRenderer);

        int padX = SidebarLayout.PAD_X;
        int lineH = SidebarLayout.LINE_H;
        int titleH = SidebarLayout.TITLE_LINE_H;
        int contentW = SidebarHudLayer.REF_WIDTH - padX * 2;
        int leftWidth = (int) (contentW * SidebarLayout.LEFT_RATIO);
        int rightX = padX + leftWidth;
        int leftX = padX;
        int leftContentW = leftWidth - SidebarLayout.DIVIDER_TEXT_GAP;

        int rightContentX = rightX + SidebarLayout.DIVIDER_TEXT_GAP;
        int rightContentW = contentW - leftWidth - SidebarLayout.DIVIDER_TEXT_GAP;
        int rightColGap = 14;
        int rightColInset = 4;
        int rightColWidth = (rightContentW - rightColGap) / 2;
        int rightCol2X = rightContentX + rightColWidth + rightColGap;
        int rightCol1ContentX = rightContentX;
        int rightCol2ContentX = rightCol2X + rightColInset;
        int rightColContentW = rightColWidth - rightColInset;

        int leftRowCount = CACHE.leftRows.size();
        int rightCol1Count = (CACHE.rightRows.size() + 1) / 2;
        int rowCount = Math.max(leftRowCount, rightCol1Count);
        int sectionH = titleH + lineH * rowCount + SidebarLayout.DIVIDER_BOTTOM_GAP;

        textRenderer.drawText(mc.font, CACHE.leftTitle, leftX, startY, SidebarHudLayer.TEXT_PRIMARY, false);
        textRenderer.drawText(mc.font, CACHE.rightTitle, rightContentX, startY, SidebarHudLayer.TEXT_PRIMARY, false);

        int rowY = startY + titleH;
        for (int i = 0; i < rowCount; i++) {
            if (i < leftRowCount) {
                renderRow(mc, textRenderer, CACHE.leftRows.get(i), leftX, rowY, leftContentW);
            }
            if (i < rightCol1Count) {
                renderRow(mc, textRenderer, CACHE.rightRows.get(i), rightCol1ContentX, rowY, rightColContentW);
            }
            int col2Index = i + rightCol1Count;
            if (col2Index < CACHE.rightRows.size()) {
                renderRow(mc, textRenderer, CACHE.rightRows.get(col2Index), rightCol2ContentX, rowY, rightColContentW);
            }
            rowY += lineH;
        }

        rectRenderer.fill(rightX - 1, startY, 1, sectionH, SidebarHudLayer.DIVIDER);
        int dividerX = rightContentX + rightColWidth + rightColGap / 2;
        int dashTop = startY + titleH;
        int dashBottom = startY + sectionH - SidebarLayout.DIVIDER_BOTTOM_GAP;
        for (int dy = dashTop; dy < dashBottom; dy += 5) {
            int dashHeight = Math.min(2, dashBottom - dy);
            rectRenderer.fill(dividerX, dy, 1, dashHeight, SidebarHudLayer.DIVIDER);
        }

        int bottomY = startY + sectionH;
        rectRenderer.fill(padX, bottomY, contentW, 1, SidebarHudLayer.DIVIDER);
        return bottomY + SidebarLayout.SECTION_GAP;
    }

    private static void renderRow(Minecraft mc, BatchTextRenderer textRenderer,
                                  RowCache row, int x, int y, int width) {
        textRenderer.drawText(mc.font, row.label, x, y, SidebarHudLayer.TEXT_SECONDARY, false);
        textRenderer.drawText(mc.font, row.value, x + width - row.valueWidth, y,
                SidebarHudLayer.TEXT_PRIMARY, false);
    }

    private static final class Cache {
        private String language = "";
        private SidebarDataSnapshot snapshot;
        private String leftTitle = "";
        private String rightTitle = "";
        private final List<RowCache> leftRows = new ArrayList<>();
        private final List<RowCache> rightRows = new ArrayList<>();

        void refresh(Minecraft mc, BatchTextRenderer textRenderer) {
            SidebarDataSnapshot current = SidebarDataSnapshot.get();
            String currentLanguage = mc.getLanguageManager().getSelected();
            if (Objects.equals(language, currentLanguage) && snapshot == current) {
                return;
            }
            language = currentLanguage;
            snapshot = current;

            leftTitle = Component.translatable("hud.xy2407_nsuk_addition.build_stats.title").getString();
            rightTitle = Component.translatable("hud.xy2407_nsuk_addition.material.title").getString();

            leftRows.clear();
            leftRows.add(new RowCache(textRenderer, mc, "hud.xy2407_nsuk_addition.build_stats.shop", current.shopCount()));
            leftRows.add(new RowCache(textRenderer, mc, "hud.xy2407_nsuk_addition.build_stats.factory", current.factoryCount()));
            leftRows.add(new RowCache(textRenderer, mc, "hud.xy2407_nsuk_addition.build_stats.residence", current.residenceCount()));
            leftRows.add(new RowCache(textRenderer, mc, "hud.xy2407_nsuk_addition.build_stats.farm", current.farmCount()));
            leftRows.add(new RowCache(textRenderer, mc, "hud.xy2407_nsuk_addition.build_stats.ranch", current.ranchCount()));
            leftRows.add(new RowCache(textRenderer, mc, "hud.xy2407_nsuk_addition.build_stats.mine", current.mineCount()));

            rightRows.clear();
            for (MaterialCategory category : MaterialCategoryRegistry.getAll()) {
                rightRows.add(new RowCache(textRenderer, mc,
                        "hud.xy2407_nsuk_addition.material." + category.key(),
                        current.reserveCount(category.key())));
            }
        }
    }

    private static final class RowCache {
        final String label;
        final String value;
        final int valueWidth;

        RowCache(BatchTextRenderer textRenderer, Minecraft mc, String labelKey, int count) {
            this.label = Component.translatable(labelKey).getString();
            this.value = String.valueOf(count);
            this.valueWidth = textRenderer.calcWidth(mc.font, value);
        }
    }
}
