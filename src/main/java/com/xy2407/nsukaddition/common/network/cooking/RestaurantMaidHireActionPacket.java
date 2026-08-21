package com.xy2407.nsukaddition.common.network.cooking;

import com.xy2407.nsukaddition.NsukAddition;
import com.xy2407.nsukaddition.common.cooking.RestaurantControlBoxService;
import common.cn.kafei.simukraft.network.toast.InfoToastService;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

/**
 * 餐厅女仆雇佣/解雇操作网络包，客户端请求服务端雇佣或解雇指定女仆。
 */
@SuppressWarnings("null")
public record RestaurantMaidHireActionPacket(BlockPos pos, Action action, UUID maidId) implements CustomPacketPayload {

    public static final Type<RestaurantMaidHireActionPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(NsukAddition.MOD_ID, "restaurant_maid_hire_action"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RestaurantMaidHireActionPacket> STREAM_CODEC =
            StreamCodec.of(RestaurantMaidHireActionPacket::encode, RestaurantMaidHireActionPacket::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void encode(RegistryFriendlyByteBuf buf, RestaurantMaidHireActionPacket p) {
        buf.writeBlockPos(p.pos());
        buf.writeEnum(p.action());
        buf.writeUUID(p.maidId());
    }

    public static RestaurantMaidHireActionPacket decode(RegistryFriendlyByteBuf buf) {
        return new RestaurantMaidHireActionPacket(buf.readBlockPos(), buf.readEnum(Action.class), buf.readUUID());
    }

    public static void handle(RestaurantMaidHireActionPacket p, IPayloadContext ctx) {
        if (ctx.player() instanceof ServerPlayer player && player.level() instanceof ServerLevel level) {
            if (!player.blockPosition().closerThan(p.pos(), 16.0D)) return;
            if (p.action() == Action.HIRE) {
                boolean hired = RestaurantControlBoxService.hireMaid(level, p.pos(), p.maidId(), player.getUUID());
                if (!hired) {
                    InfoToastService.warning(player,
                            Component.translatable("message.xy2407_nsuk_addition.cooking.maid_hire_failed"));
                }
            } else {
                RestaurantControlBoxService.fireMaid(level, p.pos(), p.maidId());
            }
            PacketDistributor.sendToPlayer(player,
                    RestaurantControlBoxOpenResponsePacket.from(RestaurantControlBoxService.buildView(level, p.pos())));
        }
    }

    public enum Action {
        HIRE,
        FIRE
    }
}
