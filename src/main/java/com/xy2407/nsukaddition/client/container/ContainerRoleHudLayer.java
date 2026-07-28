package com.xy2407.nsukaddition.client.container;

import com.xy2407.nsukaddition.client.hud.BatchTextRenderer;
import com.xy2407.nsukaddition.common.network.ContainerRoleResponsePacket;
import com.xy2407.nsukaddition.common.network.ContainerRoleResponsePacket.RoleEntry;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.core.BlockPos;
import org.joml.Matrix4f;

import java.util.List;

/** 容器角色 HUD 图层：浅灰卡片 + 多角色堆叠 + 准星连线动画。 */
public final class ContainerRoleHudLayer implements LayeredDraw.Layer {

    public static final ContainerRoleHudLayer INSTANCE = new ContainerRoleHudLayer();

    private static final BatchTextRenderer TEXT_RENDERER = new BatchTextRenderer();
    private static final int CARD_WIDTH = 185;
    private static final int CARD_PADDING = 8;
    private static final int LINE_GAP = 12;
    private static final int CARD_GAP = 4;

    private static final int BG_COLOR       = 0xCCD0D0D0;
    private static final int BORDER_COLOR   = 0xAA888888;
    private static final int TITLE_COLOR    = 0xFF333333;
    private static final int COORD_COLOR    = 0xFF555555;
    private static final int TEXT_COLOR     = 0xFF444444;
    private static final int LINE_COLOR     = 0xAA888888;
    private static final int CORNER_RADIUS = 4;

    private float animationProgress;
    private BlockPos lastRenderedPos;
    private int cachedCardH;
    private int cachedCardW;
    private int cardCount;

    private ContainerRoleHudLayer() {}

