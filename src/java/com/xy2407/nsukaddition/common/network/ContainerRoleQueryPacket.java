package com.xy2407.nsukaddition.common.network;

import com.xy2407.nsukaddition.NsukAddition;
import com.xy2407.nsukaddition.common.network.ContainerRoleResponsePacket.ContainerRole;
import common.cn.kafei.simukraft.building.BuildingBlockData;
import common.cn.kafei.simukraft.building.PlacedBuildingRecord;
import common.cn.kafei.simukraft.building.PlacedBuildingService;
import common.cn.kafei.simukraft.industrial.IndustrialCoordinateResolver;
import common.cn.kafei.simukraft.industrial.IndustrialDefinition;
import common.cn.kafei.simukraft.industrial.IndustrialDefinitionLoader;
import common.cn.kafei.simukraft.industrial.IndustrialDefinition.ContainerDefinition;
import common.cn.kafei.simukraft.commercial.CommercialDefinition;
import common.cn.kafei.simukraft.commercial.CommercialDefinitionLoader;
import com.xy2407.nsukaddition.common.breeding.BreedingControlBoxService;
import com.xy2407.nsukaddition.common.breeding.BreedingDefinition;
import com.xy2407.nsukaddition.common.breeding.BreedingDefinitionLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.List;

public record ContainerRoleQueryPacket(BlockPos containerPos) implements CustomPacketPayload {

    public static final Type<ContainerRoleQueryPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(NsukAddition.MOD_ID, "container_role_query"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ContainerRoleQueryPacket> STREAM_CODEC =
            StreamCodec.of((buf, p) -> buf.writeBlockPos(p.containerPos),
                    buf -> new ContainerRoleQueryPacket(buf.readBlockPos()));

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(ContainerRoleQueryPacket p, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer sp)) return;
            ServerLevel level = sp.serverLevel();
            BlockPos pos = p.containerPos;
            if (!level.isLoaded(pos) || sp.blockPosition().distSqr(pos) > 64 * 64) return;

            ContainerRole role = tryIndustrial(level, pos);
            if (role == null) role = tryCommercial(level, pos);
            if (role == null) role = tryBreeding(level, pos);

            if (role != null) {
                ContainerRoleResponsePacket response = new ContainerRoleResponsePacket(
                        pos, role.role(), role.boxType(), role.relativeX(), role.relativeY(), role.relativeZ());
                ctx.reply(response);
            }
        });
    }

    private static ContainerRole tryIndustrial(ServerLevel level, BlockPos pos) {
        PlacedBuildingRecord building = PlacedBuildingService.findByContainedPosAndCategory(
                level, pos, "industry", "industrial");
        if (building == null) return null;
        BoxPos boxPos = findControlBoxInBuilding(level, building,
                common.cn.kafei.simukraft.registry.ModBlocks.INDUSTRIAL_CONTROL_BOX.get());
        if (boxPos == null) return null;

        IndustrialDefinitionLoader.LoadResult result = IndustrialDefinitionLoader.loadForBuilding(building);
        if (!result.valid() || result.definition() == null) return null;
        IndustrialDefinition def = result.definition();

        for (var entry : def.containers().entrySet()) {
            String id = entry.getKey();
            ContainerDefinition container = entry.getValue();
            if (!"structure_pos".equalsIgnoreCase(container.type())) continue;
            List<BlockPos> resolved = IndustrialCoordinateResolver.resolvePositions(building, container.positions());
            for (int i = 0; i < resolved.size(); i++) {
                if (resolved.get(i).equals(pos)) {
                    BlockPos rel = pos.subtract(building.worldOrigin());
                    return new ContainerRole(id, "industrial", rel.getX(), rel.getY(), rel.getZ());
                }
            }
        }
        return null;
    }

    private static ContainerRole tryCommercial(ServerLevel level, BlockPos pos) {
        PlacedBuildingRecord building = PlacedBuildingService.findByContainedPosAndCategory(
                level, pos, "commercial", "commerce");
        if (building == null) return null;
        BoxPos boxPos = findControlBoxInBuilding(level, building,
                common.cn.kafei.simukraft.registry.ModBlocks.COMMERCIAL_CONTROL_BOX.get());
        if (boxPos == null) return null;

        CommercialDefinitionLoader.LoadResult result = CommercialDefinitionLoader.loadForBuilding(building);
        if (!result.valid() || result.definition() == null) return null;
        CommercialDefinition def = result.definition();

        for (var entry : def.containers().entrySet()) {
            String id = entry.getKey();
            CommercialDefinition.ContainerDefinition container = entry.getValue();
            if (!"structure_pos".equalsIgnoreCase(container.type())) continue;
            List<BlockPos> resolved = IndustrialCoordinateResolver.resolvePositions(building, container.positions());
            for (int i = 0; i < resolved.size(); i++) {
                if (resolved.get(i).equals(pos)) {
                    BlockPos rel = pos.subtract(building.worldOrigin());
                    return new ContainerRole(id, "commercial", rel.getX(), rel.getY(), rel.getZ());
                }
            }
        }
        return null;
    }

    private static ContainerRole tryBreeding(ServerLevel level, BlockPos pos) {
        PlacedBuildingRecord building = BreedingControlBoxService.resolveBuilding(level, pos);
        if (building == null) return null;

        BoxPos boxPos = findControlBoxInBuilding(level, building,
                com.xy2407.nsukaddition.common.registry.ModBlocks.BREEDING_CONTROL_BOX.get());
        if (boxPos == null) return null;

        BreedingDefinitionLoader.LoadResult result = BreedingDefinitionLoader.loadForBuilding(building);
        if (!result.valid() || result.definition() == null) return null;
        BreedingDefinition def = result.definition();

        for (var entry : def.containers().entrySet()) {
            String id = entry.getKey();
            BreedingDefinition.ContainerDefinition container = entry.getValue();
            List<BlockPos> resolved;
            if ("structure_pos".equalsIgnoreCase(container.type())) {
                resolved = IndustrialCoordinateResolver.resolvePositions(building, container.positions());
            } else if ("control_box_relative".equalsIgnoreCase(container.type())) {
                resolved = container.positions().stream()
                        .map(offset -> boxPos.pos().offset(common.cn.kafei.simukraft.building.BuildingTransform.rotatePosition(
                                offset, boxRotation(building.facing()))))
                        .map(BlockPos::immutable)
                        .toList();
            } else {
                continue;
            }
            for (int i = 0; i < resolved.size(); i++) {
                if (resolved.get(i).equals(pos)) {
                    BlockPos rel = pos.subtract(building.worldOrigin());
                    return new ContainerRole(id, "breeding", rel.getX(), rel.getY(), rel.getZ());
                }
            }
        }
        return null;
    }

    private static BoxPos findControlBoxInBuilding(ServerLevel level, PlacedBuildingRecord building,
                                                    net.minecraft.world.level.block.Block blockType) {
        for (BuildingBlockData blockData : building.blocks()) {
            BlockPos bp = building.worldOrigin().offset(blockData.relativePos());
            if (level.getBlockState(bp).is(blockType)) {
                return new BoxPos(bp.immutable());
            }
        }
        return null;
    }

    private static int boxRotation(String facing) {
        if (facing == null) return 0;
        return switch (facing.toLowerCase(java.util.Locale.ROOT)) {
            case "east" -> 90;
            case "south" -> 180;
            case "west" -> 270;
            default -> 0;
        };
    }

    private record BoxPos(BlockPos pos) {}
}
