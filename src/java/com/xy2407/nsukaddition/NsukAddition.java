package com.xy2407.nsukaddition;

import com.xy2407.nsukaddition.common.breeding.BreedingDefinitionLoader;
import com.xy2407.nsukaddition.common.building.FishBuildingDeployer;
import com.xy2407.nsukaddition.common.compat.AquacultureFishCompat;
import com.xy2407.nsukaddition.common.farmland.ModFarmCropRegistry;
import com.xy2407.nsukaddition.common.foreigntrade.ForeignTradeConfig;
import com.xy2407.nsukaddition.common.mining.MiningWorkService;
import com.xy2407.nsukaddition.common.menu.ModMenuTypes;
import com.xy2407.nsukaddition.common.registry.ModBlocks;
import com.xy2407.nsukaddition.server.autorestock.AutoRestockServerTick;
import com.xy2407.nsukaddition.server.breeding.BreedingServerTick;
import com.xy2407.nsukaddition.server.citizen.CitizenEquipmentCommand;
import com.xy2407.nsukaddition.server.citizen.CitizenEquipmentServerHandler;
import com.xy2407.nsukaddition.server.city.CityMobSpawnPrevention;
import com.xy2407.nsukaddition.server.city.CityServerTick;
import com.xy2407.nsukaddition.server.mining.MiningServerTick;
import com.xy2407.nsukaddition.server.village.VillageCityConverter;
import com.xy2407.nsukaddition.server.village.VillagerToNpcConverter;
import com.xy2407.nsukaddition.server.vein.OreVeinDropHandler;
import com.xy2407.nsukaddition.server.vein.OreVeinEventHandler;
import com.xy2407.nsukaddition.server.vein.OreVeinSyncService;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** 模组主入口类，负责注册方块、菜单类型及各类服务端事件监听器。 */
@Mod(NsukAddition.MOD_ID)
public final class NsukAddition {

    public static final String MOD_ID = "xy2407_nsuk_addition";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public NsukAddition(IEventBus modEventBus, ModContainer modContainer) {
        ModBlocks.register(modEventBus);
        ModMenuTypes.register(modEventBus);

        modEventBus.addListener(RegisterCapabilitiesEvent.class, AquacultureFishCompat::registerCapabilities);

        modEventBus.addListener((FMLCommonSetupEvent event) -> ModFarmCropRegistry.registerAll());

        BreedingDefinitionLoader.init();
        ForeignTradeConfig.init();

        FishBuildingDeployer.deploy();

        NeoForge.EVENT_BUS.register(BreedingServerTick.class);
        NeoForge.EVENT_BUS.register(MiningServerTick.class);
        NeoForge.EVENT_BUS.register(CityServerTick.class);
        NeoForge.EVENT_BUS.register(CityMobSpawnPrevention.class);
        NeoForge.EVENT_BUS.register(OreVeinEventHandler.class);
        NeoForge.EVENT_BUS.register(OreVeinSyncService.class);
        NeoForge.EVENT_BUS.register(AutoRestockServerTick.class);
        NeoForge.EVENT_BUS.register(VillageCityConverter.class);
        NeoForge.EVENT_BUS.register(VillagerToNpcConverter.class);
        NeoForge.EVENT_BUS.register(CitizenEquipmentServerHandler.class);
        NeoForge.EVENT_BUS.addListener(CitizenEquipmentCommand::register);

        MiningWorkService.setVeinDropProcessor(OreVeinDropHandler::process);
    }
}
