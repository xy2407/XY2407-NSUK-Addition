package com.xy2407.nsukaddition.server.physicise;

import com.xy2407.nsukaddition.NsukAddition;
import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** 物理化建筑目录部署器：启动时自动创建 xy2407_nsuk_addition/physical 供玩家放入 .sk/.nbt 建筑。 */
public final class PhysicalBuildingDeployer {

    private PhysicalBuildingDeployer() {
    }

    public static void deploy() {
        try {
            Files.createDirectories(FMLPaths.GAMEDIR.get().resolve("xy2407_nsuk_addition/physical"));
        } catch (IOException e) {
            NsukAddition.LOGGER.warn("物理化建筑目录创建失败", e);
        }
    }
}