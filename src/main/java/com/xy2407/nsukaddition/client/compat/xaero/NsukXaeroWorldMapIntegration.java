package com.xy2407.nsukaddition.client.compat.xaero;

import com.xy2407.nsukaddition.NsukAddition;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import xaero.map.MapProcessor;
import xaero.map.WorldMapSession;
import xaero.map.highlight.AbstractHighlighter;
import xaero.map.region.LeveledRegion;
import xaero.map.region.MapRegion;
import xaero.map.world.MapDimension;
import xaero.map.world.MapWorld;

import java.util.ArrayList;
import java.util.List;

/** Xaero's World Map 矿脉高亮器注册与地图区域刷新。 */
@OnlyIn(Dist.CLIENT)
public final class NsukXaeroWorldMapIntegration {

    private static volatile boolean registeredOnce;

    private NsukXaeroWorldMapIntegration() {}

    public static void registerHighlighter(List<AbstractHighlighter> highlighters) {
        if (highlighters == null) return;
        if (!containsNsukVeinHighlighter(highlighters)) {
            highlighters.add(new NsukVeinHighlighter());
        }
        if (!containsColonyHighlighter(highlighters)) {
            highlighters.add(new ColonyTerritoryHighlighter());
        }
        refreshVeinHighlights();
    }

    public static void refreshVeinHighlights() {
        Minecraft minecraft = Minecraft.getInstance();
        if (!minecraft.isSameThread()) {
            minecraft.execute(NsukXaeroWorldMapIntegration::refreshVeinHighlights);
            return;
        }
        try {
            WorldMapSession session = WorldMapSession.getCurrentSession();
            if (session == null || !session.isUsable()) return;
            MapProcessor processor = session.getMapProcessor();
            if (processor == null) return;
            MapWorld mapWorld = processor.getMapWorld();
            if (mapWorld == null) return;
            refreshLoadedRegions(processor, mapWorld.getCurrentDimension());
        } catch (RuntimeException exception) {
            NsukAddition.LOGGER.warn("NsukAddition: Failed to refresh Xaero ore vein highlights.", exception);
        }
    }

    private static void refreshLoadedRegions(MapProcessor processor, MapDimension dimension) {
        if (processor == null || dimension == null) return;
        List<MapRegion> loadedMapRegions = new ArrayList<>();
        List<LeveledRegion<?>> loadedRegions = dimension.getLayeredMapRegions().getLoadedListUnsynced();
        synchronized (loadedRegions) {
            for (LeveledRegion<?> region : loadedRegions) {
                if (region instanceof MapRegion mapRegion) {
                    loadedMapRegions.add(mapRegion);
                }
            }
        }
        for (MapRegion mapRegion : loadedMapRegions) {
            mapRegion.requestRefresh(processor);
        }
    }

    private static boolean containsNsukVeinHighlighter(List<AbstractHighlighter> highlighters) {
        for (AbstractHighlighter h : highlighters) {
            if (h instanceof NsukVeinHighlighter) return true;
        }
        return false;
    }

    private static boolean containsColonyHighlighter(List<AbstractHighlighter> highlighters) {
        for (AbstractHighlighter h : highlighters) {
            if (h instanceof ColonyTerritoryHighlighter) return true;
        }
        return false;
    }
}
