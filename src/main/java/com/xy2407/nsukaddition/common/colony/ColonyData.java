package com.xy2407.nsukaddition.common.colony;

import net.minecraft.core.BlockPos;

import java.util.UUID;

/** 附属领地数据记录，存储附属地的核心信息。 */
public record ColonyData(

        UUID colonyId,

        UUID parentCityId,

        String name,

        BlockPos corePos,

        String dimensionId,

        long createdAt
) {

    public String corePosKey() {
        return dimensionId + "@" + corePos.asLong();
    }
}
