package com.xy2407.nsukaddition.common.storage;

import com.xy2407.nsukaddition.NsukAddition;
import com.xy2407.nsukaddition.mixin.simukraft.SimuSqliteStorageAccessor;
import common.cn.kafei.simukraft.building.BuildingPoiDefinition;
import common.cn.kafei.simukraft.building.BuildingTaskData;
import common.cn.kafei.simukraft.city.poi.CityPoiType;
import common.cn.kafei.simukraft.storage.SimuSqliteDatabase;
import common.cn.kafei.simukraft.storage.SimuSqliteStorage;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 建筑任务排队持久化：排队任务直接存 simukraft building_tasks 表(status=queued, task_id 主键多行)。
 * 同一建筑师可同时存在运行(1) + 排队(N) 多个任务，重启后由 building_tasks 全量恢复。
 */
@SuppressWarnings("null")
public final class BuildingTaskQueueStorage {

    private static final String STATUS_QUEUED = "queued";

    private BuildingTaskQueueStorage() {
    }

    private static SimuSqliteDatabase simuDb(ServerLevel level) {
        if (level == null || level.getServer() == null) {
            return null;
        }
        Map<MinecraftServer, SimuSqliteStorage> storages = SimuSqliteStorageAccessor.nsuk$getStorages();
        SimuSqliteStorage storage = storages.get(level.getServer());
        return storage == null ? null : ((SimuSqliteStorageAccessor) (Object) storage).nsuk$getDatabase();
    }

    public static void save(ServerLevel level, BuildingTaskData task) {
        if (level == null || task == null || task.taskId() == null) {
            return;
        }
        SimuSqliteDatabase db = simuDb(level);
        if (db == null) {
            return;
        }
        WriteBatchBuffer.submit("building_tasks", "building_task:" + task.taskId(), db::borrowConnection,
                connection -> upsertTask(connection, task));
    }

    private static void upsertTask(Connection connection, BuildingTaskData task) throws SQLException {
        try (PreparedStatement deletePois = connection.prepareStatement("DELETE FROM building_task_pois WHERE task_id = ?");
             PreparedStatement taskStatement = connection.prepareStatement(
                     "INSERT INTO building_tasks(task_id, citizen_id, city_id, dimension_id, build_box_x, build_box_y, build_box_z, category, building_file_name, display_name, amount, structure_file_name, origin_x, origin_y, origin_z, rotation_degrees, current_block_index, total_blocks, status, created_at, updated_at, replace_with_air) VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) ON CONFLICT(task_id) DO UPDATE SET citizen_id = excluded.citizen_id, city_id = excluded.city_id, dimension_id = excluded.dimension_id, build_box_x = excluded.build_box_x, build_box_y = excluded.build_box_y, build_box_z = excluded.build_box_z, category = excluded.category, building_file_name = excluded.building_file_name, display_name = excluded.display_name, amount = excluded.amount, structure_file_name = excluded.structure_file_name, origin_x = excluded.origin_x, origin_y = excluded.origin_y, origin_z = excluded.origin_z, rotation_degrees = excluded.rotation_degrees, current_block_index = excluded.current_block_index, total_blocks = excluded.total_blocks, status = excluded.status, created_at = excluded.created_at, updated_at = excluded.updated_at, replace_with_air = excluded.replace_with_air");
             PreparedStatement poiStatement = connection.prepareStatement("INSERT INTO building_task_pois(task_id, poi_key, poi_type, capacity) VALUES(?, ?, ?, ?)")) {
            deletePois.setString(1, task.taskId().toString());
            deletePois.executeUpdate();

            taskStatement.setString(1, task.taskId().toString());
            taskStatement.setString(2, task.citizenId().toString());
            setNullableString(taskStatement, 3, task.cityId() != null ? task.cityId().toString() : null);
            taskStatement.setString(4, task.dimensionId());
            taskStatement.setInt(5, task.buildBoxPos().getX());
            taskStatement.setInt(6, task.buildBoxPos().getY());
            taskStatement.setInt(7, task.buildBoxPos().getZ());
            taskStatement.setString(8, task.category());
            taskStatement.setString(9, task.buildingFileName());
            taskStatement.setString(10, task.displayName());
            taskStatement.setString(11, task.amount());
            taskStatement.setString(12, task.structureFileName());
            taskStatement.setInt(13, task.origin().getX());
            taskStatement.setInt(14, task.origin().getY());
            taskStatement.setInt(15, task.origin().getZ());
            taskStatement.setInt(16, task.rotationDegrees());
            taskStatement.setInt(17, task.currentBlockIndex());
            taskStatement.setInt(18, task.totalBlocks());
            taskStatement.setString(19, task.status());
            taskStatement.setLong(20, task.createdAt());
            taskStatement.setLong(21, task.updatedAt());
            taskStatement.setInt(22, task.replaceWithAir() ? 1 : 0);
            taskStatement.executeUpdate();

            for (BuildingPoiDefinition poi : task.poiDefinitions()) {
                poiStatement.setString(1, task.taskId().toString());
                poiStatement.setString(2, poi.id());
                poiStatement.setString(3, poi.poiType().name());
                poiStatement.setInt(4, poi.capacity());
                poiStatement.addBatch();
            }
            poiStatement.executeBatch();
        }
    }

