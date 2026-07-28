package com.xy2407.nsukaddition.common.advancement;

import com.xy2407.nsukaddition.NsukAddition;
import net.minecraft.advancements.CriterionTrigger;
import net.minecraft.core.registries.Registries;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

/** 自定义成就触发器注册表，集中管理模组所有自定义 CriterionTrigger 实例。 */
public final class NsukTriggers {

    private NsukTriggers() {}

    public static final DeferredRegister<CriterionTrigger<?>> TRIGGERS =
            DeferredRegister.create(Registries.TRIGGER_TYPE, NsukAddition.MOD_ID);

    /** 城市等级升级触发器，当城市成功升级到指定等级时触发。 */
    public static final Supplier<CityLevelUpTrigger> CITY_LEVEL_UP =
            TRIGGERS.register("city_level_up", CityLevelUpTrigger::new);
}
