package com.xy2407.nsukaddition.mixin.client.simukraft;

import com.xy2407.nsukaddition.NsukAddition;
import client.cn.kafei.simukraft.client.renderer.CitizenRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * NPC 皮肤纹理缺失兜底：官方打包个别皮肤文件名大小写错误（如 male 9 号为 .PNG 大写），
 * 渲染时资源找不到显示紫黑块。此处拦截 textureFromPath：
 * 1. 资源存在 → 直接放行并缓存；
 * 2. 不存在 → 尝试扩展名大小写归一化（.png ↔ .PNG）修正路径；
 * 3. 仍不存在 → 回退到同性别 0 号皮肤。
 * 纯客户端渲染层修正，不修改持久化 skinPath，不引入任何 simukraft 资产。
 */
@Mixin(CitizenRenderer.class)
public abstract class CitizenRendererSkinFallbackMixin {

    @Unique
    private static final Map<String, ResourceLocation> NSUK_SKIN_RESOLVE_CACHE = new ConcurrentHashMap<>();

    @Inject(method = "textureFromPath", at = @At("RETURN"), cancellable = true, remap = false)
    private static void nsukaddition$resolveMissingSkin(String skinPath, CallbackInfoReturnable<ResourceLocation> cir) {
        ResourceLocation location = cir.getReturnValue();
        if (location == null) {
            return;
        }
        String key = location.toString();
        ResourceLocation cached = NSUK_SKIN_RESOLVE_CACHE.get(key);
        if (cached != null) {
            cir.setReturnValue(cached);
            return;
        }
        ResourceLocation resolved = resolveExisting(location);
        if (resolved == null) {
            boolean female = skinPath != null && skinPath.contains("female");
            resolved = ResourceLocation.parse(female
                    ? "simukraft:textures/entity/female/custom_female_entity_0.png"
                    : "simukraft:textures/entity/male/custom_male_entity_0.png");
            NsukAddition.LOGGER.warn("Nsuk: skin texture missing {} fallback to {}", location, resolved);
        }
        NSUK_SKIN_RESOLVE_CACHE.put(key, resolved);
        cir.setReturnValue(resolved);
    }

    @Unique
    private static ResourceLocation resolveExisting(ResourceLocation location) {
        if (resourceExists(location)) {
            return location;
        }
        String path = location.getPath();
        String swapped = null;
        if (path.endsWith(".png")) {
            swapped = path.substring(0, path.length() - 4) + ".PNG";
        } else if (path.endsWith(".PNG")) {
            swapped = path.substring(0, path.length() - 4) + ".png";
        }
        if (swapped != null) {
            ResourceLocation alt = ResourceLocation.fromNamespaceAndPath(location.getNamespace(), swapped);
            if (resourceExists(alt)) {
                return alt;
            }
        }
        return null;
    }

    @Unique
    private static boolean resourceExists(ResourceLocation location) {
        return Minecraft.getInstance().getResourceManager().getResource(location).isPresent();
    }
}
