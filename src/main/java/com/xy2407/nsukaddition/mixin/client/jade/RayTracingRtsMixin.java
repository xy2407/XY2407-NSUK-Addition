package com.xy2407.nsukaddition.mixin.client.jade;

import com.xy2407.nsukaddition.client.rts.RtsModeManager;
import com.xy2407.nsukaddition.client.rts.RtsPicker;
import com.xy2407.nsukaddition.common.network.rts.RtsJadeFocusPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.neoforge.network.PacketDistributor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import snownee.jade.overlay.RayTracing;

import java.util.UUID;

/**
 * RTS 模式接管 Jade 拾取：Jade 默认用玩家实体视线(准星中心)拾取，
 * RTS 自由相机下玩家实体不在相机视线方向 → 改为鼠标位置→世界射线拾取，实现“鼠标指哪里显示哪里”。
 * 同时跟踪准星对准的实体(变化时通知服务端)，供服务端放行该实体的 NBT 距离检查。
 */
@Mixin(value = RayTracing.class, remap = false)
public abstract class RayTracingRtsMixin {

    @Shadow
    private HitResult target;

    @Inject(method = "fire", at = @At("HEAD"), cancellable = true)
    private void nsukaddition$rtsFire(CallbackInfo ci) {
        if (!RtsModeManager.isActive()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            return;
        }
        double[] pos = RtsModeManager.getGuiScaledMouse();
        HitResult hit = RtsPicker.pickHitResult(pos[0], pos[1]);
        this.target = hit;

        int focusedId = -1;
        if (hit instanceof EntityHitResult ehr) {
            focusedId = ehr.getEntity().getId();
            if (focusedId != RtsPicker.lastFocusEntityId) {
                PacketDistributor.sendToServer(new RtsJadeFocusPacket(ehr.getEntity().getUUID()));
            }
        } else if (RtsPicker.lastFocusEntityId != -1) {
            PacketDistributor.sendToServer(new RtsJadeFocusPacket(new UUID(0L, 0L)));
        }
        RtsPicker.lastFocusEntityId = focusedId;

        ci.cancel();
    }
}
