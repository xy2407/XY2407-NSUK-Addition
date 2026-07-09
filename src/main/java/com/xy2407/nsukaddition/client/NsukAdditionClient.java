package com.xy2407.nsukaddition.client;

import com.lowdragmc.lowdraglib2.gui.holder.ModularUIContainerScreen;
import com.xy2407.nsukaddition.NsukAddition;
import com.xy2407.nsukaddition.client.container.ContainerRoleHudLayer;
import com.xy2407.nsukaddition.client.hud.FpsHudLayer;
import com.xy2407.nsukaddition.client.hud.SidebarHudLayer;
import com.xy2407.nsukaddition.client.gui.CitizenEquipmentScreen;
import com.xy2407.nsukaddition.client.keybind.ModKeyMappings;
import com.xy2407.nsukaddition.client.city.CityCoreMoveInputHandler;
import com.xy2407.nsukaddition.client.city.CityCoreMovePreview;
import com.xy2407.nsukaddition.client.city.CityCoreMoveRenderer;
import com.xy2407.nsukaddition.client.network.SidebarSyncClientHandler;
import com.xy2407.nsukaddition.client.renderer.TouristStatusRenderer;
import com.xy2407.nsukaddition.client.vein.OreVeinRenderToggleHandler;
import com.xy2407.nsukaddition.common.menu.ModMenuTypes;
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
@SuppressWarnings("removal") 
@OnlyIn(Dist.CLIENT)
@EventBusSubscriber(modid = NsukAddition.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
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

    /**
     * 创建市民模型层定义，在 body 节点下挂接胸部部件。
     *
     * 胸部几何数据与缩放公式移植自 Minecraft Comes Alive (MCA)
     * Licensed under GPL-3.0: https://github.com/Luke100000/minecraft-comes-alive
     */
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
        event.register(ModMenuTypes.MINING_CONTROL_BOX.get(), ModularUIContainerScreen::new);
        event.register(ModMenuTypes.CITIZEN_EQUIPMENT.get(), CitizenEquipmentScreen::new);
    }

    @SubscribeEvent
    public static void onRegisterGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAboveAll(
                ResourceLocation.fromNamespaceAndPath(NsukAddition.MOD_ID, "sidebar_hud"),
                SidebarHudLayer.INSTANCE
        );
        event.registerAboveAll(
                ResourceLocation.fromNamespaceAndPath(NsukAddition.MOD_ID, "fps_hud"),
                FpsHudLayer.INSTANCE
        );
        event.registerAboveAll(
                ResourceLocation.fromNamespaceAndPath(NsukAddition.MOD_ID, "container_role_hud"),
                ContainerRoleHudLayer.INSTANCE
        );
    }

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        NeoForge.EVENT_BUS.register(NsukAdditionGameClient.class);

        SidebarSyncBridge.install(SidebarSyncClientHandler.INSTANCE);

        OreVeinRenderToggleHandler.register();

        TouristStatusRenderer.register();

        NeoForge.EVENT_BUS.addListener(CityCoreMoveRenderer::onRenderLevel);

        CityCoreMoveInputHandler.register();
    }
}
