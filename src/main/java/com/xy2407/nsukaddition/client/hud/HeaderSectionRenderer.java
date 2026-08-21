package com.xy2407.nsukaddition.client.hud;

import client.cn.kafei.simukraft.client.ClientSimukraftData;
import client.cn.kafei.simukraft.client.city.ClientCityChunkCache;
import com.xy2407.nsukaddition.client.colony.ColonyChunkClientCache;
import com.xy2407.nsukaddition.client.data.SidebarDataSnapshot;
import com.xy2407.nsukaddition.common.city.ProsperityLevel;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.network.chat.Component;

import com.teamtea.eclipticseasons.api.constant.solar.Season;
import com.teamtea.eclipticseasons.api.constant.solar.SolarTerm;
import com.teamtea.eclipticseasons.api.util.EclipticUtil;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** 侧边栏头部区域：第一部分（城市名+右上角日期+官员列表），第二部分（繁荣度/人口/附属地/资金）。 */
final class HeaderSectionRenderer {
    private static final Cache CACHE = new Cache();

    private static final String[] WEEKDAYS = {
            "weekday.sunday", "weekday.monday", "weekday.tuesday",
            "weekday.wednesday", "weekday.thursday", "weekday.friday", "weekday.saturday"
    };

    private static final int OFFICERS_PER_ROW = 3;
    private static final int DATE_BG_PADDING_X = 4;
    private static final int DATE_BG_PADDING_Y = 2;
    private static final int SEASON_ICON_SIZE = 6;

    private HeaderSectionRenderer() {}

    private static String buildDateInfo(Minecraft mc) {
        int currentDay = mc.level != null
                ? (int) Math.max(1L, mc.level.getGameTime() / 24000L + 1L)
                : ClientSimukraftData.getCurrentDay();
        String weekDayKey = WEEKDAYS[Math.floorMod(currentDay - 1, WEEKDAYS.length)];
        String weekDay = Component.translatable(weekDayKey).getString();
        String seasonTerm = getSeasonTermText(mc);
        return currentDay + "天 " + weekDay + "  " + seasonTerm;
    }

    private static String getSeasonTermText(Minecraft mc) {
        if (mc.level == null) return "";
        try {
            SolarTerm term = EclipticUtil.getNowSolarTerm(mc.level);
            if (term == null || !term.isValid()) return "";
            Season season = term.getSeason();
            String seasonName = season.getTranslation().getString();
            String termName = term.getTranslation().getString();
            return seasonName + ":" + termName;
        } catch (NoClassDefFoundError e) {
            return "";
        }
    }

    private static int getSeasonColor(Minecraft mc) {
        if (mc.level == null) return SidebarHudLayer.TEXT_MUTED;
        try {
            SolarTerm term = EclipticUtil.getNowSolarTerm(mc.level);
            if (term == null || !term.isValid()) return SidebarHudLayer.TEXT_MUTED;
            ChatFormatting fmt = term.getColor();
            Integer color = fmt.getColor();
            return color != null ? color : SidebarHudLayer.TEXT_MUTED;
        } catch (NoClassDefFoundError e) {
            return SidebarHudLayer.TEXT_MUTED;
        }
    }

