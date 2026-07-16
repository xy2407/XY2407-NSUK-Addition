package com.xy2407.nsukaddition.client.hud;

import com.xy2407.nsukaddition.client.data.SidebarDataSnapshot;
import com.xy2407.nsukaddition.common.material.MaterialCategoryRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** 侧边栏建造任务追踪区域渲染。 */
final class QuestSectionRenderer {
    private static final Map<String, ItemStack> ICON_CACHE = new HashMap<>();
    private static final Cache CACHE = new Cache();

    private QuestSectionRenderer() {}

    static void render(GuiGraphics gg, Minecraft mc, int startY,
                       BatchTextRenderer textRenderer, BatchRectRenderer rectRenderer) {
        CACHE.refresh(mc, textRenderer);

        int padX = SidebarLayout.PAD_X;
        int leftX = padX;
        int y = startY;

        textRenderer.drawText(mc.font, CACHE.title, leftX, y, SidebarHudLayer.TEXT_PRIMARY, false);
        y += SidebarLayout.TITLE_LINE_H + SidebarLayout.TITLE_CONTENT_GAP;

        if (!CACHE.hasTask) {
            textRenderer.drawText(mc.font, CACHE.emptyText, leftX, y, SidebarHudLayer.TEXT_MUTED, false);
            y += mc.font.lineHeight + SidebarLayout.SECTION_GAP;
            return;
        }

        textRenderer.drawText(mc.font, CACHE.headerText, leftX, y, SidebarHudLayer.TEXT_PRIMARY, false);
        y += mc.font.lineHeight + 6;

        float barW = SidebarHudLayer.REF_WIDTH - padX * 2;
        rectRenderer.fill(leftX, y, barW, SidebarLayout.BAR_H, 0xFF444444);
        float fillWidth = (CACHE.progressPercent / 100.0f) * barW;
        if (fillWidth > 0) {
            rectRenderer.fill(leftX, y, fillWidth, SidebarLayout.BAR_H, SidebarHudLayer.COLOR_SUCCESS);
        }
        y += SidebarLayout.BAR_H + 6;

        textRenderer.drawText(mc.font, CACHE.statusText, leftX, y, SidebarHudLayer.TEXT_SECONDARY, false);
        y += mc.font.lineHeight + 6;

        int rowH = mc.font.lineHeight + 4;
        int maxPerCol = 6;

        for (int i = 0; i < CACHE.materialRows.size() && i < maxPerCol * 2; i++) {
            MaterialRowCache row = CACHE.materialRows.get(i);
            int rowIndex = i % maxPerCol;
            float colX = i < maxPerCol ? leftX : leftX + barW / 2 + 4;
            renderMaterialRow(gg, mc, textRenderer, row, colX, y + rowIndex * rowH, barW / 2 - 4);
        }
    }

    private static void renderMaterialRow(GuiGraphics gg, Minecraft mc,
                                          BatchTextRenderer textRenderer, MaterialRowCache row,
                                          float x, float y, float colWidth) {
        if (!row.icon.isEmpty()) {
            gg.pose().pushPose();
            gg.pose().scale(0.7F, 0.7F, 1.0F);
            gg.renderFakeItem(row.icon, (int) ((x + 2) / 0.7F), (int) ((y - 2) / 0.7F));
            gg.pose().popPose();
        }
        textRenderer.drawText(mc.font, row.text, x + 18, y, row.color, false);
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
        private float progressPercent;
        private final List<MaterialRowCache> materialRows = new ArrayList<>();

        void refresh(Minecraft mc, BatchTextRenderer textRenderer) {
            SidebarDataSnapshot current = SidebarDataSnapshot.get();
            String curLang = mc.getLanguageManager().getSelected();
            if (Objects.equals(language, curLang) && snapshot == current) return;
            language = curLang;
            snapshot = current;
            title = Component.translatable("hud.xy2407_nsuk_addition.quest.title").getString();
            emptyText = Component.translatable("hud.xy2407_nsuk_addition.quest.empty").getString();
            materialRows.clear();

            if (!current.hasBuildTask()) {
                hasTask = false;
                headerText = "";
                statusText = "";
                progressPercent = 0;
                return;
            }

            hasTask = true;
            SidebarDataSnapshot.BuildTask task = findTrackedTask(current);
            if (task == null) task = current.buildTasks().get(0);
            headerText = task.displayName() + "  " + task.progressPercent() + "%";
            statusText = Component.translatable(
                    "hud.xy2407_nsuk_addition.quest.status." + task.statusKey()).getString();
            progressPercent = task.progressPercent();

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
            this.icon = ICON_CACHE.computeIfAbsent(material.categoryKey(),
                    MaterialCategoryRegistry::getDisplayStack);
            this.text = Component.translatable(
                    "hud.xy2407_nsuk_addition.material." + material.categoryKey()).getString()
                    + "  " + material.available() + "/" + material.required();
        }
    }
}
