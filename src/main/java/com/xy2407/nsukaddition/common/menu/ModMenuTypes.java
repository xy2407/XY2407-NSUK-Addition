package com.xy2407.nsukaddition.common.menu;

import com.lowdragmc.lowdraglib2.gui.holder.ModularUIContainerMenu;
import com.xy2407.nsukaddition.NsukAddition;
import com.xy2407.nsukaddition.common.mining.MiningControlBoxMenuProvider;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** 模组菜单类型注册中心，集中管理所有自定义容器菜单类型的注册与初始化。 */
@SuppressWarnings("null")
public final class ModMenuTypes {
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(BuiltInRegistries.MENU, NsukAddition.MOD_ID);

    public static final DeferredHolder<MenuType<?>, MenuType<ModularUIContainerMenu>> MINING_CONTROL_BOX = MENUS.register(
            "mining_control_box",
            () -> IMenuTypeExtension.create(MiningControlBoxMenuProvider::createClientMenu));

    private ModMenuTypes() {
    }

    public static void register(IEventBus modEventBus) {
        MENUS.register(modEventBus);
    }
}
