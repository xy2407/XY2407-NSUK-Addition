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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** 侧边栏头部区域：左侧城市/资金/人口/属地，右侧官员列表（含头像），中间分割线。 */
final class HeaderSectionRenderer {
    private static final Cache CACHE = new Cache();

    private static final String[] WEEKDAYS = {
            "weekday.sunday", "weekday.monday", "weekday.tuesday",
            "weekday.wednesday", "weekday.thursday", "weekday.friday", "weekday.saturday"
    };

    private HeaderSectionRenderer() {}

    static String buildTitleExtra(Minecraft mc) {
        int currentDay = ClientSimukraftData.getCurrentDay();
        String weekDayKey = WEEKDAYS[Math.floorMod(currentDay - 1, WEEKDAYS.length)];
        String weekDay = Component.translatable(weekDayKey).getString();
        return currentDay + "天 " + weekDay;
    }

    static int render(GuiGraphics gg, Minecraft mc,
                      BatchTextRenderer textRenderer, BatchRectRenderer rectRenderer) {
        CACHE.refresh(mc, textRenderer);

        int padX = SidebarLayout.PAD_X;
        int startY = SidebarLayout.TOP_BAR_H + SidebarLayout.PAD_Y;
        int sectionH = SidebarLayout.HEADER_SECTION_H;
        int contentW = SidebarHudLayer.REF_WIDTH - padX * 2;
        int leftWidth = (int) (contentW * SidebarLayout.LEFT_RATIO);
        int rightX = padX + leftWidth;
        int leftX = padX;
        int fieldsWidth = leftWidth - SidebarLayout.DIVIDER_TEXT_GAP;

        int rowCount = 4;
        int rowH = (sectionH - SidebarLayout.PAD_Y) / rowCount;

        renderField(mc, textRenderer, CACHE.cityLabel, CACHE.cityValue, CACHE.cityValueWidth,
                leftX, startY, fieldsWidth, rowH);
        if (CACHE.subLine != null) {
            textRenderer.drawText(mc.font, CACHE.subLine,
                    leftX, startY + rowH - mc.font.lineHeight / 2,
                    SidebarHudLayer.TEXT_MUTED, false);
        }
        renderField(mc, textRenderer, CACHE.fundsLabel, CACHE.fundsValue, CACHE.fundsValueWidth,
                leftX, startY + rowH, fieldsWidth, rowH);
        renderField(mc, textRenderer, CACHE.populationLabel, CACHE.populationValue, CACHE.populationValueWidth,
                leftX, startY + rowH * 2, fieldsWidth, rowH);
        renderField(mc, textRenderer, CACHE.colonyLabel, CACHE.colonyValue, CACHE.colonyValueWidth,
                leftX, startY + rowH * 3, fieldsWidth, rowH);

        rectRenderer.fill(rightX - 1, startY, 1, sectionH, SidebarHudLayer.DIVIDER);

        int rightContentX = rightX + SidebarLayout.DIVIDER_TEXT_GAP;
        textRenderer.drawText(mc.font, CACHE.officersTitle, rightContentX, startY,
                SidebarHudLayer.TEXT_PRIMARY, false);

        int listY = startY + mc.font.lineHeight + 4;
        if (CACHE.officerLines.isEmpty()) {
            textRenderer.drawText(mc.font, CACHE.officersEmpty, rightContentX, listY,
                    SidebarHudLayer.TEXT_MUTED, false);
        } else {
            int faceSize = mc.font.lineHeight;
            for (int i = 0; i < CACHE.officerLines.size(); i++) {
                String line = CACHE.officerLines.get(i);
                textRenderer.drawText(mc.font, line, rightContentX, listY,
                        SidebarHudLayer.TEXT_SECONDARY, false);
                String playerName = CACHE.officerPlayerNames.get(i);
                if (playerName != null && !playerName.isBlank() && CACHE.playerHeads.containsKey(playerName)) {
                    int textWidth = textRenderer.calcWidth(mc.font, line);
                    int faceX = rightContentX + textWidth + 3;
                    int faceY = listY;
                    PlayerSkin skin = CACHE.playerHeads.get(playerName);
                    PlayerFaceRenderer.draw(gg, skin, Math.round(faceX), Math.round(faceY), faceSize);
                }
                listY += mc.font.lineHeight + 2;
            }
        }

        int bottomY = startY + sectionH;
        rectRenderer.fill(padX, bottomY, SidebarHudLayer.REF_WIDTH - padX * 2, 1, SidebarHudLayer.DIVIDER);
        return bottomY + SidebarLayout.SECTION_GAP;
    }

    private static void renderField(Minecraft mc, BatchTextRenderer textRenderer,
                                    String label, String value, int valueWidth,
                                    int x, int y, int width, int rowH) {
        int textY = y + (rowH - mc.font.lineHeight) / 2;
        textRenderer.drawText(mc.font, label, x, textY, SidebarHudLayer.TEXT_SECONDARY, false);
        textRenderer.drawText(mc.font, value, x + width - valueWidth, textY, SidebarHudLayer.TEXT_PRIMARY, false);
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
        private String subLine = null;

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
        private final Map<String, PlayerSkin> playerHeads = new HashMap<>();

        void refresh(Minecraft mc, BatchTextRenderer textRenderer) {
            SidebarDataSnapshot current = SidebarDataSnapshot.get();
            String currentLanguage = mc.getLanguageManager().getSelected();
            String currentCityName = ClientSimukraftData.getCurrentCityName();
            double currentFunds = ClientSimukraftData.getCurrentCityFunds();
            int currentPopulation = ClientSimukraftData.getCurrentCityPopulation();
            UUID currentCityId = ClientCityChunkCache.getInstance().getCurrentCityId();
            int currentColonyCount = currentCityId != null
                    ? ColonyChunkClientCache.getInstance().countColoniesByParentCity(currentCityId) : 0;

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
            if (!textChanged && !dataChanged) return;

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
            colonyValue = currentColonyCount + "个";
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
                if (mc.player != null && name.equals(mc.player.getName().getString())) {
                    PlayerSkin skin = mc.getSkinManager().getInsecureSkin(mc.player.getGameProfile());
                    playerHeads.put(name, skin);
                }
            }
        }
    }
}
