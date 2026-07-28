package com.xy2407.nsukaddition.common.mining;

import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.PickaxeItem;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.item.enchantment.Enchantments;

/** 镐子挖掘计算器，根据镐子等级与效率附魔计算整层挖掘所需 tick 数；耐久消耗按耐久附魔减免。 */
public final class PickaxeMiningCalculator {

    /** 每层实际挖掘方块数（14×14，跳过16×16边缘一圈）。 */
    public static final int BLOCKS_PER_LAYER = 196;
    /** 木镐挖一个圆石的基准 tick 数（1.5 秒 × 20）。 */
    public static final int BASE_TICKS_PER_BLOCK = 30;
    /** 木镐无附魔的整层基准 tick 数（196 × 30 = 5880）。 */
    public static final int BASE_TICKS_PER_LAYER = BLOCKS_PER_LAYER * BASE_TICKS_PER_BLOCK;
    /** 整层挖掘的最小 tick 下限，防止高阶镐子瞬挖导致逻辑异常。 */
    public static final int MIN_LAYER_TICKS = 20;

    private PickaxeMiningCalculator() {}

    /** 获取镐子对应材质的基础挖掘速度（对应原版 tool speed）：木2 石4 铁6 钻石8 下界合金9 金12。 */
    private static float getToolSpeed(ItemStack stack) {
        if (stack.isEmpty() || !(stack.getItem() instanceof PickaxeItem pickaxe)) return 2.0f;
        Tier tier = pickaxe.getTier();
        if (tier == Tiers.WOOD) return 2.0f;
        if (tier == Tiers.STONE) return 4.0f;
        if (tier == Tiers.IRON) return 6.0f;
        if (tier == Tiers.DIAMOND) return 8.0f;
        if (tier == Tiers.NETHERITE) return 9.0f;
        if (tier == Tiers.GOLD) return 12.0f;
        return 2.0f;
    }

    /** 计算整层挖掘所需 tick 数；木镐无附魔为 5880，等级越高/效率附魔越快。 */
    public static int calculateLayerTicks(ItemStack stack, HolderLookup.Provider registries) {
        if (stack.isEmpty()) return BASE_TICKS_PER_LAYER;
        float toolSpeed = getToolSpeed(stack);
        int efficiency = 0;
        if (registries != null) {
            efficiency = stack.getEnchantmentLevel(registries.holderOrThrow(Enchantments.EFFICIENCY));
        }
        float totalSpeed = toolSpeed + efficiency;
        if (totalSpeed <= 0) return BASE_TICKS_PER_LAYER;
        float ticks = BASE_TICKS_PER_LAYER * (2.0f / totalSpeed);
        return Math.max(MIN_LAYER_TICKS, (int) Math.ceil(ticks));
    }

    /** 判断物品是否为有效可用的镐子。 */
    public static boolean isValidPickaxe(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() instanceof PickaxeItem
                && stack.getDamageValue() < stack.getMaxDamage();
    }
}
