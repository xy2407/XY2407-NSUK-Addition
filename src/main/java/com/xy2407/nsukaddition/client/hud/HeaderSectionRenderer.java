package com.xy2407.nsukaddition.client.hud;

import client.cn.kafei.simukraft.client.ClientSimukraftData;
import client.cn.kafei.simukraft.client.city.ClientCityChunkCache;
import com.xy2407.nsukaddition.client.colony.ColonyChunkClientCache;
import com.xy2407.nsukaddition.client.data.SidebarDataSnapshot;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.network.chat.Component;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** 侧边栏顶部区域渲染，展示城市名、资金、人口、属地和官员列表（含玩家头像）。 */
final class HeaderSectionRenderer {
    private static final Cache CACHE = new Cache();

    // simukraft 周日期翻译键
    private static final String[] WEEKDAYS = {
            "weekday.sunday", "weekday.monday", "weekday.tuesday",
            "weekday.wednesday", "weekday.thursday", "weekday.friday", "weekday.saturday"
    };

    private HeaderSectionRenderer() {}

    static int render(GuiGraphics gg, PopupLayoutManager.Rect r, Minecraft mc, Matrix4f matrix,
                      BatchTextRenderer textRenderer, BatchRectRenderer rectRenderer) {
        CACHE.refresh(mc, textRenderer);

        int padX = SidebarLayout.PAD_X;
        int padY = SidebarLayout.PAD_Y;
        int startY = r.y() + SidebarLayout.TOP_BAR_H + padY;
        int sectionH = SidebarLayout.HEADER_SECTION_H;
        int leftWidth = (int) ((r.width() - padX * 2) * SidebarLayout.LEFT_RATIO);
        int rightX = r.x() + padX + leftWidth;
        int leftX = r.x() + padX;

        // 左侧字段（4行等间距：城市名/资金/人口/属地）
        int fieldsX = leftX;
        int fieldsWidth = leftWidth - SidebarLayout.DIVIDER_TEXT_GAP;
        int rowCount = 4;
        int rowH = (sectionH - padY) / rowCount;

        // 城市名行（如果有附属地副标题，值上方多渲染一行小字）
        int cityRowY = startY;
        if (CACHE.subLine != null) {
            renderField(mc, matrix, textRenderer, CACHE.cityLabel, CACHE.cityValue, CACHE.cityValueWidth,
                    fieldsX, cityRowY, fieldsWidth, rowH - mc.font.lineHeight / 2, SidebarHudLayer.TEXT_SECONDARY, SidebarHudLayer.TEXT_PRIMARY);
            int subY = cityRowY + rowH - mc.font.lineHeight / 2;
            textRenderer.drawText(mc.font, CACHE.subLine, fieldsX, subY, SidebarHudLayer.TEXT_MUTED, false, matrix);
        } else {
            renderField(mc, matrix, textRenderer, CACHE.cityLabel, CACHE.cityValue, CACHE.cityValueWidth,
                    fieldsX, cityRowY, fieldsWidth, rowH, SidebarHudLayer.TEXT_SECONDARY, SidebarHudLayer.TEXT_PRIMARY);
        }
        renderField(mc, matrix, textRenderer, CACHE.fundsLabel, CACHE.fundsValue, CACHE.fundsValueWidth,
                fieldsX, startY + rowH, fieldsWidth, rowH, SidebarHudLayer.TEXT_SECONDARY, SidebarHudLayer.TEXT_PRIMARY);
        renderField(mc, matrix, textRenderer, CACHE.populationLabel, CACHE.populationValue, CACHE.populationValueWidth,
                fieldsX, startY + rowH * 2, fieldsWidth, rowH, SidebarHudLayer.TEXT_SECONDARY, SidebarHudLayer.TEXT_PRIMARY);
        renderField(mc, matrix, textRenderer, CACHE.colonyLabel, CACHE.colonyValue, CACHE.colonyValueWidth,
                fieldsX, startY + rowH * 3, fieldsWidth, rowH, SidebarHudLayer.TEXT_SECONDARY, SidebarHudLayer.TEXT_PRIMARY);

        // 分割线
        rectRenderer.fill(matrix, rightX - 1, startY, 1, sectionH, SidebarHudLayer.DIVIDER);

        // 右侧官员列表
        textRenderer.drawText(mc.font, CACHE.officersTitle, rightX + SidebarLayout.DIVIDER_TEXT_GAP,
                startY, SidebarHudLayer.TEXT_PRIMARY, false, matrix);

        int listY = startY + mc.font.lineHeight + 4;
        if (CACHE.officerLines.isEmpty()) {
            textRenderer.drawText(mc.font, CACHE.officersEmpty,
                    rightX + SidebarLayout.DIVIDER_TEXT_GAP, listY,
                    SidebarHudLayer.TEXT_MUTED, false, matrix);
        } else {
            int faceSize = mc.font.lineHeight;
            for (int i = 0; i < CACHE.officerLines.size(); i++) {
                String line = CACHE.officerLines.get(i);
                textRenderer.drawText(mc.font, line,
                        rightX + SidebarLayout.DIVIDER_TEXT_GAP, listY,
                        SidebarHudLayer.TEXT_SECONDARY, false, matrix);
                // 官员名右侧小方形玩家头像
                String playerName = CACHE.officerPlayerNames.get(i);
                if (playerName != null && !playerName.isBlank() && CACHE.playerHeads.containsKey(playerName)) {
                    int textWidth = mc.font.width(line);
                    int faceX = rightX + SidebarLayout.DIVIDER_TEXT_GAP + textWidth + 3;
                    int faceY = listY;
                    PlayerSkin skin = CACHE.playerHeads.get(playerName);
                    PlayerFaceRenderer.draw(gg, skin.texture(), faceX, faceY, faceSize);
                }
                listY += mc.font.lineHeight + 2;
            }
        }

        int bottomY = startY + sectionH;
        rectRenderer.fill(matrix, r.x() + padX, bottomY, r.width() - padX * 2, 1, SidebarHudLayer.DIVIDER);
        return bottomY + SidebarLayout.SECTION_GAP;
    }

