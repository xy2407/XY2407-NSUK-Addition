package com.xy2407.nsukaddition.mixin.simukraft;

import com.xy2407.nsukaddition.common.colony.ColonyData;
import com.xy2407.nsukaddition.common.colony.ColonySqliteStorage;
import common.cn.kafei.simukraft.building.BuildingBlockData;
import common.cn.kafei.simukraft.building.BuildingTerritoryValidator;
import common.cn.kafei.simukraft.city.CityChunkManager;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** 服务端建造领地校验把"本城市所有殖民地(附属地)区块"并入有效领地，避免附属地建筑被误判为界外。 */
@Mixin(value = BuildingTerritoryValidator.class, remap = false)
public abstract class BuildingTerritoryValidatorColonyMixin {

    @Inject(method = "blockBoundsInCity", at = @At("HEAD"), cancellable = true, remap = false)
    private static void nsuk$includeColonyChunks(ServerLevel level, UUID cityId,
                                                 List<BuildingBlockData> blocks,
                                                 CallbackInfoReturnable<Boolean> cir) {
        if (level == null || cityId == null) {
            cir.setReturnValue(false);
            return;
        }
        Set<Long> territory = new HashSet<>(CityChunkManager.get(level).getCityChunks(cityId));
        for (ColonyData colony : ColonySqliteStorage.loadColoniesByParentCity(level, cityId)) {
            if (colony == null || colony.colonyId() == null) {
                continue;
            }
            territory.addAll(CityChunkManager.get(level).getCityChunks(colony.colonyId()));
        }
        cir.setReturnValue(BuildingTerritoryValidator.blockBoundsInChunks(blocks, territory));
    }
}