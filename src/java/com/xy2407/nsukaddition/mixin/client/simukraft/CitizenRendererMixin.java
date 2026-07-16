
package com.xy2407.nsukaddition.mixin.client.simukraft;

import client.cn.kafei.simukraft.client.renderer.CitizenRenderer;
import com.xy2407.nsukaddition.client.NsukAdditionClient;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 修改 CitizenRenderer，将市民模型层重定向至自定义模型层，并添加盔甲渲染层。 */
@SuppressWarnings("null")
@Mixin(CitizenRenderer.class)
public abstract class CitizenRendererMixin {

    @Redirect(
            method = "<init>(Lnet/minecraft/client/renderer/entity/EntityRendererProvider$Context;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/entity/EntityRendererProvider$Context;bakeLayer(Lnet/minecraft/client/model/geom/ModelLayerLocation;)Lnet/minecraft/client/model/geom/ModelPart;"
            )
    )
    private static ModelPart nsukaddition$redirectCitizenLayer(EntityRendererProvider.Context context, ModelLayerLocation layer) {
        if (layer.equals(ModelLayers.PLAYER_SLIM)) {
            return context.bakeLayer(NsukAdditionClient.CITIZEN_SLIM);
        }
        if (layer.equals(ModelLayers.PLAYER)) {
            return context.bakeLayer(NsukAdditionClient.CITIZEN);
        }
        return context.bakeLayer(layer);
    }

    @Inject(method = "<init>(Lnet/minecraft/client/renderer/entity/EntityRendererProvider$Context;)V", at = @At("RETURN"), remap = false)
    private void nsukaddition$addArmorLayer(EntityRendererProvider.Context context, CallbackInfo ci) {
        CitizenRenderer renderer = (CitizenRenderer) (Object) this;
        renderer.addLayer(new HumanoidArmorLayer<>(renderer,
                new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER_INNER_ARMOR)),
                new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER_OUTER_ARMOR)),
                context.getModelManager()));
    }
}
