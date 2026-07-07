package com.xy2407.nsukaddition.mixin;

import com.xy2407.nsukaddition.common.city.CityLevelAccessor;
import common.cn.kafei.simukraft.city.CityData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/** 修改 CityData，注入 setCityLevel 方法使城市等级可被外部修改。 */
@Mixin(value = CityData.class, remap = false)
public class CityDataMixin implements CityLevelAccessor.Accessor {

    @Shadow
    private int cityLevel;

    @Override
    public void nsuk$setCityLevel(int newLevel) {
        this.cityLevel = Math.max(0, newLevel);
    }
}
