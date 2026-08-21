package com.xy2407.nsukaddition.server.rts;

import com.xy2407.nsukaddition.common.breeding.BreedingConstants;
import com.xy2407.nsukaddition.common.cooking.RestaurantConstants;
import common.cn.kafei.simukraft.building.BuildingBlockData;
import common.cn.kafei.simukraft.building.BuildingBlockPlacementService;
import common.cn.kafei.simukraft.building.BuildingPoiInstance;
import common.cn.kafei.simukraft.building.BuildingTransform;
import common.cn.kafei.simukraft.building.MedicalBedPoiService;
import common.cn.kafei.simukraft.building.PlacedBuildingRecord;
import common.cn.kafei.simukraft.building.PlacedBuildingService;
import common.cn.kafei.simukraft.building.ResidentialBedPoiService;
import common.cn.kafei.simukraft.citizen.CitizenData;
import common.cn.kafei.simukraft.citizen.CitizenManager;
import common.cn.kafei.simukraft.citizen.CitizenService;
import common.cn.kafei.simukraft.city.CityService;
import common.cn.kafei.simukraft.city.poi.CityPoiData;
import common.cn.kafei.simukraft.city.poi.CityPoiManager;
import common.cn.kafei.simukraft.commercial.CommercialConstants;
import common.cn.kafei.simukraft.farmland.FarmlandBoxService;
import common.cn.kafei.simukraft.industrial.IndustrialConstants;
import common.cn.kafei.simukraft.job.CitizenEmploymentService;
import common.cn.kafei.simukraft.logistics.LogisticsConstants;
import common.cn.kafei.simukraft.medical.MedicalControlBoxService;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * RTS 建筑迁移服务:清除原位置方块与旧库数据 → 新位置放置并写入新库数据 → 同步住客/工人/POI。
 * 修复要点：
 * 1. 容器等带方块实体的方块：迁移前读取世界实际 NBT（含物品），新位置写回，避免爆成掉落物；
 * 2. 清旧方块用 removeBlockEntity 避免容器 onRemove 掉落物品；
 * 3. 住客住宅 POI 使用原 UUID 迁移，保证 homeId 关联不丢失；
 * 4. 工人岗位按世界坐标迁移到新位置。
 */
@SuppressWarnings("null")
public final class RtsBuildingMoveService {

    private record SourceRole(String sourceType, String role) {
    }

    private RtsBuildingMoveService() {
    }

