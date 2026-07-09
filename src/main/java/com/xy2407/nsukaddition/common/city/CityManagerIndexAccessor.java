package com.xy2407.nsukaddition.common.city;

import common.cn.kafei.simukraft.city.CityManager;
import net.minecraft.core.BlockPos;

import java.util.UUID;

/** 通过 Mixin 访问器接口修改 CityManager 的 corePosIndex，不在 mixin 包中以避免 IllegalClassLoadError。 */
public final class CityManagerIndexAccessor {

    private CityManagerIndexAccessor() {}

    public static String coreKey(CityManager mgr, String dimensionId, BlockPos pos) {
        return ((Accessor) (Object) mgr).nsuk$coreKey(dimensionId, pos);
    }

    public static void updateCorePosIndex(CityManager mgr, String oldKey, String newKey, UUID cityId) {
        ((Accessor) (Object) mgr).nsuk$updateCorePosIndex(oldKey, newKey, cityId);
    }

    public interface Accessor {
        String nsuk$coreKey(String dimensionId, BlockPos pos);
        void nsuk$updateCorePosIndex(String oldKey, String newKey, UUID cityId);
    }
}