    public static void flush(ServerLevel level, UUID taskId) {
        if (level == null || taskId == null) {
            return;
        }
        WriteBatchBuffer.flushEntry("building_tasks", "building_task:" + taskId);
    }

    public static void flushRunning(ServerLevel level, UUID citizenId) {
        if (level == null || citizenId == null) {
            return;
        }
        WriteBatchBuffer.flushEntry("building_task", "building_task:" + citizenId);
    }

    public static List<BuildingTaskData> loadAll(ServerLevel level) {
        List<BuildingTaskData> result = new ArrayList<>();
        if (level == null) {
            return result;
        }
        for (BuildingTaskData t : SimuSqliteStorage.loadBuildingTasks(level)) {
            if (STATUS_QUEUED.equalsIgnoreCase(t.status())) {
                result.add(t);
            }
        }
        result.sort(Comparator.comparingLong(BuildingTaskData::createdAt));
        return result;
    }

    public static void deleteByTaskId(ServerLevel level, UUID taskId) {
        if (level == null || taskId == null) {
            return;
        }
        SimuSqliteDatabase db = simuDb(level);
        if (db == null) {
            return;
        }
        WriteBatchBuffer.submitPriority("building_tasks", "building_task:" + taskId, () -> deleteTaskNow(db, taskId));
    }

    public static void deleteByCitizen(ServerLevel level, UUID citizenId) {
        if (level == null || citizenId == null) {
            return;
        }
        for (UUID taskId : loadTaskIdsByCitizen(level, citizenId)) {
            deleteByTaskId(level, taskId);
        }
    }

    public static void deleteByBoxPos(ServerLevel level, BlockPos boxPos) {
        if (level == null || boxPos == null) {
            return;
        }
        for (UUID taskId : loadTaskIdsByBoxPos(level, boxPos)) {
            deleteByTaskId(level, taskId);
        }
    }

