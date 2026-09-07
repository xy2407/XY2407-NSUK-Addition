package com.xy2407.nsukaddition.common.modpack;

import com.xy2407.nsukaddition.NsukAddition;
import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** 更新日志部署器：首次启动时将内置更新日志 md 资源部署到游戏目录，供整合包信息界面读取。 */
public final class ChangelogDeployer {

    private static final String EXTERNAL_DIR = "xy2407_nsuk_addition";
    private static final String FILE_NAME = "changelog.md";
    private static final String RESOURCE_PATH = "/data/xy2407_nsuk_addition/changelog.md";
    private static final String VERSION_ENTRY = "_nsuk_changelog_version.txt";
    private static final String CURRENT_VERSION = "1";

    private ChangelogDeployer() {
    }

    public static void deployFiles() {
        Path dirPath = FMLPaths.GAMEDIR.get().resolve(EXTERNAL_DIR);
        try {
            Files.createDirectories(dirPath);
        } catch (IOException e) {
            NsukAddition.LOGGER.error("nsuk_addition: Failed to create modpack directory", e);
            return;
        }
        if (isUpToDate(dirPath)) {
            return;
        }

        try (InputStream is = ChangelogDeployer.class.getResourceAsStream(RESOURCE_PATH)) {
            if (is == null) {
                NsukAddition.LOGGER.warn("nsuk_addition: Missing changelog resource: {}", RESOURCE_PATH);
                return;
            }
            Files.copy(is, dirPath.resolve(FILE_NAME), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            NsukAddition.LOGGER.error("nsuk_addition: Failed to deploy changelog md", e);
            return;
        }
        writeVersion(dirPath);
    }

    private static boolean isUpToDate(Path dirPath) {
        Path versionFile = dirPath.resolve(VERSION_ENTRY);
        if (!Files.isRegularFile(versionFile)) {
            return false;
        }
        try {
            return CURRENT_VERSION.equals(Files.readString(versionFile, StandardCharsets.UTF_8).trim());
        } catch (IOException e) {
            return false;
        }
    }

    private static void writeVersion(Path dirPath) {
        try {
            Files.writeString(dirPath.resolve(VERSION_ENTRY), CURRENT_VERSION, StandardCharsets.UTF_8);
        } catch (IOException e) {
            NsukAddition.LOGGER.error("nsuk_addition: Failed to write changelog version file", e);
        }
    }
}