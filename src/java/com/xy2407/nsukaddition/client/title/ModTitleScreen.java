package com.xy2407.nsukaddition.client.title;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/** 自定义标题屏幕，左侧白色半透明侧边栏，悬停展开显示标题贴图与条形按钮。 */
@OnlyIn(Dist.CLIENT)
public final class ModTitleScreen extends Screen {

    private static final int BG_MAIN = 0xFF444444;
    private static final int TEXT_MUTED = 0xFFBDBDBD;

    private static final ResourceLocation BG_DAY1 =
            ResourceLocation.fromNamespaceAndPath("xy2407_nsuk_addition", "textures/background/day1.png");
    private static final ResourceLocation BG_DAY2 =
            ResourceLocation.fromNamespaceAndPath("xy2407_nsuk_addition", "textures/background/day2.png");
    private static final ResourceLocation BG_NIGHT1 =
            ResourceLocation.fromNamespaceAndPath("xy2407_nsuk_addition", "textures/background/night1.png");
    private static final ResourceLocation BG_NIGHT2 =
            ResourceLocation.fromNamespaceAndPath("xy2407_nsuk_addition", "textures/background/night2.png");

    private static final ResourceLocation TITLE_1 =
            ResourceLocation.fromNamespaceAndPath("xy2407_nsuk_addition", "textures/background/title1.png");
    private static final ResourceLocation TITLE_2 =
            ResourceLocation.fromNamespaceAndPath("xy2407_nsuk_addition", "textures/background/title2.png");

    private static final ResourceLocation ICON_5 =
            ResourceLocation.fromNamespaceAndPath("xy2407_nsuk_addition", "textures/background/5.png");
    private static final ResourceLocation ICON_6 =
            ResourceLocation.fromNamespaceAndPath("xy2407_nsuk_addition", "textures/background/6.png");

    private static final int SIDEBAR_COLLAPSED_WIDTH = 40;
    private static final int SIDEBAR_EXPANDED_WIDTH = 220;
    private static final int SIDEBAR_ANIMATION_MS = 180;
    private static final int SIDEBAR_BG = 0x7AFFFFFF;
    private static final int SIDEBAR_BORDER = 0xFF000000;

    private static final int MENU_BUTTON_HEIGHT = 27;
    private static final int MENU_BUTTON_GAP = 16;
    private static final int MENU_BUTTON_WIDTH = 188;

    private static final int LINK_FULL_WIDTH = 90;
    private static final int LINK_HEIGHT = 24;
    private static final int LINK_GAP = 4;

    private static final int ICON_SIZE = 64;

    private static final float BG_TRANSITION_SEC = 1.5f;

    private static final int TITLE_1_DISPLAY_W = 120;
    private static final int TITLE_2_DISPLAY_W = 180;
    private static final int TITLE_DISPLAY_H = 36;
    private static final int TITLE_GAP = 4;

    private static final int COLLAPSED_TEXT_COLOR = 0xFF333333;
    private static final String[] COLLAPSED_CHARS = {"开", "始", "游", "戏"};

    private final SidebarButton[] menuButtons = new SidebarButton[4];
    private final SlideButton[] linkButtons = new SlideButton[2];
    private final CelestialBody celestialBody = new CelestialBody();

    private CelestialBody.Type currentBgType = CelestialBody.Type.SUN;
    private CelestialBody.Type prevBgType = CelestialBody.Type.SUN;
    private float bgTransition = 1f;
    private long bgLastMs;

    private boolean sidebarTargetExpanded;
    private float sidebarProgress;
    private long sidebarLastMs;

    public ModTitleScreen() {
        super(Component.empty());
    }

