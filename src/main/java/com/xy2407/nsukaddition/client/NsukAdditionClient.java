package com.xy2407.nsukaddition.client;

import com.xy2407.nsukaddition.NsukAddition;
import com.xy2407.nsukaddition.client.container.ContainerRoleHudLayer;
import com.xy2407.nsukaddition.client.hud.CoreMoveHudLayer;
import com.xy2407.nsukaddition.client.hud.SidebarHudLayer;
import com.xy2407.nsukaddition.client.keybind.ModKeyMappings;
import com.xy2407.nsukaddition.client.city.CityCoreMoveInputHandler;
import com.xy2407.nsukaddition.client.city.CityCoreMovePreview;
import com.xy2407.nsukaddition.client.city.CityCoreMoveRenderer;
import com.xy2407.nsukaddition.client.citycore.CityCorePlacerRenderer;
import com.xy2407.nsukaddition.client.citycore.CityGhostRenderer;
import com.xy2407.nsukaddition.client.colony.ColonyCoreMoveInputHandler;
import com.xy2407.nsukaddition.client.colony.ColonyCoreMoveRenderer;
import com.xy2407.nsukaddition.client.network.DiningOrderClientHandler;
import com.xy2407.nsukaddition.client.network.SidebarSyncClientHandler;
import com.xy2407.nsukaddition.client.render.EntityCaptureItemRenderer;
import com.xy2407.nsukaddition.client.renderer.EmptyRenderer;
import com.xy2407.nsukaddition.client.renderer.RtsFakePlayerRenderer;
import com.xy2407.nsukaddition.client.renderer.TouristStatusRenderer;
import com.xy2407.nsukaddition.client.rts.RtsSelectionRenderer;
import com.xy2407.nsukaddition.client.rts.RtsBuildingListHudLayer;
import com.xy2407.nsukaddition.client.rts.RtsBuildingBoundaryRenderer;
import com.xy2407.nsukaddition.client.rts.RtsPlacedBuildingCache;
import com.xy2407.nsukaddition.client.rts.RtsBuildingPlacementManager;
import com.xy2407.nsukaddition.common.registry.ModEntityItems;
import com.xy2407.nsukaddition.client.rts.RtsEntityGlowRenderer;

import com.xy2407.nsukaddition.client.autorestock.ClientAutoRestockCache;
import com.xy2407.nsukaddition.common.registry.ModEntities;
import com.xy2407.nsukaddition.client.breeding.BreedingControlBoxScreenOpener;
import com.xy2407.nsukaddition.client.colony.ColonyCoreScreenOpener;
import com.xy2407.nsukaddition.client.colony.ColonyChunkClientCache;
import com.xy2407.nsukaddition.client.colony.ColonyChunkMapElement;
import com.xy2407.nsukaddition.client.container.ContainerRoleClientCache;
import com.xy2407.nsukaddition.client.cooking.RestaurantControlBoxScreenOpener;
import com.xy2407.nsukaddition.client.cooking.RestaurantMaidHireScreenOpener;
import com.xy2407.nsukaddition.client.foreigntrade.DiplomacyClientCache;
import com.xy2407.nsukaddition.client.foreigntrade.ForeignTradeControlBoxScreenOpener;
import com.xy2407.nsukaddition.client.foreigntrade.ForeignTradeMenuScreenOpener;
import com.xy2407.nsukaddition.client.hud.ImmigrationScreen;
import com.xy2407.nsukaddition.common.network.clientbound.BreedingControlBoxBridge;
import com.xy2407.nsukaddition.common.network.clientbound.ColonyCoreBridge;
import com.xy2407.nsukaddition.common.network.clientbound.RtsPlacedBuildingSyncBridge;
import com.xy2407.nsukaddition.common.network.clientbound.RtsBuildingBoundsClearBridge;
import com.xy2407.nsukaddition.common.network.clientbound.RtsStartBuildingResultBridge;
import com.xy2407.nsukaddition.common.network.clientbound.ContainerRoleBridge;
import com.xy2407.nsukaddition.common.network.clientbound.DiningOrderBridge;
import com.xy2407.nsukaddition.common.network.clientbound.DiplomacyDataBridge;
import com.xy2407.nsukaddition.common.network.clientbound.ForeignTradeControlBoxBridge;
import com.xy2407.nsukaddition.common.network.clientbound.RestaurantControlBoxBridge;
import com.xy2407.nsukaddition.common.network.clientbound.RestaurantMaidHireBridge;
import com.xy2407.nsukaddition.common.network.clientbound.ForeignTradeInventorySyncBridge;
import com.xy2407.nsukaddition.common.network.clientbound.ForeignTradeMarketDataBridge;
import com.xy2407.nsukaddition.common.network.clientbound.FreeMarketDataBridge;
import com.xy2407.nsukaddition.common.network.clientbound.FreeMarketWarehouseDataBridge;
import com.xy2407.nsukaddition.common.network.clientbound.ImmigrationScreenBridge;
import com.xy2407.nsukaddition.common.network.clientbound.AutoRestockStateBridge;
import com.xy2407.nsukaddition.common.network.clientbound.DiningOrderBridge;
import com.xy2407.nsukaddition.common.network.clientbound.CityGhostSyncBridge;
import com.xy2407.nsukaddition.common.network.clientbound.SidebarSyncBridge;

