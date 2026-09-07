package com.xy2407.nsukaddition.common.network.dialog;

import com.xy2407.nsukaddition.NsukAddition;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** 客户端点击对话选项后发往服务端的动作包，携带NPC实体ID与动作ID，服务端据其执行对话分支。 */
public record DialogNpcOptionPacket(int dialogEntityId, String action) implements CustomPacketPayload {

    public static final Type<DialogNpcOptionPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(NsukAddition.MOD_ID, "dialog_npc_option"));
    public static final StreamCodec<RegistryFriendlyByteBuf, DialogNpcOptionPacket> STREAM_CODEC =
            StreamCodec.of(DialogNpcOptionPacket::encode, DialogNpcOptionPacket::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void encode(RegistryFriendlyByteBuf buf, DialogNpcOptionPacket p) {
        buf.writeInt(p.dialogEntityId());
        buf.writeUtf(p.action() == null ? "" : p.action());
    }

    public static DialogNpcOptionPacket decode(RegistryFriendlyByteBuf buf) {
        int entityId = buf.readInt();
        String action = buf.readUtf(256);
        return new DialogNpcOptionPacket(entityId, action);
    }

    public static void handle(DialogNpcOptionPacket p, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) {
                return;
            }
            ServerLevel level = player.serverLevel();
            Entity entity = level.getEntity(p.dialogEntityId());
            if (entity instanceof com.xy2407.nsukaddition.common.entity.DialogNpcEntity dialog) {
                com.xy2407.nsukaddition.server.city.DialogNpcDialogService.handleAction(level, player, dialog, p.action());
            }
        });
    }
}