package com.xy2407.nsukaddition.common.vein;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

/** 矿脉类型枚举，定义各矿石的维度、Y 轴范围与生成权重。 */
public enum OreVeinType {

    COAL("coal", "煤矿", Items.COAL, Level.OVERWORLD, 0, 128, 30),
    COPPER("copper", "铜矿", Items.COPPER_INGOT, Level.OVERWORLD, -16, 112, 25),
    IRON("iron", "铁矿", Items.IRON_INGOT, Level.OVERWORLD, -64, 64, 18),
    GOLD("gold", "金矿", Items.GOLD_INGOT, Level.OVERWORLD, -64, 32, 10),
    REDSTONE("redstone", "红石矿", Items.REDSTONE, Level.OVERWORLD, -64, 0, 7),
    LAPIS("lapis_lazuli", "青金石矿", Items.LAPIS_LAZULI, Level.OVERWORLD, -64, 32, 5),
    DIAMOND("diamond", "钻石矿", Items.DIAMOND, Level.OVERWORLD, -64, 16, 3),
    EMERALD("emerald", "绿宝石矿", Items.EMERALD, Level.OVERWORLD, -16, 256, 2),

    QUARTZ("quartz", "石英矿", Items.QUARTZ, Level.NETHER, 10, 120, 300),
    NETHER_GOLD("nether_gold", "地狱金矿", Items.GOLD_INGOT, Level.NETHER, 10, 120, 90),
    ANCIENT_DEBRIS("ancient_debris", "远古残骸", Items.NETHERITE_SCRAP, Level.NETHER, 10, 120, 1);

    private final String id;
    private final String displayName;
    private final Item displayItem;
    private final ResourceKey<Level> dimension;
    private final int minY;
    private final int maxY;
    private final int weight;

    OreVeinType(String id, String displayName, Item displayItem, ResourceKey<Level> dimension, int minY, int maxY, int weight) {
        this.id = id;
        this.displayName = displayName;
        this.displayItem = displayItem;
        this.dimension = dimension;
        this.minY = minY;
        this.maxY = maxY;
        this.weight = weight;
    }

    public String id() { return id; }
    public String displayName() { return displayName; }
    public Item displayItem() { return displayItem; }
    public ResourceKey<Level> dimension() { return dimension; }
    public int minY() { return minY; }
    public int maxY() { return maxY; }
    public int weight() { return weight; }

    public static OreVeinType[] forDimension(ResourceKey<Level> dimension) {
        if (dimension == Level.NETHER) {
            return new OreVeinType[]{QUARTZ, NETHER_GOLD, ANCIENT_DEBRIS};
        }
        return new OreVeinType[]{COAL, COPPER, IRON, GOLD, REDSTONE, LAPIS, DIAMOND, EMERALD};
    }

    public static int totalWeightFor(ResourceKey<Level> dimension) {
        int sum = 0;
        for (OreVeinType type : forDimension(dimension)) {
            sum += type.weight;
        }
        return sum;
    }

    public static OreVeinType selectByWeight(ResourceKey<Level> dimension, int roll) {
        int cumulative = 0;
        for (OreVeinType type : forDimension(dimension)) {
            cumulative += type.weight;
            if (roll < cumulative) return type;
        }
        return forDimension(dimension)[0];
    }
}
