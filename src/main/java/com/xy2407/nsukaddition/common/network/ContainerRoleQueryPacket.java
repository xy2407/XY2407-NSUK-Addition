package com.xy2407.nsukaddition.common.network;

import com.xy2407.nsukaddition.common.network.ContainerRoleResponsePacket.RoleEntry;
import common.cn.kafei.simukraft.building.PlacedBuildingRecord;
import common.cn.kafei.simukraft.building.PlacedBuildingService;
import common.cn.kafei.simukraft.commercial.CommercialDefinition;
import common.cn.kafei.simukraft.commercial.CommercialDefinitionLoader;
import common.cn.kafei.simukraft.industrial.IndustrialCoordinateResolver;
import common.cn.kafei.simukraft.industrial.IndustrialDefinition;
import common.cn.kafei.simukraft.industrial.IndustrialDefinition.ContainerDefinition;
import common.cn.kafei.simukraft.industrial.IndustrialDefinitionLoader;
import com.xy2407.nsukaddition.common.breeding.BreedingControlBoxService;
import com.xy2407.nsukaddition.common.breeding.BreedingDefinition;
import com.xy2407.nsukaddition.common.breeding.BreedingDefinitionLoader;
import com.xy2407.nsukaddition.common.cooking.RestaurantControlBoxService;
import com.xy2407.nsukaddition.common.cooking.RestaurantDefinition;
import com.xy2407.nsukaddition.common.cooking.RestaurantDefinitionLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;

/** 容器角色查询包，服务端收集该位置所有角色后批量返回。 */
public record ContainerRoleQueryPacket(BlockPos containerPos) implements CustomPacketPayload {

    public static final Type<ContainerRoleQueryPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath("xy2407_nsuk_addition", "container_role_query"));
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

            List<RoleEntry> all = new ArrayList<>();
            collectIndustrial(level, pos, all);
            collectCommercial(level, pos, all);
            collectBreeding(level, pos, all);
            collectRestaurant(level, pos, all);

            if (!all.isEmpty()) {
                ctx.reply(new ContainerRoleResponsePacket(pos, all));
            }
        });
    }

    private static void collectIndustrial(ServerLevel level, BlockPos pos, List<RoleEntry> out) {
        PlacedBuildingRecord building = PlacedBuildingService.findByContainedPosAndCategory(
                level, pos, "industry", "industrial");
        if (building == null) return;

        IndustrialDefinitionLoader.LoadResult result = IndustrialDefinitionLoader.loadForBuilding(building);
        if (!result.valid() || result.definition() == null) return;
        IndustrialDefinition def = result.definition();

        for (var entry : def.containers().entrySet()) {
            String id = entry.getKey();
            ContainerDefinition container = entry.getValue();
            if (!"structure_pos".equalsIgnoreCase(container.type())) continue;
            List<BlockPos> resolved = IndustrialCoordinateResolver.resolvePositions(building, container.positions());
            for (BlockPos rp : resolved) {
                if (rp.equals(pos)) {
                    BlockPos rel = pos.subtract(building.worldOrigin());
                    out.add(new RoleEntry(id, "industrial", rel.getX(), rel.getY(), rel.getZ()));
                }
            }
        }
    }

    private static void collectCommercial(ServerLevel level, BlockPos pos, List<RoleEntry> out) {
        PlacedBuildingRecord building = PlacedBuildingService.findByContainedPosAndCategory(
                level, pos, "commercial", "commerce");
        if (building == null) return;

        CommercialDefinitionLoader.LoadResult result = CommercialDefinitionLoader.loadForBuilding(building);
        if (!result.valid() || result.definition() == null) return;
        CommercialDefinition def = result.definition();

        for (var entry : def.containers().entrySet()) {
            String id = entry.getKey();
            CommercialDefinition.ContainerDefinition container = entry.getValue();
            if (!"structure_pos".equalsIgnoreCase(container.type())) continue;
            List<BlockPos> resolved = IndustrialCoordinateResolver.resolvePositions(building, container.positions());
            for (BlockPos rp : resolved) {
                if (rp.equals(pos)) {
                    BlockPos rel = pos.subtract(building.worldOrigin());
                    out.add(new RoleEntry(id, "commercial", rel.getX(), rel.getY(), rel.getZ()));
                }
            }
        }
    }

    private static void collectBreeding(ServerLevel level, BlockPos pos, List<RoleEntry> out) {
        PlacedBuildingRecord building = BreedingControlBoxService.resolveBuilding(level, pos);
        if (building == null) return;

        BreedingDefinitionLoader.LoadResult result = BreedingDefinitionLoader.loadForBuilding(building);
        if (!result.valid() || result.definition() == null) return;
        BreedingDefinition def = result.definition();

        for (var entry : def.containers().entrySet()) {
            String id = entry.getKey();
            BreedingDefinition.ContainerDefinition container = entry.getValue();
            List<BlockPos> resolved;
            if ("structure_pos".equalsIgnoreCase(container.type())) {
                resolved = IndustrialCoordinateResolver.resolvePositions(building, container.positions());
            } else {
                continue;
            }
            for (BlockPos rp : resolved) {
                if (rp.equals(pos)) {
                    BlockPos rel = pos.subtract(building.worldOrigin());
                    out.add(new RoleEntry(id, "breeding", rel.getX(), rel.getY(), rel.getZ()));
                }
            }
        }
    }

    private static void collectRestaurant(ServerLevel level, BlockPos pos, List<RoleEntry> out) {
        PlacedBuildingRecord building = RestaurantControlBoxService.resolveBuilding(level, pos);
        if (building == null) return;

        RestaurantDefinitionLoader.LoadResult result = RestaurantDefinitionLoader.loadForBuilding(building);
        if (!result.valid() || result.definition() == null) return;
        RestaurantDefinition def = result.definition();

        for (var entry : def.containers().entrySet()) {
            String id = entry.getKey();
            RestaurantDefinition.ContainerDefinition container = entry.getValue();
            if (!"structure_pos".equalsIgnoreCase(container.type())) continue;
            List<BlockPos> resolved = IndustrialCoordinateResolver.resolvePositions(building, container.positions());
            for (BlockPos rp : resolved) {
                if (rp.equals(pos)) {
                    BlockPos rel = pos.subtract(building.worldOrigin());
                    out.add(new RoleEntry(id, "restaurant", rel.getX(), rel.getY(), rel.getZ()));
                }
            }
        }
    }

}
