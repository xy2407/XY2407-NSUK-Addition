package com.xy2407.nsukaddition.common.foreigntrade;

/**
 * 村庄库存上限配置。
 * 基准分类(材料类，有 category_base 基准价)统一 2400；其余分类按下表：
 * crop 作物 2000、animal 动物 64、wine 酒水 128、cheese 奶酪 128、
 * mineral 矿产 1000(钻石 256、下界合金 128)、aquatic 水产待定(0 = 暂不启用)。
 */
public final class VillageStockConfig {

    public static final int MATERIAL_LIMIT = 2400;
    public static final int CROP_LIMIT = 2000;
    public static final int ANIMAL_LIMIT = 64;
    public static final int WINE_LIMIT = 128;
    public static final int CHEESE_LIMIT = 128;
    public static final int MINERAL_LIMIT = 1000;
    public static final int MINERAL_DIAMOND_LIMIT = 256;
    public static final int MINERAL_NETHERITE_LIMIT = 128;
    public static final int AQUATIC_LIMIT = 0;

    public static final double RESTOCK_RATIO = 0.20D;
    public static final double STOCK_CAP_RATIO = 0.60D;
    public static final int MATERIAL_INIT_MIN = 1000;
    public static final int MATERIAL_INIT_MAX = 1500;

    private VillageStockConfig() {
    }

    public static boolean isMaterialCategory(String category) {
        return ForeignTradeCategoryConfig.getBasePrice(category) != null;
    }

    public static int getCategoryLimit(String category, String itemId) {
        if (category == null) {
            return MATERIAL_LIMIT;
        }
        String cat = category.toLowerCase(java.util.Locale.ROOT);
        if (isMaterialCategory(category)) {
            return MATERIAL_LIMIT;
        }
        return switch (cat) {
            case "crop" -> CROP_LIMIT;
            case "animal" -> ANIMAL_LIMIT;
            case "wine" -> WINE_LIMIT;
            case "cheese" -> CHEESE_LIMIT;
            case "mineral" -> {
                if (itemId != null) {
                    String id = itemId.toLowerCase(java.util.Locale.ROOT);
                    if (id.contains("diamond")) {
                        yield MINERAL_DIAMOND_LIMIT;
                    }
                    if (id.contains("netherite")) {
                        yield MINERAL_NETHERITE_LIMIT;
                    }
                }
                yield MINERAL_LIMIT;
            }
            case "aquatic" -> AQUATIC_LIMIT;
            default -> MATERIAL_LIMIT;
        };
    }
}