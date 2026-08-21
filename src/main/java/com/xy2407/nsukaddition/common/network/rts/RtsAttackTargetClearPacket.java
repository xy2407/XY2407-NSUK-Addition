package com.xy2407.nsukaddition.common.network.rts;

import com.xy2407.nsukaddition.NsukAddition;
import com.xy2407.nsukaddition.server.combat.CitizenCombatService;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * RTS 玩家指令网络包（客户端→服务端）：清除所有 NPC 的攻击目标指令与仇恨。
 * 玩家按 ~ 键时发送。
 */
public record RtsAttackTargetClearPacket() implements CustomPacketPayload {

    public static final Type<RtsAttackTargetClearPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(NsukAddition.MOD_ID, "rts_attack_target_clear"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RtsAttackTargetClearPacket> STREAM_CODEC =
            StreamCodec.of(RtsAttackTargetClearPacket::encode, RtsAttackTargetClearPacket::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void encode(RegistryFriendlyByteBuf b, RtsAttackTargetClearPacket p) {
    }

    public static RtsAttackTargetClearPacket decode(RegistryFriendlyByteBuf b) {
        return new RtsAttackTargetClearPacket();
    }

    public static void handle(RtsAttackTargetClearPacket p, IPayloadContext ctx) {
        if (!(ctx.player() instanceof ServerPlayer player) || !(player.level() instanceof ServerLevel level)) return;
        ctx.enqueueWork(() -> {
            CitizenCombatService.clearAllCommandTargets(level, player);
        });
    }
}
