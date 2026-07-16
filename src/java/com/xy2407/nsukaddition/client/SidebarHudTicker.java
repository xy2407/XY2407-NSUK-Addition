package com.xy2407.nsukaddition.client;

import com.xy2407.nsukaddition.client.hud.SidebarHudLayer;

/** 侧边栏 HUD 的切换与帧更新入口。 */
public final class SidebarHudTicker {

    private SidebarHudTicker() {
    }

    public static void toggle() {
        SidebarHudLayer.INSTANCE.toggle();
    }

    public static void updateFrame() {
        SidebarHudLayer.INSTANCE.updateAnimationFrame();
    }
}
