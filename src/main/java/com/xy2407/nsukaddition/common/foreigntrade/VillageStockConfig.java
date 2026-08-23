package com.xy2407.nsukaddition.common.foreigntrade;

/** 村庄库存补货比例配置。每日围绕库存上限50%收敛时使用该比例作为步长。 */
public final class VillageStockConfig {

    public static final double RESTOCK_RATIO = 0.20D;

    private VillageStockConfig() {
    }

    public static boolean isMaterialCategory(String category) {
        return ForeignTradeCategoryConfig.getBasePrice(category) != null;
    }
}