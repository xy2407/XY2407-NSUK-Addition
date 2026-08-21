package com.xy2407.nsukaddition.common.item;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/** 手持烈焰棒右键生物：在聊天框输出该生物的实体注册 id(如 minecraft:pig)，文本可点击复制。 */
public final class BlazeRodEntityIdHandler {

    private BlazeRodEntityIdHandler() {
    }

    @SubscribeEvent
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (event.getEntity().level().isClientSide()) {
            return;
        }
        if (event.getItemStack().getItem() != Items.BLAZE_ROD) {
            return;
        }
        if (!(event.getTarget() instanceof LivingEntity target)) {
            return;
        }
        ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(target.getType());
        String entityId = id == null ? "unknown" : id.toString();
        Component message = Component.literal(entityId)
                .withStyle(Style.EMPTY
                        .withColor(0x55FFFF)
                        .withUnderlined(true)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, entityId)));
        event.getEntity().sendSystemMessage(message);
        event.setCanceled(true);
    }
}