    public static boolean moveBuilding(ServerLevel level, ServerPlayer player, UUID buildingId,
                                       BlockPos newOrigin, int rotation) {
        PlacedBuildingRecord old = findById(level, buildingId);
        if (old == null) {
            return false;
        }
        UUID cityId = old.cityId();
        if (cityId == null || !CityService.canManageCity(level, cityId, player.getUUID())) {
            return false;
        }
        String dimId = old.dimensionId();
        String category = old.category();
        String buildingFile = old.buildingFileName();
        String displayName = old.displayName();
        String amount = old.amount();
        String structureFile = old.structureFileName();
        BlockPos oldOrigin = old.worldOrigin() != null ? old.worldOrigin() : old.minPos();
        int originalRotation = BuildingTransform.rotationDegreesFromFacing(old.facing());
        int deltaRotation = Math.floorMod(rotation - originalRotation, 360);

        if ("cooking".equals(category)) {
            BlockPos boxPos = resolveControlBoxPos(level, old);
            if (boxPos != null) {
                com.xy2407.nsukaddition.common.cooking.RestaurantDiningService.cleanupForBox(level, boxPos);
            }
        }

        CityPoiManager poiManager = CityPoiManager.get(level);
        Map<BuildingPoiInstance, UUID> poiIdMap = new HashMap<>();
        List<ResidentReloc> relocations = new ArrayList<>();
        for (BuildingPoiInstance poi : old.poiInstances()) {
            UUID poiId = registeredPoiId(level, poi);
            if (poiId == null) {
                continue;
            }
            poiIdMap.put(poi, poiId);
            CityPoiData registered = poiManager.getPoi(poiId);
            if (registered != null) {
                poiManager.deactivatePoi(poiId);
            }
            for (CitizenData citizen : CitizenManager.get(level).allCitizens()) {
                if (!citizen.dead() && poiId.equals(citizen.homeId())) {
                    relocations.add(new ResidentReloc(citizen.uuid(), poiId));
                }
            }
        }

        List<MovedBlock> moved = captureWorldState(level, old);
        if (moved.isEmpty()) {
            return false;
        }

        for (MovedBlock m : moved) {
            BlockPos oldPos = oldOrigin.offset(m.relative());
            if (oldPos == null) {
                continue;
            }
            BlockEntity be = level.getBlockEntity(oldPos);
            if (be != null) {
                level.removeBlockEntity(oldPos);
            }
            level.setBlock(oldPos, Blocks.AIR.defaultBlockState(), 2);
        }
        ResidentialBedPoiService.removeRecordedBeds(level, old);
        MedicalBedPoiService.removeRecordedBeds(level, old);
        PlacedBuildingService.unregister(level, buildingId);

        List<BuildingBlockData> placedBlocks = new ArrayList<>();
        for (MovedBlock m : moved) {
            BlockPos newPos = newOrigin.offset(BuildingTransform.rotatePosition(m.relative(), deltaRotation));
            if (newPos == null) {
                continue;
            }
            net.minecraft.world.level.block.state.BlockState rotatedState =
                    BuildingTransform.rotateState(m.state(), deltaRotation);
            level.setBlock(newPos, rotatedState, 3);
            placedBlocks.add(new BuildingBlockData(newPos, rotatedState, newPos, m.blockEntityData()));
            if (m.blockEntityData() != null) {
                BuildingBlockPlacementService.applyBlockEntityData(level, newPos, m.blockEntityData());
            }
        }
        if (placedBlocks.isEmpty()) {
            return false;
        }
        BlockPos minPos = newOrigin;
        BlockPos maxPos = newOrigin;
        for (BuildingBlockData block : placedBlocks) {
            BlockPos p = block.relativePos();
            minPos = minPos.offset(Math.min(0, p.getX() - minPos.getX()), Math.min(0, p.getY() - minPos.getY()), Math.min(0, p.getZ() - minPos.getZ()));
            maxPos = maxPos.offset(Math.max(0, p.getX() - maxPos.getX()), Math.max(0, p.getY() - maxPos.getY()), Math.max(0, p.getZ() - maxPos.getZ()));
        }

        List<BuildingPoiInstance> newPois = new ArrayList<>();
        for (BuildingPoiInstance poi : old.poiInstances()) {
            BlockPos newWorld = newOrigin.offset(BuildingTransform.rotatePosition(poi.worldPos().subtract(oldOrigin), deltaRotation));
            newPois.add(new BuildingPoiInstance(poi.key(), poi.poiType(), poi.capacity(), newWorld));
            UUID poiId = poiIdMap.getOrDefault(poi, UUID.nameUUIDFromBytes(poi.key().getBytes()));
            poiManager.registerPoi(poiId, cityId, newWorld, poi.poiType(), poi.capacity());
        }

        PlacedBuildingRecord newRec = new PlacedBuildingRecord(
                buildingId, cityId, dimId, category, buildingFile, displayName, amount, structureFile,
                BuildingTransform.directionFromRotation(rotation).getSerializedName(),
                newOrigin, BlockPos.ZERO, minPos, maxPos, System.currentTimeMillis(),
                placedBlocks, old.poiDefinitions(), newPois, old.unitDefinitions(), old.unitInstances());
        PlacedBuildingService.register(level, newRec);
        ResidentialBedPoiService.addRecordedBeds(level, newRec);
        MedicalBedPoiService.addRecordedBeds(level, newRec);

        for (ResidentReloc r : relocations) {
            CitizenService.setHome(level, r.citizenId(), r.poiId());
        }
        if (cityId != null) {
            common.cn.kafei.simukraft.citizen.CitizenHousingService.fillVacantHomes(level, cityId);
        }

        updateWorkers(level, old, oldOrigin, newOrigin, deltaRotation, category);
        return true;
    }

