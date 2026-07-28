package com.xy2407.nsukaddition.common.network.cooking;

import com.xy2407.nsukaddition.NsukAddition;
import com.xy2407.nsukaddition.common.autorestock.AutoRestockConfig;
import com.xy2407.nsukaddition.common.cooking.RestaurantConstants;
import com.xy2407.nsukaddition.common.cooking.RestaurantControlBoxService;
import com.xy2407.nsukaddition.common.registry.ModBlocks;
import common.cn.kafei.simukraft.building.BuildingIntegrityService;
import common.cn.kafei.simukraft.building.PlacedBuildingRecord;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** 餐厅控制箱操作网络包，处理客户端发送的食谱选择、运行切换、解雇、修复等操作。 */
@SuppressWarnings("null")
public record RestaurantControlBoxActionPacket(BlockPos pos, Action action, String recipeId) implements CustomPacketPayload {

    public static final Type<RestaurantControlBoxActionPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(NsukAddition.MOD_ID, "restaurant_control_box_action"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RestaurantControlBoxActionPacket> STREAM_CODEC =
            StreamCodec.of(RestaurantControlBoxActionPacket::encode, RestaurantControlBoxActionPacket::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void encode(RegistryFriendlyByteBuf buf, RestaurantControlBoxActionPacket p) {
        buf.writeBlockPos(p.pos());
        buf.writeEnum(p.action());
        buf.writeUtf(p.recipeId(), 128);
    }

    public static RestaurantControlBoxActionPacket decode(RegistryFriendlyByteBuf buf) {
        return new RestaurantControlBoxActionPacket(buf.readBlockPos(), buf.readEnum(Action.class), buf.readUtf(128));
    }

    public static void handle(RestaurantControlBoxActionPacket p, IPayloadContext ctx) {
        if (ctx.player() instanceof ServerPlayer player && player.level() instanceof ServerLevel level) {
            if (!player.blockPosition().closerThan(p.pos(), 16.0D)) return;
            if (!level.getBlockState(p.pos()).is(ModBlocks.RESTAURANT_CONTROL_BOX.get())) return;
            switch (p.action()) {
                case SELECT_RECIPE -> RestaurantControlBoxService.selectRecipe(level, p.pos(), p.recipeId());
                case TOGGLE_RUN -> RestaurantControlBoxService.toggleRunning(level, p.pos());
                case FIRE_CHEF -> RestaurantControlBoxService.fireRole(level, p.pos(), RestaurantConstants.HIRE_ROLE_CHEF);
                case FIRE_WAITER -> RestaurantControlBoxService.fireRole(level, p.pos(), RestaurantConstants.HIRE_ROLE_WAITER);
                case TOGGLE_AUTORESTOCK -> toggleAutoRestock(level, p.pos());
                case REPAIR_BUILDING -> repairBuilding(level, player, p.pos());
            }
            PacketDistributor.sendToPlayer(player,
                    RestaurantControlBoxOpenResponsePacket.from(RestaurantControlBoxService.buildView(level, p.pos())));
        }
    }

    private static void repairBuilding(ServerLevel level, ServerPlayer player, BlockPos pos) {
        PlacedBuildingRecord building = RestaurantControlBoxService.resolveBuilding(level, pos);
        BuildingIntegrityService.repair(level, player, building);
    }

    /** 切换餐厅自动补货开关。 */
    private static void toggleAutoRestock(ServerLevel level, BlockPos pos) {
        AutoRestockConfig.setEnabled(level, pos, !AutoRestockConfig.isEnabled(pos));
    }

    public enum Action {
        SELECT_RECIPE,
        TOGGLE_RUN,
        FIRE_CHEF,
        FIRE_WAITER,
        TOGGLE_AUTORESTOCK,
        REPAIR_BUILDING
    }
}
