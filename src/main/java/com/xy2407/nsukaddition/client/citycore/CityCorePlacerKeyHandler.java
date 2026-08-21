package com.xy2407.nsukaddition.client.citycore;

import com.xy2407.nsukaddition.common.citycore.CityCoreRotationUtil;
import com.xy2407.nsukaddition.common.network.citycore.CityCoreRotatePacket;
import com.xy2407.nsukaddition.common.registry.ModBlocks;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

/** 城市核心图纸旋转按键处理：手持图纸按 R 每次顺时针旋转 90°。 */
@OnlyIn(Dist.CLIENT)
public final class CityCorePlacerKeyHandler {

    private static boolean prevRDown = false;

    private CityCorePlacerKeyHandler() {
    }

    public static void onTick() {
        boolean rDown = isKeyDown(GLFW.GLFW_KEY_R);
        if (rDown && !prevRDown) {
            rotateHeldItem();
        }
        prevRDown = rDown;
    }

    private static void rotateHeldItem() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen != null) {
            return;
        }
        ItemStack held = heldPlacer(mc.player);
        if (held == null || held.isEmpty()) {
            return;
        }
        int rotation = CityCoreRotationUtil.getRotation(held) + 90;
        CityCoreRotationUtil.setRotation(held, rotation);
        PacketDistributor.sendToServer(new CityCoreRotatePacket(rotation));
    }

    public static ItemStack heldPlacer(Player player) {
        if (player == null) {
            return ItemStack.EMPTY;
        }
        ItemStack main = player.getMainHandItem();
        if (main.is(ModBlocks.CITY_CORE_PLACER.get())) {
            return main;
        }
        ItemStack off = player.getOffhandItem();
        if (off.is(ModBlocks.CITY_CORE_PLACER.get())) {
            return off;
        }
        return ItemStack.EMPTY;
    }

    private static boolean isKeyDown(int key) {
        long window = Minecraft.getInstance().getWindow().getWindow();
        return GLFW.glfwGetKey(window, key) == GLFW.GLFW_PRESS;
    }
}
