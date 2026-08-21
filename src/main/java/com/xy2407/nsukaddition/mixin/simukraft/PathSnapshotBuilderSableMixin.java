package com.xy2407.nsukaddition.mixin.simukraft;

import com.xy2407.nsukaddition.common.rts.path.SableStructureReader;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * SimuKraft PathSnapshotBuilder 快照桥接：
 * captureBlock() 构建寻路快照时，主世界方块为空气的格子改查 Sable 物理结构（反射桥），
 * 把结构方块以世界坐标合并进快照，让 SimuKraft 的 A* 能"看见"结构并规划路径。
 * 结构内部方块在快照中以世界坐标呈现，路径点即世界坐标，NPC 原版导航可直接使用。
 * 未安装 Sable 时 SableStructureReader 全部降级，本 Mixin 无副作用。
 * 使用 @Redirect（而非 @Inject）因为 captureBlock 有返回值，@Redirect 无需回调参数。
 * 2.2.0 兼容：官方 2.2.0 将 capture 拆为两阶段异步（capture→buildFromCapture 返回 ChunkDataCapture），
 * getBlockState 的调用从 capture 移入私有方法 captureBlock（capture 与 captureSection 均调用它），
 * 因此注入点从 method="capture" 调整为 method="captureBlock"，两阶段捕获都能合并 Sable 结构。
 * 性能护栏：
 * 1. 未加载区块直接返回空气——capture 原本会经 getChunk 强制加载区块（DistanceManager.addTicket），
 *    未加载区域的方块对寻路无意义（NPC 不会走进未加载区），该检查把 getChunk/addTicket 开销砍掉。
 * 2. 无 Sable 结构时整体跳过反射查询——20 tick 一次的存在性探测，无结构场景 getBlockStateAt 不再逐格调用。
 */
@Mixin(targets = "common.cn.kafei.simukraft.path.PathSnapshotBuilder")
public abstract class PathSnapshotBuilderSableMixin {

    @Redirect(method = "captureBlock", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerLevel;getBlockState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/state/BlockState;"),
            require = 1)
    private static BlockState nsukaddition$mergeSubLevelState(ServerLevel level, BlockPos pos) {
        if (!level.isLoaded(pos)) {
            return Blocks.AIR.defaultBlockState();
        }
        BlockState state = level.getBlockState(pos);
        if (!state.isAir()) {
            return state;
        }
        if (SableStructureReader.mayContainStructure(level)) {
            BlockState sub = SableStructureReader.getBlockStateAt(level, pos);
            if (sub != null && !sub.isAir()) {
                return sub;
            }
        }
        return state;
    }
}