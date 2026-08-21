package com.xy2407.nsukaddition.common.foreigntrade;

import com.xy2407.nsukaddition.NsukAddition;
import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

/** 将外贸建筑资源从模组jar部署到游戏运行目录。 */
public final class ForeignTradeBuildingDeployer {

    private static final String EXTERNAL_DIR = "xy2407_nsuk_addition/foreign_trade";
    private static final String RESOURCE_PREFIX = "/data/xy2407_nsuk_addition/foreign_trade/";

    private static final List<String> FILES = List.of(
            "big.nbt", "big.sk", "big.json",
            "small.nbt", "small.sk", "small.json"
    );

    private ForeignTradeBuildingDeployer() {}

    public static void deploy() {
        Path dirPath = FMLPaths.GAMEDIR.get().resolve(EXTERNAL_DIR);
        try {
            Files.createDirectories(dirPath);
        } catch (IOException e) {
            NsukAddition.LOGGER.error("nsuk_addition: Failed to create foreign_trade directory", e);
            return;
        }

        for (String file : FILES) {
            String resourcePath = RESOURCE_PREFIX + file;
            try (InputStream is = ForeignTradeBuildingDeployer.class.getResourceAsStream(resourcePath)) {
                if (is == null) {
                    NsukAddition.LOGGER.warn("nsuk_addition: Missing foreign_trade resource: {}", resourcePath);
                    continue;
                }
                Path targetPath = dirPath.resolve(file);
                Files.copy(is, targetPath, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                NsukAddition.LOGGER.error("nsuk_addition: Failed to deploy foreign_trade file: {}", file, e);
            }
        }

        NsukAddition.LOGGER.info("nsuk_addition: Deployed foreign_trade files to {}", dirPath);
    }
}