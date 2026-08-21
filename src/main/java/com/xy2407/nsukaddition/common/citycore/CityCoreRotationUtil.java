package com.xy2407.nsukaddition.common.citycore;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

/** 城市核心图纸旋转状态工具，旋转值以 90° 为单位存入物品自定义数据组件。 */
public final class CityCoreRotationUtil {

    public static final String TAG_ROTATION = "nsuk_city_core_rotation";

    private CityCoreRotationUtil() {
    }

    public static int getRotation(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !stack.has(DataComponents.CUSTOM_DATA)) {
            return 0;
        }
        CompoundTag tag = stack.get(DataComponents.CUSTOM_DATA).copyTag();
        if (tag == null || !tag.contains(TAG_ROTATION, Tag.TAG_INT)) {
            return 0;
        }
        return Math.floorMod(tag.getInt(TAG_ROTATION), 360);
    }

    public static void setRotation(ItemStack stack, int rotationDegrees) {
        if (stack == null || stack.isEmpty()) {
            return;
        }
        stack.update(DataComponents.CUSTOM_DATA, CustomData.EMPTY, customData -> {
            CompoundTag tag = customData.copyTag();
            tag.putInt(TAG_ROTATION, Math.floorMod(rotationDegrees, 360));
            return CustomData.of(tag);
        });
    }
}
