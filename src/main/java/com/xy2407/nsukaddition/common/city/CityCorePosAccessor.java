package com.xy2407.nsukaddition.common.city;

import common.cn.kafei.simukraft.city.CityData;
import net.minecraft.core.BlockPos;

/** 通过 Mixin 访问器接口修改城市核心坐标，不在 mixin 包中以避免 IllegalClassLoadError。 */
public final class CityCorePosAccessor {

    private CityCorePosAccessor() {}

    public static void setCityCorePos(CityData city, BlockPos pos) {
        if (city == null) return;
        ((Accessor) (Object) city).nsuk$setCityCorePos(pos);
    }

    public interface Accessor {
        void nsuk$setCityCorePos(BlockPos pos);
    }
}