    private static void renderField(Minecraft mc, Matrix4f matrix, BatchTextRenderer textRenderer,
                                    String label, String value, int valueWidth,
                                    int x, int y, int width, int rowH, int labelColor, int valueColor) {
        int textY = y + (rowH - mc.font.lineHeight) / 2;
        textRenderer.drawText(mc.font, label, x, textY, labelColor, false, matrix);
        textRenderer.drawText(mc.font, value, x + width - valueWidth, textY, valueColor, false, matrix);
    }

    // 生成顶部标题栏额外文本：游戏天数 + simukraft周末日期。
    static String buildTitleExtra(Minecraft mc) {
        int currentDay = ClientSimukraftData.getCurrentDay();
        String weekDayKey = WEEKDAYS[Math.floorMod(currentDay - 1, WEEKDAYS.length)];
        String weekDay = Component.translatable(weekDayKey).getString();
        return currentDay + "天 " + weekDay;
    }

    private static final class Cache {
        private String language = "";
        private SidebarDataSnapshot snapshot;
        private String cityName = "";
        private double funds = Double.NaN;
        private int population = Integer.MIN_VALUE;
        private int colonyCount = Integer.MIN_VALUE;

        private String cityLabel = "";
        private String fundsLabel = "";
        private String populationLabel = "";
        private String colonyLabel = "";
        private String officersTitle = "";
        private String officersEmpty = "";
        private String subLine = null; // 附属地副标题：所属城市xxx

        private String cityValue = "";
        private String fundsValue = "";
        private String populationValue = "";
        private String colonyValue = "";
        private int cityValueWidth;
        private int fundsValueWidth;
        private int populationValueWidth;
        private int colonyValueWidth;
        private final List<String> officerLines = new ArrayList<>();
        private final List<String> officerPlayerNames = new ArrayList<>();
        private final java.util.Map<String, PlayerSkin> playerHeads = new java.util.HashMap<>();

