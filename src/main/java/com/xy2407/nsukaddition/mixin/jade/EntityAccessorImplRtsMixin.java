package com.xy2407.nsukaddition.mixin.jade;

import com.xy2407.nsukaddition.server.rts.RtsJadeFocusService;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import snownee.jade.impl.EntityAccessorImpl;

/**
 * RTS 准星实体放行 Jade 服务端 NBT 距离检查：
 * EntityAccessorImpl.handleRequest 的 lambda 内 player.distanceToSqr(entity) 超距即拒绝。
 * 注入 lambda 合成方法 lambda$handleRequest$0，仅对玩家准星对准的实体返回 0 距离放行，
 * 其余实体保持原距离限制——精确放行准星目标，不扩大请求范围(带宽安全)。
 */
@Mixin(value = EntityAccessorImpl.class, remap = false)
public abstract class EntityAccessorImplRtsMixin {

    @Redirect(method = "lambda$handleRequest$0",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/server/level/ServerPlayer;distanceToSqr(Lnet/minecraft/world/entity/Entity;)D"),
            remap = false, require = 0)
    private static double nsukaddition$focusDistance(ServerPlayer player, Entity other) {
        if (RtsJadeFocusService.isFocused(player.getUUID(), other.getUUID())) {
            return 0.0D;
        }
        return player.distanceToSqr(other);
    }
}
