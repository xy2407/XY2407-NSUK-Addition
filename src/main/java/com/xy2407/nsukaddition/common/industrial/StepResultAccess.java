package com.xy2407.nsukaddition.common.industrial;

/** 工业步骤结果枚举的反射访问器，解耦对上游内部类的直接依赖。 */
public final class StepResultAccess {
    private static final Enum<?> PROGRESSED;
    private static final Enum<?> WAITING_RETRY;
    private static final Enum<?> WAITING_MOVE;

    static {
        try {
            Class<?> rawCls = Class.forName("common.cn.kafei.simukraft.industrial.IndustrialWorkService$StepResult");
            @SuppressWarnings({"unchecked", "rawtypes"})
            Class<Enum> cls = (Class<Enum>) rawCls;
            PROGRESSED = Enum.valueOf(cls, "PROGRESSED");
            WAITING_RETRY = Enum.valueOf(cls, "WAITING_RETRY");
            WAITING_MOVE = Enum.valueOf(cls, "WAITING_MOVE");
        } catch (Exception e) {
            throw new RuntimeException("Failed to reflect StepResult", e);
        }
    }

    private StepResultAccess() {}

    @SuppressWarnings("unchecked")
    public static <T> T progressed() {
        return (T) PROGRESSED;
    }

    @SuppressWarnings("unchecked")
    public static <T> T waitingRetry() {
        return (T) WAITING_RETRY;
    }

    @SuppressWarnings("unchecked")
    public static <T> T waitingMove() {
        return (T) WAITING_MOVE;
    }
}