    static int render(GuiGraphics gg, Minecraft mc,
                      BatchTextRenderer textRenderer, BatchRectRenderer rectRenderer) {
        CACHE.refresh(mc, textRenderer);

        int padX = SidebarLayout.PAD_X;
        int startY = SidebarLayout.TOP_BAR_H + SidebarLayout.PAD_Y;
        int contentW = SidebarHudLayer.REF_WIDTH - padX * 2;

        renderCityName(gg, mc, textRenderer, contentW);

        SidebarHudLayer.drawTitleWithBackground(textRenderer, rectRenderer, mc.font,
                CACHE.officersTitle, padX, startY, SidebarHudLayer.TEXT_PRIMARY);
        renderDateInfo(gg, mc, textRenderer, rectRenderer, padX, startY, contentW);

        int officerListY = startY + mc.font.lineHeight + 5;
        int faceSize = mc.font.lineHeight;
        int officerCount = CACHE.officerLines.size();
        int rows = officerCount == 0 ? 1 : (officerCount + OFFICERS_PER_ROW - 1) / OFFICERS_PER_ROW;
        int colWidth = contentW / OFFICERS_PER_ROW;

        if (officerCount == 0) {
            textRenderer.drawText(mc.font, CACHE.officersEmpty, padX, officerListY,
                    SidebarHudLayer.TEXT_MUTED, false);
        } else {
            for (int i = 0; i < officerCount; i++) {
                int row = i / OFFICERS_PER_ROW;
                int col = i % OFFICERS_PER_ROW;
                int colX = padX + col * colWidth;
                int rowY = officerListY + row * (faceSize + 2);
                String line = CACHE.officerLines.get(i);
                String playerName = CACHE.officerPlayerNames.get(i);

                if (playerName != null && !playerName.isBlank() && CACHE.playerHeads.containsKey(playerName)) {
                    PlayerSkin skin = CACHE.playerHeads.get(playerName);
                    PlayerFaceRenderer.draw(gg, skin, Math.round(colX), Math.round(rowY), faceSize);
                }
                textRenderer.drawText(mc.font, line, colX + faceSize + 3, rowY,
                        SidebarHudLayer.TEXT_SECONDARY, false);
            }
        }

        int officerBottomY = officerListY + rows * (faceSize + 2) + 2;
        rectRenderer.fill(padX, officerBottomY, contentW, 1, SidebarHudLayer.DIVIDER);

        int part2Y = officerBottomY + 5;
        int leftWidth = (int) (contentW * SidebarLayout.LEFT_RATIO);
        int midX = padX + leftWidth;
        int leftColW = leftWidth - SidebarLayout.DIVIDER_TEXT_GAP;
        int rightColX = padX + leftWidth + SidebarLayout.DIVIDER_TEXT_GAP;
        int rightColW = contentW - leftWidth - SidebarLayout.DIVIDER_TEXT_GAP;
        int rowH = mc.font.lineHeight + 4;
        int part2H = rowH * 2 + 2;

        renderField(mc, textRenderer, CACHE.prosperityLabel, CACHE.prosperityValue, CACHE.prosperityValueWidth,
                padX, part2Y, leftColW, rowH);
        renderField(mc, textRenderer, CACHE.populationLabel, CACHE.populationValue, CACHE.populationValueWidth,
                padX, part2Y + rowH, leftColW, rowH);

        renderField(mc, textRenderer, CACHE.colonyLabel, CACHE.colonyValue, CACHE.colonyValueWidth,
                rightColX, part2Y, rightColW, rowH);
        renderField(mc, textRenderer, CACHE.fundsLabel, CACHE.fundsValue, CACHE.fundsValueWidth,
                rightColX, part2Y + rowH, rightColW, rowH);

        rectRenderer.fill(midX - 1, part2Y, 1, part2H, SidebarHudLayer.DIVIDER);

        int bottomY = part2Y + part2H;
        rectRenderer.fill(padX, bottomY, SidebarHudLayer.REF_WIDTH - padX * 2, 1, SidebarHudLayer.DIVIDER);
        return bottomY + SidebarLayout.SECTION_GAP;
    }

    private static void renderCityName(GuiGraphics gg, Minecraft mc,
                                       BatchTextRenderer textRenderer, int contentW) {
        String cityName = CACHE.cityValue;
        if (cityName == null || cityName.isEmpty()) {
            cityName = "—";
        }
        float scale = 1.5F;
        int maxTextW = (int) (contentW / scale);
        if (textRenderer.calcWidth(mc.font, cityName) > maxTextW) {
            while (!cityName.isEmpty() && textRenderer.calcWidth(mc.font, cityName + "...") > maxTextW) {
                cityName = cityName.substring(0, cityName.length() - 1);
            }
            cityName = cityName + "...";
        }
        int scaledW = (int) (textRenderer.calcWidth(mc.font, cityName) * scale);
        int scaledH = (int) (mc.font.lineHeight * scale);
        int x = (SidebarHudLayer.REF_WIDTH - scaledW) / 2;
        int y = (SidebarLayout.TOP_BAR_H - scaledH) / 2;
        gg.pose().pushPose();
        gg.pose().translate(x, y, 0);
        gg.pose().scale(scale, scale, 1.0F);
        textRenderer.drawText(mc.font, cityName, 0, 0, SidebarHudLayer.TEXT_PRIMARY, false);
        gg.pose().popPose();
    }

