package com.xy2407.nsukaddition.client;

import com.xy2407.nsukaddition.NsukAddition;
import com.xy2407.nsukaddition.client.city.CityCoreMovePreview;
import com.xy2407.nsukaddition.client.colony.ColonyCoreMovePreview;
import com.xy2407.nsukaddition.client.container.ContainerRoleQueryHandler;
import com.xy2407.nsukaddition.client.data.SidebarDataClient;
import com.xy2407.nsukaddition.client.keybind.ModKeyMappings;
import com.xy2407.nsukaddition.client.vein.OreVeinClientCache;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import org.lwjgl.glfw.GLFW;

/** 客户端游戏事件监听，处理键位触发、玩家登出清理和登入加载。 */
@OnlyIn(Dist.CLIENT)
public final class NsukAdditionGameClient {

    private static boolean prevEnter = false;
    private static boolean prevEscape = false;

    private NsukAdditionGameClient() {
    }

    @SubscribeEvent
    public static void onClientTickPost(ClientTickEvent.Post event) {
        while (ModKeyMappings.OPEN_SIDEBAR.consumeClick()) {
            SidebarHudTicker.toggle();
        }
        ContainerRoleQueryHandler.onClientTick();

        if (CityCoreMovePreview.isActive() || ColonyCoreMovePreview.isActive()) {
            Minecraft mc = Minecraft.getInstance();
            while (mc.options.keyInventory.consumeClick()) {}

            boolean enterNow = isKeyDown(GLFW.GLFW_KEY_ENTER);
            boolean escapeNow = isKeyDown(GLFW.GLFW_KEY_ESCAPE);

            if (enterNow && !prevEnter) {
                if (CityCoreMovePreview.isActive()) CityCoreMovePreview.onConfirm();
                else ColonyCoreMovePreview.onConfirm();
            }
            if (escapeNow && !prevEscape) {
                if (CityCoreMovePreview.isActive()) CityCoreMovePreview.onCancel();
                else ColonyCoreMovePreview.onCancel();
            }

            prevEnter = enterNow;
            prevEscape = escapeNow;
        } else {
            prevEnter = false;
            prevEscape = false;
        }
    }

    private static boolean isKeyDown(int keyCode) {
        long window = Minecraft.getInstance().getWindow().getWindow();
        return GLFW.glfwGetKey(window, keyCode) == GLFW.GLFW_PRESS;
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(ClientPlayerNetworkEvent.LoggingOut event) {
        CityCoreMovePreview.exit();
        ColonyCoreMovePreview.exit();
        OreVeinClientCache.getInstance().forceSaveToDisk();
        OreVeinClientCache.getInstance().clear();
        SidebarDataClient.reset();
    }

    @SubscribeEvent
    public static void onPlayerLoggedIn(ClientPlayerNetworkEvent.LoggingIn event) {
        OreVeinClientCache.getInstance().loadFromDisk();
    }
}
