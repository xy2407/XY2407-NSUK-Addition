package com.xy2407.nsukaddition.mixin.simukraft;

import common.cn.kafei.simukraft.city.CityData;
import common.cn.kafei.simukraft.city.CityUpgradeState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** 访问 CityData 包私有升级方法：beginUpgrade（开始异步升级）与 restoreUpgradeState（持久化失败回滚）。 */
@Mixin(CityData.class)
public interface CityDataUpgradeInvoker {

    @Invoker("beginUpgrade")
    void nsuk$beginUpgrade(int targetLevel, long startedAt, int durationTicks);

    @Invoker("restoreUpgradeState")
    void nsuk$restoreUpgradeState(CityUpgradeState state);
}
