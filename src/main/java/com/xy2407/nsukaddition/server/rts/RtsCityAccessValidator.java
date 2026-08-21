package com.xy2407.nsukaddition.server.rts;

import common.cn.kafei.simukraft.citizen.CitizenData;
import common.cn.kafei.simukraft.citizen.CitizenService;
import common.cn.kafei.simukraft.city.CityService;
import common.cn.kafei.simukraft.entity.CitizenEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

/**
 * RTS 城市权限验证：只有 NPC 所属城市的市长或官员（OFFICIAL 及以上）才能操作该城市 NPC。
 * 其它城市的市长/官员无权操作，无主 NPC（不属于任何城市）也不可操作。
 */
public final class RtsCityAccessValidator {

    private RtsCityAccessValidator() {
    }

    public static boolean canControlNpc(ServerLevel level, ServerPlayer player, CitizenEntity npc) {
        if (level == null || player == null || npc == null) return false;
        UUID cityId = findNpcCityId(level, npc);
        if (cityId == null) return false;
        return CityService.canManageCity(level, cityId, player.getUUID());
    }

    public static UUID findNpcCityId(ServerLevel level, CitizenEntity npc) {
        return CitizenService.findCitizen(level, npc.getUUID())
                .map(CitizenData::cityId)
                .orElse(null);
    }
}
