package com.xy2407.nsukaddition.mixin.mysticsbiomes;

import com.mysticsbiomes.common.entity.Butterfly;
import com.xy2407.nsukaddition.common.ai.SpeciesAiScheduler;
import java.util.Optional;
import java.util.function.BiPredicate;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** 优化蝴蝶授粉寻花：80tick 冷却 + 物种 AI 队列节流，避免大量蝴蝶同帧扫描。 */
@Mixin(targets = "com.mysticsbiomes.common.entity.Butterfly$PollinateGoal")
public abstract class ButterflyPollinateGoalMixin {

    @Unique
    private long xy2407$nextAllowedTick = -1L;
    @Unique
    private Optional<BlockPos> xy2407$cachedPos = Optional.empty();

    @Redirect(method = "findNearbyFlower",
            at = @At(value = "INVOKE",
                    target = "Lcom/mysticsbiomes/common/entity/Butterfly;findNearestBlock(Ljava/util/function/BiPredicate;D)Ljava/util/Optional;"),
            remap = false)
    private Optional<BlockPos> xy2407$queuedFindNearestBlock(Butterfly butterfly,
            BiPredicate<BlockPos, BlockState> predicate, double distance) {
        Level level = butterfly.level();
        long tick = level.getGameTime();
        if (tick < this.xy2407$nextAllowedTick) {
            return this.xy2407$cachedPos;
        }
        if (!SpeciesAiScheduler.tryGrant(butterfly.getType(), tick)) {
            return this.xy2407$cachedPos;
        }
        this.xy2407$nextAllowedTick = tick + 80;
        double capped = Math.min(distance, 8.0);
        BlockPos pos = butterfly.blockPosition();
        BlockPos.MutableBlockPos mutablePos = new BlockPos.MutableBlockPos();
        Optional<BlockPos> result = Optional.empty();
        outer:
        for (int i = 0; i < (int) Math.ceil(capped); ++i) {
            for (int yOffset = -2; yOffset <= 2; ++yOffset) {
                for (int xOffset = -i; xOffset <= i; ++xOffset) {
                    for (int zOffset = -i; zOffset <= i; ++zOffset) {
                        if (Math.max(Math.abs(xOffset), Math.abs(zOffset)) != i) {
                            continue;
                        }
                        mutablePos.setWithOffset(pos, xOffset, yOffset, zOffset);
                        if (mutablePos.closerThan(pos, capped)
                                && predicate.test(mutablePos, level.getBlockState(mutablePos))) {
                            result = Optional.of(mutablePos.immutable());
                            break outer;
                        }
                    }
                }
            }
        }
        this.xy2407$cachedPos = result;
        return result;
    }
}