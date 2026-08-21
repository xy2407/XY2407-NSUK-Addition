package com.xy2407.nsukaddition.mixin.simukraft;

import common.cn.kafei.simukraft.SimuKraft;
import common.cn.kafei.simukraft.building.BuildingPoiDefinition;
import common.cn.kafei.simukraft.building.BuildingTaskData;
import common.cn.kafei.simukraft.city.poi.CityPoiType;
import common.cn.kafei.simukraft.storage.BuildingTaskSqliteRepository;
import common.cn.kafei.simukraft.storage.SimuSqliteDatabase;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

/**
 * building_tasks 多任务存储接管：
 * 1. upsert 冲突目标由 citizen_id 改为 task_id —— 同一建筑师可同时存运行+排队多个任务，互不覆盖；
 * 2. findByCitizen 返回"活跃任务"(非排队状态的最新一条)，排队任务不计入运行判定；
 * 3. deleteByCitizen 删除该市民全部任务。
 */
@Mixin(value = BuildingTaskSqliteRepository.class, remap = false)
public abstract class BuildingTaskSqliteRepositoryMultiTaskMixin {

    @Shadow
    private SimuSqliteDatabase database;

    @Overwrite
    private void saveTask(Connection connection, BuildingTaskData task) throws SQLException {
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

    @Overwrite
    public synchronized BuildingTaskData findByCitizen(UUID citizenId) {
        if (citizenId == null) {
            return null;
        }
        try (Connection connection = database.borrowConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT * FROM building_tasks WHERE citizen_id = ? AND status != 'queued' ORDER BY updated_at DESC LIMIT 1")) {
            statement.setString(1, citizenId.toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return null;
                }
                UUID taskId = UUID.fromString(resultSet.getString("task_id"));
                return new BuildingTaskData(
                        taskId,
                        UUID.fromString(resultSet.getString("citizen_id")),
                        nullableUuid(resultSet.getString("city_id")),
                        resultSet.getString("dimension_id"),
                        new BlockPos(resultSet.getInt("build_box_x"), resultSet.getInt("build_box_y"), resultSet.getInt("build_box_z")),
                        resultSet.getString("category"),
                        resultSet.getString("building_file_name"),
                        resultSet.getString("display_name"),
                        resultSet.getString("amount"),
                        resultSet.getString("structure_file_name"),
                        new BlockPos(resultSet.getInt("origin_x"), resultSet.getInt("origin_y"), resultSet.getInt("origin_z")),
                        resultSet.getInt("rotation_degrees"),
                        resultSet.getInt("current_block_index"),
                        resultSet.getInt("total_blocks"),
                        resultSet.getString("status"),
                        resultSet.getLong("created_at"),
                        resultSet.getLong("updated_at"),
                        loadTaskPois(connection, taskId),
                        resultSet.getInt("replace_with_air") != 0
                );
            }
        } catch (SQLException | IllegalArgumentException exception) {
            SimuKraft.LOGGER.error("Failed to load building task by citizen", exception);
            return null;
        }
    }

    @Overwrite
    public void deleteByCitizen(Connection connection, UUID citizenId) throws SQLException {
        if (citizenId == null) {
            return;
        }
        try (PreparedStatement statement = connection.prepareStatement("DELETE FROM building_tasks WHERE citizen_id = ?")) {
            statement.setString(1, citizenId.toString());
            statement.executeUpdate();
        }
    }

    @Shadow
    private List<BuildingPoiDefinition> loadTaskPois(Connection connection, UUID taskId) throws SQLException {
        throw new UnsupportedOperationException();
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
}