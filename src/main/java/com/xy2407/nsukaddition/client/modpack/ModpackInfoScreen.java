package com.xy2407.nsukaddition.client.modpack;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.xy2407.nsukaddition.NsukAddition;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLPaths;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** 整合包信息界面：黑底帧上随机漂移且四边反弹的 mod 图标卡片，可拖拽平移、Ctrl+滚轮居中缩放、悬停暂停并显示名称、拖动甩动、点开资源管理器。 */
@OnlyIn(Dist.CLIENT)
public class ModpackInfoScreen extends Screen {

    private static final int CARD_W = 25;
    private static final int CARD_H = 25;
    private static final int CARD_PAD = 4;
    private static final float FRAME_SCALE = 0.78F;
    private static final double MIN_ZOOM = 0.3D;
    private static final double MAX_ZOOM = 3.0D;
    private static final int FIT_EDGE = 16;
    private static final int FRAME_MARGIN = 12;
    private static final int FLICK_SCALE = 40;
    private static final int FRAME_T = 3;
    private static final int FRAME_LINE = 2;
    private static final int BG_BASE = 0xFF444444;
    private static final int PANEL_PAD = 10;
    private static final int INFO_GAP = 8;
    private static final int INFO_BAR_H = 76;
    private static final int LOG_WIDTH = 210;
    private static final int SCROLLBAR_W = 6;
    private static final int TEXT_MUTED = 0xFFBDBDBD;
    private static final int TEXT_LINK = 0xFF7FDBFF;
    private static final int TEXT_LINK_HOVER = 0xFFFFFFFF;
    private static final int TEXT_TITLE = 0xFF80E0FF;
    private static final String UPDATE_URL = "https://pan.quark.cn/s/4f6754b99942";
    private static final String SPONSOR_URL = "https://www.ifdian.net/a/xy2407";
    private static final ResourceLocation BG_TITLE =
            ResourceLocation.fromNamespaceAndPath("xy2407_nsuk_addition", "textures/background/day1.png");

    private static String[] changelogLines = null;
    private static String[] previewLines = null;
    private List<FormattedCharSequence> previewFormatted;
    private boolean previewFormattedLoaded;

    private int worldW;
    private int worldH;
    private double camX;
    private double camY;
    private double zoom = 1.0D;
    private final List<Card> cards = new ArrayList<>();
    private final List<ResourceLocation> registeredIcons = new ArrayList<>();

    private int panelX0;
    private int panelX1;
    private int panelTop;
    private int panelBottom;
    private int barX0;
    private int barX1;
    private int barY0;
    private int barY1;
    private int logX0;
    private int logX1;
    private int logY0;
    private int logY1;

    private int updateLinkX;
    private int updateLinkY;
    private int updateLinkW;
    private int updateLinkH;
    private int sponsorLinkX;
    private int sponsorLinkY;
    private int sponsorLinkW;
    private int sponsorLinkH;

    private double logScroll;
    private int logMaxScroll;
    private boolean logDrag;
    private double logGrabOffset;
    private List<FormattedCharSequence> logDisplayLines = List.of();

    private Card hoveredCard;
    private Card grabbed;
    private boolean panning;
    private double dragTotal;
    private double smoothedVx;
    private double smoothedVy;

    public ModpackInfoScreen() {
        super(Component.literal("modpack_info"));
    }

    private static final class Card {
        double x;
        double y;
        double px;
        double py;
        double vx;
        double vy;
        final double normalMagX;
        final double normalMagY;
        final int w;
        final int h;
        final IconTex icon;
        final Component name;

        Card(double x, double y, int w, int h, IconTex icon, Component name,
             double normalMagX, double normalMagY) {
            this.px = x;
            this.py = y;
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
            this.icon = icon;
            this.name = name;
            this.normalMagX = normalMagX;
            this.normalMagY = normalMagY;
        }
    }

    private record IconTex(ResourceLocation loc, int width, int height) {
    }

    @Override
    public boolean isPauseScreen() {
        return true;
    }

    private static String[] getChangelogLines() {
        String[] cached = changelogLines;
        if (cached != null) {
            return cached;
        }
        List<String> out = new ArrayList<>();
        Path path = FMLPaths.GAMEDIR.get().resolve("xy2407_nsuk_addition/changelog.md");
        try {
            if (Files.isRegularFile(path)) {
                List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
                for (String line : lines) {
                    if (!line.isBlank()) {
                        out.add(line);
                    }
                }
            }
        } catch (Exception e) {
            NsukAddition.LOGGER.warn("nsuk_addition: Failed to read changelog md, show empty", e);
        }
        changelogLines = out.toArray(new String[0]);
        return changelogLines;
    }

