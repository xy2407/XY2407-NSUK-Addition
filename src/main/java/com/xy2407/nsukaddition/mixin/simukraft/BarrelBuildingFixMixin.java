package com.xy2407.nsukaddition.mixin.simukraft;

import com.github.ysbbbbbb.kaleidoscopetavern.block.brew.BarrelBlock;
import common.cn.kafei.simukraft.building.BuilderConstructionService;
import common.cn.kafei.simukraft.building.PlacedBuildingRecord;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

/**
 * SimuKraft 建筑系统通过 level.setBlock() 逐个放置方块，不经过 BlockItem.useOn -> setPlacedBy，
 * 导致 BarrelBlock 的 3×3×3 多方块结构缺少周围 26 个组成方块。
 * 此 Mixin 在建筑完成后扫描其中的 BarrelBlock 原点，调用 setPlacedBy 补齐完整结构。
 */
@Mixin(BuilderConstructionService.class)
public class BarrelBuildingFixMixin {

    @ModifyArgs(
            method = "completeTask",
            at = @At(value = "INVOKE", target = "Lcommon/cn/kafei/simukraft/building/PlacedBuildingService;register(Lnet/minecraft/server/level/ServerLevel;Lcommon/cn/kafei/simukraft/building/PlacedBuildingRecord;)V"),
            remap = false
    )
    private static void xy2407$fixBarrelMultiBlock(Args args) {
        ServerLevel level = args.get(0);
        PlacedBuildingRecord placedBuilding = args.get(1);
        if (level == null || placedBuilding == null) return;
        fixBarrelsInBuilding(level, placedBuilding);
    }

    private static void fixBarrelsInBuilding(ServerLevel level, PlacedBuildingRecord placedBuilding) {
        var blocks = placedBuilding.blocks();
        if (blocks.isEmpty()) return;

        for (var block : blocks) {
            BlockState state = block.state();
            if (!(state.getBlock() instanceof BarrelBlock)) continue;
            if (!state.hasProperty(BarrelBlock.LAYER) || state.getValue(BarrelBlock.LAYER) != AttachFace.FLOOR) continue;
            if (!state.hasProperty(BarrelBlock.INDEX) || state.getValue(BarrelBlock.INDEX) != 4) continue;

            BlockPos origin = block.relativePos().immutable();
            state.getBlock().setPlacedBy(level, origin, state, null, net.minecraft.world.item.ItemStack.EMPTY);
        }
    }
}
