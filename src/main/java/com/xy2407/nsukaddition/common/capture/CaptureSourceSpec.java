package com.xy2407.nsukaddition.common.capture;

/** 商业报价 JSON 中捕获器产出的实体规格(尚未解析为 EntityType)，供捕获买入登记使用。 */
public record CaptureSourceSpec(String entity, boolean baby) {
}