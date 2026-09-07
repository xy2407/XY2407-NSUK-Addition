package com.xy2407.nsukaddition.common.colony;

import common.cn.kafei.simukraft.citizen.CitizenData;
import common.cn.kafei.simukraft.city.CityChunkManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;

import java.util.UUID;

/**
 * 附属地/主城雇佣隔离的通用判定渠道（单一事实源）：
 * - 雇佣源分组 = 其区块所属殖民地 id（附属地内）或主城 id（正常情况下区块 owner）；
 * - 市民分组 = 其所在殖民地 id（已搬迁进附属地）或主城 id。
 * 只允许同组之间雇佣，从而把主城与附属地的雇佣彻底隔离。
 */
public final class ColonyEmploymentResolver {

    private ColonyEmploymentResolver() {
    }

    /** employerGroup: 雇佣源方块所属的招募组（区块 owner；附属地内即殖民地 id）。 */
    public static UUID employerGroup(ServerLevel level, BlockPos sourcePos) {
        if (level == null || sourcePos == null) {
            return null;
        }
        return CityChunkManager.get(level).getChunkOwner(new ChunkPos(sourcePos).toLong());
    }

    /** 该区块 owner 是否是一个附属地(殖民地)。 */
    public static boolean isColony(ServerLevel level, UUID ownerId) {
        return ownerId != null && ColonySqliteStorage.loadColonyById(level, ownerId) != null;
    }

    /** citizenGroup: 市民所属的招募组（已分配附属地则殖民地 id，否则主城 id）。 */
    public static UUID citizenGroup(ServerLevel level, CitizenData citizen) {
        if (level == null || citizen == null) {
            return null;
        }
        UUID colonyId = ColonySqliteStorage.getColonyForCitizen(level, citizen.uuid());
        return colonyId != null ? colonyId : citizen.cityId();
    }

    /** citizenGroup: 无 level 版本，供无 server 上下文的调用点使用。 */
    public static UUID citizenGroup(CitizenData citizen) {
        if (citizen == null) {
            return null;
        }
        UUID colonyId = ColonySqliteStorage.getColonyForCitizen(citizen.uuid());
        return colonyId != null ? colonyId : citizen.cityId();
    }
}