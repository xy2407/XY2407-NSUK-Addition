package com.xy2407.nsukaddition;

import com.xy2407.nsukaddition.common.advancement.NsukTriggers;
import com.xy2407.nsukaddition.common.breeding.BreedingDefinitionLoader;
import com.xy2407.nsukaddition.common.building.CommercialBuildingDeployer;
import com.xy2407.nsukaddition.common.building.IndustrialBuildingDeployer;

import com.xy2407.nsukaddition.common.compat.AquacultureFishCompat;
import com.xy2407.nsukaddition.common.compat.vinerykaleidoscope.VineryFluidCompat;
import com.xy2407.nsukaddition.common.cooking.RestaurantDefinitionLoader;
import com.xy2407.nsukaddition.common.farmland.ModFarmCropRegistry;
import com.xy2407.nsukaddition.common.foreigntrade.ForeignTradeBuildingDeployer;
import com.xy2407.nsukaddition.common.foreigntrade.ForeignTradeCategoryConfig;
import com.xy2407.nsukaddition.common.foreigntrade.ForeignTradeConfig;
import com.xy2407.nsukaddition.common.foreigntrade.VillageTradeConfig;
import com.xy2407.nsukaddition.common.entity.RtsFakePlayerEntity;
import com.xy2407.nsukaddition.common.item.EntityCaptureInteractHandler;
import com.xy2407.nsukaddition.common.menu.ModMenuTypes;
import com.xy2407.nsukaddition.common.registry.ModBlocks;
import com.xy2407.nsukaddition.common.registry.ModCreativeTabs;
import com.xy2407.nsukaddition.common.registry.ModEntities;
import com.xy2407.nsukaddition.common.registry.ModEntityItems;
import com.xy2407.nsukaddition.common.registry.ModFluids;
import com.xy2407.nsukaddition.common.registry.ModMilkFluids;
import com.xy2407.nsukaddition.server.autorestock.AutoRestockServerTick;
import com.xy2407.nsukaddition.server.ServerShutdownHandler;
import com.xy2407.nsukaddition.server.breeding.BreedingServerTick;
import com.xy2407.nsukaddition.server.cooking.RestaurantServerTick;
import com.xy2407.nsukaddition.server.city.CityMobSpawnPrevention;
import com.xy2407.nsukaddition.server.city.CityServerTick;
import com.xy2407.nsukaddition.server.combat.CitizenCombatService;
import com.xy2407.nsukaddition.server.combat.CitizenGunFriendlyFireHandler;
import com.xy2407.nsukaddition.server.village.VillageCityConverter;
import com.xy2407.nsukaddition.server.village.VillagerToNpcConverter;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.event.entity.EntityAttributeModificationEvent;
import net.minecraft.world.entity.ai.attributes.Attributes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** 模组主入口类，负责注册方块、菜单类型及各类服务端事件监听器。 */
@Mod(NsukAddition.MOD_ID)
public final class NsukAddition {

    public static final String MOD_ID = "xy2407_nsuk_addition";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public NsukAddition(IEventBus modEventBus, ModContainer modContainer) {
        ModBlocks.register(modEventBus);
        ModEntities.register(modEventBus);
        ModEntityItems.register(modEventBus);
        ModCreativeTabs.register(modEventBus);
        ModFluids.register(modEventBus);
        ModMilkFluids.register(modEventBus);
        ModMenuTypes.register(modEventBus);
        NsukTriggers.TRIGGERS.register(modEventBus);

        modEventBus.addListener(RegisterCapabilitiesEvent.class, AquacultureFishCompat::registerCapabilities);
        modEventBus.addListener(RegisterCapabilitiesEvent.class, VineryFluidCompat::registerCapabilities);
        modEventBus.addListener(RegisterCapabilitiesEvent.class, CitizenCombatService::registerCapabilities);

        modEventBus.addListener(EntityAttributeCreationEvent.class, event -> {
            event.put(ModEntities.RTS_FAKE_PLAYER.get(), RtsFakePlayerEntity.createAttributes().build());
        });

        modEventBus.addListener(EntityAttributeModificationEvent.class, event -> {
            event.add(common.cn.kafei.simukraft.registry.ModEntities.CITIZEN.get(), Attributes.ATTACK_DAMAGE, 3.0D);
        });

        modEventBus.addListener((FMLCommonSetupEvent event) -> {
            ModFarmCropRegistry.registerAll();
            event.enqueueWork(() -> {
                BreedingDefinitionLoader.deployFiles();
                RestaurantDefinitionLoader.deployFiles();
                IndustrialBuildingDeployer.deploy();
                ForeignTradeBuildingDeployer.deploy();
                CommercialBuildingDeployer.deploy();
                com.xy2407.nsukaddition.common.capture.CapturableEntityRegistry.load();
            });
        });

        BreedingDefinitionLoader.init();
        RestaurantDefinitionLoader.init();
        ForeignTradeConfig.init();
        VillageTradeConfig.init();
        ForeignTradeCategoryConfig.init();

        NeoForge.EVENT_BUS.register(CitizenGunFriendlyFireHandler.class);
        NeoForge.EVENT_BUS.register(EntityCaptureInteractHandler.class);
        NeoForge.EVENT_BUS.register(com.xy2407.nsukaddition.common.item.BlazeRodEntityIdHandler.class);
        NeoForge.EVENT_BUS.register(BreedingServerTick.class);
        NeoForge.EVENT_BUS.register(RestaurantServerTick.class);
        NeoForge.EVENT_BUS.register(CityServerTick.class);
        NeoForge.EVENT_BUS.register(CityMobSpawnPrevention.class);
        NeoForge.EVENT_BUS.register(AutoRestockServerTick.class);
        NeoForge.EVENT_BUS.register(ServerShutdownHandler.class);
        NeoForge.EVENT_BUS.register(VillageCityConverter.class);
        NeoForge.EVENT_BUS.register(VillagerToNpcConverter.class);
    }
}
