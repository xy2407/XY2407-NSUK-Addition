package com.xy2407.nsukaddition.mixin.simukraft;

import com.xy2407.nsukaddition.common.rts.path.SableTargetTracker;
import common.cn.kafei.simukraft.entity.CitizenEntity;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Map;

/**
 * ActiveNavigation.tick 桥接：对登记了结构锚点的市民，每 tick 校验结构是否漂移，
 * 漂移超阈值就对最新世界位置重发路径请求，实现追着移动/旋转结构到达其局部目标点。
 */
@Mixin(targets = "common.cn.kafei.simukraft.path.ActiveNavigation")
public abstract class ActiveNavigationSableMixin {

    @Inject(method = "tick", at = @At("HEAD"))
    private void nsukaddition$followStructureTarget(ServerLevel level, CitizenEntity citizen,
                                                    Map<?, ?> openedDoors, CallbackInfoReturnable<?> cir) {
        if (level == null || citizen == null) return;
        SableTargetTracker.follow(level, citizen.getUUID());
    }
}