package com.xy2407.nsukaddition.mixin;

import common.cn.kafei.simukraft.network.npc.hire.NpcHireListRequestPacket;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 调试用：在雇佣网络包处理的关键步骤输出到玩家聊天框 */
@Mixin(targets = "common.cn.kafei.simukraft.network.npc.hire.NpcHireListRequestPacket", remap = false)
public class NpcHireListRequestPacketMixin {

    @Inject(method = "handle", at = @At("HEAD"), remap = false)
    private static void nsuk$debugHead(NpcHireListRequestPacket packet, IPayloadContext context, CallbackInfo ci) {
        if (context.player() instanceof ServerPlayer player) {
            player.displayClientMessage(Component.literal("§e[NSUK-DEBUG] handle收到包 → sourceType=" + packet.sourceType() + " role=" + packet.role() + " pos=" + packet.sourcePos()), false);
        }
    }

    @Inject(method = "handle", at = @At(value = "INVOKE",
            target = "Lcommon/cn/kafei/simukraft/network/npc/hire/NpcHireAccessValidator;validateSource(Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;Ljava/lang/String;Ljava/lang/String;)Lcommon/cn/kafei/simukraft/network/npc/hire/NpcHireAccessValidator$SourceContext;",
            shift = At.Shift.AFTER), remap = false)
    private static void nsuk$debugAfterValidate(NpcHireListRequestPacket packet, IPayloadContext context, CallbackInfo ci) {
        if (context.player() instanceof ServerPlayer player) {
            player.displayClientMessage(Component.literal("§e[NSUK-DEBUG] validateSource已返回（见下一条）"), false);
        }
    }
}
