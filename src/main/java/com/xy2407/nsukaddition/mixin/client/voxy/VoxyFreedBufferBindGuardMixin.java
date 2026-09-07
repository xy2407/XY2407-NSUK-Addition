package com.xy2407.nsukaddition.mixin.client.voxy;

import me.cortex.voxy.client.core.gl.GlBuffer;
import me.cortex.voxy.client.core.gl.shader.AutoBindingShader;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 拦截 AutoBindingShader.bind() 中 GlBuffer.assertNotFreed 校验。
 * voxy 在扩大位置缓冲（visiblePosBuffer/chunkPosBuffer）时会先 free 旧缓冲，
 * 当帧再执行 rasterShader.bind() 时仍会断言旧缓冲已释放，导致 IllegalStateException 崩溃。
 * 关闭该缓冲断言以跳过崩溃；渲染随后会把正确缓冲区重新绑到 SSBO。未安装 voxy 时自动降级。
 */
@Pseudo
@OnlyIn(Dist.CLIENT)
@Mixin(value = AutoBindingShader.class, remap = false)
public abstract class VoxyFreedBufferBindGuardMixin {

    @Redirect(method = "bind",
            at = @At(value = "INVOKE", target = "Lme/cortex/voxy/client/core/gl/GlBuffer;assertNotFreed()V"))
    private static void nsuk$skipFreedBufferAssert(GlBuffer self) {
    }
}