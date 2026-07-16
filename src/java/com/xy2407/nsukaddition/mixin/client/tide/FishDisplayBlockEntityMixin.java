package com.xy2407.nsukaddition.mixin.client.tide;

import com.li64.tide.registries.blocks.entities.FishDisplayBlockEntity;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 修复 Tide 鱼类展示板渲染 Aquaculture 鱼类时空白的问题。
 *
 * 根因：Aquaculture 的 AquaFishEntity 继承自 WaterAnimal，
 * WaterAnimal.isInWater() 在展示板环境中返回 false，
 * 导致 AquaFishRenderer.setupRotations() 做额外旋转和位移，
 * 将鱼渲染到展示板视野外，表现为"完全空白"。
 *
 * 修复：在 setRenderedEntity 时对 Aquaculture 鱼实体做初始化，
 * 设置 noAI、关闭 invisible，确保渲染状态正确。
 */
@Mixin(FishDisplayBlockEntity.class)
public class FishDisplayBlockEntityMixin {

    @Inject(method = "setRenderedEntity", at = @At("HEAD"), remap = false)
    private void onSetRenderedEntity(Entity entity, CallbackInfo ci) {
        if (entity instanceof Mob mob) {
            ResourceLocation key = BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType());
            if ("aquaculture".equals(key.getNamespace())) {
                mob.setNoAi(true);
                mob.setInvisible(false);
                mob.setNoGravity(true);
            }
        }
    }
}
