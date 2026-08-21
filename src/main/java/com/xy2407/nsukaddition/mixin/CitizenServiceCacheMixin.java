package com.xy2407.nsukaddition.mixin;

import com.xy2407.nsukaddition.common.index.CitizenWorkplaceIndex;
import common.cn.kafei.simukraft.citizen.CitizenData;
import common.cn.kafei.simukraft.citizen.CitizenService;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;
import java.util.UUID;

/**
 * 将 findAssignedCitizen 的 O(N) 全量扫描改为 O(1) 反向索引查询。
 * 索引由 CitizenWorkplaceIndexMixin 维护（setWorkplaceId 增量更新）、
 * CitizenManagerIndexRebuildMixin 在开服加载后重建。索引未就绪时回退到原扫描逻辑。
 */
@Mixin(value = CitizenService.class, remap = false)
public class CitizenServiceCacheMixin {

    @Inject(method = "findAssignedCitizen", at = @At("HEAD"), cancellable = true, remap = false)
    private static void nsuk$hitWorkplaceIndex(ServerLevel level, UUID workplaceId, CallbackInfoReturnable<UUID> cir) {
        if (level == null || workplaceId == null) {
            return;
        }
        if (!CitizenWorkplaceIndex.isIndexReady()) {
            return;
        }
        String dimension = level.dimension().location().toString();
        UUID citizenUuid = CitizenWorkplaceIndex.indexGet(dimension, workplaceId);
        if (citizenUuid == null) {
            cir.setReturnValue(null);
            return;
        }
        Optional<CitizenData> citizen = CitizenService.findCitizen(level, citizenUuid);
        if (citizen.isEmpty() || citizen.get().dead()) {
            CitizenWorkplaceIndex.indexRemove(dimension, workplaceId, citizenUuid);
            cir.setReturnValue(null);
            return;
        }
        cir.setReturnValue(citizenUuid);
    }
}
