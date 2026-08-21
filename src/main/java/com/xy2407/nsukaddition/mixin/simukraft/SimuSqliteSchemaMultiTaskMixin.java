package com.xy2407.nsukaddition.mixin.simukraft;

import com.xy2407.nsukaddition.NsukAddition;
import common.cn.kafei.simukraft.storage.core.SchemaMigrator;
import common.cn.kafei.simukraft.storage.core.SqliteConnectionPool;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

/**
 * building_tasks 表迁移：去掉 citizen_id UNIQUE 约束，使一个建筑师可存多个任务(运行+排队，队列形式)。
 * 注入点必须放在 SchemaMigrator.migrate(RETURN)——每次服务启动打开数据库都会执行；
 * 原注入点 createBaseline 只在 user_version=0 的首次建库时执行一次，已有存档永远不会触发迁移，
 * UNIQUE 约束残留导致排队任务 INSERT 冲突(SQLITE_CONSTRAINT)全部写入失败，重启后只剩第一个任务。
 */
@Mixin(value = SchemaMigrator.class, remap = false)
public abstract class SimuSqliteSchemaMultiTaskMixin {

    @Inject(method = "migrate(Lcommon/cn/kafei/simukraft/storage/core/SqliteConnectionPool;)V",
            at = @At("RETURN"), remap = false)
    private static void nsuk$dropCitizenIdUnique(SqliteConnectionPool pool, CallbackInfo ci) {
        if (pool == null) {
            return;
        }
        try (Connection connection = pool.borrow()) {
            if (!hasCitizenUniqueIndex(connection)) {
                return;
            }
            try (Statement st = connection.createStatement()) {
                st.executeUpdate("DROP TABLE IF EXISTS building_tasks_nsuk");
                st.executeUpdate("PRAGMA foreign_keys=OFF");
                st.executeUpdate("CREATE TABLE building_tasks_nsuk(task_id TEXT PRIMARY KEY, citizen_id TEXT NOT NULL, city_id TEXT, dimension_id TEXT NOT NULL, build_box_x INTEGER NOT NULL, build_box_y INTEGER NOT NULL, build_box_z INTEGER NOT NULL, category TEXT NOT NULL, building_file_name TEXT NOT NULL, display_name TEXT NOT NULL, amount TEXT NOT NULL DEFAULT '', structure_file_name TEXT NOT NULL, origin_x INTEGER NOT NULL, origin_y INTEGER NOT NULL, origin_z INTEGER NOT NULL, rotation_degrees INTEGER NOT NULL, current_block_index INTEGER NOT NULL, total_blocks INTEGER NOT NULL, status TEXT NOT NULL, created_at INTEGER NOT NULL, updated_at INTEGER NOT NULL, replace_with_air INTEGER NOT NULL DEFAULT 0)");
                st.executeUpdate("INSERT INTO building_tasks_nsuk(task_id, citizen_id, city_id, dimension_id, build_box_x, build_box_y, build_box_z, category, building_file_name, display_name, amount, structure_file_name, origin_x, origin_y, origin_z, rotation_degrees, current_block_index, total_blocks, status, created_at, updated_at, replace_with_air) SELECT task_id, citizen_id, city_id, dimension_id, build_box_x, build_box_y, build_box_z, category, building_file_name, display_name, amount, structure_file_name, origin_x, origin_y, origin_z, rotation_degrees, current_block_index, total_blocks, status, created_at, updated_at, replace_with_air FROM building_tasks");
                st.executeUpdate("DROP TABLE building_tasks");
                st.executeUpdate("ALTER TABLE building_tasks_nsuk RENAME TO building_tasks");
                st.executeUpdate("PRAGMA foreign_keys=ON");
            }
            NsukAddition.LOGGER.info("building_tasks 表已迁移: 去除 citizen_id UNIQUE，支持一建筑师多任务");
        } catch (Exception e) {
            NsukAddition.LOGGER.error("building_tasks 表迁移失败", e);
        }
    }

    private static boolean hasCitizenUniqueIndex(Connection connection) throws Exception {
        try (Statement st = connection.createStatement();
             ResultSet idxs = st.executeQuery("PRAGMA index_list('building_tasks')")) {
            while (idxs.next()) {
                if (idxs.getInt("unique") != 1) {
                    continue;
                }
                String idxName = idxs.getString("name");
                try (ResultSet cols = st.executeQuery("PRAGMA index_info('" + idxName + "')")) {
                    while (cols.next()) {
                        if ("citizen_id".equals(cols.getString("name"))) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }
}