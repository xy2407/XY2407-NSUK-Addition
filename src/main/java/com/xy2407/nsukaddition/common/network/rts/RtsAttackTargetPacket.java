package com.xy2407.nsukaddition.common.network.rts;

import com.xy2407.nsukaddition.NsukAddition;
import com.xy2407.nsukaddition.server.combat.CitizenCombatService;
import com.xy2407.nsukaddition.server.rts.RtsCityAccessValidator;
import common.cn.kafei.simukraft.entity.CitizenEntity;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * RTS 玩家指令网络包（客户端→服务端）：为选中的 NPC 设置攻击目标。
 * 携带 npcId -> 目标 UUID 集合：多目标支持（一个 NPC 可攻击多个目标，Ctrl 框选整体集火）。
 */
public record RtsAttackTargetPacket(Map<UUID, Set<UUID>> assignments) implements CustomPacketPayload {

    public static final Type<RtsAttackTargetPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(NsukAddition.MOD_ID, "rts_attack_target"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RtsAttackTargetPacket> STREAM_CODEC =
            StreamCodec.of(RtsAttackTargetPacket::encode, RtsAttackTargetPacket::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void encode(RegistryFriendlyByteBuf b, RtsAttackTargetPacket p) {
        b.writeInt(p.assignments().size());
        for (Map.Entry<UUID, Set<UUID>> e : p.assignments().entrySet()) {
            b.writeUUID(e.getKey());
            b.writeInt(e.getValue().size());
            for (UUID targetId : e.getValue()) {
                b.writeUUID(targetId);
            }
        }
    }

    public static RtsAttackTargetPacket decode(RegistryFriendlyByteBuf b) {
        int size = b.readInt();
        Map<UUID, Set<UUID>> assignments = new LinkedHashMap<>(size);
        for (int i = 0; i < size; i++) {
            UUID npcId = b.readUUID();
            int targetCount = b.readInt();
            Set<UUID> targets = new LinkedHashSet<>(targetCount);
            for (int j = 0; j < targetCount; j++) {
                targets.add(b.readUUID());
            }
            assignments.put(npcId, targets);
        }
        return new RtsAttackTargetPacket(assignments);
    }

    public static void handle(RtsAttackTargetPacket p, IPayloadContext ctx) {
        if (!(ctx.player() instanceof ServerPlayer player) || !(player.level() instanceof ServerLevel level)) return;
        if (p.assignments().isEmpty()) return;

        ctx.enqueueWork(() -> {
            for (Map.Entry<UUID, Set<UUID>> e : p.assignments().entrySet()) {
                UUID npcId = e.getKey();
                if (!(level.getEntity(npcId) instanceof CitizenEntity citizen)
                        || !RtsCityAccessValidator.canControlNpc(level, player, citizen)) {
                    continue;
                }
                Set<UUID> validTargets = new LinkedHashSet<>();
                for (UUID targetId : e.getValue()) {
                    if (!(level.getEntity(targetId) instanceof LivingEntity living) || !living.isAlive()) continue;
                    if (living instanceof CitizenEntity) continue;
                    if (living instanceof net.minecraft.world.entity.player.Player) continue;
                    validTargets.add(targetId);
                }
                if (!validTargets.isEmpty()) {
                    CitizenCombatService.setCommandTargets(npcId, validTargets);
                }
            }
        });
    }
}
