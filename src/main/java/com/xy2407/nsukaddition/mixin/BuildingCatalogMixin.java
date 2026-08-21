package com.xy2407.nsukaddition.mixin;

import common.cn.kafei.simukraft.building.BuildingCatalog;
import common.cn.kafei.simukraft.building.BuildingCatalog.BuildingDefinition;
import common.cn.kafei.simukraft.building.BuildingCatalog.BuildingType;
import common.cn.kafei.simukraft.building.BuildingPackageCatalog.PackageSource;
import net.neoforged.fml.loading.FMLPaths;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * 散装建筑目录支持：官方 2.2.0 的 BuildingCatalog 只扫描 simukraftbuilding/*.zip，
 * xy 的养殖/餐厅/外贸建筑散装部署在 game dir/xy2407_nsuk_addition/{category}/，
 * 放置流程的 findBuilding 找不到定义导致"未找到建筑结构"。
 * 此处对散装分类直接从目录构造 BuildingDefinition（PackageSource 指向目录，
 * 配合 BuildingPackageCatalogMixin 的 openEntry 目录分支读取 .sk/.nbt）。
 */
@Mixin(value = BuildingCatalog.class, remap = false)
public abstract class BuildingCatalogMixin {

    @Inject(method = "findBuilding", at = @At("HEAD"), cancellable = true, remap = false)
    private static void nsuk$findLooseBuilding(String category, String buildingFileName,
                                               CallbackInfoReturnable<Optional<BuildingDefinition>> cir) {
        BuildingDefinition definition = buildLooseDefinition(category, buildingFileName);
        if (definition != null) {
            cir.setReturnValue(Optional.of(definition));
        }
    }

    @Inject(method = "findBuildingByStructureFile", at = @At("HEAD"), cancellable = true, remap = false)
    private static void nsuk$findLooseBuildingByStructure(String category, String structureFileName,
                                                          CallbackInfoReturnable<Optional<BuildingDefinition>> cir) {
        BuildingDefinition definition = buildLooseDefinition(category, structureFileName);
        if (definition != null) {
            cir.setReturnValue(Optional.of(definition));
        }
    }

    private static BuildingDefinition buildLooseDefinition(String category, String fileName) {
        if (category == null || fileName == null || fileName.isBlank()) {
            return null;
        }
        Path dir = looseDir(category);
        if (dir == null || !Files.isDirectory(dir)) {
            return null;
        }
        String baseName = stripExtension(fileName);

        Path metaPath = findFile(dir, baseName + ".sk");
        if (metaPath == null) {
            return null;
        }
        String metaFileName = metaPath.getFileName().toString();

        String metaText = readText(metaPath);
        String displayName = findValue(metaText, "name", baseName);
        String size = findValue(metaText, "size", "-");
        String amount = findValue(metaText, "amount", findValue(metaText, "price", "-"));
        String author = findValue(metaText, "author", "External");
        String description = findValue(metaText, "description", "");
        int unlockLevel = parseUnlockLevel(metaText);

        String structureName = findValue(metaText, "structure", baseName + ".nbt");
        Path structurePath = findFile(dir, structureName);
        if (structurePath == null) {
            structurePath = findFile(dir, baseName + ".nbt");
        }
        if (structurePath == null) {
            return null;
        }
        String structureFileName = structurePath.getFileName().toString();

        Map<String, String> files = new HashMap<>();
        try (var stream = Files.list(dir)) {
            stream.filter(Files::isRegularFile).forEach(file -> {
                String name = file.getFileName().toString();
                if (isSafeName(name)) {
                    files.put(name.toLowerCase(Locale.ROOT), name);
                    files.putIfAbsent(relaxedKey(name), name);
                }
            });
        } catch (IOException e) {
            return null;
        }

        PackageSource source = new PackageSource(dir.toAbsolutePath().normalize(),
                "nsuk_" + category.toLowerCase(Locale.ROOT),
                Map.of(category, Map.copyOf(files)));

        return new BuildingDefinition(category, displayName, size, amount, author, description,
                unlockLevel, metaFileName, structureFileName, BuildingType.STANDARD, source);
    }

    private static Path looseDir(String category) {
        String normalized = category == null ? "" : category.toLowerCase(Locale.ROOT);
        if (!"breeding".equals(normalized) && !"cooking".equals(normalized) && !"foreign_trade".equals(normalized)) {
            return null;
        }
        return FMLPaths.GAMEDIR.get().resolve("xy2407_nsuk_addition/" + normalized);
    }

    private static Path findFile(Path dir, String targetName) {
        String lower = targetName.toLowerCase(Locale.ROOT);
        try (var stream = Files.list(dir)) {
            for (Path path : stream.filter(Files::isRegularFile).toList()) {
                if (path.getFileName().toString().toLowerCase(Locale.ROOT).equals(lower)) {
                    return path;
                }
            }
        } catch (IOException e) {
            return null;
        }
        return null;
    }

    private static String readText(Path path) {
        try {
            return Files.readString(path, java.nio.charset.StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "";
        }
    }

    private static String findValue(String text, String key, String fallback) {
        if (text == null || key == null) {
            return fallback;
        }
        String prefix = key + ":";
        for (String line : text.split("\\R")) {
            String trimmed = line.trim();
            if (!trimmed.regionMatches(true, 0, prefix, 0, prefix.length())) {
                continue;
            }
            String value = trimmed.substring(prefix.length()).trim();
            return value.isEmpty() ? fallback : value;
        }
        return fallback;
    }

    private static int parseUnlockLevel(String metaText) {
        String value = findValue(metaText, "unlockLevel", findValue(metaText, "unlock_level", ""));
        if (value == null || value.isBlank()) {
            return 0;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static String stripExtension(String fileName) {
        int index = fileName.lastIndexOf('.');
        return index > 0 ? fileName.substring(0, index) : fileName;
    }

    private static boolean isSafeName(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return false;
        }
        String lower = fileName.toLowerCase(Locale.ROOT);
        return lower.endsWith(".sk") || lower.endsWith(".nbt") || lower.endsWith(".json");
    }

    private static String relaxedKey(String fileName) {
        return fileName.toLowerCase(Locale.ROOT).replace("_", "").replace("-", "").replace(" ", "");
    }
}
