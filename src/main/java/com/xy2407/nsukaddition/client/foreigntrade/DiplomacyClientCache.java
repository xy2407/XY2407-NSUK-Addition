package com.xy2407.nsukaddition.client.foreigntrade;

import com.xy2407.nsukaddition.common.foreigntrade.DiplomacyStorage.DiplomacyRelation;

import java.util.List;

/** 外交关系客户端缓存，存储服务端同步的玩家建交关系列表。 */
public final class DiplomacyClientCache {

    private DiplomacyClientCache() {}

    private static volatile List<DiplomacyRelation> relations = List.of();

    public static void update(List<DiplomacyRelation> newRelations) {
        relations = newRelations != null ? List.copyOf(newRelations) : List.of();
    }

    public static List<DiplomacyRelation> getRelations() {
        return relations;
    }

    public static boolean hasRelation(String villageType) {
        if (villageType == null) return false;
        List<DiplomacyRelation> snapshot = relations;
        for (DiplomacyRelation r : snapshot) {
            if (villageType.equals(r.villageType())) return true;
        }
        return false;
    }

    public static String getVillageTypeByCityId(String cityId) {
        if (cityId == null) return null;
        List<DiplomacyRelation> snapshot = relations;
        for (DiplomacyRelation r : snapshot) {
            if (cityId.equals(r.cityId())) return r.villageType();
        }
        return null;
    }

    public static String getCityNameByCityId(String cityId) {
        if (cityId == null) return null;
        List<DiplomacyRelation> snapshot = relations;
        for (DiplomacyRelation r : snapshot) {
            if (cityId.equals(r.cityId())) return r.cityName();
        }
        return null;
    }
}
