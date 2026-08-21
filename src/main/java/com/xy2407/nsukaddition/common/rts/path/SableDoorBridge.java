package com.xy2407.nsukaddition.common.rts.path;

import common.cn.kafei.simukraft.citizen.CitizenTeleportService;
import common.cn.kafei.simukraft.entity.CitizenEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.Vec3;

import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Sable 物理结构木门桥接：让 SimuKraft 市民能开/关结构门，门追踪独立于原版。
 */
public final class SableDoorBridge {

    private static final double DOOR_INTERACT_RANGE_SQR = 9.0D;
    private static final double DOOR_CLEAR_RANGE_SQR = 2.25D;
    private static final double DOOR_DOORWAY_RANGE_SQR = 1.44D;
    private static final int MAX_TRACKED_DOORS = 128;

    private SableDoorBridge() {
    }

    private record OpenedDoor(UUID citizenId, long openedAt) {
    }

    private static final Map<Long, OpenedDoor> openedDoors = new ConcurrentHashMap<>();

    public record ResolvedDoor(Level ownerLevel, BlockPos lowerPlotPos, BlockState state, DoorBlock doorBlock) {
    }

    public static boolean tryOpenWoodenDoor(ServerLevel level, CitizenEntity citizen, BlockPos waypointPos) {
        if (level == null || citizen == null || waypointPos == null) return false;
        if (citizen.position().distanceToSqr(Vec3.atCenterOf(waypointPos)) > DOOR_INTERACT_RANGE_SQR + 4.0D) return false;
        ResolvedDoor door = resolveWoodenDoor(level, waypointPos);
        if (door == null || door.state().getValue(DoorBlock.OPEN)) return false;
        if (citizen.position().distanceToSqr(Vec3.atCenterOf(waypointPos)) > DOOR_INTERACT_RANGE_SQR) return false;
        door.doorBlock().setOpen(citizen, door.ownerLevel(), door.state(), door.lowerPlotPos(), true);
        track(level, citizen, waypointPos);
        return true;
    }

    public static void processOpenedDoors(ServerLevel level, Set<UUID> activeCitizenIds) {
        if (openedDoors.isEmpty()) return;
        for (Iterator<Map.Entry<Long, OpenedDoor>> iterator = openedDoors.entrySet().iterator(); iterator.hasNext();) {
            Map.Entry<Long, OpenedDoor> entry = iterator.next();
            BlockPos worldPos = BlockPos.of(entry.getKey());
            ResolvedDoor door = resolveWoodenDoor(level, worldPos);
            if (door == null || !door.state().getValue(DoorBlock.OPEN)) {
                iterator.remove();
                continue;
            }
            CitizenEntity opener = CitizenTeleportService.findCitizenEntity(level, entry.getValue().citizenId());
            boolean cleared = opener == null
                    || horizontalDistanceSqr(opener.position(), Vec3.atCenterOf(worldPos)) > DOOR_CLEAR_RANGE_SQR;
            if (!cleared) continue;
            if (isOtherCitizenInDoorway(level, activeCitizenIds, worldPos, entry.getValue().citizenId())) continue;
            door.doorBlock().setOpen(null, door.ownerLevel(), door.state(), door.lowerPlotPos(), false);
            iterator.remove();
        }
    }

    public static ResolvedDoor resolveWoodenDoor(ServerLevel level, BlockPos worldPos) {
        if (!SableStructureReader.isAvailable() || level == null || worldPos == null) return null;
        SableStructureReader.SubLevelBlock block = SableStructureReader.getSubLevelBlockAt(level, worldPos);
        if (block == null) return null;
        BlockState state = block.state();
        if (!(state.getBlock() instanceof DoorBlock doorBlock)
                || !state.hasProperty(DoorBlock.HALF)) {
            return null;
        }
        if (state.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER) {
            return new ResolvedDoor(block.ownerLevel(), block.plotPos(), state, doorBlock);
        }
        SableStructureReader.SubLevelBlock lower = SableStructureReader.getSubLevelBlockAt(level, worldPos.below());
        if (lower == null) return null;
        BlockState lowerState = lower.state();
        if (!(lowerState.getBlock() instanceof DoorBlock lowerDoor)
                || !lowerState.hasProperty(DoorBlock.HALF)
                || lowerState.getValue(DoorBlock.HALF) != DoubleBlockHalf.LOWER) {
            return null;
        }
        return new ResolvedDoor(lower.ownerLevel(), lower.plotPos(), lowerState, lowerDoor);
    }

    private static void track(ServerLevel level, CitizenEntity citizen, BlockPos worldPos) {
        if (openedDoors.size() >= MAX_TRACKED_DOORS) {
            Long oldestKey = null;
            long oldestAt = Long.MAX_VALUE;
            for (Map.Entry<Long, OpenedDoor> entry : openedDoors.entrySet()) {
                if (entry.getValue().openedAt() < oldestAt) {
                    oldestAt = entry.getValue().openedAt();
                    oldestKey = entry.getKey();
                }
            }
            if (oldestKey != null) openedDoors.remove(oldestKey);
        }
        openedDoors.put(worldPos.asLong(), new OpenedDoor(citizen.getUUID(), level.getGameTime()));
    }

    private static boolean isOtherCitizenInDoorway(ServerLevel level, Set<UUID> activeCitizenIds, BlockPos pos, UUID excludeId) {
        Vec3 center = Vec3.atCenterOf(pos);
        for (UUID id : activeCitizenIds) {
            if (id.equals(excludeId)) continue;
            CitizenEntity other = CitizenTeleportService.findCitizenEntity(level, id);
            if (other != null && horizontalDistanceSqr(other.position(), center) <= DOOR_DOORWAY_RANGE_SQR) {
                return true;
            }
        }
        return false;
    }

    private static double horizontalDistanceSqr(Vec3 a, Vec3 b) {
        double dx = a.x - b.x;
        double dz = a.z - b.z;
        return dx * dx + dz * dz;
    }
}