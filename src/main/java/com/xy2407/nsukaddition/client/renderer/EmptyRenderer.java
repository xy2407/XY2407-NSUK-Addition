package com.xy2407.nsukaddition.client.renderer;

import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;

/** 不可现实体渲染器，用于 SitEntity。 */
public class EmptyRenderer<T extends Entity> extends EntityRenderer<T> {
    public EmptyRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
    }

    @Override
    public ResourceLocation getTextureLocation(T entity) {
        return null;
    }
}
