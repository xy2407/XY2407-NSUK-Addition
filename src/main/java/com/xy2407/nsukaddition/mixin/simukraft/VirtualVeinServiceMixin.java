package com.xy2407.nsukaddition.mixin.simukraft;

import common.cn.kafei.simukraft.virtualvein.VirtualVeinFieldKey;
import common.cn.kafei.simukraft.virtualvein.VirtualVeinFieldResolver;
import common.cn.kafei.simukraft.virtualvein.VirtualVeinService;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 扩展虚拟矿脉：仅将 targetCount 概率整体上调(无/一/两 = 10%/30%/60%)。
 * 自定义矿脉(石英/海晶石)已改由数据包提供，放在本工程 resources 下
 * data/simukraft/virtual_veins(使用 simukraft 命名空间)，由 simukraft 的 VirtualVeinDefinitionLoader 加载，无需注入 selectCandidates/generateProfile。
 */
@Mixin(value = VirtualVeinService.class, remap = false)
public abstract class VirtualVeinServiceMixin {

    /** targetCount: 无/一/两 条矿脉概率为 10%/30%/60%(期望 1.5)。 */
    @Inject(method = "targetCount", at = @At("HEAD"), cancellable = true, remap = false)
    private static void nsuk$targetCount(long seed, VirtualVeinFieldKey key,
                                         CallbackInfoReturnable<Integer> cir) {
        double value = VirtualVeinFieldResolver.unit(VirtualVeinFieldResolver.seededValue(seed, key, "count"));
        cir.setReturnValue(value < 0.10D ? 0 : (value < 0.40D ? 1 : 2));
    }
}