package com.xy2407.nsukaddition.common.network.cooking;

import com.xy2407.nsukaddition.NsukAddition;
import com.xy2407.nsukaddition.common.cooking.RestaurantControlBoxService;
import com.xy2407.nsukaddition.common.registry.ModBlocks;
import common.cn.kafei.simukraft.building.PlacedBuildingDemolitionService;
import common.cn.kafei.simukraft.building.PlacedBuildingRecord;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** 餐厅控制箱拆除网络包，处理客户端发送的拆除餐厅控制箱请求，拆除整个建筑。 */
@SuppressWarnings("null")
public record RestaurantControlBoxDemolishPacket(BlockPos pos) implements CustomPacketPayload {

    public static final Type<RestaurantControlBoxDemolishPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(NsukAddition.MOD_ID, "restaurant_control_box_demolish"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RestaurantControlBoxDemolishPacket> STREAM_CODEC =
            StreamCodec.of(RestaurantControlBoxDemolishPacket::encode, RestaurantControlBoxDemolishPacket::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void encode(RegistryFriendlyByteBuf b, RestaurantControlBoxDemolishPacket p) { b.writeBlockPos(p.pos()); }
    public static RestaurantControlBoxDemolishPacket decode(RegistryFriendlyByteBuf b) { return new RestaurantControlBoxDemolishPacket(b.readBlockPos()); }

    public static void handle(RestaurantControlBoxDemolishPacket p, IPayloadContext ctx) {
        if (ctx.player() instanceof ServerPlayer player && player.level() instanceof ServerLevel level) {
            if (!player.blockPosition().closerThan(p.pos(), 16.0D)) return;
            if (!level.getBlockState(p.pos()).is(ModBlocks.RESTAURANT_CONTROL_BOX.get())) return;
            PlacedBuildingRecord building = RestaurantControlBoxService.resolveBuilding(level, p.pos());
            RestaurantControlBoxService.onRemoved(level, p.pos());
            if (building != null) {
                PlacedBuildingDemolitionService.demolish(level, building);
            }
        }
    }
}
