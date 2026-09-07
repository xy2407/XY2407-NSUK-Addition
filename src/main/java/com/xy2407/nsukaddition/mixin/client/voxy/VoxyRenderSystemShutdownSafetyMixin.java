package com.xy2407.nsukaddition.mixin.client.voxy;

import me.cortex.voxy.client.core.VoxyRenderSystem;
import me.cortex.voxy.client.core.rendering.Viewport;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 拦截 Voxy 渲染系统关闭后再渲染：shutdown 后跳过 renderOpaque，防止渲染线程绑定已释放的 GlBuffer 而崩溃。未安装 voxy 时自动降级不生效。 */
@Pseudo
@OnlyIn(Dist.CLIENT)
@Mixin(value = VoxyRenderSystem.class, remap = false)
public abstract class VoxyRenderSystemShutdownSafetyMixin {

    @Unique
    private volatile boolean nsuk$destroyed;

    @Inject(method = "shutdown", at = @At("HEAD"), remap = false, require = 0)
    private void nsuk$markDestroyed(CallbackInfo ci) {
        this.nsuk$destroyed = true;
    }

    @Inject(method = "renderOpaque", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void nsuk$skipRenderAfterShutdown(Viewport<?> viewport, CallbackInfo ci) {
        if (this.nsuk$destroyed) {
            ci.cancel();
        }
    }
}