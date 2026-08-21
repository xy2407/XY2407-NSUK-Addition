package com.xy2407.nsukaddition.common.network.building;

import com.xy2407.nsukaddition.NsukAddition;
import com.xy2407.nsukaddition.server.building.BuildTaskActionHandler;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

/** 建造任务操作网络包，携带市民与任务 ID，处理暂停、恢复、追踪和终止建造任务操作。 */
public record BuildTaskActionPacket(UUID citizenId, UUID taskId, Action action) implements CustomPacketPayload {

    public static final Type<BuildTaskActionPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(NsukAddition.MOD_ID, "build_task_action"));
    public static final StreamCodec<RegistryFriendlyByteBuf, BuildTaskActionPacket> STREAM_CODEC =
            StreamCodec.of(BuildTaskActionPacket::encode, BuildTaskActionPacket::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void encode(RegistryFriendlyByteBuf b, BuildTaskActionPacket p) {
        b.writeUUID(p.citizenId);
        b.writeUUID(p.taskId);
        b.writeEnum(p.action);
    }

    public static BuildTaskActionPacket decode(RegistryFriendlyByteBuf b) {
        return new BuildTaskActionPacket(b.readUUID(), b.readUUID(), b.readEnum(Action.class));
    }

    public static void handle(BuildTaskActionPacket p, IPayloadContext ctx) {
        if (ctx.player() instanceof ServerPlayer player && player.level() instanceof ServerLevel level) {
            BuildTaskActionHandler.handle(level, player, p.citizenId, p.taskId, p.action);
        }
    }

    public enum Action { PAUSE, RESUME, TRACK, ABORT }
}
