package com.xy2407.nsukaddition.mixin.mysticsbiomes;

import com.mysticsbiomes.common.entity.SeaOtter;
import com.mysticsbiomes.common.entity.core.goal.SwimToSurfaceGoal;
import com.xy2407.nsukaddition.common.ai.SpeciesAiScheduler;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

/** 优化海獭上浮寻点：120tick 冷却 + 物种 AI 队列节流 + 高度图按列定位，替代 O(n³) 扫描。 */
@Mixin(SwimToSurfaceGoal.class)
public abstract class SwimToSurfaceGoalMixin {

    @Shadow
    @Final
    private SeaOtter seaOtter;

    @Unique
    private long xy2407$nextAllowedTick = -1L;
    @Unique
    private Optional<BlockPos> xy2407$cachedPos = Optional.empty();

    /**
     * @reason 原实现每 tick 执行 48×48×48 螺旋扫描找水面，开销极高；
     * 改为 120tick 冷却 + 物种 AI 队列节流（每 40tick 每种生物放行 2 只，未放行冻结等待）+ 高度图按列定位。
     */
    @Overwrite
    protected Optional<BlockPos> findNearestPos() {
        Level level = this.seaOtter.level();
        long tick = level.getGameTime();
        if (tick < this.xy2407$nextAllowedTick) {
            return this.xy2407$cachedPos;
        }
        if (!SpeciesAiScheduler.tryGrant(this.seaOtter.getType(), tick)) {
            return this.xy2407$cachedPos;
        }
        this.xy2407$nextAllowedTick = tick + 120;
        BlockPos pos = this.seaOtter.blockPosition();
        BlockPos.MutableBlockPos surfacePos = new BlockPos.MutableBlockPos();
        Optional<BlockPos> result = Optional.empty();
        outer:
        for (int distance = 0; distance < 12; ++distance) {
            for (int xOffset = -distance; xOffset <= distance; ++xOffset) {
                for (int zOffset = -distance; zOffset <= distance; ++zOffset) {
                    if (Math.max(Math.abs(xOffset), Math.abs(zOffset)) != distance) {
                        continue;
                    }
                    int x = pos.getX() + xOffset;
                    int z = pos.getZ() + zOffset;
                    BlockPos probe = new BlockPos(x, pos.getY(), z);
                    if (!level.isLoaded(probe)) {
                        continue;
                    }
                    int topY = level.getHeight(Heightmap.Types.OCEAN_FLOOR, x, z);
                    surfacePos.set(x, topY + 1, z);
                    if (this.seaOtter.isWithinRestriction(surfacePos)
                            && level.getBlockState(surfacePos.below()).is(Blocks.WATER)
                            && level.getBlockState(surfacePos).isAir()) {
                        result = Optional.of(surfacePos.immutable());
                        break outer;
                    }
                }
            }
        }
        this.xy2407$cachedPos = result;
        return result;
    }
}