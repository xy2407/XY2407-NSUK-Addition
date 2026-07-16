package com.xy2407.nsukaddition.client.city;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.common.NeoForge;

/** 城市核心迁移预览模式下的右键拦截。 */
@OnlyIn(Dist.CLIENT)
public final class CityCoreMoveInputHandler {

    private CityCoreMoveInputHandler() {}

    public static void register() {
        NeoForge.EVENT_BUS.addListener(CityCoreMoveInputHandler::onRightClick);
    }

    private static void onRightClick(InputEvent.InteractionKeyMappingTriggered event) {
        if (!CityCoreMovePreview.isActive()) return;
        if (event.isAttack()) return;

        if (event.isUseItem()) {
            if (CityCoreMovePreview.onRightClick()) {
                event.setCanceled(true);
            }
        }
    }
}
