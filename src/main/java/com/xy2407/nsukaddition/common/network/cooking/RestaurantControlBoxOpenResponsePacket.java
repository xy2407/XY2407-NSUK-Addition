package com.xy2407.nsukaddition.common.network.cooking;

import com.xy2407.nsukaddition.NsukAddition;
import com.xy2407.nsukaddition.common.cooking.RestaurantControlBoxView;
import com.xy2407.nsukaddition.common.network.clientbound.RestaurantControlBoxBridge;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** 餐厅控制箱打开响应网络包，服务端返回餐厅控制箱完整界面数据供客户端渲染。 */
@SuppressWarnings("null")
public record RestaurantControlBoxOpenResponsePacket(BlockPos boxPos,
                                                     boolean hasBuilding,
                                                     String buildingName,
                                                     boolean definitionValid,
                                                     String definitionName,
                                                     String statusKey,
                                                     String statusText,
                                                     boolean running,
                                                     String selectedRecipeId,
                                                     boolean hasWorker,
                                                     UUID workerId,
                                                     String workerName,
                                                     boolean hasWaiter,
                                                     UUID waiterId,
                                                     String waiterName,
                                                     boolean hasBuildingBounds,
                                                     BlockPos boundsMin,
                                                     BlockPos boundsMax,
                                                     boolean integrityAvailable,
                                                     double integrityPercent,
                                                     int integrityRepairableBlocks,
                                                     int integrityManualRepairBlocks,
                                                     double integrityRepairCost,
                                                     List<PointMarkerEntry> pointMarkers,
                                                     List<RecipeEntry> recipes,
                                                     Set<String> selectedCookItems,
                                                     boolean autoRestock,
                                                     String waiterType,
                                                     List<MaidEntry> maidWaiters) implements CustomPacketPayload {

    public static final Type<RestaurantControlBoxOpenResponsePacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(NsukAddition.MOD_ID, "restaurant_control_box_open_response"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RestaurantControlBoxOpenResponsePacket> STREAM_CODEC =
            StreamCodec.of(RestaurantControlBoxOpenResponsePacket::encode, RestaurantControlBoxOpenResponsePacket::decode);

    public static RestaurantControlBoxOpenResponsePacket from(RestaurantControlBoxView view) {
        return new RestaurantControlBoxOpenResponsePacket(
                view.boxPos(), view.hasBuilding(), view.buildingName(),
                view.definitionValid(), view.definitionName(),
                view.statusKey(), view.statusText(), view.running(), view.selectedRecipeId(),
                view.hasWorker(), view.workerId(), view.workerName(),
                view.hasWaiter(), view.waiterId(), view.waiterName(),
                view.hasBuildingBounds(), view.boundsMin(), view.boundsMax(),
                view.integrityAvailable(), view.integrityPercent(),
                view.integrityRepairableBlocks(), view.integrityManualRepairBlocks(), view.integrityRepairCost(),
                view.pointMarkers().stream()
                        .map(m -> new PointMarkerEntry(m.id(), m.kind(), m.pos(), m.color()))
                        .toList(),
                view.recipes().stream()
                        .map(r -> new RecipeEntry(r.id(), r.name(),
                                r.inputs().stream().map(i -> new ItemEntry(i.itemId(), i.potionId(), i.count(), i.connector(), i.itemSpec())).toList(),
                                r.outputs().stream().map(i -> new ItemEntry(i.itemId(), i.potionId(), i.count(), i.connector(), i.itemSpec())).toList()))
                        .toList(),
                new HashSet<>(view.selectedCookItems()),
                view.autoRestock(),
                view.waiterType(),
                view.maidWaiters().stream()
                        .map(m -> new MaidEntry(m.uuid(), m.name()))
                        .toList()
        );
    }

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void encode(RegistryFriendlyByteBuf buf, RestaurantControlBoxOpenResponsePacket p) {
        buf.writeBlockPos(p.boxPos());
        buf.writeBoolean(p.hasBuilding());
        buf.writeUtf(p.buildingName(), 128);
        buf.writeBoolean(p.definitionValid());
        buf.writeUtf(p.definitionName(), 128);
        buf.writeUtf(p.statusKey(), 128);
        buf.writeUtf(p.statusText(), 256);
        buf.writeBoolean(p.running());
        buf.writeUtf(p.selectedRecipeId(), 128);
        buf.writeBoolean(p.hasWorker());
        if (p.hasWorker() && p.workerId() != null) buf.writeUUID(p.workerId());
        buf.writeUtf(p.workerName(), 128);
        buf.writeBoolean(p.hasWaiter());
        if (p.hasWaiter() && p.waiterId() != null) buf.writeUUID(p.waiterId());
        buf.writeUtf(p.waiterName(), 128);
        buf.writeBoolean(p.hasBuildingBounds());
        buf.writeBlockPos(p.boundsMin());
        buf.writeBlockPos(p.boundsMax());
        buf.writeBoolean(p.integrityAvailable());
        buf.writeDouble(p.integrityPercent());
        buf.writeVarInt(p.integrityRepairableBlocks());
        buf.writeVarInt(p.integrityManualRepairBlocks());
        buf.writeDouble(p.integrityRepairCost());
        buf.writeVarInt(p.pointMarkers().size());
        for (PointMarkerEntry m : p.pointMarkers()) m.encode(buf);
        buf.writeVarInt(p.recipes().size());
        for (RecipeEntry r : p.recipes()) r.encode(buf);
        buf.writeVarInt(p.selectedCookItems().size());
        for (String s : p.selectedCookItems()) buf.writeUtf(s, 128);
        buf.writeBoolean(p.autoRestock());
        buf.writeUtf(p.waiterType(), 16);
        buf.writeVarInt(p.maidWaiters().size());
        for (MaidEntry m : p.maidWaiters()) {
            buf.writeUUID(m.uuid());
            buf.writeUtf(m.name(), 64);
        }
    }

    public static RestaurantControlBoxOpenResponsePacket decode(RegistryFriendlyByteBuf buf) {
        BlockPos boxPos = buf.readBlockPos();
        boolean hasBuilding = buf.readBoolean();
        String buildingName = buf.readUtf(128);
        boolean definitionValid = buf.readBoolean();
        String definitionName = buf.readUtf(128);
        String statusKey = buf.readUtf(128);
        String statusText = buf.readUtf(256);
        boolean running = buf.readBoolean();
        String selectedRecipeId = buf.readUtf(128);
        boolean hasWorker = buf.readBoolean();
        UUID workerId = hasWorker ? buf.readUUID() : null;
        String workerName = buf.readUtf(128);
        boolean hasWaiter = buf.readBoolean();
        UUID waiterId = hasWaiter ? buf.readUUID() : null;
        String waiterName = buf.readUtf(128);
        boolean hasBuildingBounds = buf.readBoolean();
        BlockPos boundsMin = buf.readBlockPos();
        BlockPos boundsMax = buf.readBlockPos();
        boolean integrityAvailable = buf.readBoolean();
        double integrityPercent = buf.readDouble();
        int integrityRepairableBlocks = buf.readVarInt();
        int integrityManualRepairBlocks = buf.readVarInt();
        double integrityRepairCost = buf.readDouble();
        int markerCount = buf.readVarInt();
        List<PointMarkerEntry> markers = new ArrayList<>();
        for (int i = 0; i < markerCount; i++) markers.add(PointMarkerEntry.decode(buf));
        int recipeCount = buf.readVarInt();
        List<RecipeEntry> recipes = new ArrayList<>();
        for (int i = 0; i < recipeCount; i++) recipes.add(RecipeEntry.decode(buf));
        int cookCount = buf.readVarInt();
        Set<String> selectedCookItems = new HashSet<>();
        for (int i = 0; i < cookCount; i++) selectedCookItems.add(buf.readUtf(128));
        boolean autoRestock = buf.readBoolean();
        String waiterType = buf.readUtf(16);
        int maidListSize = buf.readVarInt();
        List<MaidEntry> maidWaiters = new ArrayList<>();
        for (int i = 0; i < maidListSize; i++) {
            maidWaiters.add(new MaidEntry(buf.readUUID(), buf.readUtf(64)));
        }
        return new RestaurantControlBoxOpenResponsePacket(boxPos, hasBuilding, buildingName,
                definitionValid, definitionName, statusKey, statusText, running, selectedRecipeId,
                hasWorker, workerId, workerName, hasWaiter, waiterId, waiterName, hasBuildingBounds, boundsMin, boundsMax,
                integrityAvailable, integrityPercent, integrityRepairableBlocks, integrityManualRepairBlocks,
                integrityRepairCost, List.copyOf(markers), List.copyOf(recipes), selectedCookItems, autoRestock,
                waiterType, List.copyOf(maidWaiters));
    }

    public static void handle(RestaurantControlBoxOpenResponsePacket p, IPayloadContext ctx) {
        ctx.enqueueWork(() -> RestaurantControlBoxBridge.open(p));
    }

    public record RecipeEntry(String id, String name, List<ItemEntry> inputs, List<ItemEntry> outputs) {
        private void encode(RegistryFriendlyByteBuf buf) {
            buf.writeUtf(id, 128);
            buf.writeUtf(name, 128);
            buf.writeVarInt(inputs.size());
            for (ItemEntry i : inputs) i.encode(buf);
            buf.writeVarInt(outputs.size());
            for (ItemEntry o : outputs) o.encode(buf);
        }
        private static RecipeEntry decode(RegistryFriendlyByteBuf buf) {
            String id = buf.readUtf(128);
            String name = buf.readUtf(128);
            int inCount = buf.readVarInt();
            List<ItemEntry> inputs = new ArrayList<>();
            for (int i = 0; i < inCount; i++) inputs.add(ItemEntry.decode(buf));
            int outCount = buf.readVarInt();
            List<ItemEntry> outputs = new ArrayList<>();
            for (int i = 0; i < outCount; i++) outputs.add(ItemEntry.decode(buf));
            return new RecipeEntry(id, name, List.copyOf(inputs), List.copyOf(outputs));
        }
    }

    public record ItemEntry(String itemId, String potionId, int count, String connector, String itemSpec) {
        public ItemEntry(String itemId, int count) { this(itemId, "", count, "", ""); }
        private void encode(RegistryFriendlyByteBuf buf) {
            buf.writeUtf(itemId, 128);
            buf.writeUtf(potionId, 128);
            buf.writeVarInt(count);
            buf.writeUtf(connector, 8);
            buf.writeUtf(itemSpec, 4096);
        }
        private static ItemEntry decode(RegistryFriendlyByteBuf buf) {
            return new ItemEntry(buf.readUtf(128), buf.readUtf(128), buf.readVarInt(), buf.readUtf(8), buf.readUtf(4096));
        }
    }

    public record PointMarkerEntry(String id, String kind, BlockPos pos, int color) {
        private void encode(RegistryFriendlyByteBuf buf) {
            buf.writeUtf(id, 128);
            buf.writeUtf(kind, 64);
            buf.writeBlockPos(pos);
            buf.writeInt(color);
        }
        private static PointMarkerEntry decode(RegistryFriendlyByteBuf buf) {
            return new PointMarkerEntry(buf.readUtf(128), buf.readUtf(64), buf.readBlockPos(), buf.readInt());
        }
    }

    public record MaidEntry(UUID uuid, String name) {
    }
}
