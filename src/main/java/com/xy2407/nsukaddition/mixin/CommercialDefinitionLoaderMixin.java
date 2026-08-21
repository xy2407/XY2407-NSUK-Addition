package com.xy2407.nsukaddition.mixin;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.xy2407.nsukaddition.common.capture.CaptureBuySupport;
import com.xy2407.nsukaddition.common.capture.CaptureSourceSpec;
import common.cn.kafei.simukraft.commercial.CommercialDefinitionLoader;
import net.minecraft.world.entity.EntityType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import javax.annotation.Nullable;
import java.nio.file.Path;

/** 从商业报价 JSON 的产出资源中解析捕获器生物规格，登记到买入侧供交付使用。 */
@Mixin(CommercialDefinitionLoader.class)
public class CommercialDefinitionLoaderMixin {

    private static final String CAPTURE_ITEM = "xy2407_nsuk_addition:entity_capture";

    @Inject(method = "loadText", at = @At("TAIL"), remap = false)
    private static void nsuk$readCaptureBuySpecs(String text, String fallbackId, @Nullable Path sourcePath,
                                                  CallbackInfoReturnable<?> cir) {
        if (text == null || text.isBlank()) {
            return;
        }
        try {
            JsonObject root = JsonParser.parseString(text).getAsJsonObject();
            JsonArray offers = root.has("offers") && root.get("offers").isJsonArray()
                    ? root.getAsJsonArray("offers") : null;
            if (offers == null) {
                return;
            }
            for (int i = 0; i < offers.size(); i++) {
                JsonElement element = offers.get(i);
                if (element == null || !element.isJsonObject()) {
                    continue;
                }
                JsonObject offer = element.getAsJsonObject();
                String offerId = offer.has("id") && offer.get("id").isJsonPrimitive()
                        ? offer.get("id").getAsString() : "offer_" + i;
                CaptureSourceSpec scan = findCaptureResult(offer);
                if (scan == null) {
                    continue;
                }
                EntityType<?> type = EntityType.byString(scan.entity()).orElse(null);
                if (type != null) {
                    CaptureBuySupport.register(offerId, new CaptureBuySupport.Spec(type, scan.baby()));
                }
            }
        } catch (RuntimeException ignored) {
        }
    }

    @Nullable
    private static CaptureSourceSpec findCaptureResult(JsonObject offer) {
        JsonArray results = offer.has("result") && offer.get("result").isJsonArray()
                ? offer.getAsJsonArray("result") : null;
        if (results == null) {
            return null;
        }
        for (JsonElement element : results) {
            if (element == null || !element.isJsonObject()) {
                continue;
            }
            JsonObject resource = element.getAsJsonObject();
            if (!resource.has("item") || !resource.get("item").isJsonPrimitive()) {
                continue;
            }
            if (!CAPTURE_ITEM.equals(resource.get("item").getAsString())) {
                continue;
            }
            if (!resource.has("entity") || !resource.get("entity").isJsonPrimitive()) {
                continue;
            }
            return new CaptureSourceSpec(resource.get("entity").getAsString(),
                    resource.has("baby") && resource.get("baby").isJsonPrimitive()
                            && resource.get("baby").getAsBoolean());
        }
        return null;
    }

    @Inject(method = "clearCache", at = @At("HEAD"), remap = false)
    private static void nsuk$clearCaptureBuySpecs(CallbackInfo ci) {
        CaptureBuySupport.clear();
    }
}