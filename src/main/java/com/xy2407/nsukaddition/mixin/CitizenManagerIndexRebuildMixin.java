package com.xy2407.nsukaddition.mixin;

import com.xy2407.nsukaddition.common.index.CitizenWorkplaceIndex;
import common.cn.kafei.simukraft.citizen.CitizenManager;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 开服从 SQLite 加载市民后全量重建 workplaceId 反向索引。
 * 加载路径（CitizenData.fromTag 反序列化）直写字段不走 setWorkplaceId setter，
 * 因此必须在加载完成后遍历重建。loadFromSqlite 每次 get() 都会执行（含提前 return），
 * 用索引就绪标志防抖，只在首次加载与手动重载后重建。
 */
@Mixin(value = CitizenManager.class, remap = false)
public class CitizenManagerIndexRebuildMixin {

    @Inject(method = "loadFromSqlite", at = @At("RETURN"), remap = false)
    private void nsuk$rebuildWorkplaceIndex(ServerLevel level, CallbackInfo ci) {
        if (!CitizenWorkplaceIndex.isIndexReady()) {
            CitizenWorkplaceIndex.rebuildIndex(((CitizenManager) (Object) this).allCitizens());
        }
    }

    @Inject(method = "reloadFromSqlite", at = @At("RETURN"), remap = false)
    private void nsuk$resetIndexOnReload(ServerLevel level, CallbackInfo ci) {
        CitizenWorkplaceIndex.reset();
    }
}
