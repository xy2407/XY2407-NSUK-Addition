package com.xy2407.nsukaddition.mixin;

import com.xy2407.nsukaddition.common.cooking.RestaurantBoxData;
import com.xy2407.nsukaddition.common.cooking.RestaurantBoxManager;
import com.xy2407.nsukaddition.common.cooking.RestaurantConstants;
import com.xy2407.nsukaddition.common.cooking.RestaurantControlBoxService;
import com.xy2407.nsukaddition.common.cooking.RestaurantDefinition;
import com.xy2407.nsukaddition.common.cooking.RestaurantDefinitionLoader;
import com.xy2407.nsukaddition.common.registry.ModBlocks;
import common.cn.kafei.simukraft.network.npc.hire.NpcHireAssignPacket;
import common.cn.kafei.simukraft.network.toast.InfoToastService;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 餐厅服务员互斥：纯 maid 模式禁止雇佣市民服务员；已雇佣女仆时禁止再雇佣市民服务员。 */
@Mixin(NpcHireAssignPacket.class)
public abstract class NpcHireAssignPacketMixin {

    @Inject(method = "handle", at = @At("HEAD"), cancellable = true, remap = false)
    private static void nsukaddition$restaurantWaiterExclusive(NpcHireAssignPacket packet, IPayloadContext ctx, CallbackInfo ci) {
        if (!(ctx.player() instanceof ServerPlayer player) || !(player.level() instanceof ServerLevel level)) {
            return;
        }
        if (!RestaurantConstants.HIRE_SOURCE_TYPE.equals(packet.sourceType())
                || !RestaurantConstants.HIRE_ROLE_WAITER.equals(packet.role())) {
            return;
        }
        if (!level.getBlockState(packet.sourcePos()).is(ModBlocks.RESTAURANT_CONTROL_BOX.get())) {
            return;
        }
        RestaurantBoxManager manager = RestaurantBoxManager.get(level);
        RestaurantBoxData data = manager.get(packet.sourcePos());
        RestaurantDefinition definition = RestaurantDefinitionLoader
                .loadForBuilding(RestaurantControlBoxService.resolveBuilding(level, packet.sourcePos())).definition();
        if (definition == null) {
            return;
        }
        if (!definition.isNsukWaiter()) {
            InfoToastService.warning(player, Component.translatable("message.xy2407_nsuk_addition.cooking.waiter_maid_only"));
            ci.cancel();
            return;
        }
        if (data != null && !data.maidWaiters().isEmpty()) {
            InfoToastService.warning(player, Component.translatable("message.xy2407_nsuk_addition.cooking.waiter_occupied_by_maid"));
            ci.cancel();
        }
    }
}
