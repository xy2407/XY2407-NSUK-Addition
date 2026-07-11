package com.xy2407.nsukaddition.mixin;

import com.xy2407.nsukaddition.common.city.CityManagerIndexAccessor;
import com.xy2407.nsukaddition.server.city.TownImmigrationService;
import com.xy2407.nsukaddition.server.city.VillageTourismService;
import common.cn.kafei.simukraft.city.CityChunkManager;
import common.cn.kafei.simukraft.city.CityManager;
import common.cn.kafei.simukraft.city.poi.CityPoiManager;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.UUID;
import java.util.concurrent.ConcurrentMap;

/** 修改 CityManager，暴露 coreKey 方法和 corePosIndex 供迁移服务更新索引，并在城市删除时清理关联服务缓存。 */
@Mixin(value = CityManager.class, remap = false)
public class CityManagerMixin implements CityManagerIndexAccessor.Accessor {

    @Shadow
    private ConcurrentMap<String, UUID> corePosIndex;

    @Shadow
    private volatile ServerLevel level;

    @Override
    public String nsuk$coreKey(String dimensionId, net.minecraft.core.BlockPos pos) {
        return dimensionId + "@" + pos.asLong();
    }

    @Override
    public void nsuk$updateCorePosIndex(String oldKey, String newKey, UUID cityId) {
        corePosIndex.remove(oldKey);
        corePosIndex.put(newKey, cityId);
    }

    // 主城市删除时清理旅游和移民服务的缓存数据，防止内存泄漏。
    @Inject(method = "deleteCity(Ljava/util/UUID;Ljava/util/UUID;Lcommon/cn/kafei/simukraft/city/CityChunkManager;Lcommon/cn/kafei/simukraft/city/poi/CityPoiManager;)Z",
            at = @At("TAIL"))
    private void onDeleteCity(UUID cityId, UUID operatorId, CityChunkManager chunkManager,
                              CityPoiManager poiManager, CallbackInfoReturnable<Boolean> cir) {
        if (level == null) return;
        VillageTourismService.onCityDeleted(cityId);
        TownImmigrationService.onCityDeleted(level, cityId);
    }
}
