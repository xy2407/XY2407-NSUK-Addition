package com.xy2407.nsukaddition.mixin;

import com.xy2407.nsukaddition.common.farmland.ModFarmCropRegistry;
import common.cn.kafei.simukraft.farmland.FarmCrop;
import common.cn.kafei.simukraft.farmland.FarmlandBoxData;
import net.minecraft.nbt.CompoundTag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 保留作物ID字符串，防止 FarmCrop 实例为 null 时 crop ID 在 toTag/persist 流程中丢失。 */
@Mixin(FarmlandBoxData.class)
public abstract class FarmlandBoxDataCropPreserveMixin {

    @Inject(method = "setCrop", at = @At("HEAD"), remap = false)
    private void xy2407$onSetCrop(FarmCrop crop, CallbackInfo ci) {
        if (crop != null) {
            FarmlandBoxData self = (FarmlandBoxData) (Object) this;
            ModFarmCropRegistry.preserveCropId(self.boxPos(), crop.id());
        }
    }

    @Inject(method = "fromTag", at = @At("RETURN"), remap = false)
    private static void xy2407$onFromTag(CompoundTag tag,
                                          CallbackInfoReturnable<FarmlandBoxData> cir) {
        if (tag == null || !tag.contains("Crop")) return;
        String cropId = tag.getString("Crop");
        if (cropId == null || cropId.isBlank()) return;

        FarmlandBoxData data = cir.getReturnValue();
        if (data == null) return;

        ModFarmCropRegistry.preserveCropId(data.boxPos(), cropId);

        if (data.crop() == null && ModFarmCropRegistry.cropMapSize() > 0) {
            FarmCrop crop = ModFarmCropRegistry.findById(cropId);
            if (crop != null) {
                data.setCrop(crop);
            }
        }
    }

    @Inject(method = "toTag", at = @At("RETURN"), remap = false)
    private void xy2407$onToTag(CallbackInfoReturnable<CompoundTag> cir) {
        FarmlandBoxData self = (FarmlandBoxData) (Object) this;
        if (self.crop() != null) return;
        String preservedId = ModFarmCropRegistry.getPreservedCropId(self.boxPos());
        if (preservedId != null && !preservedId.isBlank()) {
            cir.getReturnValue().putString("Crop", preservedId);
        }
    }
}