import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.common.NeoForge;

/** 客户端模组事件总线初始化，注册模型层、键位、屏幕和 HUD 图层。 */
@OnlyIn(Dist.CLIENT)
@EventBusSubscriber(modid = NsukAddition.MOD_ID, value = Dist.CLIENT)
public final class NsukAdditionClient {

    public static final ModelLayerLocation CITIZEN = new ModelLayerLocation(
            ResourceLocation.fromNamespaceAndPath(NsukAddition.MOD_ID, "citizen"), "main");
    public static final ModelLayerLocation CITIZEN_SLIM = new ModelLayerLocation(
            ResourceLocation.fromNamespaceAndPath(NsukAddition.MOD_ID, "citizen_slim"), "main");

    private NsukAdditionClient() {
    }

    @SubscribeEvent
    public static void onRegisterLayerDefinitions(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(CITIZEN, () -> createCitizenLayerDefinition(false));
        event.registerLayerDefinition(CITIZEN_SLIM, () -> createCitizenLayerDefinition(true));
    }

    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.SIT_ENTITY.get(), EmptyRenderer::new);
        event.registerEntityRenderer(ModEntities.RTS_FAKE_PLAYER.get(), RtsFakePlayerRenderer::new);
    }

    private static LayerDefinition createCitizenLayerDefinition(boolean slim) {
        MeshDefinition mesh = PlayerModel.createMesh(CubeDeformation.NONE, slim);
        PartDefinition root = mesh.getRoot();
        PartDefinition body = root.getChild("body");
        body.addOrReplaceChild("nsukaddition_breasts",
                CubeListBuilder.create().texOffs(18, 21)
                        .addBox(-3.25F, -1.25F, -1.5F, 6, 3, 3, CubeDeformation.NONE),
                PartPose.ZERO);
        return LayerDefinition.create(mesh, 64, 64);
    }

    @SubscribeEvent
    public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        ModKeyMappings.register(event);
    }

    @SubscribeEvent
    public static void onRegisterMenuScreens(RegisterMenuScreensEvent event) {
    }

    @SubscribeEvent
    public static void onRegisterGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAboveAll(
                ResourceLocation.fromNamespaceAndPath(NsukAddition.MOD_ID, "sidebar_hud"),
                SidebarHudLayer.INSTANCE
        );
        event.registerAboveAll(
                ResourceLocation.fromNamespaceAndPath(NsukAddition.MOD_ID, "container_role_hud"),
                ContainerRoleHudLayer.INSTANCE
        );
        event.registerAboveAll(
                ResourceLocation.fromNamespaceAndPath(NsukAddition.MOD_ID, "core_move_hud"),
                CoreMoveHudLayer.INSTANCE
        );
        event.registerAboveAll(
                ResourceLocation.fromNamespaceAndPath(NsukAddition.MOD_ID, "rts_building_list_hud"),
                RtsBuildingListHudLayer.INSTANCE
        );
        event.registerAboveAll(
                ResourceLocation.fromNamespaceAndPath(NsukAddition.MOD_ID, "rts_selection_hud"),
                RtsSelectionRenderer.INSTANCE
        );
    }

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        NeoForge.EVENT_BUS.register(NsukAdditionGameClient.class);

        SidebarSyncBridge.install(SidebarSyncClientHandler.INSTANCE);

        RtsPlacedBuildingSyncBridge.install(RtsPlacedBuildingCache::applySync);
        RtsBuildingBoundsClearBridge.install(() -> client.cn.kafei.simukraft.client.buildbox.BuildingBoundsRenderer.clearAll());
        RtsStartBuildingResultBridge.install(RtsBuildingPlacementManager::onResult);
        com.xy2407.nsukaddition.common.network.clientbound.RtsSelectionCorrectionBridge.install(
                ids -> com.xy2407.nsukaddition.client.rts.RtsModeManager.setSelectedEntities(ids));
        com.xy2407.nsukaddition.common.network.clientbound.RtsNpcListBridge.install(
                com.xy2407.nsukaddition.client.rts.RtsNpcCache::apply);

        CityGhostSyncBridge.install(CityGhostRenderer::applySnapshot);

        AutoRestockStateBridge.install(ClientAutoRestockCache::setFromServer);
        DiningOrderBridge.install(DiningOrderClientHandler::handle);

        BreedingControlBoxBridge.install(BreedingControlBoxScreenOpener::open, BreedingControlBoxScreenOpener::refreshIfOpen);
        RestaurantControlBoxBridge.install(RestaurantControlBoxScreenOpener::open, RestaurantControlBoxScreenOpener::refreshIfOpen);
        RestaurantMaidHireBridge.install(RestaurantMaidHireScreenOpener::open);

        ContainerRoleBridge.install(ContainerRoleClientCache::setResponse);

        ImmigrationScreenBridge.install(ImmigrationScreen::refresh);

        ColonyCoreBridge.install(ColonyCoreScreenOpener::open,
                (colonyId, chunks) -> {
                    ColonyChunkClientCache.getInstance().removeColony(colonyId);
                    ColonyChunkMapElement.onColonyRemoved(colonyId);
                },
                (colonyId, name, parentName, parentId, chunks) -> {
                    ColonyChunkClientCache.getInstance().updateFromSync(colonyId, name, parentId, chunks);
                    ColonyChunkMapElement.onColonyChunkSync(colonyId, chunks);
                }
        );

        ForeignTradeControlBoxBridge.install(ForeignTradeControlBoxScreenOpener::open);
        ForeignTradeMarketDataBridge.install(ForeignTradeMenuScreenOpener::openWithMarketData);
        ForeignTradeInventorySyncBridge.install(ForeignTradeMenuScreenOpener::updateAvailableCounts);
        FreeMarketDataBridge.install(ForeignTradeMenuScreenOpener::updateFreeMarketData);
        FreeMarketWarehouseDataBridge.install(ForeignTradeMenuScreenOpener::updateWarehouseData);
        DiplomacyDataBridge.install(DiplomacyClientCache::update);

        TouristStatusRenderer.register();

        NeoForge.EVENT_BUS.addListener(CityCoreMoveRenderer::onRenderLevel);
        NeoForge.EVENT_BUS.addListener(ColonyCoreMoveRenderer::onRenderLevel);
        NeoForge.EVENT_BUS.addListener(RtsEntityGlowRenderer::onRenderLevel);
        NeoForge.EVENT_BUS.addListener(CityCorePlacerRenderer::onRenderLevel);
        NeoForge.EVENT_BUS.addListener(CityGhostRenderer::onRenderLevel);
        NeoForge.EVENT_BUS.addListener(RtsBuildingBoundaryRenderer::onRenderLevel);

        CityCoreMoveInputHandler.register();
        ColonyCoreMoveInputHandler.register();
    }

    @SubscribeEvent
    public static void onRegisterClientExtensions(net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent event) {
        net.neoforged.neoforge.client.extensions.common.IClientItemExtensions extensions =
                new net.neoforged.neoforge.client.extensions.common.IClientItemExtensions() {
                    @Override
                    public net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer getCustomRenderer() {
                        return EntityCaptureItemRenderer.getInstance();
                    }
                };
        event.registerItem(extensions, ModEntityItems.ENTITY_CAPTURE.get());
    }
}