    private static List<UUID> loadTaskIdsByCitizen(ServerLevel level, UUID citizenId) {
        List<UUID> ids = new ArrayList<>();
        if (level == null || citizenId == null) {
            return ids;
        }
        SimuSqliteDatabase db = simuDb(level);
        if (db == null) {
            return ids;
        }
        try (Connection connection = db.borrowConnection();
             PreparedStatement ps = connection.prepareStatement(
                     "SELECT task_id FROM building_tasks WHERE citizen_id = ? AND status = ?")) {
            ps.setString(1, citizenId.toString());
            ps.setString(2, STATUS_QUEUED);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    ids.add(UUID.fromString(rs.getString("task_id")));
                }
            }
        } catch (SQLException | IllegalArgumentException e) {
            NsukAddition.LOGGER.error("BuildingTaskQueueStorage: load queued ids by citizen {} failed", citizenId, e);
        }
        return ids;
    }

    private static List<UUID> loadTaskIdsByBoxPos(ServerLevel level, BlockPos boxPos) {
        List<UUID> ids = new ArrayList<>();
        if (level == null || boxPos == null) {
            return ids;
        }
        SimuSqliteDatabase db = simuDb(level);
        if (db == null) {
            return ids;
        }
        try (Connection connection = db.borrowConnection();
             PreparedStatement ps = connection.prepareStatement(
                     "SELECT task_id FROM building_tasks WHERE build_box_x = ? AND build_box_y = ? AND build_box_z = ? AND status = ?")) {
            ps.setInt(1, boxPos.getX());
            ps.setInt(2, boxPos.getY());
            ps.setInt(3, boxPos.getZ());
            ps.setString(4, STATUS_QUEUED);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    ids.add(UUID.fromString(rs.getString("task_id")));
                }
            }
        } catch (SQLException | IllegalArgumentException e) {
            NsukAddition.LOGGER.error("BuildingTaskQueueStorage: load queued ids by box {} failed", boxPos, e);
        }
        return ids;
    }

    public static void deleteActiveTask(ServerLevel level, UUID citizenId) {
        if (level == null || citizenId == null) {
            return;
        }
        BuildingTaskData active = SimuSqliteStorage.loadBuildingTask(level, citizenId);
        if (active != null) {
            deleteByTaskId(level, active.taskId());
        }
    }

    public static int countByCitizen(ServerLevel level, UUID citizenId) {
        if (level == null || citizenId == null) {
            return 0;
        }
        SimuSqliteDatabase db = simuDb(level);
        if (db == null) {
            return 0;
        }
        try (Connection connection = db.borrowConnection();
             PreparedStatement ps = connection.prepareStatement(
                     "SELECT COUNT(*) FROM building_tasks WHERE citizen_id = ?")) {
            ps.setString(1, citizenId.toString());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        } catch (SQLException e) {
            NsukAddition.LOGGER.error("BuildingTaskQueueStorage: count by citizen {} failed", citizenId, e);
            return 0;
        }
    }

    public static BuildingTaskData loadByTaskId(ServerLevel level, UUID taskId) {
        if (level == null || taskId == null) {
            return null;
        }
        SimuSqliteDatabase db = simuDb(level);
        if (db == null) {
            return null;
        }
        try (Connection connection = db.borrowConnection();
             PreparedStatement ps = connection.prepareStatement(
                     "SELECT * FROM building_tasks WHERE task_id = ?")) {
            ps.setString(1, taskId.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return readTask(connection, rs);
                }
            }
        } catch (SQLException | IllegalArgumentException e) {
            NsukAddition.LOGGER.error("BuildingTaskQueueStorage: load task {} failed", taskId, e);
        }
        return null;
    }

    public static List<BuildingTaskData> loadQueuedByCity(ServerLevel level, UUID cityId) {
        List<BuildingTaskData> result = new ArrayList<>();
        if (level == null || cityId == null) {
            return result;
        }
        SimuSqliteDatabase db = simuDb(level);
        if (db == null) {
            return result;
        }
        try (Connection connection = db.borrowConnection();
             PreparedStatement ps = connection.prepareStatement(
                     "SELECT * FROM building_tasks WHERE city_id = ? AND status = ? ORDER BY created_at")) {
            ps.setString(1, cityId.toString());
            ps.setString(2, STATUS_QUEUED);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(readTask(connection, rs));
                }
            }
        } catch (SQLException | IllegalArgumentException e) {
            NsukAddition.LOGGER.error("BuildingTaskQueueStorage: load queued by city {} failed", cityId, e);
        }
        return result;
    }

    private static BuildingTaskData readTask(Connection connection, ResultSet rs) throws SQLException {
        UUID taskId = UUID.fromString(rs.getString("task_id"));
        return new BuildingTaskData(
                taskId,
                UUID.fromString(rs.getString("citizen_id")),
                nullableUuid(rs.getString("city_id")),
                rs.getString("dimension_id"),
                new BlockPos(rs.getInt("build_box_x"), rs.getInt("build_box_y"), rs.getInt("build_box_z")),
                rs.getString("category"),
                rs.getString("building_file_name"),
                rs.getString("display_name"),
                rs.getString("amount"),
                rs.getString("structure_file_name"),
                new BlockPos(rs.getInt("origin_x"), rs.getInt("origin_y"), rs.getInt("origin_z")),
                rs.getInt("rotation_degrees"),
                rs.getInt("current_block_index"),
                rs.getInt("total_blocks"),
                rs.getString("status"),
                rs.getLong("created_at"),
                rs.getLong("updated_at"),
                loadPois(connection, taskId),
                rs.getInt("replace_with_air") != 0
        );
    }

    private static List<BuildingPoiDefinition> loadPois(Connection connection, UUID taskId) throws SQLException {
        List<BuildingPoiDefinition> pois = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT * FROM building_task_pois WHERE task_id = ?")) {
            ps.setString(1, taskId.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    try {
                        pois.add(new BuildingPoiDefinition(
                                rs.getString("poi_key"),
                                CityPoiType.valueOf(rs.getString("poi_type")),
                                rs.getInt("capacity")));
                    } catch (IllegalArgumentException e) {
                        NsukAddition.LOGGER.warn("BuildingTaskQueueStorage: 未知 POI 类型 {} task {}", rs.getString("poi_type"), taskId);
                    }
                }
            }
        }
        return pois;
    }

    private static UUID nullableUuid(String value) {
        return value == null || value.isBlank() ? null : UUID.fromString(value);
    }

    private static void setNullableString(PreparedStatement ps, int index, String value) throws SQLException {
        if (value == null) {
            ps.setNull(index, java.sql.Types.VARCHAR);
        } else {
            ps.setString(index, value);
        }
    }

    public static void deleteByTaskIdSync(ServerLevel level, UUID taskId) {
        if (level == null || taskId == null) {
            return;
        }
        SimuSqliteDatabase db = simuDb(level);
        if (db == null) {
            return;
        }
        try {
            deleteTaskNow(db, taskId);
        } catch (Exception e) {
            NsukAddition.LOGGER.error("BuildingTaskQueueStorage: sync delete task {} failed", taskId, e);
        }
    }

    private static void deleteTaskNow(SimuSqliteDatabase db, UUID taskId) {
        try (Connection connection = db.borrowConnection()) {
            boolean previousAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                try (PreparedStatement delPoi = connection.prepareStatement("DELETE FROM building_task_pois WHERE task_id = ?")) {
                    delPoi.setString(1, taskId.toString());
                    delPoi.executeUpdate();
                }
                try (PreparedStatement del = connection.prepareStatement("DELETE FROM building_tasks WHERE task_id = ?")) {
                    del.setString(1, taskId.toString());
                    del.executeUpdate();
                }
                connection.commit();
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(previousAutoCommit);
            }
        } catch (Exception e) {
            NsukAddition.LOGGER.error("BuildingTaskQueueStorage: delete task {} failed", taskId, e);
        }
    }
}