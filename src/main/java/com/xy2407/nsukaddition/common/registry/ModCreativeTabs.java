package com.xy2407.nsukaddition.common.registry;

import com.xy2407.nsukaddition.NsukAddition;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * nsuk 创造标签页:容纳生物物品等 nsuk 专属物品(不塞进 simukraft 标签页)。
 */
public final class ModCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, NsukAddition.MOD_ID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> NSUK_TAB = TABS.register("nsuk", () ->
            CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.nsuk"))
                    .icon(() -> new ItemStack(ModEntityItems.ENTITY_CAPTURE.get()))
                    .displayItems((parameters, output) -> output.accept(ModEntityItems.ENTITY_CAPTURE.get()))
                    .build());

    private ModCreativeTabs() {
    }

    public static void register(IEventBus modEventBus) {
        TABS.register(modEventBus);
    }
}
