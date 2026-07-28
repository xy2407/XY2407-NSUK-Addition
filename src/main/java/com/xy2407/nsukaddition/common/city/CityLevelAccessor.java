package com.xy2407.nsukaddition.common.city;

import common.cn.kafei.simukraft.city.CityData;

/** 通过 Mixin 访问器接口修改城市等级，解耦对原始城市数据字段的直接访问。 */
public final class CityLevelAccessor {

    private CityLevelAccessor() {}

    @SuppressWarnings("unchecked")
    public static void setCityLevel(CityData city, int newLevel) {
        if (city == null) return;

        ((Accessor) (Object) city).nsuk$setCityLevel(newLevel);
    }

    public interface Accessor {
        void nsuk$setCityLevel(int newLevel);
    }
}
