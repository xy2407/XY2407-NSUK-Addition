package com.xy2407.nsukaddition.mixin.client;

import client.cn.kafei.simukraft.client.buildbox.BuildingCacheService;
import com.xy2407.nsukaddition.NsukAddition;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.fml.loading.FMLPaths;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** 修改 BuildingCacheService，动态扫描养殖建筑目录以加载养殖分类到 UI。 */
@OnlyIn(Dist.CLIENT)
@Mixin(value = BuildingCacheService.class, remap = false)
public class BuildingCacheServiceMixin {

    private static final Path BREEDING_DIR = FMLPaths.GAMEDIR.get().resolve("xy2407_nsuk_addition/breeding");

    @Inject(method = "getBuildings", at = @At("HEAD"), cancellable = true)
    private static void nsuk$dynamicLoadBreeding(String category, CallbackInfoReturnable<List<BuildingCacheService.BuildingMeta>> cir) {
        String normalized = category == null ? "" : category.toLowerCase(Locale.ROOT);

        if (!"breeding".equals(normalized)) {
            return;
        }

        List<BuildingCacheService.BuildingMeta> result = loadBreedingBuildings();

        cir.setReturnValue(result);
    }

    private static List<BuildingCacheService.BuildingMeta> loadBreedingBuildings() {
        if (!Files.isDirectory(BREEDING_DIR)) {
            return List.of();
        }

        Map<String, String> packageFiles = new HashMap<>();
        List<String> metaFiles = new ArrayList<>();

        try (var stream = Files.list(BREEDING_DIR)) {
            stream.filter(Files::isRegularFile).forEach(file -> {
                String fileName = file.getFileName().toString();
                if (!isSafePackageFileName(fileName)) {
                    return;
                }
                putPackageFile(packageFiles, fileName);
                if (fileName.toLowerCase(Locale.ROOT).endsWith(".sk")) {
                    metaFiles.add(fileName);
                }
            });
        } catch (IOException e) {
            return List.of();
        }

        if (metaFiles.isEmpty()) {
            return List.of();
        }

        metaFiles.sort(String.CASE_INSENSITIVE_ORDER);

        List<BuildingCacheService.BuildingMeta> result = new ArrayList<>();
        for (String metaFile : metaFiles) {
            BuildingCacheService.BuildingMeta meta = readBreedingMeta(metaFile, packageFiles);
            if (meta != null) {
                result.add(meta);
            }
        }

        return result;
    }

    private static BuildingCacheService.BuildingMeta readBreedingMeta(
            String metaFile, Map<String, String> packageFiles) {

        Path metaPath = BREEDING_DIR.resolve(metaFile);

        String metaText;
        try {
            metaText = Files.readString(metaPath, StandardCharsets.UTF_8);
        } catch (IOException e) {
            return null;
        }

        String baseName = stripExtension(metaFile);
        String displayName = findValue(metaText, "name", baseName);
        String author = findValue(metaText, "author", "External");
        String size = findValue(metaText, "size", "-");
        String amount = findValue(metaText, "amount", findValue(metaText, "price", "-"));
        String description = findValue(metaText, "description", findValue(metaText, "desc", ""));
        String structureFile = findValue(metaText, "structure", findValue(metaText, "file", ""));

        if (structureFile.isBlank()) {
            structureFile = baseName + ".nbt";
        }

        String actualStructureFile = isSafePackageFileName(structureFile)
                ? actualFileName(packageFiles, structureFile)
                : null;

        if (actualStructureFile == null) {
            return null;
        }

        return new BuildingCacheService.BuildingMeta(
                "breeding", displayName, size, amount, author, description,
                metaFile, actualStructureFile, "nsuk_breeding"
        );
    }

    private static boolean isSafePackageFileName(String fileName) {
        if (fileName == null || fileName.isBlank()) return false;
        if (fileName.contains("/") || fileName.contains("\\") || fileName.contains("..")) return false;
        String lowerName = fileName.toLowerCase(Locale.ROOT);
        return lowerName.endsWith(".sk") || lowerName.endsWith(".nbt") || lowerName.endsWith(".json");
    }

    private static void putPackageFile(Map<String, String> packageFiles, String fileName) {
        packageFiles.put(fileName.toLowerCase(Locale.ROOT), fileName);
        packageFiles.putIfAbsent(relaxedFileKey(fileName), fileName);
    }

    private static String actualFileName(Map<String, String> packageFiles, String fileName) {
        String actualName = packageFiles.get(fileName.toLowerCase(Locale.ROOT));
        return actualName != null ? actualName : packageFiles.get(relaxedFileKey(fileName));
    }

    private static String relaxedFileKey(String fileName) {
        String safeName = fileName != null ? fileName : "";
        return safeName.toLowerCase(Locale.ROOT).replace("_", "").replace("-", "").replace(" ", "");
    }

    private static String findValue(String text, String key, String fallback) {
        String prefix = key + ":";
        for (String line : text.split("\\R")) {
            String trimmedLine = line.trim();
            if (!trimmedLine.regionMatches(true, 0, prefix, 0, prefix.length())) continue;
            String val = trimmedLine.substring(prefix.length()).trim();
            return val.isEmpty() ? fallback : val;
        }
        return fallback;
    }

    private static String stripExtension(String fileName) {
        int index = fileName.lastIndexOf('.');
        return index > 0 ? fileName.substring(0, index) : fileName;
    }
}
