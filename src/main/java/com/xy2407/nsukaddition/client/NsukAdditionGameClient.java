package com.xy2407.nsukaddition.client;

import com.xy2407.nsukaddition.NsukAddition;
import com.xy2407.nsukaddition.client.data.SidebarDataClient;
import com.xy2407.nsukaddition.client.keybind.ModKeyMappings;
import com.xy2407.nsukaddition.client.vein.OreVeinClientCache;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;

/** 客户端游戏事件监听，处理键位触发、玩家登出清理和登入加载。 */
@OnlyIn(Dist.CLIENT)
public final class NsukAdditionGameClient {

    private NsukAdditionGameClient() {
    }

    @SubscribeEvent
    public static void onClientTickPost(ClientTickEvent.Post event) {
        while (ModKeyMappings.OPEN_SIDEBAR.consumeClick()) {
            SidebarHudTicker.toggle();
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(ClientPlayerNetworkEvent.LoggingOut event) {

        OreVeinClientCache.getInstance().forceSaveToDisk();
        OreVeinClientCache.getInstance().clear();
        SidebarDataClient.reset();
    }

    @SubscribeEvent
    public static void onPlayerLoggedIn(ClientPlayerNetworkEvent.LoggingIn event) {
        OreVeinClientCache.getInstance().loadFromDisk();
    }
}
