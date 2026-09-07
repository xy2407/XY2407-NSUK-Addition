package com.xy2407.nsukaddition.mixin.simukraft;

import com.xy2407.nsukaddition.common.entity.DialogNpcEntity;
import common.cn.kafei.simukraft.commercial.CommercialTradeMenuHolder;
import common.cn.kafei.simukraft.network.commercial.CommercialTradeOpenResponsePacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 初始物资商店容器有效性：当交易方是世界管家实体时始终有效，避免被LDLib按距离/商业绑定关闭。 */
@Mixin(CommercialTradeMenuHolder.class)
public abstract class CommercialTradeMenuHolderMixin {

    @Shadow
    @Final
    private CommercialTradeOpenResponsePacket packet;

    @Inject(method = "isStillValid", at = @At("HEAD"), cancellable = true, remap = false)
    private void nsuk$dialogNpcShopValid(Player player, CallbackInfoReturnable<Boolean> cir) {
        if (this.packet != null && this.packet.workerId() != null && player != null
                && player.level() instanceof ServerLevel level) {
            Entity entity = level.getEntity(this.packet.workerId());
            if (entity instanceof DialogNpcEntity) {
                cir.setReturnValue(true);
            }
        }
    }
}