    private static String[] getPreviewLines() {
        String[] cached = previewLines;
        if (cached != null) {
            return cached;
        }
        List<String> out = new ArrayList<>();
        Path path = FMLPaths.GAMEDIR.get().resolve("xy2407_nsuk_addition/preview_plan.md");
        try {
            if (Files.isRegularFile(path)) {
                List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
                for (String line : lines) {
                    if (!line.isBlank()) {
                        out.add(line);
                    }
                }
            }
        } catch (Exception e) {
            NsukAddition.LOGGER.warn("nsuk_addition: Failed to read preview plan md, show empty", e);
        }
        previewLines = out.toArray(new String[0]);
        return previewLines;
    }

    private static boolean isVersionHeader(String line) {
        String trimmed = line.trim();
        return !trimmed.isEmpty() && trimmed.charAt(0) == '#';
    }

    private static String stripHeader(String line) {
        StringBuilder sb = new StringBuilder(line);
        int i = 0;
        while (i < sb.length() && (sb.charAt(i) == '#' || sb.charAt(i) == ' ')) {
            i++;
        }
        return sb.substring(i).trim();
    }

    @Override
    public void init() {
        computeGeometry();
        fitZoomToWorld();
        centerView();
        clampCamera();
        cards.clear();
        buildCards();
    }

