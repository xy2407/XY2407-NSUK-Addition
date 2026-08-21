package com.xy2407.nsukaddition.common.network;

import com.xy2407.nsukaddition.NsukAddition;
import com.xy2407.nsukaddition.common.network.building.BuildTaskActionPacket;
import com.xy2407.nsukaddition.common.network.city.CityCoreMovePacket;
import com.xy2407.nsukaddition.common.network.city.CityCorePositionsPacket;
import com.xy2407.nsukaddition.common.network.city.CityUpgradeRequestPacket;
import com.xy2407.nsukaddition.common.network.city.ImmigrationActionPacket;
import com.xy2407.nsukaddition.common.network.city.ImmigrationListRequestPacket;
import com.xy2407.nsukaddition.common.network.city.ImmigrationListResponsePacket;
import com.xy2407.nsukaddition.common.network.colony.ColonyCreatePacket;
import com.xy2407.nsukaddition.common.network.colony.ColonyDeletePacket;
import com.xy2407.nsukaddition.common.network.colony.ColonyChunkBuyPacket;
import com.xy2407.nsukaddition.common.network.colony.ColonyChunkAbandonPacket;
import com.xy2407.nsukaddition.common.network.colony.ColonyCitizenReleasePacket;
import com.xy2407.nsukaddition.common.network.colony.ColonyCitizenRelocatePacket;
import com.xy2407.nsukaddition.common.network.colony.ColonyCoreMovePacket;
import com.xy2407.nsukaddition.common.network.colony.ColonyCoreOpenRequestPacket;
import com.xy2407.nsukaddition.common.network.colony.ColonyCoreOpenResponsePacket;
import com.xy2407.nsukaddition.common.network.colony.ColonyRenamePacket;
import com.xy2407.nsukaddition.common.network.colony.ColonyChunkSyncPacket;
import com.xy2407.nsukaddition.common.network.colony.ColonyChunkBatchBuyPacket;
import com.xy2407.nsukaddition.common.network.colony.ColonyChunkBatchAbandonPacket;
import com.xy2407.nsukaddition.common.network.breeding.BreedingControlBoxActionPacket;
import com.xy2407.nsukaddition.common.network.breeding.BreedingControlBoxDemolishPacket;
import com.xy2407.nsukaddition.common.network.breeding.BreedingControlBoxOpenRequestPacket;
import com.xy2407.nsukaddition.common.network.breeding.BreedingControlBoxOpenResponsePacket;
import com.xy2407.nsukaddition.common.network.breeding.BreedingControlBoxViewUpdatePacket;
import com.xy2407.nsukaddition.common.network.cooking.RestaurantControlBoxOpenRequestPacket;
import com.xy2407.nsukaddition.common.network.cooking.RestaurantControlBoxOpenResponsePacket;
import com.xy2407.nsukaddition.common.network.cooking.RestaurantControlBoxActionPacket;
import com.xy2407.nsukaddition.common.network.cooking.RestaurantControlBoxDemolishPacket;
import com.xy2407.nsukaddition.common.network.cooking.RestaurantMenuSelectPacket;
import com.xy2407.nsukaddition.common.network.cooking.RestaurantControlBoxViewUpdatePacket;
import com.xy2407.nsukaddition.common.network.cooking.DiningOrderSyncPacket;
import com.xy2407.nsukaddition.common.network.cooking.RestaurantMaidHireRequestPacket;
import com.xy2407.nsukaddition.common.network.cooking.RestaurantMaidHireResponsePacket;
import com.xy2407.nsukaddition.common.network.cooking.RestaurantMaidHireActionPacket;
import com.xy2407.nsukaddition.common.network.AutoRestockStatePacket;
import com.xy2407.nsukaddition.common.network.AutoRestockTogglePacket;
import com.xy2407.nsukaddition.common.network.foreigntrade.ForeignTradeControlBoxOpenRequestPacket;
import com.xy2407.nsukaddition.common.network.foreigntrade.ForeignTradeControlBoxOpenResponsePacket;
import com.xy2407.nsukaddition.common.network.foreigntrade.ForeignTradeControlBoxActionPacket;
import com.xy2407.nsukaddition.common.network.foreigntrade.ForeignTradeControlBoxDemolishPacket;
import com.xy2407.nsukaddition.common.network.foreigntrade.ForeignTradeTransactionPacket;
import com.xy2407.nsukaddition.common.network.foreigntrade.ForeignTradeMarketRequestPacket;
import com.xy2407.nsukaddition.common.network.foreigntrade.ForeignTradeMarketDataPacket;
import com.xy2407.nsukaddition.common.network.foreigntrade.ForeignTradeInventorySyncPacket;
import com.xy2407.nsukaddition.common.network.foreigntrade.FreeMarketListPacket;
import com.xy2407.nsukaddition.common.network.foreigntrade.FreeMarketBuyPacket;
import com.xy2407.nsukaddition.common.network.foreigntrade.FreeMarketCancelPacket;
import com.xy2407.nsukaddition.common.network.foreigntrade.FreeMarketModifyPacket;
import com.xy2407.nsukaddition.common.network.foreigntrade.FreeMarketDataPacket;
import com.xy2407.nsukaddition.common.network.foreigntrade.FreeMarketDataRequestPacket;
import com.xy2407.nsukaddition.common.network.foreigntrade.FreeMarketWarehouseRequestPacket;
import com.xy2407.nsukaddition.common.network.foreigntrade.FreeMarketWarehouseDataPacket;
import com.xy2407.nsukaddition.common.network.foreigntrade.DiplomacyDataRequestPacket;
import com.xy2407.nsukaddition.common.network.foreigntrade.DiplomacyDataPacket;
import com.xy2407.nsukaddition.common.network.foreigntrade.EstablishDiplomacyRequestPacket;
import com.xy2407.nsukaddition.common.network.citycore.CityCoreRotatePacket;
import com.xy2407.nsukaddition.common.network.citycore.CityGhostRequestPacket;
import com.xy2407.nsukaddition.common.network.citycore.CityGhostSyncPacket;
import com.xy2407.nsukaddition.common.network.rts.RtsAttackTargetClearPacket;
import com.xy2407.nsukaddition.common.network.rts.RtsAttackTargetPacket;
import com.xy2407.nsukaddition.common.network.rts.RtsFakePlayerSpawnPacket;
import com.xy2407.nsukaddition.common.network.rts.RtsInteractBlockPacket;
import com.xy2407.nsukaddition.common.network.rts.RtsJadeFocusPacket;
import com.xy2407.nsukaddition.common.network.rts.RtsMountPacket;
import com.xy2407.nsukaddition.common.network.rts.RtsMoveCommandPacket;
import com.xy2407.nsukaddition.common.network.rts.RtsPlayerTeleportPacket;
import com.xy2407.nsukaddition.common.network.rts.RtsSelectionSyncPacket;
import com.xy2407.nsukaddition.common.network.rts.RtsSelectionRequestPacket;
import com.xy2407.nsukaddition.common.network.rts.RtsSelectionCorrectionPacket;
import com.xy2407.nsukaddition.common.network.rts.RtsNpcListPacket;
import com.xy2407.nsukaddition.common.network.rts.RtsBuildingBoundsClearPacket;
import com.xy2407.nsukaddition.common.network.rts.RtsPlacedBuildingSyncPacket;
import com.xy2407.nsukaddition.common.network.rts.RtsBuildingMovePacket;
import com.xy2407.nsukaddition.common.network.rts.RtsStartBuildingPacket;
import com.xy2407.nsukaddition.common.network.rts.RtsStartBuildingResultPacket;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/** 模组网络包注册中心，统一注册所有自定义网络包的编解码器和处理函数。 */
@EventBusSubscriber(modid = NsukAddition.MOD_ID)
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

        r.playToServer(RestaurantControlBoxOpenRequestPacket.TYPE, RestaurantControlBoxOpenRequestPacket.STREAM_CODEC, RestaurantControlBoxOpenRequestPacket::handle);
        r.playToClient(RestaurantControlBoxOpenResponsePacket.TYPE, RestaurantControlBoxOpenResponsePacket.STREAM_CODEC, RestaurantControlBoxOpenResponsePacket::handle);
        r.playToClient(RestaurantControlBoxViewUpdatePacket.TYPE, RestaurantControlBoxViewUpdatePacket.STREAM_CODEC, RestaurantControlBoxViewUpdatePacket::handle);
        r.playToServer(RestaurantControlBoxActionPacket.TYPE, RestaurantControlBoxActionPacket.STREAM_CODEC, RestaurantControlBoxActionPacket::handle);
        r.playToServer(RestaurantControlBoxDemolishPacket.TYPE, RestaurantControlBoxDemolishPacket.STREAM_CODEC, RestaurantControlBoxDemolishPacket::handle);
        r.playToServer(RestaurantMenuSelectPacket.TYPE, RestaurantMenuSelectPacket.STREAM_CODEC, RestaurantMenuSelectPacket::handle);

        r.playToServer(RestaurantMaidHireRequestPacket.TYPE, RestaurantMaidHireRequestPacket.STREAM_CODEC, RestaurantMaidHireRequestPacket::handle);
        r.playToClient(RestaurantMaidHireResponsePacket.TYPE, RestaurantMaidHireResponsePacket.STREAM_CODEC, RestaurantMaidHireResponsePacket::handle);
        r.playToServer(RestaurantMaidHireActionPacket.TYPE, RestaurantMaidHireActionPacket.STREAM_CODEC, RestaurantMaidHireActionPacket::handle);

        r.playToClient(DiningOrderSyncPacket.TYPE, DiningOrderSyncPacket.STREAM_CODEC, DiningOrderSyncPacket::handle);

        r.playToServer(ImmigrationListRequestPacket.TYPE, ImmigrationListRequestPacket.STREAM_CODEC, ImmigrationListRequestPacket::handle);
        r.playToClient(ImmigrationListResponsePacket.TYPE, ImmigrationListResponsePacket.STREAM_CODEC, ImmigrationListResponsePacket::handle);
        r.playToServer(ImmigrationActionPacket.TYPE, ImmigrationActionPacket.STREAM_CODEC, ImmigrationActionPacket::handle);

        r.playToServer(CityUpgradeRequestPacket.TYPE, CityUpgradeRequestPacket.STREAM_CODEC, CityUpgradeRequestPacket::handle);

        r.playToServer(CityCoreMovePacket.TYPE, CityCoreMovePacket.STREAM_CODEC, CityCoreMovePacket::handle);
        r.playToServer(CityCoreRotatePacket.TYPE, CityCoreRotatePacket.STREAM_CODEC, CityCoreRotatePacket::handle);

        r.playToServer(CityGhostRequestPacket.TYPE, CityGhostRequestPacket.STREAM_CODEC, CityGhostRequestPacket::handle);
        r.playToClient(CityGhostSyncPacket.TYPE, CityGhostSyncPacket.STREAM_CODEC, CityGhostSyncPacket::handle);

        r.playToServer(ColonyCoreOpenRequestPacket.TYPE, ColonyCoreOpenRequestPacket.STREAM_CODEC, ColonyCoreOpenRequestPacket::handle);
        r.playToClient(ColonyCoreOpenResponsePacket.TYPE, ColonyCoreOpenResponsePacket.STREAM_CODEC, ColonyCoreOpenResponsePacket::handle);
        r.playToServer(ColonyCoreMovePacket.TYPE, ColonyCoreMovePacket.STREAM_CODEC, ColonyCoreMovePacket::handle);
        r.playToServer(ColonyCitizenRelocatePacket.TYPE, ColonyCitizenRelocatePacket.STREAM_CODEC, ColonyCitizenRelocatePacket::handle);
        r.playToServer(ColonyCitizenReleasePacket.TYPE, ColonyCitizenReleasePacket.STREAM_CODEC, ColonyCitizenReleasePacket::handle);
        r.playToServer(ColonyChunkBuyPacket.TYPE, ColonyChunkBuyPacket.STREAM_CODEC, ColonyChunkBuyPacket::handle);
        r.playToServer(ColonyChunkAbandonPacket.TYPE, ColonyChunkAbandonPacket.STREAM_CODEC, ColonyChunkAbandonPacket::handle);
        r.playToServer(ColonyRenamePacket.TYPE, ColonyRenamePacket.STREAM_CODEC, ColonyRenamePacket::handle);
        r.playToServer(ColonyCreatePacket.TYPE, ColonyCreatePacket.STREAM_CODEC, ColonyCreatePacket::handle);
        r.playToServer(ColonyDeletePacket.TYPE, ColonyDeletePacket.STREAM_CODEC, ColonyDeletePacket::handle);
        r.playToClient(ColonyChunkSyncPacket.TYPE, ColonyChunkSyncPacket.STREAM_CODEC, ColonyChunkSyncPacket::handle);
        r.playToServer(ColonyChunkBatchBuyPacket.TYPE, ColonyChunkBatchBuyPacket.STREAM_CODEC, ColonyChunkBatchBuyPacket::handle);
        r.playToServer(ColonyChunkBatchAbandonPacket.TYPE, ColonyChunkBatchAbandonPacket.STREAM_CODEC, ColonyChunkBatchAbandonPacket::handle);

        r.playToServer(AutoRestockTogglePacket.TYPE, AutoRestockTogglePacket.STREAM_CODEC, AutoRestockTogglePacket::handle);
        r.playBidirectional(AutoRestockStatePacket.TYPE, AutoRestockStatePacket.STREAM_CODEC, AutoRestockStatePacket::handle);

        r.playToServer(ContainerRoleQueryPacket.TYPE, ContainerRoleQueryPacket.STREAM_CODEC, ContainerRoleQueryPacket::handle);
        r.playToClient(ContainerRoleResponsePacket.TYPE, ContainerRoleResponsePacket.STREAM_CODEC, ContainerRoleResponsePacket::handle);

        r.playToServer(ForeignTradeControlBoxOpenRequestPacket.TYPE, ForeignTradeControlBoxOpenRequestPacket.STREAM_CODEC, ForeignTradeControlBoxOpenRequestPacket::handle);
        r.playToClient(ForeignTradeControlBoxOpenResponsePacket.TYPE, ForeignTradeControlBoxOpenResponsePacket.STREAM_CODEC, ForeignTradeControlBoxOpenResponsePacket::handle);
        r.playToServer(ForeignTradeControlBoxActionPacket.TYPE, ForeignTradeControlBoxActionPacket.STREAM_CODEC, ForeignTradeControlBoxActionPacket::handle);
        r.playToServer(ForeignTradeControlBoxDemolishPacket.TYPE, ForeignTradeControlBoxDemolishPacket.STREAM_CODEC, ForeignTradeControlBoxDemolishPacket::handle);
        r.playToServer(ForeignTradeTransactionPacket.TYPE, ForeignTradeTransactionPacket.STREAM_CODEC, ForeignTradeTransactionPacket::handle);
        r.playToServer(ForeignTradeMarketRequestPacket.TYPE, ForeignTradeMarketRequestPacket.STREAM_CODEC, ForeignTradeMarketRequestPacket::handle);
        r.playToClient(ForeignTradeMarketDataPacket.TYPE, ForeignTradeMarketDataPacket.STREAM_CODEC, ForeignTradeMarketDataPacket::handle);
        r.playToClient(ForeignTradeInventorySyncPacket.TYPE, ForeignTradeInventorySyncPacket.STREAM_CODEC, ForeignTradeInventorySyncPacket::handle);

        r.playToServer(FreeMarketListPacket.TYPE, FreeMarketListPacket.STREAM_CODEC, FreeMarketListPacket::handle);
        r.playToServer(FreeMarketBuyPacket.TYPE, FreeMarketBuyPacket.STREAM_CODEC, FreeMarketBuyPacket::handle);
        r.playToServer(FreeMarketCancelPacket.TYPE, FreeMarketCancelPacket.STREAM_CODEC, FreeMarketCancelPacket::handle);
        r.playToServer(FreeMarketModifyPacket.TYPE, FreeMarketModifyPacket.STREAM_CODEC, FreeMarketModifyPacket::handle);
        r.playToClient(FreeMarketDataPacket.TYPE, FreeMarketDataPacket.STREAM_CODEC, FreeMarketDataPacket::handle);
        r.playToServer(FreeMarketDataRequestPacket.TYPE, FreeMarketDataRequestPacket.STREAM_CODEC, FreeMarketDataRequestPacket::handle);
        r.playToServer(FreeMarketWarehouseRequestPacket.TYPE, FreeMarketWarehouseRequestPacket.STREAM_CODEC, FreeMarketWarehouseRequestPacket::handle);
        r.playToClient(FreeMarketWarehouseDataPacket.TYPE, FreeMarketWarehouseDataPacket.STREAM_CODEC, FreeMarketWarehouseDataPacket::handle);

        r.playToServer(DiplomacyDataRequestPacket.TYPE, DiplomacyDataRequestPacket.STREAM_CODEC, DiplomacyDataRequestPacket::handle);
        r.playToClient(DiplomacyDataPacket.TYPE, DiplomacyDataPacket.STREAM_CODEC, DiplomacyDataPacket::handle);
        r.playToServer(EstablishDiplomacyRequestPacket.TYPE, EstablishDiplomacyRequestPacket.STREAM_CODEC, EstablishDiplomacyRequestPacket::handle);
        r.playToClient(CityCorePositionsPacket.TYPE, CityCorePositionsPacket.STREAM_CODEC, CityCorePositionsPacket::handle);

        r.playToServer(RtsMoveCommandPacket.TYPE, RtsMoveCommandPacket.STREAM_CODEC, RtsMoveCommandPacket::handle);
        r.playToServer(RtsInteractBlockPacket.TYPE, RtsInteractBlockPacket.STREAM_CODEC, RtsInteractBlockPacket::handle);
        r.playToServer(RtsMountPacket.TYPE, RtsMountPacket.STREAM_CODEC, RtsMountPacket::handle);
        r.playToServer(RtsAttackTargetPacket.TYPE, RtsAttackTargetPacket.STREAM_CODEC, RtsAttackTargetPacket::handle);
        r.playToServer(RtsAttackTargetClearPacket.TYPE, RtsAttackTargetClearPacket.STREAM_CODEC, RtsAttackTargetClearPacket::handle);
        r.playToServer(RtsFakePlayerSpawnPacket.TYPE, RtsFakePlayerSpawnPacket.STREAM_CODEC, RtsFakePlayerSpawnPacket::handle);
        r.playToServer(RtsSelectionSyncPacket.TYPE, RtsSelectionSyncPacket.STREAM_CODEC, RtsSelectionSyncPacket::handle);
        r.playToServer(RtsSelectionRequestPacket.TYPE, RtsSelectionRequestPacket.STREAM_CODEC, RtsSelectionRequestPacket::handle);
        r.playToClient(RtsSelectionCorrectionPacket.TYPE, RtsSelectionCorrectionPacket.STREAM_CODEC, RtsSelectionCorrectionPacket::handle);
        r.playToClient(RtsNpcListPacket.TYPE, RtsNpcListPacket.STREAM_CODEC, RtsNpcListPacket::handle);
        r.playToServer(RtsPlayerTeleportPacket.TYPE, RtsPlayerTeleportPacket.STREAM_CODEC, RtsPlayerTeleportPacket::handle);
        r.playToServer(RtsStartBuildingPacket.TYPE, RtsStartBuildingPacket.STREAM_CODEC, RtsStartBuildingPacket::handle);
        r.playToServer(RtsJadeFocusPacket.TYPE, RtsJadeFocusPacket.STREAM_CODEC, RtsJadeFocusPacket::handle);
        r.playToClient(RtsPlacedBuildingSyncPacket.TYPE, RtsPlacedBuildingSyncPacket.STREAM_CODEC, RtsPlacedBuildingSyncPacket::handle);
        r.playToClient(RtsBuildingBoundsClearPacket.TYPE, RtsBuildingBoundsClearPacket.STREAM_CODEC, RtsBuildingBoundsClearPacket::handle);
        r.playToServer(RtsBuildingMovePacket.TYPE, RtsBuildingMovePacket.STREAM_CODEC, RtsBuildingMovePacket::handle);
        r.playToClient(RtsStartBuildingResultPacket.TYPE, RtsStartBuildingResultPacket.STREAM_CODEC, RtsStartBuildingResultPacket::handle);
    }
}
