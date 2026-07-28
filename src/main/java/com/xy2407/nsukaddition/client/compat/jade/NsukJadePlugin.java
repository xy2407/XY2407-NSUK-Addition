package com.xy2407.nsukaddition.client.compat.jade;

import com.github.ysbbbbbb.kaleidoscopetavern.block.AbstractStorageBlock;
import com.github.ysbbbbbb.kaleidoscopetavern.block.brew.BarrelBlock;
import com.xy2407.nsukaddition.NsukAddition;
import com.xy2407.nsukaddition.client.compat.jade.block.ApplePressProvider;
import com.xy2407.nsukaddition.client.compat.jade.block.BarrelFluidProvider;
import com.xy2407.nsukaddition.client.compat.jade.block.StorageRackProvider;
import com.xy2407.nsukaddition.client.compat.jade.block.WineBottleProvider;
import net.minecraft.resources.ResourceLocation;
import net.satisfy.vinery.core.block.ApplePressBlock;
import net.satisfy.vinery.core.block.WineBottleBlock;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;

/** Jade 插件注册：为 Vinery ApplePress、酒瓶方块及 Kaleidoscope 存储架添加信息提示。 */
@WailaPlugin
public class NsukJadePlugin implements IWailaPlugin {

    public static final ResourceLocation APPLE_PRESS = ResourceLocation.fromNamespaceAndPath(NsukAddition.MOD_ID, "apple_press");
    public static final ResourceLocation WINE_BOTTLE = ResourceLocation.fromNamespaceAndPath(NsukAddition.MOD_ID, "wine_bottle");
    public static final ResourceLocation STORAGE_RACK = ResourceLocation.fromNamespaceAndPath(NsukAddition.MOD_ID, "storage_rack");
    public static final ResourceLocation BARREL_FLUID = ResourceLocation.fromNamespaceAndPath(NsukAddition.MOD_ID, "barrel_fluid");

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        registration.registerBlockComponent(ApplePressProvider.INSTANCE, ApplePressBlock.class);

        registration.registerBlockComponent(WineBottleProvider.INSTANCE, WineBottleBlock.class);

        registration.registerBlockComponent(StorageRackProvider.INSTANCE, AbstractStorageBlock.class);

        registration.registerBlockComponent(BarrelFluidProvider.INSTANCE, BarrelBlock.class);
    }
}