    @Override
    protected void init() {
        super.init();
        bgLastMs = Util.getMillis();
        sidebarLastMs = Util.getMillis();
        sidebarProgress = 0f;

        menuButtons[0] = new SidebarButton(
                Component.translatable("menu.singleplayer"),
                () -> openScreen(new net.minecraft.client.gui.screens.worldselection.SelectWorldScreen(this))
        );
        menuButtons[1] = new SidebarButton(
                Component.translatable("menu.multiplayer"),
                () -> openScreen(new net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen(this))
        );
        menuButtons[2] = new SidebarButton(
                Component.translatable("menu.options"),
                () -> openScreen(new net.minecraft.client.gui.screens.options.OptionsScreen(this, minecraft.options))
        );
        menuButtons[3] = new SidebarButton(
                Component.translatable("menu.quit"),
                () -> minecraft.stop()
        );
        layoutMenuButtons();

        linkButtons[0] = new SlideButton(
                Component.literal("Bilibili"), ICON_5, ICON_SIZE, ICON_SIZE,
                LINK_FULL_WIDTH, LINK_HEIGHT,
                () -> Util.getPlatform().openUri("https://b23.tv/DP0kkSv")
        );
        linkButtons[1] = new SlideButton(
                Component.literal("爱发电"), ICON_6, ICON_SIZE, ICON_SIZE,
                LINK_FULL_WIDTH, LINK_HEIGHT,
                () -> Util.getPlatform().openUri("https://www.ifdian.net/a/xy2407")
        );

        int linkX = menuButtonX;
        linkButtons[0].x = linkX;
        linkButtons[0].y = height - LINK_HEIGHT * 2 - LINK_GAP - 8;
        linkButtons[1].x = linkX;
        linkButtons[1].y = height - LINK_HEIGHT - 8;
    }

    private int menuButtonX;

    private void layoutMenuButtons() {
        int btnW = MENU_BUTTON_WIDTH;
        int totalH = menuButtons.length * MENU_BUTTON_HEIGHT + (menuButtons.length - 1) * MENU_BUTTON_GAP;
        int startY = (height - totalH) / 2;
        menuButtonX = (SIDEBAR_EXPANDED_WIDTH - btnW) / 2;
        for (int i = 0; i < menuButtons.length; i++) {
            menuButtons[i].setBounds(
                    menuButtonX,
                    startY + i * (MENU_BUTTON_HEIGHT + MENU_BUTTON_GAP),
                    btnW,
                    MENU_BUTTON_HEIGHT
            );
        }
    }

    private void openScreen(Screen screen) {
        if (minecraft != null) {
            minecraft.setScreen(screen);
        }
    }

    @Override
    public void render(GuiGraphics gg, int mouseX, int mouseY, float partialTick) {
        celestialBody.update();
        updateBgTransition();
        updateSidebarTarget(mouseX, mouseY);
        updateSidebarAnimation();

        gg.fill(0, 0, width, height, BG_MAIN);

        renderBackgroundLayer(gg, true);

        celestialBody.render(gg, width, height);

        renderBackgroundLayer(gg, false);

        int sidebarW = getCurrentSidebarWidth();
        renderSidebar(gg, sidebarW, mouseX, mouseY);

        String line2 = "整合包永久免费，更新请点击此处";
        String line1 = "整合包作者:星影2407";
        int line2W = minecraft.font.width(line2);
        int line1W = minecraft.font.width(line1);
        int rightX = width - 4;
        int line2X = rightX - line2W;
        int line1X = rightX - line1W;
        gg.drawString(minecraft.font, line2, line2X, height - 4 - minecraft.font.lineHeight, TEXT_MUTED, false);
        gg.drawString(minecraft.font, line1, line1X, height - 4 - minecraft.font.lineHeight * 2 - 1, TEXT_MUTED, false);
    }

    @Override
    public void renderBackground(GuiGraphics gg, int mouseX, int mouseY, float partialTick) {
        gg.fill(0, 0, width, height, BG_MAIN);
        renderFullscreenBg(gg, currentBgType, true, 1f);
        celestialBody.render(gg, width, height);
        renderFullscreenBg(gg, currentBgType, false, 1f);
    }

    @Override
    public void onClose() {
    }

    private void updateSidebarTarget(int mouseX, int mouseY) {
        if (sidebarProgress < 0.05f) {
            sidebarTargetExpanded = mouseX <= SIDEBAR_COLLAPSED_WIDTH;
        } else {
            sidebarTargetExpanded = mouseX <= getCurrentSidebarWidth();
        }
    }

    private void updateSidebarAnimation() {
        long now = Util.getMillis();
        float delta = (float) (now - sidebarLastMs) / SIDEBAR_ANIMATION_MS;
        sidebarLastMs = now;
        if (sidebarTargetExpanded) {
            sidebarProgress = Math.min(1f, sidebarProgress + delta);
        } else {
            sidebarProgress = Math.max(0f, sidebarProgress - delta);
        }
    }

    private int getCurrentSidebarWidth() {
        float t = sidebarProgress;
        float eased = t * (2f - t);
        return Math.round(SIDEBAR_COLLAPSED_WIDTH + (SIDEBAR_EXPANDED_WIDTH - SIDEBAR_COLLAPSED_WIDTH) * eased);
    }

