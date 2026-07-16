package com.xy2407.nsukaddition.client.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/** 渲染市民皮肤的头部图标，用于侧边栏和人口列表。 */
@OnlyIn(Dist.CLIENT)
public final class CitizenHeadRenderer {

    private static final String MOD_ID = "simukraft";
    private static final int TEX_SIZE = 64;

    private CitizenHeadRenderer() {
    }

    public static void render(GuiGraphics gg, String skinPath, int x, int y, int size, int borderColor) {
        ResourceLocation texture = resolveSkinTexture(skinPath);
        if (texture == null) {
            gg.fill(x, y, x + size, y + size, 0xFF666666);
        } else {
            RenderSystem.enableBlend();

            gg.blit(texture, x, y, size, size, 8.0F, 8.0F, 8, 8, TEX_SIZE, TEX_SIZE);

            gg.blit(texture, x, y, size, size, 40.0F, 8.0F, 8, 8, TEX_SIZE, TEX_SIZE);
            RenderSystem.disableBlend();
        }
        gg.renderOutline(x, y, size, size, borderColor);
    }

    private static ResourceLocation resolveSkinTexture(String skinPath) {
        if (skinPath == null || skinPath.isBlank() || skinPath.contains("..") || skinPath.startsWith("/")) {
            return null;
        }
        String normalized = skinPath.replace('\\', '/').trim();
        if (normalized.startsWith(MOD_ID + ":")) {
            normalized = normalized.substring((MOD_ID + ":").length());
        }
        if (normalized.startsWith("assets/" + MOD_ID + "/")) {
            normalized = normalized.substring(("assets/" + MOD_ID + "/").length());
        }
        if (normalized.endsWith(".png")) {
            normalized = normalized.substring(0, normalized.length() - 4);
        }
        if (!normalized.startsWith("textures/")) {
            normalized = normalized.startsWith("entity/")
                    ? "textures/" + normalized
                    : "textures/entity/" + normalized;
        }
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, normalized + ".png");
    }
}
