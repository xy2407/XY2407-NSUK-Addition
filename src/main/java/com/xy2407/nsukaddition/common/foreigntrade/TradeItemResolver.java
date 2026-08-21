package com.xy2407.nsukaddition.common.foreigntrade;

import com.xy2407.nsukaddition.common.foreigntrade.ForeignTradeConfig.TradeItemDef;
import com.xy2407.nsukaddition.common.item.EntityCaptureItem;
import com.xy2407.nsukaddition.common.registry.ModEntityItems;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * 外贸/商队商品解析:动物(category=animal)条目以捕获器(entity_capture)为交易载体,
 * 具体动物由 NBT(entity/baby) 指定,成交时组装装有对应实体的捕获器;其余条目按普通物品处理。
 */
public final class TradeItemResolver {

    private TradeItemResolver() {
    }

    public static boolean isAnimal(TradeItemDef def) {
        return def != null && def.isAnimal();
    }

    public static EntityType<?> resolveEntity(TradeItemDef def) {
        if (!isAnimal(def)) {
            return null;
        }
        return EntityType.byString(def.captureEntityId()).orElse(null);
    }

    public static ItemStack buildStack(TradeItemDef def, int totalCount) {
        if (isAnimal(def)) {
            EntityType<?> type = resolveEntity(def);
            if (type == null) {
                return ItemStack.EMPTY;
            }
            ItemStack seed = new ItemStack(ModEntityItems.ENTITY_CAPTURE.get());
            return EntityCaptureItem.createCapture(seed, type, def.captureBaby(), Math.max(1, totalCount));
        }
        ResourceLocation rl = ResourceLocation.tryParse(def.item_id());
        Item item = rl != null ? BuiltInRegistries.ITEM.get(rl) : null;
        return item != null ? new ItemStack(item, Math.max(1, totalCount)) : ItemStack.EMPTY;
    }

    public static ItemStack deliver(TradeItemDef def, int totalCount) {
        return buildStack(def, totalCount);
    }

    public static ItemStack buildDisplay(String itemId, String category, int count) {
        if (category != null && category.equalsIgnoreCase("animal")) {
            EntityType<?> type = EntityType.byString(itemId).orElse(null);
            if (type == null) {
                return ItemStack.EMPTY;
            }
            ItemStack seed = new ItemStack(ModEntityItems.ENTITY_CAPTURE.get());
            return EntityCaptureItem.createCapture(seed, type, false, Math.max(1, count));
        }
        ResourceLocation rl = ResourceLocation.tryParse(itemId);
        Item item = rl != null ? BuiltInRegistries.ITEM.get(rl) : null;
        return item != null ? new ItemStack(item, Math.max(1, count)) : ItemStack.EMPTY;
    }

    public static boolean matches(ItemStack stack, TradeItemDef def) {
        if (stack == null || stack.isEmpty() || def == null) {
            return false;
        }
        if (isAnimal(def)) {
            if (!(stack.getItem() instanceof EntityCaptureItem)) {
                return false;
            }
            EntityType<?> type = resolveEntity(def);
            return type != null && EntityCaptureItem.isCompatibleCapture(stack, type);
        }
        ResourceLocation rl = ResourceLocation.tryParse(def.item_id());
        return rl != null && stack.is(BuiltInRegistries.ITEM.get(rl));
    }

    public static int countIn(ItemStack stack, TradeItemDef def) {
        if (stack == null || def == null) {
            return 0;
        }
        if (isAnimal(def) && stack.getItem() instanceof EntityCaptureItem) {
            return EntityCaptureItem.getEntryCount(stack);
        }
        return stack.getCount();
    }

    public static int removeFrom(ItemStack stack, TradeItemDef def, int amount) {
        if (stack == null || def == null || amount <= 0) {
            return 0;
        }
        if (isAnimal(def) && stack.getItem() instanceof EntityCaptureItem) {
            return EntityCaptureItem.removeEntries(stack, amount);
        }
        int taken = Math.min(amount, stack.getCount());
        stack.shrink(taken);
        return taken;
    }
}