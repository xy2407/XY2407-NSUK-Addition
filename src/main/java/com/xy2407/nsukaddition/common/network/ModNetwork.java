package com.xy2407.nsukaddition.common.network;

import com.xy2407.nsukaddition.NsukAddition;
import com.xy2407.nsukaddition.common.network.building.BuildTaskActionPacket;
import com.xy2407.nsukaddition.common.network.city.CityUpgradeRequestPacket;
import com.xy2407.nsukaddition.common.network.city.ImmigrationActionPacket;
import com.xy2407.nsukaddition.common.network.city.ImmigrationListRequestPacket;
import com.xy2407.nsukaddition.common.network.city.ImmigrationListResponsePacket;
import com.xy2407.nsukaddition.common.network.breeding.BreedingControlBoxActionPacket;
import com.xy2407.nsukaddition.common.network.breeding.BreedingControlBoxDemolishPacket;
import com.xy2407.nsukaddition.common.network.breeding.BreedingControlBoxOpenRequestPacket;
import com.xy2407.nsukaddition.common.network.breeding.BreedingControlBoxOpenResponsePacket;
import com.xy2407.nsukaddition.common.network.breeding.BreedingControlBoxViewUpdatePacket;
import com.xy2407.nsukaddition.common.network.mining.MiningControlBoxActionPacket;
import com.xy2407.nsukaddition.common.network.mining.MiningControlBoxDemolishPacket;
import com.xy2407.nsukaddition.common.network.mining.MiningControlBoxOpenRequestPacket;
import com.xy2407.nsukaddition.common.network.mining.MiningControlBoxOpenResponsePacket;
import com.xy2407.nsukaddition.common.network.mining.MiningControlBoxViewUpdatePacket;
import com.xy2407.nsukaddition.common.network.vein.OreVeinChunkSyncPacket;
import com.xy2407.nsukaddition.common.network.AutoRestockTogglePacket;
import com.xy2407.nsukaddition.common.network.vein.OreVeinDiscoveryRequestPacket;
import com.xy2407.nsukaddition.common.network.vein.OreVeinDiscoveryResponsePacket;
import com.xy2407.nsukaddition.server.vein.OreVeinDiscoveryHandler;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/** 模组网络包注册中心，统一注册所有自定义网络包的编解码器和处理函数。 */
@SuppressWarnings("removal")
@EventBusSubscriber(modid = NsukAddition.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public final class ModNetwork {
    private ModNetwork() {}

    @SubscribeEvent
    public static void onRegister(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar r = event.registrar("1");
        r.playToClient(SidebarSyncPacket.TYPE, SidebarSyncPacket.STREAM_CODEC, SidebarSyncPacket::handle);

        r.playToServer(BuildTaskActionPacket.TYPE, BuildTaskActionPacket.STREAM_CODEC, BuildTaskActionPacket::handle);

        r.playToServer(BreedingControlBoxOpenRequestPacket.TYPE, BreedingControlBoxOpenRequestPacket.STREAM_CODEC, BreedingControlBoxOpenRequestPacket::handle);
        r.playToClient(BreedingControlBoxOpenResponsePacket.TYPE, BreedingControlBoxOpenResponsePacket.STREAM_CODEC, BreedingControlBoxOpenResponsePacket::handle);
        r.playToClient(BreedingControlBoxViewUpdatePacket.TYPE, BreedingControlBoxViewUpdatePacket.STREAM_CODEC, BreedingControlBoxViewUpdatePacket::handle);
        r.playToServer(BreedingControlBoxActionPacket.TYPE, BreedingControlBoxActionPacket.STREAM_CODEC, BreedingControlBoxActionPacket::handle);
        r.playToServer(BreedingControlBoxDemolishPacket.TYPE, BreedingControlBoxDemolishPacket.STREAM_CODEC, BreedingControlBoxDemolishPacket::handle);

        r.playToServer(MiningControlBoxOpenRequestPacket.TYPE, MiningControlBoxOpenRequestPacket.STREAM_CODEC, MiningControlBoxOpenRequestPacket::handle);
        r.playToClient(MiningControlBoxOpenResponsePacket.TYPE, MiningControlBoxOpenResponsePacket.STREAM_CODEC, MiningControlBoxOpenResponsePacket::handle);
        r.playToClient(MiningControlBoxViewUpdatePacket.TYPE, MiningControlBoxViewUpdatePacket.STREAM_CODEC, MiningControlBoxViewUpdatePacket::handle);
        r.playToServer(MiningControlBoxActionPacket.TYPE, MiningControlBoxActionPacket.STREAM_CODEC, MiningControlBoxActionPacket::handle);
        r.playToServer(MiningControlBoxDemolishPacket.TYPE, MiningControlBoxDemolishPacket.STREAM_CODEC, MiningControlBoxDemolishPacket::handle);

        r.playToClient(OreVeinChunkSyncPacket.TYPE, OreVeinChunkSyncPacket.STREAM_CODEC, OreVeinChunkSyncPacket::handle);

        r.playToServer(OreVeinDiscoveryRequestPacket.TYPE, OreVeinDiscoveryRequestPacket.STREAM_CODEC, OreVeinDiscoveryHandler::handle);
        r.playToClient(OreVeinDiscoveryResponsePacket.TYPE, OreVeinDiscoveryResponsePacket.STREAM_CODEC, OreVeinDiscoveryResponsePacket::handle);

        r.playToServer(ImmigrationListRequestPacket.TYPE, ImmigrationListRequestPacket.STREAM_CODEC, ImmigrationListRequestPacket::handle);
        r.playToClient(ImmigrationListResponsePacket.TYPE, ImmigrationListResponsePacket.STREAM_CODEC, ImmigrationListResponsePacket::handle);
        r.playToServer(ImmigrationActionPacket.TYPE, ImmigrationActionPacket.STREAM_CODEC, ImmigrationActionPacket::handle);

        r.playToServer(CityUpgradeRequestPacket.TYPE, CityUpgradeRequestPacket.STREAM_CODEC, CityUpgradeRequestPacket::handle);

        r.playToServer(AutoRestockTogglePacket.TYPE, AutoRestockTogglePacket.STREAM_CODEC, AutoRestockTogglePacket::handle);
    }
}
