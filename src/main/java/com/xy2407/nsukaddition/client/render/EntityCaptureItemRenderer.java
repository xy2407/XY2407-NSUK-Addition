package com.xy2407.nsukaddition.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.xy2407.nsukaddition.common.item.EntityCaptureItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** 生物物品渲染器:按 NBT 渲染实体模型图标,无 NBT 时显示拴绳默认图标。 */
@OnlyIn(Dist.CLIENT)
public class EntityCaptureItemRenderer extends BlockEntityWithoutLevelRenderer {

    private static final Map<String, Entity> ENTITY_CACHE = new ConcurrentHashMap<>();
    private static volatile EntityCaptureItemRenderer INSTANCE;

    public static EntityCaptureItemRenderer getInstance() {
        EntityCaptureItemRenderer r = INSTANCE;
        if (r == null) {
            synchronized (EntityCaptureItemRenderer.class) {
                r = INSTANCE;
                if (r == null) {
                    r = new EntityCaptureItemRenderer();
                    INSTANCE = r;
                }
            }
        }
        return r;
    }

    public EntityCaptureItemRenderer() {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext transformType,
                             PoseStack poseStack, MultiBufferSource bufferSource, int light, int overlay) {
        EntityType<?> type = EntityCaptureItem.getEntityType(stack);
        if (type == null) {
            renderLeadIcon(transformType, poseStack, bufferSource, light, overlay);
            return;
        }
        Level level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }
        Entity entity = getOrCreateEntity(type, EntityCaptureItem.isBaby(stack), level);
        if (entity == null) {
            return;
        }
        poseStack.pushPose();
        float width = entity.getBbWidth();
        float height = entity.getBbHeight();
        float scale = (float) Math.min(1.5, 1.0 / Math.max(Math.max(width, height), 0.5));
        poseStack.scale(scale, scale, scale);
        poseStack.translate(0.16F, -height / 2.0F + 0.16F, 0.0F);
        poseStack.mulPose(Axis.YP.rotationDegrees(90.0F));
        EntityRenderDispatcher dispatcher = Minecraft.getInstance().getEntityRenderDispatcher();
        dispatcher.render(entity, 0.0, 0.0, 0.0, 0.0F, 1.0F, poseStack, bufferSource, light);
        poseStack.popPose();
    }

    private void renderLeadIcon(ItemDisplayContext transformType, PoseStack poseStack,
                                MultiBufferSource bufferSource, int light, int overlay) {
        Minecraft mc = Minecraft.getInstance();
        ItemStack leadStack = new ItemStack(Items.LEAD);
        BakedModel model = mc.getItemRenderer().getModel(leadStack, null, null, 0);
        poseStack.pushPose();
        poseStack.translate(0.5F, 0.5F, 0.5F);
        mc.getItemRenderer().render(leadStack, transformType, false, poseStack, bufferSource, light, overlay, model);
        poseStack.popPose();
    }

    private Entity getOrCreateEntity(EntityType<?> type, boolean baby, Level level) {
        String key = EntityType.getKey(type) + (baby ? "#baby" : "#adult");
        return ENTITY_CACHE.computeIfAbsent(key, k -> {
            Entity entity = type.create(level);
            if (entity instanceof AgeableMob ageable && baby) {
                ageable.setAge(-24000);
            }
            return entity;
        });
    }
}
