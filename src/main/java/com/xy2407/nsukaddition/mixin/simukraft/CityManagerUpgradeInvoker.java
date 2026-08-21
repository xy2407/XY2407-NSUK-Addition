package com.xy2407.nsukaddition.mixin.simukraft;

import common.cn.kafei.simukraft.city.CityData;
import common.cn.kafei.simukraft.city.CityManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** 访问 CityManager 包私有 persistUpgrade：持久化升级后的等级与资金流水。 */
@Mixin(CityManager.class)
public interface CityManagerUpgradeInvoker {

    @Invoker("persistUpgrade")
    boolean nsuk$persistUpgrade(CityData city);
}
