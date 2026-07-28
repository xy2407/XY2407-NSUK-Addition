package com.xy2407.nsukaddition.common.network.cooking;

import com.xy2407.nsukaddition.NsukAddition;
import com.xy2407.nsukaddition.common.cooking.RestaurantBoxData;
import com.xy2407.nsukaddition.common.cooking.RestaurantBoxManager;
import com.xy2407.nsukaddition.common.cooking.RestaurantControlBoxService;
import com.xy2407.nsukaddition.common.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.HashSet;
import java.util.Set;

/** 餐厅菜品选择网络包，客户端发送玩家勾选的菜品物品 id 列表到服务端持久化。 */
@SuppressWarnings("null")
public record RestaurantMenuSelectPacket(BlockPos boxPos, Set<String> selectedCookItems) implements CustomPacketPayload {

    public static final Type<RestaurantMenuSelectPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(NsukAddition.MOD_ID, "restaurant_menu_select"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RestaurantMenuSelectPacket> STREAM_CODEC =
            StreamCodec.of(RestaurantMenuSelectPacket::encode, RestaurantMenuSelectPacket::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void encode(RegistryFriendlyByteBuf buf, RestaurantMenuSelectPacket p) {
        buf.writeBlockPos(p.boxPos());
        buf.writeVarInt(p.selectedCookItems().size());
        for (String s : p.selectedCookItems()) buf.writeUtf(s, 128);
    }

    public static RestaurantMenuSelectPacket decode(RegistryFriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        int count = buf.readVarInt();
        Set<String> items = new HashSet<>();
        for (int i = 0; i < count; i++) items.add(buf.readUtf(128));
        return new RestaurantMenuSelectPacket(pos, items);
    }

    public static void handle(RestaurantMenuSelectPacket p, IPayloadContext ctx) {
        if (ctx.player() instanceof ServerPlayer player && player.level() instanceof ServerLevel level) {
            if (!player.blockPosition().closerThan(p.boxPos(), 16.0D)) return;
            if (!level.getBlockState(p.boxPos()).is(ModBlocks.RESTAURANT_CONTROL_BOX.get())) return;
            RestaurantBoxManager manager = RestaurantBoxManager.get(level);
            RestaurantBoxData data = manager.getOrCreate(p.boxPos());
            data.setSelectedCookItems(p.selectedCookItems());
            manager.persist(data);
            PacketDistributor.sendToPlayer(player,
                    RestaurantControlBoxOpenResponsePacket.from(RestaurantControlBoxService.buildView(level, p.boxPos())));
        }
    }
}
