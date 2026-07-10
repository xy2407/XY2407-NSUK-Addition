package com.xy2407.nsukaddition.common.city;

/** 城市等级枚举，定义从聚落到都市的五个等级。 */
public enum CityLevel {

    SETTLEMENT(1, "聚落", 25),
    VILLAGE(2, "村庄", 49),
    TOWN(3, "城镇", 100),
    CITY_STATE(4, "城邦", 196),
    METROPOLIS(5, "都市", 361);

    private final int level;

    private final String displayName;

    private final int maxChunks;

    CityLevel(int level, String displayName, int maxChunks) {
        this.level = level;
        this.displayName = displayName;
        this.maxChunks = maxChunks;
    }

    public int level() {
        return level;
    }

    public String displayName() {
        return displayName;
    }

    public static CityLevel fromLevel(int level) {
        if (level <= SETTLEMENT.level) return SETTLEMENT;
        if (level >= METROPOLIS.level) return METROPOLIS;
        for (CityLevel cl : values()) {
            if (cl.level == level) return cl;
        }
        return SETTLEMENT;
    }

    public CityLevel next() {
        return fromLevelOrNull(this.level + 1);
    }

    private static CityLevel fromLevelOrNull(int level) {
        for (CityLevel cl : values()) {
            if (cl.level == level) return cl;
        }
        return null;
    }

    public boolean isMax() {
        return this == METROPOLIS;
    }

    public boolean atLeast(CityLevel target) {
        return target != null && this.level >= target.level;
    }

    /** 该等级允许拥有的最大区块领地数量。 */
    public int maxChunks() {
        return maxChunks;
    }
}
