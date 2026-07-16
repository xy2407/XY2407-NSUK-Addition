package com.xy2407.nsukaddition.common.building;

import com.xy2407.nsukaddition.NsukAddition;
import common.cn.kafei.simukraft.building.BuildingPackageCatalog;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** 服务器启动时将内嵌建筑包资源部署到SimuKraft建筑目录。 */
@EventBusSubscriber(modid = NsukAddition.MOD_ID)
public final class BuildingPackageDeployService {
    private static final String RESOURCE_PATH = "assets/xy2407_nsuk_addition/building/xy2407_nsuk_addition_buildings.zip";
    private static final String PACKAGE_FILE = "xy2407_nsuk_addition_buildings.zip";
    private static final Set<String> DEPLOYED_ROOTS = ConcurrentHashMap.newKeySet();

    private BuildingPackageDeployService() {}

    @SubscribeEvent
    public static void onServerAboutToStart(ServerAboutToStartEvent event) {
        Path rootDir = BuildingPackageCatalog.rootDirectory();
        String rootKey = rootDir.toString().toLowerCase(Locale.ROOT);
        if (DEPLOYED_ROOTS.contains(rootKey)) return;
        DEPLOYED_ROOTS.add(rootKey);

        Path targetFile = rootDir.resolve(PACKAGE_FILE);
        if (Files.exists(targetFile)) return;

        try (InputStream in = BuildingPackageDeployService.class.getClassLoader().getResourceAsStream(RESOURCE_PATH)) {
            if (in == null) {
                NsukAddition.LOGGER.warn("NSUK: Building package not found in resources: {}", RESOURCE_PATH);
                return;
            }
            Files.createDirectories(rootDir);
            Files.copy(in, targetFile);
            NsukAddition.LOGGER.info("NSUK: Deployed building package to {}", targetFile);
        } catch (IOException e) {
            NsukAddition.LOGGER.error("NSUK: Failed to deploy building package", e);
        }
    }
}
