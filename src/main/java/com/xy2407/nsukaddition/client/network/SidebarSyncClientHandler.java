package com.xy2407.nsukaddition.client.network;

import com.xy2407.nsukaddition.client.data.SidebarDataClient;
import com.xy2407.nsukaddition.common.network.SidebarSyncPacket;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.function.Consumer;

/** 侧边栏同步网络包的客户端消费者，将数据包转交 SidebarDataClient 处理。 */
@OnlyIn(Dist.CLIENT)
public final class SidebarSyncClientHandler implements Consumer<SidebarSyncPacket> {

    public static final SidebarSyncClientHandler INSTANCE = new SidebarSyncClientHandler();

    private SidebarSyncClientHandler() {
    }

    @Override
    public void accept(SidebarSyncPacket packet) {
        SidebarDataClient.handlePacket(packet);
    }
}