    private void fitZoomToWorld() {
        double availW = Math.max(1, this.width - FIT_EDGE * 2);
        double availH = Math.max(1, this.height - FIT_EDGE * 2);
        double fit = Math.min(availW / worldW, availH / worldH);
        zoom = Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, fit));
    }

    private void computeGeometry() {
        int lh = minecraft.font.lineHeight;
        int rowGap = 4;
        int baseH = PANEL_PAD * 2 + lh * 5 + rowGap * 4;
        int rightH = PANEL_PAD * 2 + lh * 3;
        int barH = Math.max(baseH, rightH);
        int logW = Math.min(LOG_WIDTH, (int) (this.width * 0.24D));
        worldW = (int) (this.width * FRAME_SCALE) + logW + INFO_GAP + PANEL_PAD;
        worldH = (int) (this.height * FRAME_SCALE) + barH + INFO_GAP + PANEL_PAD;
        panelX0 = PANEL_PAD;
        panelX1 = worldW - PANEL_PAD - logW - INFO_GAP;
        panelTop = PANEL_PAD + barH + INFO_GAP;
        panelBottom = worldH - PANEL_PAD;
        barX0 = PANEL_PAD;
        barX1 = panelX1;
        barY0 = PANEL_PAD;
        barY1 = PANEL_PAD + barH;
        logX0 = panelX1 + INFO_GAP;
        logX1 = worldW - PANEL_PAD;
        logY0 = PANEL_PAD;
        logY1 = worldH - PANEL_PAD;
        logScroll = 0.0D;
        logMaxScroll = 0;
    }

    @Override
    public void onClose() {
        Minecraft mc = Minecraft.getInstance();
        for (ResourceLocation loc : registeredIcons) {
            mc.getTextureManager().release(loc);
        }
        registeredIcons.clear();
        super.onClose();
    }

    private void centerView() {
        camX = (worldW - this.width / zoom) / 2.0D;
        camY = (worldH - this.height / zoom) / 2.0D;
    }

    private void clampCamera() {
        double viewW = this.width / zoom;
        double viewH = this.height / zoom;
        if (worldW > viewW) {
            camX = Math.max(0, Math.min(camX, worldW - viewW));
        } else {
            camX = (worldW - viewW) / 2.0D;
        }
        if (worldH > viewH) {
            camY = Math.max(0, Math.min(camY, worldH - viewH));
        } else {
            camY = (worldH - viewH) / 2.0D;
        }
    }

    private void buildCards() {
        var mods = ModList.get().getMods();
        Random rnd = new Random();
        for (int i = 0; i < mods.size(); i++) {
            var info = mods.get(i);
            String modId = info.getModId();
            IconTex icon = registerIcon(i, modId, info.getLogoFile().orElse(null));
            Component name = Component.literal(firstNonBlank(info.getDisplayName(), modId));
            double minCX = panelX0 + FRAME_MARGIN;
            double minCY = panelTop + FRAME_MARGIN;
            double maxCX = Math.max(minCX, panelX1 - CARD_W - FRAME_MARGIN);
            double maxCY = Math.max(minCY, panelBottom - CARD_H - FRAME_MARGIN);
            double x = minCX + rnd.nextDouble() * (maxCX - minCX);
            double y = minCY + rnd.nextDouble() * (maxCY - minCY);
            double angle = rnd.nextDouble() * Math.PI * 2.0D;
            double speed = 30.0D + rnd.nextDouble() * 60.0D;
            double vx = Math.cos(angle) * speed;
            double vy = Math.sin(angle) * speed;
            Card card = new Card(x, y, CARD_W, CARD_H, icon, name, Math.abs(vx), Math.abs(vy));
            card.vx = vx;
            card.vy = vy;
            cards.add(card);
        }
    }

    private static String firstNonBlank(String a, String b) {
        return a != null && !a.isBlank() ? a : (b != null ? b : "");
    }

    private IconTex registerIcon(int index, String modId, String logo) {
        NativeImage img = loadIcon(modId, logo);
        if (img == null) {
            return null;
        }
        ResourceLocation loc = ResourceLocation.fromNamespaceAndPath("xy2407_nsuk_addition",
                "modpack_mod_icon/" + index);
        Minecraft mc = Minecraft.getInstance();
        try {
            mc.getTextureManager().register(loc, new DynamicTexture(img));
            registeredIcons.add(loc);
            return new IconTex(loc, img.getWidth(), img.getHeight());
        } catch (Throwable t) {
            img.close();
            return null;
        }
    }

    private NativeImage loadIcon(String modId, String logo) {
        Minecraft mc = Minecraft.getInstance();
        NativeImage img = null;
        if (logo != null && !logo.isBlank()) {
            img = readFromModJar(modId, logo);
            if (img == null) {
                img = openImage(mc, ResourceLocation.fromNamespaceAndPath("modlogo", modId + "/" + logo));
            }
        }
        if (img == null) {
            img = openImage(mc, ResourceLocation.fromNamespaceAndPath(modId, "icon.png"));
        }
        if (img == null) {
            img = openImage(mc, ResourceLocation.fromNamespaceAndPath(modId, "textures/icon.png"));
        }
        return img;
    }

    private NativeImage readFromModJar(String modId, String logo) {
        try {
            var mf = ModList.get().getModFileById(modId);
            if (mf == null) {
                return null;
            }
            Path p = mf.getFile().getSecureJar().getPath(logo);
            if (p == null || !Files.exists(p)) {
                return null;
            }
            try (InputStream in = Files.newInputStream(p)) {
                return NativeImage.read(in);
            }
        } catch (Throwable t) {
            return null;
        }
    }

    private NativeImage openImage(Minecraft mc, ResourceLocation loc) {
        try {
            var res = mc.getResourceManager().getResource(loc);
            if (res.isEmpty()) {
                return null;
            }
            try (var in = res.get().open()) {
                return NativeImage.read(in);
            }
        } catch (Throwable t) {
            return null;
        }
    }

    private double toWorldX(double screenX) {
        return camX + screenX / zoom;
    }

    private double toWorldY(double screenY) {
        return camY + screenY / zoom;
    }

    private Card hitTest(double screenX, double screenY) {
        double wx = toWorldX(screenX);
        double wy = toWorldY(screenY);
        for (int i = cards.size() - 1; i >= 0; i--) {
            Card c = cards.get(i);
            if (wx >= c.x && wx <= c.x + c.w && wy >= c.y && wy <= c.y + c.h) {
                return c;
            }
        }
        return null;
    }

    private void clampCard(Card card) {
        double minX = panelX0 + FRAME_MARGIN;
        double minY = panelTop + FRAME_MARGIN;
        card.x = Math.max(minX, Math.min(card.x, panelX1 - card.w - FRAME_MARGIN));
        card.y = Math.max(minY, Math.min(card.y, panelBottom - card.h - FRAME_MARGIN));
    }

    @Override
    public void tick() {
        for (Card card : cards) {
            if (card == grabbed || card == hoveredCard) {
                card.px = card.x;
                card.py = card.y;
                continue;
            }
            card.px = card.x;
            card.py = card.y;
            card.x += card.vx / 20.0D;
            card.y += card.vy / 20.0D;
            double minX = panelX0 + FRAME_MARGIN;
            double maxX = panelX1 - card.w - FRAME_MARGIN;
            double minY = panelTop + FRAME_MARGIN;
            double maxY = panelBottom - card.h - FRAME_MARGIN;
            if (card.x < minX) {
                card.x = minX;
                card.vx = bounceDampen(Math.abs(card.vx), card.normalMagX);
            } else if (card.x > maxX) {
                card.x = maxX;
                card.vx = -bounceDampen(Math.abs(card.vx), card.normalMagX);
            }
            if (card.y < minY) {
                card.y = minY;
                card.vy = bounceDampen(Math.abs(card.vy), card.normalMagY);
            } else if (card.y > maxY) {
                card.y = maxY;
                card.vy = -bounceDampen(Math.abs(card.vy), card.normalMagY);
            }
        }
    }

    private static double bounceDampen(double magnitude, double normalMag) {
        double base = Math.abs(normalMag);
        if (magnitude > base) {
            double extra = (magnitude - base) * 0.55D;
            if (extra < 0.5D) {
                extra = 0.0D;
            }
            magnitude = base + extra;
        } else {
            magnitude = base;
        }
        return magnitude;
    }

    @Override
    public void renderBackground(GuiGraphics gg, int mouseX, int mouseY, float partialTick) {
    }

    @Override
    public void render(GuiGraphics gg, int mouseX, int mouseY, float partialTick) {
        gg.fill(0, 0, this.width, this.height, BG_BASE);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(1f, 1f, 1f, 0.5f);
        gg.blit(BG_TITLE, 0, 0, 0, 0f, 0f, this.width, this.height, this.width, this.height);
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        RenderSystem.disableBlend();

        hoveredCard = (grabbed == null) ? hitTest(mouseX, mouseY) : grabbed;

        gg.pose().pushPose();
        gg.pose().scale((float) zoom, (float) zoom, 1.0F);
        gg.pose().translate((float) -camX, (float) -camY, 0.0F);

        renderPanels(gg, mouseX, mouseY);

        gg.pose().popPose();

        for (Card card : cards) {
            renderCardScreen(gg, card, partialTick);
        }

        if (hoveredCard != null && grabbed == null && hoveredCard.name != null) {
            String name = hoveredCard.name.getString();
            int tw = this.font.width(name);
            int tx = (this.width - tw) / 2;
            int ty = 12;
            gg.fill(tx - 5, ty - 2, tx + tw + 5, ty + this.font.lineHeight + 2, 0xCC000000);
            gg.drawString(this.font, name, tx, ty, 0xFFFFFFFF, false);
        }
    }

    private void renderPanels(GuiGraphics gg, int mouseX, int mouseY) {
        renderInfoBar(gg, mouseX, mouseY);
        renderMainFrame(gg);
        renderLogBox(gg);
    }

    private void renderBox(GuiGraphics gg, int x0, int y0, int x1, int y1, int bg, int border) {
        gg.fill(x0, y0, x1, y1, bg);
        gg.hLine(x0, x1 - 1, y0, border);
        gg.hLine(x0, x1 - 1, y1 - 1, border);
        gg.vLine(x0, y0, y1 - 1, border);
        gg.vLine(x1 - 1, y0, y1 - 1, border);
    }

    private void drawBox(GuiGraphics gg, int bx, int by, int bw, int bh, int color) {
        gg.fill(bx, by, bx + bw, by + 1, color);
        gg.fill(bx, by + bh - 1, bx + bw, by + bh, color);
        gg.fill(bx, by, bx + 1, by + bh, color);
        gg.fill(bx + bw - 1, by, bx + bw, by + bh, color);
    }

    private static boolean creditAvatarLoaded = false;
    private static ResourceLocation creditAvatarTex = null;

    private static final int CREDIT_AVATAR_SIZE = 128;

    private void ensureCreditAvatar(Minecraft mc) {
        if (creditAvatarLoaded) return;
        creditAvatarLoaded = true;
        creditAvatarTex = null;
        try {
            ResourceLocation loc = ResourceLocation.fromNamespaceAndPath(
                    "xy2407_nsuk_addition", "textures/credit/1.jpg");
            NativeImage src = openImage(mc, loc);
            if (src == null) return;
            int iw = src.getWidth(), ih = src.getHeight();
            NativeImage sq = new NativeImage(CREDIT_AVATAR_SIZE, CREDIT_AVATAR_SIZE, false);
            for (int y = 0; y < CREDIT_AVATAR_SIZE; y++) {
                int sy = Math.min(ih - 1, y * ih / CREDIT_AVATAR_SIZE);
                for (int x = 0; x < CREDIT_AVATAR_SIZE; x++) {
                    int sx = Math.min(iw - 1, x * iw / CREDIT_AVATAR_SIZE);
                    sq.setPixelRGBA(x, y, src.getPixelRGBA(sx, sy));
                }
            }
            int c = CREDIT_AVATAR_SIZE / 2;
            int r2 = c * c;
            for (int y = 0; y < CREDIT_AVATAR_SIZE; y++) {
                int dy = y - c;
                for (int x = 0; x < CREDIT_AVATAR_SIZE; x++) {
                    int dx = x - c;
                    if (dx * dx + dy * dy > r2) {
                        sq.setPixelRGBA(x, y, 0);
                    }
                }
            }
            creditAvatarTex = mc.getTextureManager().register(
                    "xy2407_nsuk_addition_credit_avatar", new DynamicTexture(sq));
        } catch (Throwable ignore) {
            creditAvatarTex = null;
        }
    }

    private void renderCreditAvatar(GuiGraphics gg, Minecraft mc, int x, int y, int dia) {
        ensureCreditAvatar(mc);
        if (creditAvatarTex == null) return;
        gg.blit(creditAvatarTex, x, y, dia, dia,
                0.0F, 0.0F, CREDIT_AVATAR_SIZE, CREDIT_AVATAR_SIZE, CREDIT_AVATAR_SIZE, CREDIT_AVATAR_SIZE);
    }

    private void renderInfoBar(GuiGraphics gg, int mouseX, int mouseY) {
        renderBox(gg, barX0, barY0, barX1, barY1, 0xFF282838, 0xFFAAAAAA);

        int lh = minecraft.font.lineHeight;
        int rowGap = 4;
        int leftX = barX0 + PANEL_PAD;
        String[] leftLines = {
                "整合包名称:[CT]城市模拟City Simulation",
                "整合包作者：星影2407",
                "整合包交流：1061156620",
                "整合包更新（点击跳转）",
                "整合包赞助（点击跳转）"
        };
        int lineX = leftX;
        int lineY = barY0 + PANEL_PAD;
        for (int i = 0; i < leftLines.length; i++) {
            int color = TEXT_MUTED;
            int textW = minecraft.font.width(leftLines[i]);
            if (i == 3) {
                updateLinkX = lineX;
                updateLinkY = lineY;
                updateLinkW = textW;
                updateLinkH = lh;
                color = overScreenPoint(mouseX, mouseY, updateLinkX, updateLinkY, updateLinkW, updateLinkH)
                        ? TEXT_LINK_HOVER : TEXT_LINK;
            }
            if (i == 4) {
                sponsorLinkX = lineX;
                sponsorLinkY = lineY;
                sponsorLinkW = textW;
                sponsorLinkH = lh;
                color = overScreenPoint(mouseX, mouseY, sponsorLinkX, sponsorLinkY, sponsorLinkW, sponsorLinkH)
                        ? TEXT_LINK_HOVER : TEXT_LINK;
            }
            gg.drawString(minecraft.font, leftLines[i], lineX, lineY, color, false);
            if ((i == 3 || i == 4) && color == TEXT_LINK_HOVER) {
                gg.hLine(lineX, lineX + textW - 1, lineY + lh, TEXT_LINK_HOVER);
            }
            lineY += lh + rowGap;
        }

        int rightZoneW = 130;
        int splitX = (barX1 == panelX1) ? panelX1 - PANEL_PAD - rightZoneW : barX1 - PANEL_PAD - rightZoneW;
        gg.vLine(splitX, barY0 + PANEL_PAD, barY1 - PANEL_PAD, 0xFF666688);

        int rx = splitX + PANEL_PAD;
        gg.drawString(minecraft.font, "特别贡献：", rx, barY0 + PANEL_PAD, TEXT_MUTED, false);
        int nameY = barY0 + PANEL_PAD + lh + 3;
        renderCreditAvatar(gg, minecraft, rx, nameY, lh);
        gg.drawString(minecraft.font, "米青", rx + lh + 3, nameY, TEXT_MUTED, false);
    }

    private void renderMainFrame(GuiGraphics gg) {
        int panelXE = panelX0 + FRAME_T;
        int panelYE = panelTop + FRAME_T;
        gg.fill(panelX0, panelTop, panelX1, panelBottom, 0xFF14141F);
        gg.fill(panelX0, panelTop, panelX1, panelTop + FRAME_T, 0xFFDDDDDD);
        gg.fill(panelX0, panelBottom - FRAME_T, panelX1, panelBottom, 0xFFDDDDDD);
        gg.fill(panelX0, panelTop, panelX0 + FRAME_T, panelBottom, 0xFFDDDDDD);
        gg.fill(panelX1 - FRAME_T, panelTop, panelX1, panelBottom, 0xFFDDDDDD);
        int inset = FRAME_T + 6;
        gg.fill(panelXE, panelYE, panelX1 - inset, panelYE + FRAME_LINE, 0xFF666688);
        gg.fill(panelXE, panelBottom - inset - FRAME_LINE, panelX1 - inset, panelBottom - inset, 0xFF666688);
        gg.fill(panelXE, panelYE, panelXE + FRAME_LINE, panelBottom - inset, 0xFF666688);
        gg.fill(panelX1 - inset - FRAME_LINE, panelYE, panelX1 - inset, panelBottom - inset, 0xFF666688);
    }

    private void renderLogBox(GuiGraphics gg) {
        renderBox(gg, logX0, logY0, logX1, logY1, 0xFF1C1C2A, 0xFFAAAAAA);

        int lh = minecraft.font.lineHeight;
        int padX = logX0 + 6;
        int maxTextX = logX1 - SCROLLBAR_W - 8;
        int maxLineW = Math.max(20, maxTextX - padX);

        List<FormattedCharSequence> preview = previewFormatted();
        String pTitle = "预更新";
        int pTitleY = logY0 + 5;
        gg.drawString(minecraft.font, pTitle, logX0 + (logX1 - logX0 - minecraft.font.width(pTitle)) / 2, pTitleY, TEXT_TITLE, false);
        int py = pTitleY + lh + 3;
        for (FormattedCharSequence seq : preview) {
            gg.drawString(minecraft.font, seq, padX, py, TEXT_MUTED, false);
            py += lh;
        }
        int previewBottom = py + 4;
        gg.hLine(logX0 + 6, logX1 - SCROLLBAR_W - 6, previewBottom, 0xFF444466);

        String uTitle = "更新";
        int uTitleY = previewBottom + 5;
        gg.drawString(minecraft.font, uTitle, logX0 + (logX1 - logX0 - minecraft.font.width(uTitle)) / 2, uTitleY, TEXT_TITLE, false);
        int viewTop = uTitleY + lh + 3;

        String[] logLines = getChangelogLines();
        int viewY1 = logViewY1();
        int viewH = viewY1 - viewTop;
        List<FormattedCharSequence> displayLines = new ArrayList<>();
        List<Boolean> versionFlags = new ArrayList<>();
        for (String line : logLines) {
            boolean versionLine = isVersionHeader(line);
            String text = versionLine ? stripHeader(line) : line;
            for (FormattedCharSequence seq : this.font.split(Component.literal(text), maxLineW)) {
                displayLines.add(seq);
                versionFlags.add(versionLine);
            }
        }
        logDisplayLines = displayLines;
        int contentH = displayLines.size() * lh;
        logMaxScroll = Math.max(0, contentH - viewH);
        logScroll = Math.max(0.0D, Math.min(logMaxScroll, logScroll));

        int sx0 = toScreenX(padX);
        int sy0 = toScreenY(viewTop);
        int sx1 = toScreenX(maxTextX);
        int sy1 = toScreenY(viewY1);
        gg.enableScissor(sx0, sy0, sx1, sy1);
        for (int i = 0; i < displayLines.size(); i++) {
            int y = viewTop + i * lh - (int) logScroll;
            int lineColor = versionFlags.get(i) ? TEXT_TITLE : TEXT_MUTED;
            gg.drawString(minecraft.font, displayLines.get(i), padX, y, lineColor, false);
        }
        gg.disableScissor();

        int trackX = logTrackX();
        gg.fill(trackX, viewTop, trackX + SCROLLBAR_W, viewY1, 0xFF0F0F18);
        int handleH = logHandleH();
        int handleTop = logHandleTop();
        int handleColor = logDrag ? 0xFFDDDDDD : 0xFF8888AA;
        gg.fill(trackX, handleTop, trackX + SCROLLBAR_W, handleTop + handleH, handleColor);
    }

    private List<FormattedCharSequence> previewFormatted() {
        if (previewFormattedLoaded) {
            return previewFormatted;
        }
        previewFormattedLoaded = true;
        int padX = logX0 + 6;
        int maxTextX = logX1 - SCROLLBAR_W - 8;
        int maxLineW = Math.max(20, maxTextX - padX);
        List<FormattedCharSequence> out = new ArrayList<>();
        for (String line : getPreviewLines()) {
            for (FormattedCharSequence seq : this.font.split(Component.literal(line), maxLineW)) {
                out.add(seq);
            }
        }
        previewFormatted = out;
        return previewFormatted;
    }

    private int logViewY0() {
        int lh = minecraft.font.lineHeight;
        int pTitleY = logY0 + 5;
        int previewLinesH = previewFormatted().size() * lh;
        int previewBottom = pTitleY + lh + 3 + previewLinesH + 4;
        return previewBottom + 5 + lh + 3;
    }

    private int logViewY1() {
        return logY1 - 6;
    }

    private int logTrackX() {
        return logX1 - SCROLLBAR_W - 3;
    }

    private int logHandleH() {
        int viewH = logViewY1() - logViewY0();
        int contentH = logDisplayLines.size() * minecraft.font.lineHeight;
        return (logMaxScroll <= 0) ? viewH
                : Math.max(SCROLLBAR_W, viewH * viewH / Math.max(viewH, contentH));
    }

    private int logHandleTop() {
        int viewH = logViewY1() - logViewY0();
        int handleH = logHandleH();
        return (logMaxScroll <= 0) ? logViewY0()
                : logViewY0() + (int) ((long) (viewH - handleH) * (long) logScroll / logMaxScroll);
    }

    private int toScreenX(double wx) {
        return (int) Math.round((wx - camX) * zoom);
    }

    private int toScreenY(double wy) {
        return (int) Math.round((wy - camY) * zoom);
    }

    private boolean overScreenPoint(double mouseX, double mouseY, int wx0, int wy0, int ww, int wh) {
        double mx = toWorldX(mouseX);
        double my = toWorldY(mouseY);
        return mx >= wx0 && mx <= wx0 + ww && my >= wy0 && my <= wy0 + wh;
    }

    private void renderCardScreen(GuiGraphics gg, Card card, float partialTick) {
        float t = Math.max(0.0F, Math.min(1.0F, partialTick));
        double ix = card.px + (card.x - card.px) * t;
        double iy = card.py + (card.y - card.py) * t;
        // 亚像素渲染：保留浮点屏幕坐标，整数只取底，把小数部分作为偏移平移绘制，消除移动颗粒感
        double sx = (ix - camX) * zoom;
        double sy = (iy - camY) * zoom;
        int x = (int) Math.floor(sx);
        int y = (int) Math.floor(sy);
        float subX = (float) (sx - x);
        float subY = (float) (sy - y);
        int w = Math.max(1, Math.round(card.w * (float) zoom));
        int h = Math.max(1, Math.round(card.h * (float) zoom));
        int pad = Math.max(1, Math.round(CARD_PAD * (float) zoom));
        gg.pose().pushPose();
        gg.pose().translate(subX, subY, 0.0F);
        gg.fill(x, y, x + w, y + h, 0xFF202030);
        drawBox(gg, x, y, w, h, 0xFFDDDDDD);
        int inset = Math.max(1, Math.round(2.0F * (float) zoom));
        if (w - inset * 2 > 0 && h - inset * 2 > 0) {
            drawBox(gg, x + inset, y + inset, w - inset * 2, h - inset * 2, 0xFF666688);
        }
        IconTex it = card.icon;
        if (it != null) {
            int box = w - pad * 2;
            if (box > 0) {
                float ratio = Math.min((float) box / it.width, (float) box / it.height);
                int dw = Math.max(1, Math.round(it.width * ratio));
                int dh = Math.max(1, Math.round(it.height * ratio));
                int ix2 = x + (w - dw) / 2;
                int iy2 = y + (h - dh) / 2;
                gg.blit(it.loc, ix2, iy2, dw, dh, 0.0F, 0.0F, it.width, it.height, it.width, it.height);
            }
        }
        gg.pose().popPose();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            if (overScreenPoint(mouseX, mouseY, updateLinkX, updateLinkY, updateLinkW, updateLinkH)) {
                net.minecraft.Util.getPlatform().openUri(UPDATE_URL);
                return true;
            }
            if (overScreenPoint(mouseX, mouseY, sponsorLinkX, sponsorLinkY, sponsorLinkW, sponsorLinkH)) {
                net.minecraft.Util.getPlatform().openUri(SPONSOR_URL);
                return true;
            }
            double wx = toWorldX(mouseX);
            double wy = toWorldY(mouseY);
            int trackX = logTrackX();
            int viewY0 = logViewY0();
            int viewY1 = logViewY1();
            if (wx >= trackX && wx <= trackX + SCROLLBAR_W && wy >= viewY0 && wy <= viewY1) {
                int handleTop = logHandleTop();
                int handleH = logHandleH();
                if (wy >= handleTop && wy <= handleTop + handleH) {
                    logDrag = true;
                    logGrabOffset = handleTop - wy;
                } else if (logMaxScroll > 0) {
                    logScroll = (double) (wy - viewY0 - handleH / 2.0D) / (viewY1 - viewY0 - handleH) * logMaxScroll;
                    logScroll = Math.max(0.0D, Math.min(logMaxScroll, logScroll));
                }
                return true;
            }

            Card hit = hitTest(mouseX, mouseY);
            if (hit != null) {
                grabbed = hit;
                dragTotal = 0.0D;
                smoothedVx = 0.0D;
                smoothedVy = 0.0D;
                return true;
            }
            panning = true;
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (button == 0) {
            if (logDrag) {
                double wy = toWorldY(mouseY);
                double viewY0 = logViewY0();
                double trackSpan = (logViewY1() - viewY0) - logHandleH();
                if (trackSpan > 0 && logMaxScroll > 0) {
                    logScroll = (wy - viewY0 + logGrabOffset) / trackSpan * logMaxScroll;
                    logScroll = Math.max(0.0D, Math.min(logMaxScroll, logScroll));
                }
                return true;
            }
            if (grabbed != null) {
                double wd = dragX / zoom;
                double wh = dragY / zoom;
                grabbed.x += wd;
                grabbed.y += wh;
                clampCard(grabbed);
                grabbed.px = grabbed.x;
                grabbed.py = grabbed.y;
                dragTotal += Math.abs(dragX) + Math.abs(dragY);
                smoothedVx = smoothedVx * 0.5D + wd * 0.5D;
                smoothedVy = smoothedVy * 0.5D + wh * 0.5D;
                return true;
            }
            if (panning) {
                camX -= dragX / zoom;
                camY -= dragY / zoom;
                clampCamera();
                return true;
            }
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0) {
            logDrag = false;
            if (grabbed != null) {
                if (dragTotal >= 6.0D) {
                    grabbed.vx = clampSpeed(smoothedVx * FLICK_SCALE);
                    grabbed.vy = clampSpeed(smoothedVy * FLICK_SCALE);
                }
                grabbed = null;
            }
            panning = false;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    private static double clampSpeed(double v) {
        return Math.max(-450.0D, Math.min(450.0D, v));
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double deltaX, double deltaY) {
        if (hasControlDown()) {
            double newZoom = Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, zoom * (1.0D + deltaY * 0.1D)));
            double centerW = camX + (this.width / 2.0D) / zoom;
            double centerH = camY + (this.height / 2.0D) / zoom;
            zoom = newZoom;
            camX = centerW - (this.width / 2.0D) / zoom;
            camY = centerH - (this.height / 2.0D) / zoom;
            clampCamera();
            return true;
        }
        double mx = toWorldX(mouseX);
        double my = toWorldY(mouseY);
        if (mx >= logX0 && mx <= logX1 && my >= logY0 && my <= logY1) {
            logScroll -= deltaY * 12.0D;
            logScroll = Math.max(0.0D, Math.min(logMaxScroll, logScroll));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, deltaX, deltaY);
    }
}