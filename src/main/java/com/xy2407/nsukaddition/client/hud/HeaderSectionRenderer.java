package com.xy2407.nsukaddition.client.hud;

import client.cn.kafei.simukraft.client.ClientSimukraftData;
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

/** 侧边栏顶部区域渲染，展示玩家头像、城市名、资金、人口和官员列表。 */
final class HeaderSectionRenderer {
    private static final Cache CACHE = new Cache();

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
        int avatarY = startY + (sectionH - padY - SidebarLayout.AVATAR_SIZE) / 2;
        renderAvatar(gg, mc, leftX, avatarY);

        int fieldsX = leftX + SidebarLayout.AVATAR_SIZE + 10;
        int fieldsWidth = leftX + leftWidth - fieldsX - SidebarLayout.DIVIDER_TEXT_GAP;
        int rowH = (sectionH - padY) / 3;

        renderField(mc, matrix, textRenderer, CACHE.cityLabel, CACHE.cityValue, CACHE.cityValueWidth,
                fieldsX, startY, fieldsWidth, rowH, SidebarHudLayer.TEXT_SECONDARY, SidebarHudLayer.TEXT_PRIMARY);
        renderField(mc, matrix, textRenderer, CACHE.fundsLabel, CACHE.fundsValue, CACHE.fundsValueWidth,
                fieldsX, startY + rowH, fieldsWidth, rowH, SidebarHudLayer.TEXT_SECONDARY, SidebarHudLayer.TEXT_PRIMARY);
        renderField(mc, matrix, textRenderer, CACHE.populationLabel, CACHE.populationValue, CACHE.populationValueWidth,
                fieldsX, startY + rowH * 2, fieldsWidth, rowH, SidebarHudLayer.TEXT_SECONDARY, SidebarHudLayer.TEXT_PRIMARY);

        rectRenderer.fill(matrix, rightX - 1, startY, 1, sectionH, SidebarHudLayer.DIVIDER);
        textRenderer.drawText(mc.font, CACHE.officersTitle, rightX + SidebarLayout.DIVIDER_TEXT_GAP,
                startY, SidebarHudLayer.TEXT_PRIMARY, false, matrix);

        int listY = startY + mc.font.lineHeight + 4;
        if (CACHE.officerLines.isEmpty()) {
            textRenderer.drawText(mc.font, CACHE.officersEmpty,
                    rightX + SidebarLayout.DIVIDER_TEXT_GAP, listY,
                    SidebarHudLayer.TEXT_MUTED, false, matrix);
        } else {
            for (String line : CACHE.officerLines) {
                textRenderer.drawText(mc.font, line,
                        rightX + SidebarLayout.DIVIDER_TEXT_GAP, listY,
                        SidebarHudLayer.TEXT_SECONDARY, false, matrix);
                listY += mc.font.lineHeight + 2;
            }
        }

        int bottomY = startY + sectionH;
        rectRenderer.fill(matrix, r.x() + padX, bottomY, r.width() - padX * 2, 1, SidebarHudLayer.DIVIDER);
        return bottomY + SidebarLayout.SECTION_GAP;
    }

    private static void renderAvatar(GuiGraphics gg, Minecraft mc, int x, int y) {
        if (mc.player == null) return;
        PlayerSkin skin = mc.getSkinManager().getInsecureSkin(mc.player.getGameProfile());
        PlayerFaceRenderer.draw(gg, skin.texture(), x, y, SidebarLayout.AVATAR_SIZE);
    }

    private static void renderField(Minecraft mc, Matrix4f matrix, BatchTextRenderer textRenderer,
                                    String label, String value, int valueWidth,
                                    int x, int y, int width, int rowH, int labelColor, int valueColor) {
        int textY = y + (rowH - mc.font.lineHeight) / 2;
        textRenderer.drawText(mc.font, label, x, textY, labelColor, false, matrix);
        textRenderer.drawText(mc.font, value, x + width - valueWidth, textY, valueColor, false, matrix);
    }

    private static final class Cache {
        private String language = "";
        private SidebarDataSnapshot snapshot;
        private String cityName = "";
        private double funds = Double.NaN;
        private int population = Integer.MIN_VALUE;

        private String cityLabel = "";
        private String fundsLabel = "";
        private String populationLabel = "";
        private String officersTitle = "";
        private String officersEmpty = "";

        private String cityValue = "";
        private String fundsValue = "";
        private String populationValue = "";
        private int cityValueWidth;
        private int fundsValueWidth;
        private int populationValueWidth;
        private final List<String> officerLines = new ArrayList<>();

        void refresh(Minecraft mc, BatchTextRenderer textRenderer) {
            SidebarDataSnapshot current = SidebarDataSnapshot.get();
            String currentLanguage = mc.getLanguageManager().getSelected();
            String currentCityName = ClientSimukraftData.getCurrentCityName();
            double currentFunds = ClientSimukraftData.getCurrentCityFunds();
            int currentPopulation = ClientSimukraftData.getCurrentCityPopulation();

            boolean textChanged = !Objects.equals(language, currentLanguage);
            boolean dataChanged = snapshot != current
                    || !Objects.equals(cityName, currentCityName)
                    || Double.compare(funds, currentFunds) != 0
                    || population != currentPopulation;
            if (!textChanged && !dataChanged) {
                return;
            }

            language = currentLanguage;
            snapshot = current;
            cityName = currentCityName;
            funds = currentFunds;
            population = currentPopulation;

            cityLabel = Component.translatable("hud.xy2407_nsuk_addition.header.city_name").getString();
            fundsLabel = Component.translatable("hud.xy2407_nsuk_addition.header.funds").getString();
            populationLabel = Component.translatable("hud.xy2407_nsuk_addition.header.population").getString();
            officersTitle = Component.translatable("hud.xy2407_nsuk_addition.header.officers_title").getString();
            officersEmpty = Component.translatable("hud.xy2407_nsuk_addition.header.officers.empty").getString();

            cityValue = currentCityName == null || currentCityName.isEmpty() ? "—" : currentCityName;
            fundsValue = String.format("%.2f", currentFunds);
            populationValue = String.valueOf(currentPopulation);
            cityValueWidth = textRenderer.calcWidth(mc.font, cityValue);
            fundsValueWidth = textRenderer.calcWidth(mc.font, fundsValue);
            populationValueWidth = textRenderer.calcWidth(mc.font, populationValue);

            officerLines.clear();
            for (SidebarDataSnapshot.Officer officer : current.officers()) {
                officerLines.add(officer.playerName() + " [" + officer.permissionDisplay() + "]");
            }
        }
    }
}
