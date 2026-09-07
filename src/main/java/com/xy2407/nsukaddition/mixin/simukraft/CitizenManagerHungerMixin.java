package com.xy2407.nsukaddition.mixin.simukraft;

import com.xy2407.nsukaddition.server.city.TownImmigrationService;
import common.cn.kafei.simukraft.citizen.CitizenData;
import common.cn.kafei.simukraft.citizen.CitizenManager;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 市民饥饿调整：只在城市状态机上做拦截判定，不遍历任何集合。
 * 未通过审批的移民(待审批队列)、以及不归属任何玩家城市(无cityId)的NPC，不消耗饥饿。
 */
@Mixin(CitizenManager.class)
public class CitizenManagerHungerMixin {

    /** 状态机拦截：城市状态缺失(非玩家城市NPC)或处于移民待审批，则跳过 tickCitizenData 的饥饿衰减。 */
    @Inject(method = "tickCitizenData", at = @At("HEAD"), cancellable = true, remap = false)
    private void nsuk$skipHungerForPendingImmigrant(ServerLevel level, CitizenData data, CallbackInfo ci) {
        if (data == null) {
            return;
        }
        boolean noCity = data.cityId() == null;
        boolean pending = TownImmigrationService.isPendingCitizen(data.uuid());
        if (noCity || pending) {
            ci.cancel();
        }
    }
}