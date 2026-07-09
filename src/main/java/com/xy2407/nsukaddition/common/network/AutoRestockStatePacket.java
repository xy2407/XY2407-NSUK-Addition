package com.xy2407.nsukaddition.common.network;

import com.xy2407.nsukaddition.NsukAddition;
import com.xy2407.nsukaddition.common.autorestock.AutoRestockConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.minecraft.network.protocol.PacketFlow;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** C→S查询 + S→C响应：同步自动补货开关状态。 */
@SuppressWarnings("null")
public record AutoRestockStatePacket(BlockPos pos, boolean enabled) implements CustomPacketPayload {

    public static final Type<AutoRestockStatePacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(NsukAddition.MOD_ID, "auto_restock_state"));
    public static final StreamCodec<RegistryFriendlyByteBuf, AutoRestockStatePacket> STREAM_CODEC =
            StreamCodec.of(AutoRestockStatePacket::encode, AutoRestockStatePacket::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void encode(RegistryFriendlyByteBuf buf, AutoRestockStatePacket p) {
        buf.writeBlockPos(p.pos());
        buf.writeBoolean(p.enabled());
    }

    public static AutoRestockStatePacket decode(RegistryFriendlyByteBuf buf) {
        return new AutoRestockStatePacket(buf.readBlockPos(), buf.readBoolean());
    }

    /** 统一入口，根据连接方向分发到查询或响应处理 */
    public static void handle(AutoRestockStatePacket p, IPayloadContext ctx) {
        if (ctx.flow() == PacketFlow.SERVERBOUND) {
            handleQuery(p, ctx);
        } else {
            handleResponse(p, ctx);
        }
    }

    /** C→S：查询指定位置的自动补货状态，服务端回发响应 */
    public static void handleQuery(AutoRestockStatePacket p, IPayloadContext ctx) {
        if (ctx.player() instanceof ServerPlayer player && player.level() instanceof ServerLevel level) {
            boolean enabled = AutoRestockConfig.isEnabled(p.pos());
            PacketDistributor.sendToPlayer(player, new AutoRestockStatePacket(p.pos(), enabled));
        }
    }

    /** S→C：接收服务端返回的自动补货状态，更新客户端缓存 */
    public static void handleResponse(AutoRestockStatePacket p, IPayloadContext ctx) {
        com.xy2407.nsukaddition.client.autorestock.ClientAutoRestockCache.set(p.pos(), p.enabled());
    }
}