    private static void renderDateInfo(GuiGraphics gg, Minecraft mc,
                                        BatchTextRenderer textRenderer, BatchRectRenderer rectRenderer,
                                        int padX, int startY, int contentW) {
        String dateText = buildDateInfo(mc);
        int textWidth = textRenderer.calcWidth(mc.font, dateText);
        int textHeight = mc.font.lineHeight;
        int totalContentW = textWidth + SEASON_ICON_SIZE + 3;
        int bgW = totalContentW + DATE_BG_PADDING_X * 2;
        int bgH = textHeight + DATE_BG_PADDING_Y * 2;
        int bgX = padX + contentW - bgW;
        int bgY = startY;

        rectRenderer.fill(bgX, bgY, bgW, bgH, 0x80303030);
        rectRenderer.outline(bgX, bgY, bgW, bgH, SidebarHudLayer.DIVIDER);

        int seasonColor = getSeasonColor(mc);
        int iconX = bgX + DATE_BG_PADDING_X;
        int iconY = bgY + (bgH - SEASON_ICON_SIZE) / 2;
        rectRenderer.fill(iconX, iconY, SEASON_ICON_SIZE, SEASON_ICON_SIZE, seasonColor);

        int textX = iconX + SEASON_ICON_SIZE + 3;
        int textY = bgY + DATE_BG_PADDING_Y;
        textRenderer.drawText(mc.font, dateText, textX, textY, SidebarHudLayer.TEXT_PRIMARY, false);
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
        private long prosperity = Long.MIN_VALUE;

        private String fundsLabel = "";
        private String populationLabel = "";
        private String prosperityLabel = "";
        private String colonyLabel = "";
        private String officersTitle = "";
        private String officersEmpty = "";
        private String subLine = null;

        private String cityValue = "";
        private String fundsValue = "";
        private String populationValue = "";
        private String prosperityValue = "";
        private String colonyValue = "";
        private int cityValueWidth;
        private int fundsValueWidth;
        private int populationValueWidth;
        private int prosperityValueWidth;
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
                    || prosperity != current.prosperity()
                    || (displaySubLine != null) != (subLine != null)
                    || (displaySubLine != null && !displaySubLine.equals(subLine));
            if (!textChanged && !dataChanged) return;

            language = currentLanguage;
            snapshot = current;
            cityName = displayCityName;
            funds = currentFunds;
            population = currentPopulation;
            colonyCount = currentColonyCount;
            prosperity = current.prosperity();
            subLine = displaySubLine;

            fundsLabel = Component.translatable("hud.xy2407_nsuk_addition.header.funds").getString();
            populationLabel = Component.translatable("hud.xy2407_nsuk_addition.header.population").getString();
            prosperityLabel = Component.translatable("hud.xy2407_nsuk_addition.header.prosperity").getString();
            colonyLabel = Component.translatable("hud.xy2407_nsuk_addition.header.colony").getString();
            officersTitle = Component.translatable("hud.xy2407_nsuk_addition.header.officers_title").getString();
            officersEmpty = Component.translatable("hud.xy2407_nsuk_addition.header.officers.empty").getString();

            cityValue = displayCityName == null || displayCityName.isEmpty() ? "—" : displayCityName;
            fundsValue = String.format("%.2f", currentFunds);
            populationValue = String.valueOf(currentPopulation);
            long currentProsperity = current.prosperity();
            if (currentProsperity > 0) {
                prosperityValue = currentProsperity + " (" + ProsperityLevel.fromValue(currentProsperity).displayName() + ")";
            } else {
                prosperityValue = "0";
            }
            colonyValue = currentColonyCount + "个";
            cityValueWidth = textRenderer.calcWidth(mc.font, cityValue);
            fundsValueWidth = textRenderer.calcWidth(mc.font, fundsValue);
            populationValueWidth = textRenderer.calcWidth(mc.font, populationValue);
            prosperityValueWidth = textRenderer.calcWidth(mc.font, prosperityValue);
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
