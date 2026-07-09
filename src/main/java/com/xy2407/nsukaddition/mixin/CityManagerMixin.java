package com.xy2407.nsukaddition.mixin;

import com.xy2407.nsukaddition.common.city.CityManagerIndexAccessor;
import common.cn.kafei.simukraft.city.CityManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import java.util.UUID;
import java.util.concurrent.ConcurrentMap;

/** 修改 CityManager，暴露 coreKey 方法和 corePosIndex 供迁移服务更新索引。 */
@Mixin(value = CityManager.class, remap = false)
public class CityManagerMixin implements CityManagerIndexAccessor.Accessor {

    @Shadow
    private ConcurrentMap<String, UUID> corePosIndex;

    @Override
    public String nsuk$coreKey(String dimensionId, net.minecraft.core.BlockPos pos) {
        return dimensionId + "@" + pos.asLong();
    }

    @Override
    public void nsuk$updateCorePosIndex(String oldKey, String newKey, UUID cityId) {
        corePosIndex.remove(oldKey);
        corePosIndex.put(newKey, cityId);
    }
}
