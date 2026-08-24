package com.xy2407.nsukaddition.mixin.mysticsbiomes;

import com.mysticsbiomes.common.entity.RedPanda;
import com.mysticsbiomes.init.MysticBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

/** 优化红熊猫繁殖条件检查：canFindBamboo 竹子扫描加 40tick 冷却，避免发情期每 tick 嵌套扫描。 */
@Mixin(targets = "com.mysticsbiomes.common.entity.RedPanda$RedPandaBreedGoal")
public abstract class RedPandaBreedMixin {

    @Shadow
    @Final
    private RedPanda redPanda;

    @Unique
    private int xy2407$lastScanTick = -1;
    @Unique
    private boolean xy2407$cachedHasBamboo = false;

    /**
     * @reason 原实现发情期间每 tick 执行 3×8 嵌套循环扫竹子（约 400 次方块读取）；
     * 改为 40tick 冷却缓存结果。
     */
    @Overwrite
    private boolean canFindBamboo() {
        int tick = this.redPanda.tickCount;
        if (tick - this.xy2407$lastScanTick < 80) {
            return this.xy2407$cachedHasBamboo;
        }
        this.xy2407$lastScanTick = tick;
        BlockPos pos = this.redPanda.blockPosition();
        BlockPos.MutableBlockPos mutablePos = new BlockPos.MutableBlockPos();
        boolean found = false;
        outer:
        for (int i = 0; i < 3; ++i) {
            for (int j = 0; j < 8; ++j) {
                int k = 0;
                while (k <= j) {
                    int l = k < j && k > -j ? j : 0;
                    while (l <= j) {
                        mutablePos.setWithOffset(pos, k, i, l);
                        BlockState state = this.redPanda.level().getBlockState(mutablePos);
                        if (state.is(Blocks.BAMBOO) || state.is(MysticBlocks.SPRING_BAMBOO.get())) {
                            found = true;
                            break outer;
                        }
                        l = l > 0 ? -l : 1 - l;
                    }
                    k = k > 0 ? -k : 1 - k;
                }
            }
        }
        this.xy2407$cachedHasBamboo = found;
        return found;
    }
}