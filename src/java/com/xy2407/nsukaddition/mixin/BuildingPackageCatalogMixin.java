package com.xy2407.nsukaddition.mixin;

import com.xy2407.nsukaddition.NsukAddition;
import common.cn.kafei.simukraft.building.BuildingPackageCatalog;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** 修改 BuildingPackageCatalog，注入养殖分类并支持散装目录文件读取。 */
@Mixin(value = BuildingPackageCatalog.class, remap = false)
public class BuildingPackageCatalogMixin {

    @Shadow
    private static boolean isSafePackageFileName(String fileName) {
        return false;
    }

    @Inject(method = "categories", at = @At("RETURN"), cancellable = true)
    private static void nsuk$categories(CallbackInfoReturnable<List<String>> cir) {
        List<String> original = cir.getReturnValue();
        if (original.contains("breeding")) {
            return;
        }
        List<String> extended = new ArrayList<>(original);
        extended.add("breeding");
        cir.setReturnValue(List.copyOf(extended));
    }

    @Inject(method = "normalizeCategory", at = @At("HEAD"), cancellable = true)
    private static void nsuk$normalizeCategory(String category, CallbackInfoReturnable<String> cir) {
        if (category != null && "breeding".equalsIgnoreCase(category)) {
            cir.setReturnValue("breeding");
        }
    }

    @Inject(method = "openEntry", at = @At("HEAD"), cancellable = true)
    private static void nsuk$openEntry(BuildingPackageCatalog.PackageSource source,
                                        String category, String fileName,
                                        CallbackInfoReturnable<Optional<InputStream>> cir) throws IOException {
        if (source == null) {
            return;
        }
        Path packagePath = source.packagePath();
        if (packagePath == null || !Files.isDirectory(packagePath)) {
            return;
        }

        if (!isSafePackageFileName(fileName)) {
            cir.setReturnValue(Optional.empty());
            return;
        }

        String normalizedCategory = BuildingPackageCatalog.normalizeCategory(category);
        if (!source.isAllowed(normalizedCategory, fileName)) {
            cir.setReturnValue(Optional.empty());
            return;
        }

        String actualFile = source.actualFileName(normalizedCategory, fileName);
        Path loosePath = packagePath.resolve(normalizedCategory).resolve(actualFile);

        if (Files.isRegularFile(loosePath)) {
            cir.setReturnValue(Optional.of(new FileInputStream(loosePath.toFile())));
        } else {
            cir.setReturnValue(Optional.empty());
        }
    }
}
