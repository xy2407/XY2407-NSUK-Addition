package com.xy2407.nsukaddition.common.vein;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;

/** 矿脉掉落物的后处理函数接口，可对原始掉落物进行转换。 */
@FunctionalInterface
public interface OreVeinDropProcessor {

    ItemStack process(ServerLevel level, BlockPos pos, ItemStack drop);
}
