package com.xy2407.nsukaddition.common.city;

import java.util.List;
import java.util.Map;

/** 城市升级所需的人口、材料与资金需求，定义各级别升阶阈值并提供查询方法。 */
public record CityUpgradeRequirement(

        CityLevel targetLevel,

        int requiredPopulation,

        int requiredLogs,

        int requiredStone,

        double requiredFunds
) {

    private static final CityUpgradeRequirement SETTLEMENT_TO_VILLAGE = new CityUpgradeRequirement(
            CityLevel.VILLAGE, 12, 32, 32, 0
    );

    private static final CityUpgradeRequirement VILLAGE_TO_TOWN = new CityUpgradeRequirement(
            CityLevel.TOWN, 36, 128, 128, 256
    );

    private static final CityUpgradeRequirement TOWN_TO_CITY_STATE = new CityUpgradeRequirement(
            CityLevel.CITY_STATE, 108, 256, 256, 512
    );

    private static final CityUpgradeRequirement CITY_STATE_TO_METROPOLIS = new CityUpgradeRequirement(
            CityLevel.METROPOLIS, 240, 512, 512, 1024
    );

    private static final Map<CityLevel, CityUpgradeRequirement> UPGRADE_TABLE = Map.of(
            SETTLEMENT_TO_VILLAGE.targetLevel, SETTLEMENT_TO_VILLAGE,
            VILLAGE_TO_TOWN.targetLevel, VILLAGE_TO_TOWN,
            TOWN_TO_CITY_STATE.targetLevel, TOWN_TO_CITY_STATE,
            CITY_STATE_TO_METROPOLIS.targetLevel, CITY_STATE_TO_METROPOLIS
    );

    public static CityUpgradeRequirement forCurrentLevel(CityLevel currentLevel) {
        if (currentLevel == null || currentLevel.isMax()) {
            return null;
        }
        CityLevel next = currentLevel.next();
        if (next == null) {
            return null;
        }
        return UPGRADE_TABLE.get(next);
    }

    public static CityUpgradeRequirement forTargetLevel(CityLevel targetLevel) {
        if (targetLevel == null) {
            return null;
        }
        return UPGRADE_TABLE.get(targetLevel);
    }

    public static List<CityUpgradeRequirement> allRequirements() {
        return List.of(SETTLEMENT_TO_VILLAGE, VILLAGE_TO_TOWN, TOWN_TO_CITY_STATE, CITY_STATE_TO_METROPOLIS);
    }
}
