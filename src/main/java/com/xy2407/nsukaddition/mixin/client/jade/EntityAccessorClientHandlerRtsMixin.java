package com.xy2407.nsukaddition.mixin.client.jade;

import com.xy2407.nsukaddition.client.rts.RtsModeManager;
import com.xy2407.nsukaddition.client.rts.RtsPicker;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import snownee.jade.api.EntityAccessor;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.impl.EntityAccessorClientHandler;

import java.util.List;

/**
 * RTS 模式下实体服务器 NBT 请求只对“准星对准的实体”放行：
 * Jade 服务端距离检查(player 实体在假人处，离相机视野 NPC 很远)会拒绝远距离实体的数据请求，
 * 导致整个实体 tooltip 不渲染。这里非准星实体不发起请求(纯客户端显示)，准星实体正常请求，
 * 由服务端 mixin 放行其距离检查——既恢复实体 NBT 信息，又不扩大请求范围(带宽安全)。
 */
@Mixin(value = EntityAccessorClientHandler.class, remap = false)
public abstract class EntityAccessorClientHandlerRtsMixin {

    @Inject(method = "shouldRequestData", at = @At("HEAD"), cancellable = true, remap = false)
    private void nsukaddition$rtsFocusOnly(EntityAccessor accessor,
                                           CallbackInfoReturnable<List<IServerDataProvider<EntityAccessor>>> cir) {
        if (!RtsModeManager.isActive()) {
            return;
        }
        Entity e = accessor.getEntity();
        if (e == null || e.getId() != RtsPicker.lastFocusEntityId) {
            cir.setReturnValue(List.of());
        }
    }
}
