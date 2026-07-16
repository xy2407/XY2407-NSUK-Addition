package com.xy2407.nsukaddition.common.farmland;

/** 通过反射访问农场工作结果枚举的常量，避免编译期依赖。 */
public final class FarmlandWorkResultAccess {
    private static final Enum<?> PROCESSED;
    private static final Enum<?> WAITING_SEED;

    static {
        try {
            Class<?> rawCls = Class.forName("common.cn.kafei.simukraft.farmland.FarmlandWorkResult");
            @SuppressWarnings({"unchecked", "rawtypes"})
            Class<Enum> cls = (Class<Enum>) rawCls;
            PROCESSED = Enum.valueOf(cls, "PROCESSED");
            WAITING_SEED = Enum.valueOf(cls, "WAITING_SEED");
        } catch (Exception e) {
            throw new RuntimeException("无法反射访问 FarmlandWorkResult", e);
        }
    }

    private FarmlandWorkResultAccess() {
    }

    @SuppressWarnings("unchecked")
    public static <T> T processed() {
        return (T) PROCESSED;
    }

    @SuppressWarnings("unchecked")
    public static <T> T waitingSeed() {
        return (T) WAITING_SEED;
    }
}
