package com.xy2407.nsukaddition.common.network.colony;

import com.xy2407.nsukaddition.NsukAddition;
import com.xy2407.nsukaddition.common.colony.ColonyData;
import com.xy2407.nsukaddition.common.colony.ColonySqliteStorage;
import common.cn.kafei.simukraft.network.toast.InfoToastService;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

/** 附属地改名网络包，客户端发送新名称到服务端更新 SQLite。 */
@SuppressWarnings("null")
public record ColonyRenamePacket(UUID colonyId, String newName) implements CustomPacketPayload {

    public static final Type<ColonyRenamePacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(NsukAddition.MOD_ID, "colony_rename"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ColonyRenamePacket> STREAM_CODEC =
            StreamCodec.of(ColonyRenamePacket::encode, ColonyRenamePacket::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void encode(RegistryFriendlyByteBuf b, ColonyRenamePacket p) {
        b.writeUUID(p.colonyId());
        b.writeUtf(p.newName(), 64);
    }

    public static ColonyRenamePacket decode(RegistryFriendlyByteBuf b) {
        return new ColonyRenamePacket(b.readUUID(), b.readUtf(64));
    }

    public static void handle(ColonyRenamePacket p, IPayloadContext ctx) {
        if (!(ctx.player() instanceof ServerPlayer player) || !(player.level() instanceof ServerLevel level)) return;

        ColonyData colony = ColonySqliteStorage.loadColonyById(level, p.colonyId());
        if (colony == null) return;

        ColonyData updated = new ColonyData(
                colony.colonyId(), colony.parentCityId(), p.newName(),
                colony.corePos(), colony.dimensionId(), colony.createdAt()
        );
        ColonySqliteStorage.saveColony(level, updated);

        InfoToastService.success(player, Component.translatable(
                "message.xy2407_nsuk_addition.colony.rename_success", p.newName()));
    }
}
