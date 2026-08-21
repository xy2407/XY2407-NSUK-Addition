package com.xy2407.nsukaddition.common.capture;

import net.minecraft.world.entity.EntityType;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** 记录「买入报价 → 产出生物类型」的映射，供商队/外贸交易交付时生成捕获器。 */
public final class CaptureBuySupport {

    public record Spec(EntityType<?> type, boolean baby) {
    }

    private static final Map<String, Spec> BUY_SPECS = new ConcurrentHashMap<>();

    private CaptureBuySupport() {
    }

    public static void register(String offerId, Spec spec) {
        if (offerId != null && spec != null) {
            BUY_SPECS.put(offerId, spec);
        }
    }

    public static Spec spec(String offerId) {
        return offerId == null ? null : BUY_SPECS.get(offerId);
    }

    public static void clear() {
        BUY_SPECS.clear();
    }
}