    @Override
    public void render(GuiGraphics gg, DeltaTracker dt) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen != null) return;

        ContainerRoleResponsePacket response = ContainerRoleClientCache.getResponse();
        if (response == null) {
            animationProgress = 0.0F;
            lastRenderedPos = null;
            return;
        }

        List<RoleEntry> roles = response.roles();
        if (roles.isEmpty()) {
            animationProgress = 0.0F;
            lastRenderedPos = null;
            return;
        }

        if (lastRenderedPos == null || !lastRenderedPos.equals(response.containerPos())) {
            lastRenderedPos = response.containerPos();
            animationProgress = 0.0F;
            cardCount = roles.size();
            precomputeCardSize(mc.font, roles);
        }

        animationProgress += (1.0F - animationProgress) * 0.18F;
        if (animationProgress < 0.01F) return;
        float p = Math.min(animationProgress, 1.0F);

        Font font = mc.font;
        int sw = gg.guiWidth();
        int sh = gg.guiHeight();
        int cx = sw / 2;
        int cy = sh / 2;

        int cardX = cx + 24;
        int cardY = cy - cachedCardH / 2;

        Matrix4f matrix = gg.pose().last().pose();

        int baseY = cardY;
        int lineAlpha = (int)(200 * p) << 24;
        for (int i = 0; i < roles.size(); i++) {
            RoleEntry role = roles.get(i);
            int cy2 = baseY + i * (cachedCardH / cardCount + CARD_GAP);
            int cardHeight = cachedCardH / cardCount;

            int elbowX = cx + 16;
            int cardCenterY = cy2 + cardHeight / 2;
            int elbowY = cardCenterY;

            int segDx = elbowX - cx, segDy = elbowY - cy;
            float seg1Len = (float) Math.sqrt(segDx * segDx + segDy * segDy);
            float seg2Len = (float)(cardX - elbowX);
            float totalLen = seg1Len + seg2Len;
            float drawn = p * totalLen;

            if (drawn > 0 && seg1Len > 0) {
                float t = Math.min(drawn / seg1Len, 1.0F);
                int ex = cx + (int)(segDx * t);
                int ey = cy + (int)(segDy * t);
                drawLine(gg, cx, cy, ex, ey, lineAlpha | 0x888888);
            }
            drawn -= seg1Len;
            if (drawn > 0 && seg2Len > 0) {
                float t = Math.min(drawn / seg2Len, 1.0F);
                int ex = elbowX + (int)((cardX - elbowX) * t);
                drawLine(gg, elbowX, elbowY, ex, elbowY, lineAlpha | 0x888888);
            }

            int alpha = (int)(p * 200) << 24;
            int bg = (BG_COLOR & 0x00FFFFFF) | alpha;
            int border = (BORDER_COLOR & 0x00FFFFFF) | Math.min(alpha, 0xAA000000);

            gg.fill(cardX, cy2, cardX + cachedCardW, cy2 + cardHeight, bg);
            gg.fill(cardX, cy2, cardX + cachedCardW, cy2 + 1, border);
            gg.fill(cardX, cy2 + cardHeight - 1, cardX + cachedCardW, cy2 + cardHeight, border);
            gg.fill(cardX, cy2, cardX + 1, cy2 + cardHeight, border);
            gg.fill(cardX + cachedCardW - 1, cy2, cardX + cachedCardW, cy2 + cardHeight, border);

            String title = switch (role.role()) {
                case "input" -> "\u6750\u6599\u8F93\u5165\u5904";
                case "output" -> "\u4EA7\u51FA\u5B58\u653E\u5904";
                default -> "\u5BB9\u5668";
            };
            String coords = "[" + role.relativeX() + " " + role.relativeY() + " " + role.relativeZ() + "]";
            String desc = switch (role.role()) {
                case "input" -> switch (role.boxType()) {
                    case "restaurant" -> "\u70F9\u996A\u539F\u6599\u8865\u5145\u5904";
                    default -> "\u8BF7\u5C06\u6750\u6599\u653E\u5165\u5176\u4E2D";
                };
                case "output" -> switch (role.boxType()) {
                    case "restaurant" -> "\u83DC\u54C1\u51FA\u9910\u5904";
                    default -> "\u4EA7\u51FA\u7269\u54C1\u6536\u96C6\u5728\u6B64\u5904";
                };
                default -> "";
            };

            TEXT_RENDERER.beginFrame(gg);
            try {
                int tx = cardX + CARD_PADDING;
                int ty = cy2 + CARD_PADDING;
                TEXT_RENDERER.drawText(font, title, tx, ty, TITLE_COLOR, false, matrix);
                ty += LINE_GAP;
                TEXT_RENDERER.drawText(font, coords, tx, ty, COORD_COLOR, false, matrix);
                if (!desc.isEmpty()) {
                    ty += LINE_GAP;
                    TEXT_RENDERER.drawText(font, desc, tx, ty, TEXT_COLOR, false, matrix);
                }
            } finally {
                TEXT_RENDERER.endFrame();
            }
        }
    }

    private void precomputeCardSize(Font font, List<RoleEntry> roles) {
        int maxW = 0;
        int totalH = 0;
        for (RoleEntry role : roles) {
            String title = switch (role.role()) {
                case "input" -> "\u6750\u6599\u8F93\u5165\u5904";
                case "output" -> "\u4EA7\u51FA\u5B58\u653E\u5904";
                default -> "\u5BB9\u5668";
            };
            String coords = "[" + role.relativeX() + " " + role.relativeY() + " " + role.relativeZ() + "]";
            String desc = switch (role.role()) {
                case "input" -> switch (role.boxType()) {
                    case "restaurant" -> "\u70F9\u996A\u539F\u6599\u8865\u5145\u5904";
                    default -> "\u8BF7\u5C06\u6750\u6599\u653E\u5165\u5176\u4E2D";
                };
                case "output" -> switch (role.boxType()) {
                    case "restaurant" -> "\u83DC\u54C1\u51FA\u9910\u5904";
                    default -> "\u4EA7\u51FA\u7269\u54C1\u6536\u96C6\u5728\u6B64\u5904";
                };
                default -> "";
            };
            int w = Math.max(font.width(title), Math.max(font.width(coords), font.width(desc)));
            if (w > maxW) maxW = w;
            totalH += (desc.isEmpty() ? 2 : 3) * LINE_GAP + CARD_PADDING * 2 + (roles.size() > 1 ? CARD_GAP : 0);
        }
        cachedCardW = Math.min(maxW + CARD_PADDING * 2, CARD_WIDTH);
        cachedCardH = Math.max(totalH, 60);
    }

    private static void drawLine(GuiGraphics gg, int x1, int y1, int x2, int y2, int color) {
        if (x1 == x2 && y1 == y2) return;
        if (x1 == x2) {
            int minY = Math.min(y1, y2), maxY = Math.max(y1, y2);
            gg.fill(x1, minY, x1 + 1, maxY + 1, color);
        } else if (y1 == y2) {
            int minX = Math.min(x1, x2), maxX = Math.max(x1, x2);
            gg.fill(minX, y1, maxX + 1, y1 + 1, color);
        } else {
            int dx = Math.abs(x2 - x1), dy = Math.abs(y2 - y1);
            int steps = Math.max(dx, dy);
            for (int i = 0; i <= steps; i++) {
                int x = x1 + (x2 - x1) * i / steps;
                int y = y1 + (y2 - y1) * i / steps;
                gg.fill(x, y, x + 1, y + 1, color);
            }
        }
    }
}
