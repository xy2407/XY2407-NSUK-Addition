package com.xy2407.nsukaddition.mixin;

import com.xy2407.nsukaddition.NsukAddition;
import com.xy2407.nsukaddition.common.breeding.BreedingConstants;
import com.xy2407.nsukaddition.common.mining.MiningConstants;
import com.xy2407.nsukaddition.common.registry.ModBlocks;
import common.cn.kafei.simukraft.building.PlacedBuildingRecord;
import common.cn.kafei.simukraft.building.PlacedBuildingService;
import common.cn.kafei.simukraft.city.CityChunkManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.UUID;

/** 修改 NpcHireAccessValidator，让养殖/采矿控制箱成为合法的 NPC 雇佣来源。 */
@Mixin(targets = "common.cn.kafei.simukraft.network.npc.hire.NpcHireAccessValidator", remap = false)
public class NpcHireAccessValidatorMixin {

    @Inject(
            method = "resolveCityId(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;Ljava/lang/String;Ljava/lang/String;)Ljava/util/UUID;",
            at = @At("HEAD"),
            cancellable = true,
            remap = false
    )
    private static void nsuk$resolveCityId(ServerLevel level, BlockPos sourcePos, String sourceType, String role,
                                           CallbackInfoReturnable<UUID> cir) {

        if (BreedingConstants.HIRE_SOURCE_TYPE.equals(sourceType) && BreedingConstants.HIRE_ROLE.equals(role)) {
            if (level.getBlockState(sourcePos).is(ModBlocks.BREEDING_CONTROL_BOX.get())) {
                PlacedBuildingRecord building = PlacedBuildingService.findByContainedPosAndCategory(
                        level, sourcePos, BreedingConstants.BUILDING_CATEGORY, "industry", "industrial");
                UUID cityId = building != null ? building.cityId() : null;
                NsukAddition.LOGGER.info("[NsukAddition] breeding control box at {} -> cityId={}", sourcePos, cityId);
                cir.setReturnValue(cityId);
            }
            return;
        }

        if (MiningConstants.HIRE_SOURCE_TYPE.equals(sourceType) && MiningConstants.HIRE_ROLE.equals(role)) {
            if (level.getBlockState(sourcePos).is(ModBlocks.MINING_CONTROL_BOX.get())) {
                UUID cityId = CityChunkManager.get(level).getChunkOwner(new ChunkPos(sourcePos).toLong());
                NsukAddition.LOGGER.info("[NsukAddition] mining control box at {} chunk {} -> cityId={}",
                        sourcePos, new ChunkPos(sourcePos), cityId);
                cir.setReturnValue(cityId);
            }
        }
    }
}
