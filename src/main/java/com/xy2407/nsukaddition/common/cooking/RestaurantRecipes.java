package com.xy2407.nsukaddition.common.cooking;

/** 餐厅菜品设备类型枚举，菜品定义由 restaurant.json 的 cook 字段提供。 */
public final class RestaurantRecipes {
    public enum DeviceType { POT, STOCKPOT, STEAMER, BAKERY_OVEN, TAVERN_SHAKER,
        KAWAII_BLENDER, KAWAII_COFFEE_MACHINE, KAWAII_ICE_CREAM_MAKER, BREWERY_BREWSTATION,
        DIRECT }

    private RestaurantRecipes() {}
}
