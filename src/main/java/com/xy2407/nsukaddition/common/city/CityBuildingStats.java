package com.xy2407.nsukaddition.common.city;

import com.xy2407.nsukaddition.common.block.RestaurantControlBoxBlock;
import com.xy2407.nsukaddition.common.restaurant.RestaurantBoxData;
import com.xy2407.nsukaddition.common.restaurant.RestaurantBoxManager;
import common.cn.kafei.simukraft.building.PlacedBuildingRecord;
import common.cn.kafei.simukraft.building.PlacedBuildingService;
import common.cn.kafei.simukraft.block.MineralDrillingControlBoxBlock;
import common.cn.kafei.simukraft.city.CityChunkManager;
import common.cn.kafei.simukraft.city.poi.CityPoiManager;
import common.cn.kafei.simukraft.city.poi.CityPoiType;
import common.cn.kafei.simukraft.mineraldrilling.MineralDrillingBoxData;
import common.cn.kafei.simukraft.mineraldrilling.MineralDrillingBoxManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;

import java.util.UUID;

/** 城市建筑数量统计，按类别统计住宅、农场、牧场、餐厅、工厂、矿场数量。 */
public record CityBuildingStats(

        int farmCount,

        int ranchCount,

        int restaurantCount,

        int factoryCount,

        int mineCount,

        int housingCount
) {

    public static CityBuildingStats collect(ServerLevel level, UUID cityId) {
        if (level == null || cityId == null) {
            return new CityBuildingStats(0, 0, 0, 0, 0, 0);
        }

        int factory = 0, ranch = 0, housing = 0;
        for (PlacedBuildingRecord rec : PlacedBuildingService.getBuildings(level)) {
            if (cityId.equals(rec.cityId())) {
                switch (rec.category()) {
                    case "residential" -> housing++;
                    case "industry" -> factory++;
                    case "breeding" -> ranch++;
                }
            }
        }

        int farm = CityPoiManager.get(level).getCityPois(cityId, CityPoiType.FARMLAND).size();

        int mine = 0;
        CityChunkManager chunkManager = CityChunkManager.get(level);
        for (MineralDrillingBoxData box : MineralDrillingBoxManager.get(level).all()) {
            // 未加载区块不强制加载，避免 getBlockState 触发 chunk load 阻塞主线程。
            if (!level.isLoaded(box.boxPos())) {
                continue;
            }
            if (level.getBlockState(box.boxPos()).getBlock() instanceof MineralDrillingControlBoxBlock
                    && cityId.equals(chunkManager.getChunkOwner(new ChunkPos(box.boxPos()).toLong()))) {
                mine++;
            }
        }

        int restaurant = 0;
        for (RestaurantBoxData box : RestaurantBoxManager.get(level).all()) {
            // 同上：未加载区块跳过，避免强取区块。
            if (!level.isLoaded(box.boxPos())) {
                continue;
            }
            if (level.getBlockState(box.boxPos()).getBlock() instanceof RestaurantControlBoxBlock
                    && cityId.equals(chunkManager.getChunkOwner(new ChunkPos(box.boxPos()).toLong()))) {
                restaurant++;
            }
        }

        return new CityBuildingStats(farm, ranch, restaurant, factory, mine, housing);
    }
}
