package com.xy2407.nsukaddition.client.city;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.UUID;

/** 客户端玩家所属城市缓存，由区块同步包以玩家本人城市写入，供地图领地着色稳定使用。 */
@OnlyIn(Dist.CLIENT)
public final class OwnCityClientCache {
    private static volatile UUID ownCityId;

    private OwnCityClientCache() {
    }

    public static void update(UUID cityId) {
        ownCityId = cityId;
    }

    public static UUID getOwnCityId() {
        return ownCityId;
    }

    public static void clear() {
        ownCityId = null;
    }
}