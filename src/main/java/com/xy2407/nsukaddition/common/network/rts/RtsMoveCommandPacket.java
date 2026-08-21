package com.xy2407.nsukaddition.common.network.rts;

import com.xy2407.nsukaddition.NsukAddition;
import com.xy2407.nsukaddition.common.entity.RtsFakePlayerEntity;
import com.xy2407.nsukaddition.server.rts.FormationUtil;
import com.xy2407.nsukaddition.server.rts.RtsCitizenTaskManager;
import com.xy2407.nsukaddition.server.rts.RtsCityAccessValidator;
import com.xy2407.nsukaddition.server.rts.RtsPhysicsMoveTask;
import common.cn.kafei.simukraft.entity.CitizenEntity;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** RTS 命令选中实体移动到目标点的网络包（客户端→服务端），市民走 SimuKraft 导航系统，玩家骑乘虚拟坐骑。 */
@SuppressWarnings("null")
public record RtsMoveCommandPacket(List<UUID> entityIds, Vec3 target, int formation) implements CustomPacketPayload {

    public static final Type<RtsMoveCommandPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(NsukAddition.MOD_ID, "rts_move_command"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RtsMoveCommandPacket> STREAM_CODEC =
            StreamCodec.of(RtsMoveCommandPacket::encode, RtsMoveCommandPacket::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void encode(RegistryFriendlyByteBuf b, RtsMoveCommandPacket p) {
        b.writeInt(p.entityIds().size());
        for (UUID id : p.entityIds()) {
            b.writeUUID(id);
        }
        b.writeDouble(p.target().x);
        b.writeDouble(p.target().y);
        b.writeDouble(p.target().z);
        b.writeInt(p.formation());
    }

    public static RtsMoveCommandPacket decode(RegistryFriendlyByteBuf b) {
        int size = b.readInt();
        List<UUID> ids = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            ids.add(b.readUUID());
        }
        Vec3 target = new Vec3(b.readDouble(), b.readDouble(), b.readDouble());
        int formation = b.readInt();
        return new RtsMoveCommandPacket(ids, target, formation);
    }

    public static void handle(RtsMoveCommandPacket p, IPayloadContext ctx) {
        if (!(ctx.player() instanceof ServerPlayer player) || !(player.level() instanceof ServerLevel level)) return;
        if (p.entityIds().isEmpty() || p.target() == null) return;

        ctx.enqueueWork(() -> {
            int npcIndex = 0;
            FormationUtil.Formation formType = FormationUtil.Formation.fromId(p.formation());
            List<Vec3> formOffsets = null;
            if (formType != FormationUtil.Formation.NONE) {
                formOffsets = FormationUtil.generateOffsets(p.entityIds().size(), formType);
            }
            for (UUID id : p.entityIds()) {
                Entity entity = level.getEntity(id);
                if (entity == null) {
                    for (ServerLevel otherLevel : player.getServer().getAllLevels()) {
                        entity = otherLevel.getEntity(id);
                        if (entity != null) break;
                    }
                }
                if (entity instanceof CitizenEntity citizen) {
                    if (!RtsCityAccessValidator.canControlNpc(level, player, citizen)) {
                        continue;
                    }
                    Vec3 offset = formOffsets != null
                            ? formOffsets.get(Math.min(npcIndex, formOffsets.size() - 1))
                            : scatterOffset(npcIndex, 2.0D);
                    Vec3 npcTarget = p.target().add(offset);
                    npcIndex++;
                    RtsCitizenTaskManager.assignTask(id,
                            new RtsPhysicsMoveTask(npcTarget, formType != FormationUtil.Formation.NONE));
                } else if (entity instanceof RtsFakePlayerEntity fakePlayer) {
                    if (fakePlayer.getOwnerUUID().equals(player.getUUID())) {
                        fakePlayer.setTarget(p.target());
                    }
                } else if (entity instanceof ServerPlayer targetPlayer) {
                    if (!targetPlayer.getUUID().equals(player.getUUID())) {
                        continue;
                    }
                    RtsFakePlayerEntity fake = findFakePlayer(player.getServer(), targetPlayer.getUUID());
                    if (fake == null) {
                        fake = new RtsFakePlayerEntity(targetPlayer.level(), targetPlayer.position(), targetPlayer.getUUID());
                        fake.setYRot(targetPlayer.getYRot());
                        fake.yBodyRot = targetPlayer.getYRot();
                        fake.yHeadRot = targetPlayer.getYRot();
                        targetPlayer.level().addFreshEntity(fake);
                    }
                    fake.setTarget(p.target());
                }
            }
        });
    }

    private static Vec3 scatterOffset(int index, double spacing) {
        if (index == 0) return Vec3.ZERO;
        final double GOLDEN_ANGLE = 2.39996D;
        double radius = spacing * Math.sqrt(index);
        double angle = index * GOLDEN_ANGLE;
        return new Vec3(Math.cos(angle) * radius, 0, Math.sin(angle) * radius);
    }

    private static RtsFakePlayerEntity findFakePlayer(MinecraftServer server, UUID ownerUUID) {
        for (ServerLevel level : server.getAllLevels()) {
            for (Entity entity : level.getAllEntities()) {
                if (entity instanceof RtsFakePlayerEntity fake && fake.getOwnerUUID().equals(ownerUUID)) {
                    return fake;
                }
            }
        }
        return null;
    }
}