    private static List<MovedBlock> captureWorldState(ServerLevel level, PlacedBuildingRecord old) {
        BlockPos origin = old.worldOrigin() != null ? old.worldOrigin() : old.minPos();
        int minX = old.minPos().getX(), minY = old.minPos().getY(), minZ = old.minPos().getZ();
        int maxX = old.maxPos().getX(), maxY = old.maxPos().getY(), maxZ = old.maxPos().getZ();
        List<MovedBlock> result = new ArrayList<>();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int y = minY; y <= maxY; y++) {
            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    cursor.set(x, y, z);
                    net.minecraft.world.level.block.state.BlockState state = level.getBlockState(cursor);
                    if (state == null || state.isAir()) {
                        continue;
                    }
                    CompoundTag nbt = null;
                    BlockEntity be = level.getBlockEntity(cursor);
                    if (be != null) {
                        try {
                            nbt = be.saveWithFullMetadata(level.registryAccess());
                        } catch (RuntimeException e) {
                            nbt = null;
                        }
                    }
                    result.add(new MovedBlock(cursor.immutable().subtract(origin), state, nbt));
                }
            }
        }
        return result;
    }

    private record MovedBlock(BlockPos relative,
                              net.minecraft.world.level.block.state.BlockState state,
                              CompoundTag blockEntityData) {
    }

    private static UUID registeredPoiId(ServerLevel level, BuildingPoiInstance poi) {
        CityPoiManager poiManager = CityPoiManager.get(level);
        CityPoiData registered = poiManager.getPoiAt(poi.worldPos());
        if (registered != null) {
            return registered.poiId();
        }
        return null;
    }

    private static void updateWorkers(ServerLevel level, PlacedBuildingRecord old, BlockPos oldOrigin,
                                      BlockPos newOrigin, int deltaRotation, String category) {
        List<SourceRole> roles = sourceRolesFor(category);
        if (roles.isEmpty() && !"cooking".equals(category)) {
            return;
        }
        for (CitizenData citizen : CitizenManager.get(level).allCitizens()) {
            if (citizen.dead() || citizen.workplacePos() == null
                    || !inside(citizen.workplacePos(), old.minPos(), old.maxPos())) {
                continue;
            }
            BlockPos oldBox = citizen.workplacePos();
            BlockPos newBox = newOrigin.offset(BuildingTransform.rotatePosition(oldBox.subtract(oldOrigin), deltaRotation));
            for (SourceRole sr : roles) {
                UUID oldWorkplaceId = CitizenEmploymentService.workplaceId(sr.sourceType(), sr.role(), oldBox);
                if (oldWorkplaceId.equals(citizen.workplaceId())) {
                    UUID newWorkplaceId = CitizenEmploymentService.workplaceId(sr.sourceType(), sr.role(), newBox);
                    CitizenService.applyEmployment(level, citizen.uuid(), citizen.jobType(), newWorkplaceId, newBox,
                            citizen.statusLabel() != null ? citizen.statusLabel() : "");
                    break;
                }
            }
        }

        if ("cooking".equals(category)) {
            BlockPos movedBox = resolveControlBoxPosAt(level, old, newOrigin, oldOrigin, deltaRotation);
            if (movedBox != null) {
                com.xy2407.nsukaddition.common.cooking.RestaurantBoxManager manager =
                        com.xy2407.nsukaddition.common.cooking.RestaurantBoxManager.get(level);
                com.xy2407.nsukaddition.common.cooking.RestaurantBoxData data = manager.get(movedBox);
                if (data != null) {
                    for (com.xy2407.nsukaddition.common.cooking.RestaurantBoxData.MaidEntry entry : data.maidWaiters()) {
                        net.minecraft.world.entity.LivingEntity maid =
                                com.xy2407.nsukaddition.common.compat.maid.MaidWaiterBridge.findMaid(level, entry.uuid());
                        if (maid != null) {
                            com.xy2407.nsukaddition.common.compat.maid.MaidWaiterBridge.assignRestaurantJob(level, maid, movedBox);
                        }
                    }
                }
            }
        }
    }

    private static BlockPos resolveControlBoxPosAt(ServerLevel level, PlacedBuildingRecord old,
                                                    BlockPos newOrigin, BlockPos oldOrigin, int deltaRotation) {
        BlockPos oldBox = resolveControlBoxPos(level, old);
        if (oldBox == null) {
            return null;
        }
        return newOrigin.offset(BuildingTransform.rotatePosition(oldBox.subtract(oldOrigin), deltaRotation));
    }

    private static BlockPos resolveControlBoxPos(ServerLevel level, PlacedBuildingRecord old) {
        String buildingId = old.buildingId().toString();
        com.xy2407.nsukaddition.common.cooking.RestaurantBoxManager manager =
                com.xy2407.nsukaddition.common.cooking.RestaurantBoxManager.get(level);
        for (com.xy2407.nsukaddition.common.cooking.RestaurantBoxData data : manager.all()) {
            if (buildingId.equals(data.buildingId())) {
                return data.boxPos();
            }
        }
        return null;
    }

    private static List<SourceRole> sourceRolesFor(String category) {
        if (category == null) {
            return List.of();
        }
        return switch (category) {
            case "industry" -> List.of(new SourceRole(IndustrialConstants.HIRE_SOURCE_TYPE, IndustrialConstants.HIRE_ROLE));
            case "commercial" -> List.of(new SourceRole(CommercialConstants.HIRE_SOURCE_TYPE, CommercialConstants.HIRE_ROLE));
            case "medical" -> List.of(new SourceRole(MedicalControlBoxService.HIRE_SOURCE_TYPE, MedicalControlBoxService.HIRE_ROLE));
            case "farmland" -> List.of(new SourceRole(FarmlandBoxService.HIRE_SOURCE_TYPE, FarmlandBoxService.HIRE_ROLE));
            case "logistics" -> List.of(new SourceRole(LogisticsConstants.SERVER_SOURCE_TYPE, LogisticsConstants.STORAGE_ROLE));
            case "breeding" -> List.of(new SourceRole(BreedingConstants.HIRE_SOURCE_TYPE, BreedingConstants.HIRE_ROLE));
            case "cooking" -> List.of(
                    new SourceRole(RestaurantConstants.HIRE_SOURCE_TYPE, RestaurantConstants.HIRE_ROLE_CHEF),
                    new SourceRole(RestaurantConstants.HIRE_SOURCE_TYPE, RestaurantConstants.HIRE_ROLE_WAITER));
            default -> List.of();
        };
    }

    private static PlacedBuildingRecord findById(ServerLevel level, UUID buildingId) {
        for (PlacedBuildingRecord record : PlacedBuildingService.getBuildings(level)) {
            if (buildingId.equals(record.buildingId())) {
                return record;
            }
        }
        return null;
    }

    private static boolean inside(BlockPos pos, BlockPos min, BlockPos max) {
        return pos.getX() >= Math.min(min.getX(), max.getX()) && pos.getX() <= Math.max(min.getX(), max.getX())
                && pos.getY() >= Math.min(min.getY(), max.getY()) && pos.getY() <= Math.max(min.getY(), max.getY())
                && pos.getZ() >= Math.min(min.getZ(), max.getZ()) && pos.getZ() <= Math.max(min.getZ(), max.getZ());
    }

    private record ResidentReloc(UUID citizenId, UUID poiId) {
    }
}