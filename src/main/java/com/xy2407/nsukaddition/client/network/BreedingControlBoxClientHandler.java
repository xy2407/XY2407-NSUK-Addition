package com.xy2407.nsukaddition.client.network;

import com.xy2407.nsukaddition.client.breeding.BreedingControlBoxScreenOpener;
import com.xy2407.nsukaddition.common.network.breeding.BreedingControlBoxOpenResponsePacket;
import com.xy2407.nsukaddition.common.network.breeding.BreedingControlBoxViewUpdatePacket;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/** 养殖控制箱网络包的客户端处理入口，转发打开和更新事件到界面。 */
@OnlyIn(Dist.CLIENT)
public final class BreedingControlBoxClientHandler {

    private BreedingControlBoxClientHandler() {
    }

    public static void handleOpen(BreedingControlBoxOpenResponsePacket p) {
        BreedingControlBoxScreenOpener.open(p);
    }

    public static void handleUpdate(BreedingControlBoxViewUpdatePacket p) {
        BreedingControlBoxScreenOpener.refreshIfOpen(p.response());
    }
}
