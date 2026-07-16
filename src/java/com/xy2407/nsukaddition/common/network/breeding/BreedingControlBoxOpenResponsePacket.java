package com.xy2407.nsukaddition.common.network.breeding;

import com.xy2407.nsukaddition.NsukAddition;
import com.xy2407.nsukaddition.common.network.clientbound.BreedingControlBoxBridge;
import com.xy2407.nsukaddition.common.breeding.BreedingControlBoxView;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** 繁育控制箱打开响应网络包，服务端返回繁育控制箱完整界面数据供客户端渲染。 */
@SuppressWarnings("null")
public record BreedingControlBoxOpenResponsePacket(BlockPos boxPos,
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
                                                    boolean hasBuildingBounds,
                                                    BlockPos boundsMin,
                                                    BlockPos boundsMax,
                                                    boolean integrityAvailable,
                                                    double integrityPercent,
                                                    int integrityRepairableBlocks,
                                                    int integrityManualRepairBlocks,
                                                    double integrityRepairCost,
                                                    List<PointMarkerEntry> pointMarkers,
                                                    List<RecipeEntry> recipes) implements CustomPacketPayload {

    public static final Type<BreedingControlBoxOpenResponsePacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(NsukAddition.MOD_ID, "breeding_control_box_open_response"));
    public static final StreamCodec<RegistryFriendlyByteBuf, BreedingControlBoxOpenResponsePacket> STREAM_CODEC =
            StreamCodec.of(BreedingControlBoxOpenResponsePacket::encode, BreedingControlBoxOpenResponsePacket::decode);

    public static BreedingControlBoxOpenResponsePacket from(BreedingControlBoxView view) {
        return new BreedingControlBoxOpenResponsePacket(
                view.boxPos(), view.hasBuilding(), view.buildingName(),
                view.definitionValid(), view.definitionName(),
                view.statusKey(), view.statusText(), view.running(), view.selectedRecipeId(),
                view.hasWorker(), view.workerId(), view.workerName(),
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
                        .toList()
        );
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void encode(RegistryFriendlyByteBuf buf, BreedingControlBoxOpenResponsePacket p) {
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
    }

    public static BreedingControlBoxOpenResponsePacket decode(RegistryFriendlyByteBuf buf) {
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
        return new BreedingControlBoxOpenResponsePacket(boxPos, hasBuilding, buildingName,
                definitionValid, definitionName, statusKey, statusText, running, selectedRecipeId,
                hasWorker, workerId, workerName, hasBuildingBounds, boundsMin, boundsMax,
                integrityAvailable, integrityPercent, integrityRepairableBlocks, integrityManualRepairBlocks,
                integrityRepairCost, List.copyOf(markers), List.copyOf(recipes));
    }

    public static void handle(BreedingControlBoxOpenResponsePacket p, IPayloadContext ctx) {
        ctx.enqueueWork(() -> BreedingControlBoxBridge.open(p));
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
}
