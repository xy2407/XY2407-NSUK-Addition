package com.xy2407.nsukaddition.mixin;

import com.xy2407.nsukaddition.common.city.CityCorePosAccessor;
import com.xy2407.nsukaddition.common.city.CityLevelAccessor;
import common.cn.kafei.simukraft.city.CityData;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/** 修改 CityData，注入 setCityLevel 和 setCityCorePos 方法。 */
@Mixin(value = CityData.class, remap = false)
public class CityDataMixin implements CityLevelAccessor.Accessor, CityCorePosAccessor.Accessor {

    @Shadow
    private int cityLevel;

    @Shadow
    private BlockPos cityCorePos;

    @Override
    public void nsuk$setCityLevel(int newLevel) {
        this.cityLevel = Math.max(0, newLevel);
    }

    @Override
    public void nsuk$setCityCorePos(BlockPos pos) {
        this.cityCorePos = pos != null ? pos.immutable() : BlockPos.ZERO;
    }
}
