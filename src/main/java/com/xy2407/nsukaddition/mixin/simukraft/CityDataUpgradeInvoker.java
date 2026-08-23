package com.xy2407.nsukaddition.mixin.simukraft;

import common.cn.kafei.simukraft.city.CityData;
import common.cn.kafei.simukraft.city.CityUpgradeState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** 访问 CityData 包私有方法：直接写入等级与异步升级。 */
@Mixin(CityData.class)
public interface CityDataUpgradeInvoker {

    @Invoker("setCityLevel")
    void nsuk$setCityLevel(int cityLevel);

    @Invoker("beginUpgrade")
    void nsuk$beginUpgrade(int targetLevel, long startedAt, int durationTicks);

    @Invoker("restoreUpgradeState")
    void nsuk$restoreUpgradeState(CityUpgradeState state);
}
