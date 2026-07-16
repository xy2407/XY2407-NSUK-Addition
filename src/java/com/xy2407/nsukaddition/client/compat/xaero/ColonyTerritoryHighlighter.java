package com.xy2407.nsukaddition.client.compat.xaero;

import client.cn.kafei.simukraft.client.city.ClientCityChunkCache;
import com.xy2407.nsukaddition.client.colony.ColonyChunkClientCache;
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
import java.util.UUID;

/** Xaero's World Map 附属地领地高亮渲染器，蓝色=自己附属地，红色=他人附属地。 */
@OnlyIn(Dist.CLIENT)
public final class ColonyTerritoryHighlighter extends ChunkHighlighter {

    private static final int SELF_COLOR = 0xFF4488FF;
    private static final int ENEMY_COLOR = 0xFFFF2222;
    private static final int FILL_ALPHA = 0x50;
    private static final int BORDER_ALPHA = 0xDD;

    private final int selfFill = xaeroColor(SELF_COLOR, FILL_ALPHA);
    private final int selfBorder = xaeroColor(SELF_COLOR, BORDER_ALPHA);
    private final int enemyFill = xaeroColor(ENEMY_COLOR, FILL_ALPHA);
    private final int enemyBorder = xaeroColor(ENEMY_COLOR, BORDER_ALPHA);

    public ColonyTerritoryHighlighter() {
        super(true);
    }

    @Override
    public boolean regionHasHighlights(ResourceKey<Level> dimension, int regionX, int regionZ) {
        return isCurrentDimension(dimension);
    }

    @Override
    protected int[] getColors(ResourceKey<Level> dimension, int chunkX, int chunkZ) {
        if (!isCurrentDimension(dimension)) return null;
        long chunkLong = ChunkPos.asLong(chunkX, chunkZ);
        UUID owner = ColonyChunkClientCache.getInstance().getColonyOwner(chunkLong);
        if (owner == null) return null;

        ColonyChunkClientCache cache = ColonyChunkClientCache.getInstance();
        boolean isSelf = isOwnColony(cache, owner);
        int fill = isSelf ? selfFill : enemyFill;
        int border = isSelf ? selfBorder : enemyBorder;

        this.resultStore[0] = fill;
        this.resultStore[1] = sameColony(cache, chunkLong, chunkX, chunkZ - 1) ? fill : border;
        this.resultStore[2] = sameColony(cache, chunkLong, chunkX + 1, chunkZ) ? fill : border;
        this.resultStore[3] = sameColony(cache, chunkLong, chunkX, chunkZ + 1) ? fill : border;
        this.resultStore[4] = sameColony(cache, chunkLong, chunkX - 1, chunkZ) ? fill : border;
        return this.resultStore;
    }

    private static boolean isOwnColony(ColonyChunkClientCache cache, UUID colonyId) {
        ColonyChunkClientCache.ColonyEntry entry = cache.getColonyEntry(colonyId);
        if (entry == null || entry.parentCityId() == null) return false;
        ClientCityChunkCache cityCache = ClientCityChunkCache.getInstance();
        return cityCache.getCurrentCityId() != null
                && cityCache.getCurrentCityId().equals(entry.parentCityId());
    }

    @Override
    public int calculateRegionHash(ResourceKey<Level> dimension, int regionX, int regionZ) {
        return isCurrentDimension(dimension) ? (int) (System.currentTimeMillis() / 5000L) : 0;
    }

    @Override
    public boolean chunkIsHighlit(ResourceKey<Level> dimension, int chunkX, int chunkZ) {
        if (!isCurrentDimension(dimension)) return false;
        long chunkLong = ChunkPos.asLong(chunkX, chunkZ);
        return ColonyChunkClientCache.getInstance().getColonyOwner(chunkLong) != null;
    }

    @Override
    public Component getChunkHighlightSubtleTooltip(ResourceKey<Level> dimension, int chunkX, int chunkZ) {
        return tooltip(chunkX, chunkZ);
    }

    @Override
    public Component getChunkHighlightBluntTooltip(ResourceKey<Level> dimension, int chunkX, int chunkZ) {
        return null;
    }

    @Override
    public void addMinimapBlockHighlightTooltips(List<Component> list, ResourceKey<Level> dimension, int blockX, int blockZ, int width) {
        Component tooltip = tooltip(blockX >> 4, blockZ >> 4);
        if (tooltip != null) list.add(tooltip);
    }

    private static Component tooltip(int chunkX, int chunkZ) {
        long chunkLong = ChunkPos.asLong(chunkX, chunkZ);
        UUID owner = ColonyChunkClientCache.getInstance().getColonyOwner(chunkLong);
        if (owner == null) return null;
        ColonyChunkClientCache.ColonyEntry entry = ColonyChunkClientCache.getInstance().getColonyEntry(owner);
        if (entry == null) return null;
        boolean isSelf = isOwnColony(ColonyChunkClientCache.getInstance(), owner);
        return Component.literal("附属地：" + entry.colonyName())
                .withStyle(isSelf ? ChatFormatting.BLUE : ChatFormatting.RED);
    }

    private static boolean sameColony(ColonyChunkClientCache cache, long chunkLong, int otherX, int otherZ) {
        UUID owner = cache.getColonyOwner(chunkLong);
        UUID other = cache.getColonyOwner(ChunkPos.asLong(otherX, otherZ));
        return owner != null && owner.equals(other);
    }

    private static boolean isCurrentDimension(ResourceKey<Level> dimension) {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.level != null && minecraft.level.dimension().equals(dimension);
    }

    private static int xaeroColor(int argb, int alpha) {
        int red = (argb >> 16) & 0xFF;
        int green = (argb >> 8) & 0xFF;
        int blue = argb & 0xFF;
        return blue << 24 | green << 16 | red << 8 | alpha;
    }
}
