package com.xy2407.nsukaddition.common.network.rts;

import com.xy2407.nsukaddition.NsukAddition;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * 已放置建筑同步请求包的独立注册点。
 * 该包原注册在 ModNetwork，但 Gradle 对 ModNetwork 的内容级增量缓存异常导致其始终未进入编译产物，
 * 客户端发送 rts_placed_building_sync_request 时被 NeoForge 网络层拒绝。此处单独注册，绕开缓存问题。
 */
@EventBusSubscriber(modid = NsukAddition.MOD_ID)
public final class RtsBuildingSyncPayloadRegistration {

    private RtsBuildingSyncPayloadRegistration() {
    }

    @SubscribeEvent
    public static void onRegisterPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToServer(RtsPlacedBuildingSyncRequestPacket.TYPE,
                RtsPlacedBuildingSyncRequestPacket.STREAM_CODEC,
                RtsPlacedBuildingSyncRequestPacket::handle);
    }
}