    private void renderSidebar(GuiGraphics gg, int sidebarW, int mouseX, int mouseY) {
        gg.fill(0, 0, sidebarW, height, SIDEBAR_BG);
        gg.hLine(0, sidebarW - 1, 0, SIDEBAR_BORDER);
        gg.hLine(0, sidebarW - 1, height - 1, SIDEBAR_BORDER);
        gg.vLine(0, 0, height - 1, SIDEBAR_BORDER);
        gg.vLine(sidebarW - 1, 0, height - 1, SIDEBAR_BORDER);

        renderCollapsedText(gg, sidebarW);

        if (sidebarProgress > 0.05f) {
            gg.enableScissor(0, 0, sidebarW, height);
            float alpha = Math.clamp((sidebarProgress - 0.2f) / 0.8f, 0f, 1f);

            if (minecraft.getWindow().getHeight() > 700) {
                renderTitleTextures(gg, sidebarW, alpha);
            }

            for (SidebarButton btn : menuButtons) {
                btn.render(gg, minecraft, mouseX, mouseY, alpha);
            }

            if (minecraft.getWindow().getHeight() > 700) {
                for (SlideButton btn : linkButtons) {
                    btn.setHovered(btn.isMouseOver(mouseX, mouseY));
                    btn.updateAnimation();
                    btn.render(gg, minecraft, mouseX, mouseY);
                }
            }
            gg.disableScissor();
        }
    }

    private void renderCollapsedText(GuiGraphics gg, int sidebarW) {
        float fadeOut = 1f - Math.clamp(sidebarProgress / 0.3f, 0f, 1f);
        if (fadeOut <= 0f) return;

        int a = Math.clamp((int) (fadeOut * 255), 0, 255);
        int color = (a << 24) | (COLLAPSED_TEXT_COLOR & 0xFFFFFF);

        int marginY = Math.round(height * 0.2f);
        int usableH = height - marginY * 2;
        int step = usableH / (COLLAPSED_CHARS.length - 1);

        float fontScale = 1.2f;
        for (int i = 0; i < COLLAPSED_CHARS.length; i++) {
            String ch = COLLAPSED_CHARS[i];
            int charW = minecraft.font.width(ch);
            int charH = minecraft.font.lineHeight;
            int scaledW = Math.round(charW * fontScale);
            int scaledH = Math.round(charH * fontScale);
            int cx = (sidebarW - scaledW) / 2;
            int cy = marginY + step * i - scaledH / 2;

            gg.pose().pushPose();
            gg.pose().translate(cx, cy, 0);
            gg.pose().scale(fontScale, fontScale, 1f);
            gg.drawString(minecraft.font, ch, 0, 0, color, false);
            gg.pose().popPose();
        }
    }

    private void renderTitleTextures(GuiGraphics gg, int sidebarW, float alpha) {
        int startY = Math.round(height * 0.08f);

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(1f, 1f, 1f, alpha);

        int t1X = (sidebarW - TITLE_1_DISPLAY_W) / 2;
        gg.blit(TITLE_1, t1X, startY, 0, 0f, 0f,
                TITLE_1_DISPLAY_W, TITLE_DISPLAY_H,
                TITLE_1_DISPLAY_W, TITLE_DISPLAY_H);

        int t2Y = startY + TITLE_DISPLAY_H + TITLE_GAP;
        int t2X = (sidebarW - TITLE_2_DISPLAY_W) / 2;
        gg.blit(TITLE_2, t2X, t2Y, 0, 0f, 0f,
                TITLE_2_DISPLAY_W, TITLE_DISPLAY_H,
                TITLE_2_DISPLAY_W, TITLE_DISPLAY_H);

        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        RenderSystem.disableBlend();
    }

    private void updateBgTransition() {
        long now = Util.getMillis();
        float dt = (float) (now - bgLastMs) / 1000f;
        bgLastMs = now;

        CelestialBody.Type bodyType = celestialBody.type();
        if (bodyType != currentBgType) {
            prevBgType = currentBgType;
            currentBgType = bodyType;
            bgTransition = 0f;
        }
        bgTransition = Math.min(1f, bgTransition + dt / BG_TRANSITION_SEC);
    }

    private void renderBackgroundLayer(GuiGraphics gg, boolean baseLayer) {
        if (bgTransition < 1f) {
            renderFullscreenBg(gg, prevBgType, baseLayer, 1f - bgTransition);
        }
        renderFullscreenBg(gg, currentBgType, baseLayer, bgTransition);
    }

