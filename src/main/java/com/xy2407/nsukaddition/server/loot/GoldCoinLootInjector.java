package com.xy2407.nsukaddition.server.loot;

import common.cn.kafei.simukraft.registry.ModItems;
import com.xy2407.nsukaddition.NsukAddition;
import java.util.Locale;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.functions.SetItemCountFunction;
import net.minecraft.world.level.storage.loot.providers.number.UniformGenerator;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.LootTableLoadEvent;

/** 为所有箱子类战利品随机注入 simukraft 金币，占 1~4 格，总数量约 4~24。 */
@EventBusSubscriber(modid = NsukAddition.MOD_ID)
public final class GoldCoinLootInjector {

    private GoldCoinLootInjector() {}

    @SubscribeEvent
    public static void onLootTableLoad(LootTableLoadEvent event) {
        Item goldCoin = ModItems.GOLD_COIN.get();
        if (goldCoin == Items.AIR) return;
        String path = event.getName().getPath().toLowerCase(Locale.ROOT);
        if (!path.contains("chest")) return;
        LootPool pool = LootPool.lootPool()
                .setRolls(UniformGenerator.between(1.0F, 4.0F))
                .add(LootItem.lootTableItem(goldCoin).apply(
                        SetItemCountFunction.setCount(UniformGenerator.between(4.0F, 6.0F))))
                .build();
        event.getTable().addPool(pool);
    }
}