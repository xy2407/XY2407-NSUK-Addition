package com.xy2407.nsukaddition.common.registry;

import com.xy2407.nsukaddition.NsukAddition;
import com.xy2407.nsukaddition.common.item.EntityCaptureItem;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/** 生物物品注册:固定注册一个可捕获生物的通用物品。 */
public final class ModEntityItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(NsukAddition.MOD_ID);

    public static final DeferredItem<EntityCaptureItem> ENTITY_CAPTURE =
            ITEMS.register("entity_capture", () -> new EntityCaptureItem(new Item.Properties()));

    private ModEntityItems() {
    }

    public static void register(IEventBus modEventBus) {
        ITEMS.register(modEventBus);
    }
}