        void refresh(Minecraft mc, BatchTextRenderer textRenderer) {
            SidebarDataSnapshot current = SidebarDataSnapshot.get();
            String currentLanguage = mc.getLanguageManager().getSelected();
            String currentCityName = ClientSimukraftData.getCurrentCityName();
            double currentFunds = ClientSimukraftData.getCurrentCityFunds();
            int currentPopulation = ClientSimukraftData.getCurrentCityPopulation();
            UUID currentCityId = ClientCityChunkCache.getInstance().getCurrentCityId();
            int currentColonyCount = currentCityId != null
                    ? ColonyChunkClientCache.getInstance().countColoniesByParentCity(currentCityId) : 0;

            // 检查玩家是否在附属地区块内，如果是则显示附属地名称+所属城市
            String displayCityName = currentCityName;
            String displaySubLine = null;
            if (mc.player != null) {
                long playerChunk = mc.player.chunkPosition().toLong();
                UUID colonyOwner = ColonyChunkClientCache.getInstance().getColonyOwner(playerChunk);
                if (colonyOwner != null) {
                    ColonyChunkClientCache.ColonyEntry entry = ColonyChunkClientCache.getInstance().getColonyEntry(colonyOwner);
                    if (entry != null && entry.colonyName() != null && !entry.colonyName().isEmpty()) {
                        displayCityName = entry.colonyName();
                        displaySubLine = "所属城市：" + (entry.parentCityName() != null ? entry.parentCityName() : currentCityName);
                    }
                }
            }

            boolean textChanged = !Objects.equals(language, currentLanguage);
            boolean dataChanged = snapshot != current
                    || !Objects.equals(cityName, displayCityName)
                    || Double.compare(funds, currentFunds) != 0
                    || population != currentPopulation
                    || colonyCount != currentColonyCount
                    || (displaySubLine != null) != (subLine != null)
                    || (displaySubLine != null && !displaySubLine.equals(subLine));
            if (!textChanged && !dataChanged) {
                return;
            }

            language = currentLanguage;
            snapshot = current;
            cityName = displayCityName;
            funds = currentFunds;
            population = currentPopulation;
            colonyCount = currentColonyCount;
            subLine = displaySubLine;

            cityLabel = Component.translatable("hud.xy2407_nsuk_addition.header.city_name").getString();
            fundsLabel = Component.translatable("hud.xy2407_nsuk_addition.header.funds").getString();
            populationLabel = Component.translatable("hud.xy2407_nsuk_addition.header.population").getString();
            colonyLabel = Component.translatable("hud.xy2407_nsuk_addition.header.colony").getString();
            officersTitle = Component.translatable("hud.xy2407_nsuk_addition.header.officers_title").getString();
            officersEmpty = Component.translatable("hud.xy2407_nsuk_addition.header.officers.empty").getString();

            cityValue = displayCityName == null || displayCityName.isEmpty() ? "—" : displayCityName;
            fundsValue = String.format("%.2f", currentFunds);
            populationValue = String.valueOf(currentPopulation);
            colonyValue = String.valueOf(currentColonyCount);
            cityValueWidth = textRenderer.calcWidth(mc.font, cityValue);
            fundsValueWidth = textRenderer.calcWidth(mc.font, fundsValue);
            populationValueWidth = textRenderer.calcWidth(mc.font, populationValue);
            colonyValueWidth = textRenderer.calcWidth(mc.font, colonyValue);

            officerLines.clear();
            officerPlayerNames.clear();
            playerHeads.clear();
            for (SidebarDataSnapshot.Officer officer : current.officers()) {
                String name = officer.playerName();
                officerLines.add(name + " [" + officer.permissionDisplay() + "]");
                officerPlayerNames.add(name);
                // 预加载玩家皮肤
                if (mc.player != null && name.equals(mc.player.getName().getString())) {
                    PlayerSkin skin = mc.getSkinManager().getInsecureSkin(mc.player.getGameProfile());
                    playerHeads.put(name, skin);
                }
            }
        }
    }
}
