package com.xy2407.nsukaddition.common.building;

import com.xy2407.nsukaddition.NsukAddition;
import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** 将工业建筑文件打包为 zip 部署到 simukraftbuilding/ 目录。 */
public final class IndustrialBuildingDeployer {

    private static final String ZIP_NAME = "nsuk_industrial_buildings.zip";
    private static final String RESOURCE_PREFIX = "/data/xy2407_nsuk_addition/industry/";
    private static final String CATEGORY = "industry";

    private static final List<String> BUILDING_FILES = List.of(
            "smeltery.json", "smeltery.sk", "smeltery.nbt",
            "winery.json", "winery.sk", "winery.nbt"
    );

    private static final String VERSION_ENTRY = "_nsuk_version.txt";
    private static final String CURRENT_VERSION = "1";

    private IndustrialBuildingDeployer() {
    }

    public static void deploy() {
        Path simukraftDir = FMLPaths.GAMEDIR.get().resolve("simukraftbuilding");
        Path zipPath = simukraftDir.resolve(ZIP_NAME);

        try {
            Files.createDirectories(simukraftDir);
        } catch (IOException e) {
            NsukAddition.LOGGER.error("nsuk_addition: Failed to create simukraftbuilding directory", e);
            return;
        }
        if (isUpToDate(zipPath)) return;

        try {
            createZip(zipPath);
            NsukAddition.LOGGER.info("nsuk_addition: Deployed industrial building package to {}", zipPath);
        } catch (IOException e) {
            NsukAddition.LOGGER.error("nsuk_addition: Failed to deploy industrial building package", e);
        }
    }

    private static boolean isUpToDate(Path zipPath) {
        if (!Files.isRegularFile(zipPath)) return false;
        try (var zipFile = new java.util.zip.ZipFile(zipPath.toFile(), StandardCharsets.UTF_8)) {
            ZipEntry entry = zipFile.getEntry(VERSION_ENTRY);
            if (entry == null) return false;
            try (InputStream is = zipFile.getInputStream(entry)) {
                String version = new String(is.readAllBytes(), StandardCharsets.UTF_8).trim();
                return CURRENT_VERSION.equals(version);
            }
        } catch (IOException e) {
            return false;
        }
    }

    private static void createZip(Path zipPath) throws IOException {
        Path tempZip = zipPath.resolveSibling(ZIP_NAME + ".tmp");
        try {
            try (OutputStream fos = Files.newOutputStream(tempZip);
                 ZipOutputStream zos = new ZipOutputStream(fos, StandardCharsets.UTF_8)) {

                writeEntry(zos, VERSION_ENTRY, CURRENT_VERSION.getBytes(StandardCharsets.UTF_8));

                for (String file : BUILDING_FILES) {
                    String resourcePath = RESOURCE_PREFIX + file;
                    try (InputStream is = IndustrialBuildingDeployer.class.getResourceAsStream(resourcePath)) {
                        if (is == null) {
                            NsukAddition.LOGGER.warn("nsuk_addition: Missing industrial resource: {}", resourcePath);
                            continue;
                        }
                        byte[] data = is.readAllBytes();
                        String entryPath = "buildings/" + CATEGORY + "/" + file;
                        writeEntry(zos, entryPath, data);
                    }
                }
            }
            Files.deleteIfExists(zipPath);
            Files.move(tempZip, zipPath);
        } finally {
            Files.deleteIfExists(tempZip);
        }
    }

    private static void writeEntry(ZipOutputStream zos, String name, byte[] data) throws IOException {
        ZipEntry entry = new ZipEntry(name);
        zos.putNextEntry(entry);
        zos.write(data);
        zos.closeEntry();
    }
}
