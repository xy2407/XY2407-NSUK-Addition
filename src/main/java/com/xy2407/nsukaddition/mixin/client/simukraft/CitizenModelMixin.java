package com.xy2407.nsukaddition.mixin.client.simukraft;

import client.cn.kafei.simukraft.client.renderer.CitizenModel;
import common.cn.kafei.simukraft.entity.CitizenEntity;
import net.minecraft.client.model.geom.ModelPart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 修改 CitizenModel，为女性市民添加胸部模型部件及动画。 */
@SuppressWarnings("null")
@Mixin(CitizenModel.class)
public abstract class CitizenModelMixin {

    @Unique
    private ModelPart nsukaddition$breasts;

    @Inject(method = "<init>(Lnet/minecraft/client/model/geom/ModelPart;Z)V", at = @At("TAIL"))
    private void nsukaddition$initBreasts(ModelPart root, boolean slim, CallbackInfo ci) {

        this.nsukaddition$breasts = ((CitizenModel) (Object) this).body.getChild("nsukaddition_breasts");
    }

    @Inject(
            method = "setupAnim(Lcommon/cn/kafei/simukraft/entity/CitizenEntity;FFFFF)V",
            at = @At("TAIL")
    )
    private void nsukaddition$applyBreastPose(CitizenEntity entity, float limbSwing, float limbSwingAmount,
                                              float ageInTicks, float netHeadYaw, float headPitch, CallbackInfo ci) {
        if (nsukaddition$breasts == null) {
            return;
        }

        String skinPath = entity.getSkinPath();
        boolean female = skinPath != null
                && (skinPath.contains("female") || skinPath.toLowerCase().endsWith("_f"));
        nsukaddition$breasts.visible = female;

        if (!female) {
            return;
        }

        float breastSize = ((entity.getUUID().hashCode() & 0x7FFFFFFF) % 100) / 100.0f;

        breastSize = 0.20f + breastSize * (0.79f / 0.99f);
        boolean sneaking = entity.isCrouching();

        nsukaddition$breasts.xScale = breastSize * 0.2f + 1.05f;
        nsukaddition$breasts.yScale = breastSize * 0.75f + 0.75f;
        nsukaddition$breasts.zScale = breastSize * 0.75f + 0.75f;

        nsukaddition$breasts.xRot = (float) Math.PI * 0.3f;

        float yOffset = 5.0f - (float) Math.pow(breastSize, 0.5) * 2.5f + (sneaking ? 3.0f : 0.0f);
        float zOffset = -1.5f + breastSize * 0.25f + (sneaking ? 1.5f : 0.0f);
        nsukaddition$breasts.setPos(0.25f, yOffset, zOffset);
    }
}
