
package com.xy2407.nsukaddition.mixin.client.simukraft;

import client.cn.kafei.simukraft.client.renderer.CitizenRenderer;
import com.xy2407.nsukaddition.client.NsukAdditionClient;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.model.geom.ModelLayers;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** 修改 CitizenRenderer，将市民模型层重定向至自定义的含胸部扩展模型层。 */
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
}