    private void renderFullscreenBg(GuiGraphics gg, CelestialBody.Type type, boolean baseLayer, float alpha) {
        if (alpha <= 0f) return;
        ResourceLocation tex;
        if (type == CelestialBody.Type.SUN) {
            tex = baseLayer ? BG_DAY1 : BG_DAY2;
        } else {
            tex = baseLayer ? BG_NIGHT1 : BG_NIGHT2;
        }
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(1f, 1f, 1f, alpha);
        gg.blit(tex, 0, 0, 0, 0f, 0f, width, height, width, height);
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        RenderSystem.disableBlend();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int mx = (int) mouseX, my = (int) mouseY;

        if (sidebarProgress > 0.5f) {
            for (SidebarButton btn : menuButtons) {
                if (btn.isMouseOver(mx, my)) {
                    btn.setPressed(true);
                    btn.click();
                    return true;
                }
            }

            if (minecraft.getWindow().getHeight() > 700) {
                for (SlideButton btn : linkButtons) {
                    if (btn.isMouseOver(mx, my)) {
                        btn.setPressed(true);
                        btn.click();
                        return true;
                    }
                }
            }
        }

        if (celestialBody.isMouseOver(mx, my, width, height)) {
            celestialBody.startDrag(mx, my);
            return true;
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (celestialBody.isDragging()) {
            celestialBody.dragTo((int) mouseX, (int) mouseY);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (celestialBody.isDragging()) {
            celestialBody.endDrag(width, height);
            return true;
        }
        for (SidebarButton btn : menuButtons) {
            btn.setPressed(false);
        }
        for (SlideButton btn : linkButtons) {
            btn.setPressed(false);
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    private static final class SidebarButton {

        private static final int BG_NORMAL = 0xFFFFFFFF;
        private static final int BG_HOVER = 0xFFF0F0F0;
        private static final int BG_PRESSED = 0xFFD0D0D0;
        private static final int BORDER = 0xFF000000;
        private static final int TEXT_COLOR = 0xFF000000;

        private final Component text;
        private final Runnable onClick;

        private int x;
        private int y;
        private int width;
        private int height;
        private boolean pressed;

        SidebarButton(Component text, Runnable onClick) {
            this.text = text;
            this.onClick = onClick;
        }

        void setBounds(int x, int y, int width, int height) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
        }

        boolean isMouseOver(int mouseX, int mouseY) {
            return mouseX >= x && mouseX <= x + width
                    && mouseY >= y && mouseY <= y + height;
        }

        void setPressed(boolean pressed) {
            this.pressed = pressed;
        }

        void click() {
            if (onClick != null) {
                onClick.run();
            }
        }

        void render(GuiGraphics gg, Minecraft mc, int mouseX, int mouseY, float alpha) {
            boolean hovered = isMouseOver(mouseX, mouseY);
            int bg = (pressed && hovered) ? BG_PRESSED : (hovered ? BG_HOVER : BG_NORMAL);
            int a = Math.clamp((int) (alpha * 255), 0, 255);
            int bgColor = (a << 24) | (bg & 0xFFFFFF);
            int borderColor = (a << 24) | (BORDER & 0xFFFFFF);
            int textColor = (a << 24) | (TEXT_COLOR & 0xFFFFFF);

            float scale = hovered ? 1.05f : 1f;
            float cx = x + width / 2f;
            float cy = y + height / 2f;

            gg.pose().pushPose();
            gg.pose().translate(cx, cy, 0);
            gg.pose().scale(scale, scale, 1f);

            float hw = width / 2f;
            float hh = height / 2f;
            int x0 = Math.round(-hw), y0 = Math.round(-hh);
            int x1 = Math.round(hw), y1 = Math.round(hh);

            gg.fill(x0, y0, x1, y1, bgColor);
            gg.hLine(x0, x1 - 1, y0, borderColor);
            gg.hLine(x0, x1 - 1, y1 - 1, borderColor);
            gg.vLine(x0, y0, y1 - 1, borderColor);
            gg.vLine(x1 - 1, y0, y1 - 1, borderColor);

            int textW = mc.font.width(text);
            int textX = -textW / 2;
            int textY = -(mc.font.lineHeight) / 2;
            if (textW > width - 8) {
                float textScale = (float) (width - 8) / textW;
                gg.pose().pushPose();
                gg.pose().scale(textScale, textScale, 1f);
                gg.drawString(mc.font, text, (int) (-textW * textScale / 2), 0, textColor, false);
                gg.pose().popPose();
            } else {
                gg.drawString(mc.font, text, textX, textY, textColor, false);
            }

            gg.pose().popPose();
        }
    }
}
