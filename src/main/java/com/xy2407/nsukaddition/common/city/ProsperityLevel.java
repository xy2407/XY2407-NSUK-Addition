package com.xy2407.nsukaddition.common.city;

/** 城市繁荣度等级枚举，定义5个繁荣度区间及对应每日人均住宅租金税率。 */
public enum ProsperityLevel {
    LEVEL_1(0L, 100L, 5.0, "起步"),
    LEVEL_2(101L, 1000L, 10.0, "发展"),
    LEVEL_3(1001L, 5000L, 15.0, "稳定"),
    LEVEL_4(5001L, 10000L, 20.0, "繁荣"),
    LEVEL_5(10001L, Long.MAX_VALUE, 30.0, "鼎盛");

    private final long lowerBound;
    private final long upperBound;
    private final double dailyTaxPerCitizen;
    private final String displayName;

    ProsperityLevel(long lowerBound, long upperBound, double dailyTaxPerCitizen, String displayName) {
        this.lowerBound = lowerBound;
        this.upperBound = upperBound;
        this.dailyTaxPerCitizen = dailyTaxPerCitizen;
        this.displayName = displayName;
    }

    public double dailyTaxPerCitizen() {
        return dailyTaxPerCitizen;
    }

    public String displayName() {
        return displayName;
    }

    public static ProsperityLevel fromValue(long prosperity) {
        if (prosperity < 0L) prosperity = 0L;
        for (ProsperityLevel level : values()) {
            if (prosperity >= level.lowerBound && prosperity <= level.upperBound) {
                return level;
            }
        }
        return LEVEL_5;
    }
}