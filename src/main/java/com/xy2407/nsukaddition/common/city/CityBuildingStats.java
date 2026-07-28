package com.xy2407.nsukaddition.common.city;

import com.xy2407.nsukaddition.common.mining.MiningBoxData;
import com.xy2407.nsukaddition.common.mining.MiningBoxManager;
import com.xy2407.nsukaddition.common.mining.MiningControlBoxBlock;
import common.cn.kafei.simukraft.building.PlacedBuildingRecord;
import common.cn.kafei.simukraft.building.PlacedBuildingService;
import common.cn.kafei.simukraft.city.CityChunkManager;
import common.cn.kafei.simukraft.city.poi.CityPoiManager;
import common.cn.kafei.simukraft.city.poi.CityPoiType;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;

import java.util.UUID;

/** 城市建筑数量统计，按类别统计农场、牧场、商业、工厂、矿场数量。 */
public record CityBuildingStats(

        int farmCount,

        int ranchCount,

        int shopCount,

        int factoryCount,

        int mineCount
) {

    public static CityBuildingStats collect(ServerLevel level, UUID cityId) {
        if (level == null || cityId == null) {
            return new CityBuildingStats(0, 0, 0, 0, 0);
        }

        int shop = 0, factory = 0, ranch = 0;
        for (PlacedBuildingRecord rec : PlacedBuildingService.getBuildings(level)) {
            if (cityId.equals(rec.cityId())) {
                switch (rec.category()) {
                    case "commercial" -> shop++;
                    case "industry" -> factory++;
                    case "breeding" -> ranch++;
                }
            }
        }

        int farm = CityPoiManager.get(level).getCityPois(cityId, CityPoiType.FARMLAND).size();

        int mine = 0;
        CityChunkManager chunkManager = CityChunkManager.get(level);
        for (MiningBoxData box : MiningBoxManager.get(level).all()) {
            if (level.getBlockState(box.boxPos()).getBlock() instanceof MiningControlBoxBlock
                    && cityId.equals(chunkManager.getChunkOwner(new ChunkPos(box.boxPos()).toLong()))) {
                mine++;
            }
        }

        return new CityBuildingStats(farm, ranch, shop, factory, mine);
    }
}
