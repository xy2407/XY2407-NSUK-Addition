package com.xy2407.nsukaddition.mixin;

import com.xy2407.nsukaddition.common.foreigntrade.ForeignTradeConstants;
import com.xy2407.nsukaddition.common.registry.ModBlocks;
import common.cn.kafei.simukraft.city.CityChunkManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.UUID;

/** 扩展NpcHireAccessValidator以识别外贸控制箱的雇佣来源和距离提示。 */
@Mixin(targets = "common.cn.kafei.simukraft.network.npc.hire.NpcHireAccessValidator")
public class NpcHireAccessValidatorMixin {

    @Inject(method = "resolveCityId", at = @At("RETURN"), cancellable = true, remap = false)
    private static void onResolveCityId(ServerLevel level, BlockPos sourcePos, String sourceType, String role, CallbackInfoReturnable<UUID> cir) {
        if (cir.getReturnValue() != null) return;
        if (ForeignTradeConstants.HIRE_SOURCE_TYPE.equals(sourceType)
                && ForeignTradeConstants.HIRE_ROLE.equals(role)
                && level.getBlockState(sourcePos).is(ModBlocks.FOREIGN_TRADE_CONTROL_BOX.get())) {
            UUID cityId = CityChunkManager.get(level).getChunkOwner(new ChunkPos(sourcePos).toLong());
            cir.setReturnValue(cityId);
        }
    }

    @Inject(method = "tooFarMessage", at = @At("RETURN"), cancellable = true, remap = false)
    private static void onTooFarMessage(String sourceType, CallbackInfoReturnable<String> cir) {
        if (ForeignTradeConstants.HIRE_SOURCE_TYPE.equals(sourceType)) {
            cir.setReturnValue(ForeignTradeConstants.TOO_FAR_MESSAGE);
        }
    }
}
