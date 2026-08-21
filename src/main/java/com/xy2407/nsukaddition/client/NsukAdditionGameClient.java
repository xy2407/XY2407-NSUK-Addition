package com.xy2407.nsukaddition.client;

import com.xy2407.nsukaddition.NsukAddition;
import com.xy2407.nsukaddition.client.citycore.CityCorePlacerKeyHandler;
import com.xy2407.nsukaddition.client.citycore.CityGhostRenderer;
import com.xy2407.nsukaddition.client.city.CityCoreMovePreview;
import com.xy2407.nsukaddition.client.colony.ColonyCoreMovePreview;
import com.xy2407.nsukaddition.client.container.ContainerRoleQueryHandler;
import com.xy2407.nsukaddition.client.data.SidebarDataClient;
import com.xy2407.nsukaddition.client.keybind.ModKeyMappings;
import com.xy2407.nsukaddition.client.rts.RtsInputHandler;
import com.xy2407.nsukaddition.client.rts.RtsModeManager;
import com.xy2407.nsukaddition.client.title.ModTitleScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import org.lwjgl.glfw.GLFW;

/** 客户端游戏事件监听，处理键位触发、玩家登出清理和登入加载。 */
@OnlyIn(Dist.CLIENT)
public final class NsukAdditionGameClient {

    private static boolean prevEnter = false;
    private static boolean prevEscape = false;
    private static boolean prevTilde = false;
    private static boolean prevPlayerDead = false;

    private NsukAdditionGameClient() {
    }

    @SubscribeEvent
    public static void onClientTickPost(ClientTickEvent.Post event) {
        while (ModKeyMappings.OPEN_SIDEBAR.consumeClick()) {
            SidebarHudTicker.toggle();
        }
        while (ModKeyMappings.TOGGLE_RTS_MODE.consumeClick()) {
            RtsModeManager.toggle();
        }
        while (ModKeyMappings.TOGGLE_RTS_ORTHO.consumeClick()) {
            if (RtsModeManager.isActive()) {
                RtsModeManager.setOrthoEnabled(!RtsModeManager.isOrthoEnabled());
            }
        }

        Minecraft mc = Minecraft.getInstance();
        if (RtsModeManager.isActive() && mc.player != null) {
            boolean deadNow = mc.player.isDeadOrDying();
            if (deadNow && !prevPlayerDead) {
                RtsModeManager.onPlayerDeath();
            } else if (!deadNow && prevPlayerDead) {
                RtsModeManager.syncCameraToRespawn();
            }
            prevPlayerDead = deadNow;
        } else {
            prevPlayerDead = false;
        }

        RtsModeManager.tickCamera();
        RtsInputHandler.onTick();

        CityCorePlacerKeyHandler.onTick();

        CityGhostRenderer.onClientTick();

        RtsModeManager.setCtrlHeld(isKeyDown(GLFW.GLFW_KEY_LEFT_CONTROL));

        boolean tildeNow = isKeyDown(GLFW.GLFW_KEY_GRAVE_ACCENT);
        if (tildeNow && !prevTilde && RtsModeManager.isActive()) {
            RtsModeManager.clearAllAttackTargets();
        }
        prevTilde = tildeNow;

        ContainerRoleQueryHandler.onClientTick();

        if (CityCoreMovePreview.isActive() || ColonyCoreMovePreview.isActive()) {
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
        RtsModeManager.onLogout();
        SidebarDataClient.reset();
        CityGhostRenderer.onLogout();
    }

    @SubscribeEvent
    public static void onPlayerLoggedIn(ClientPlayerNetworkEvent.LoggingIn event) {
    }

    @SubscribeEvent
    public static void onScreenOpening(ScreenEvent.Opening event) {
        if (event.getNewScreen() instanceof TitleScreen) {
            event.setNewScreen(new ModTitleScreen());
        }
    }
}
