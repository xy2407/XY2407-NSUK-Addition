package com.xy2407.nsukaddition.client.compat.xaero;

import com.xy2407.nsukaddition.client.vein.OreVeinClientCache;
import com.xy2407.nsukaddition.common.vein.OreVeinType;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import xaero.map.highlight.ChunkHighlighter;

import java.util.List;

/** Xaero's World Map 矿脉区块高亮渲染器，按矿脉类型着色并在地图上显示提示。 */
@OnlyIn(Dist.CLIENT)
public final class NsukVeinHighlighter extends ChunkHighlighter {

    private static final int[] ORE_COLORS = {
            0xFF3D3D3D,
            0xFFB87333,
            0xFFD9D9D9,
            0xFFFFD700,
            0xFFFF0000,
            0xFF4169E1,
            0xFF00CED1,
            0xFF50C878,
            0xFFE8E6D9,
            0xFFFFA500,
            0xFF4B0082
    };

    private static final int FILL_ALPHA = 0x50;
    private static final int BORDER_ALPHA = 0xDD;

    private static volatile boolean renderEnabled;
    private volatile int cachedDataVersion = -1;

    public NsukVeinHighlighter() {
        super(true);
    }

    public static void setRenderEnabled(boolean enabled) {
        renderEnabled = enabled;
    }

    @Override
    public boolean regionHasHighlights(ResourceKey<Level> dimension, int regionX, int regionZ) {
        return renderEnabled
                && isCurrentDimension(dimension)
                && OreVeinClientCache.getInstance().regionHasVeins(dimension, regionX, regionZ);
    }

    @Override
    protected int[] getColors(ResourceKey<Level> dimension, int chunkX, int chunkZ) {
        if (!renderEnabled || !isCurrentDimension(dimension)) return null;
        OreVeinType type = OreVeinClientCache.getInstance().getVein(dimension, chunkX, chunkZ);
        if (type == null) return null;

        int baseColor = ORE_COLORS[Math.floorMod(type.ordinal(), ORE_COLORS.length)];
        int fillColor = xaeroColor(baseColor, FILL_ALPHA);
        int borderColor = xaeroColor(baseColor, BORDER_ALPHA);

        OreVeinClientCache cache = OreVeinClientCache.getInstance();
        this.resultStore[0] = fillColor;
        this.resultStore[1] = sameVein(cache, dimension, chunkX, chunkZ, chunkX, chunkZ - 1) ? fillColor : borderColor;
        this.resultStore[2] = sameVein(cache, dimension, chunkX, chunkZ, chunkX + 1, chunkZ) ? fillColor : borderColor;
        this.resultStore[3] = sameVein(cache, dimension, chunkX, chunkZ, chunkX, chunkZ + 1) ? fillColor : borderColor;
        this.resultStore[4] = sameVein(cache, dimension, chunkX, chunkZ, chunkX - 1, chunkZ) ? fillColor : borderColor;
        return this.resultStore;
    }

    @Override
    public int calculateRegionHash(ResourceKey<Level> dimension, int regionX, int regionZ) {
        if (!isCurrentDimension(dimension)) return 0;
        int v = OreVeinClientCache.getInstance().getDataVersion();
        if (v != cachedDataVersion) {
            cachedDataVersion = v;
            return v;
        }
        return v;
    }

    @Override
    public boolean chunkIsHighlit(ResourceKey<Level> dimension, int chunkX, int chunkZ) {
        return renderEnabled
                && isCurrentDimension(dimension)
                && OreVeinClientCache.getInstance().hasVein(dimension, chunkX, chunkZ);
    }

    @Override
    public Component getChunkHighlightSubtleTooltip(ResourceKey<Level> dimension, int chunkX, int chunkZ) {
        return tooltip(dimension, chunkX, chunkZ);
    }

    @Override
    public Component getChunkHighlightBluntTooltip(ResourceKey<Level> dimension, int chunkX, int chunkZ) {
        return null;
    }

    @Override
    public void addMinimapBlockHighlightTooltips(List<Component> list, ResourceKey<Level> dimension, int blockX, int blockZ, int width) {
        Component tooltip = tooltip(dimension, blockX >> 4, blockZ >> 4);
        if (tooltip != null) list.add(tooltip);
    }

    private static Component tooltip(ResourceKey<Level> dimension, int chunkX, int chunkZ) {
        if (!renderEnabled || !isCurrentDimension(dimension)) return null;
        OreVeinType type = OreVeinClientCache.getInstance().getVein(dimension, chunkX, chunkZ);
        if (type == null) return null;
        return Component.literal(type.displayName() + " 矿脉").withStyle(ChatFormatting.YELLOW);
    }

    private static boolean sameVein(OreVeinClientCache cache, ResourceKey<Level> dimension,
                                     int ax, int az, int bx, int bz) {
        OreVeinType a = cache.getVein(dimension, ax, az);
        OreVeinType b = cache.getVein(dimension, bx, bz);
        return a != null && a == b;
    }

    private static int xaeroColor(int argb, int alpha) {
        int red = (argb >> 16) & 0xFF;
        int green = (argb >> 8) & 0xFF;
        int blue = argb & 0xFF;
        return blue << 24 | green << 16 | red << 8 | alpha;
    }

    private static boolean isCurrentDimension(ResourceKey<Level> dimension) {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.level != null && minecraft.level.dimension().equals(dimension);
    }
}
