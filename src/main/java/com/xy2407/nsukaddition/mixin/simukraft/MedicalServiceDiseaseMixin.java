package com.xy2407.nsukaddition.mixin.simukraft;

import com.xy2407.nsukaddition.common.city.SimuKraftCityActivation;
import common.cn.kafei.simukraft.citizen.CitizenData;
import common.cn.kafei.simukraft.citizen.CitizenManager;
import common.cn.kafei.simukraft.citizen.CitizenService;
import common.cn.kafei.simukraft.citizen.CitizenWorkStatus;
import common.cn.kafei.simukraft.config.ServerConfig;
import common.cn.kafei.simukraft.medical.MedicalService;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 修复感冒与工作逻辑冲突：
 * 1. 未入院感冒 NPC 也推进治疗自然康复，避免无医院城市 NPC 永久生病。
 *    （官方 2.2.0 只在住院时推进疾病治疗，isOnMedicalLeave 已内置 disease().isActive() 患病判定，无需重复注入。）
 * 2. 维度内无激活城市时跳过生病判定，避免未加载城市市民被写库。
 */
@Mixin(value = MedicalService.class, remap = false)
public class MedicalServiceDiseaseMixin {

    @Inject(method = "runTick", at = @At("RETURN"), remap = false)
    private static void nsuk$naturalHeal(ServerLevel level, CallbackInfo ci) {
        if (level == null) {
            return;
        }
        long currentDay = level.getDayTime() / 24_000L;
        for (CitizenData citizen : CitizenManager.get(level).allCitizens()) {
            if (citizen.dead() || !citizen.disease().isActive() || MedicalService.isAdmitted(citizen)) {
                continue;
            }
            citizen.medical().addDiseaseTreatmentTicks(20L);
            if (citizen.medical().diseaseTreatmentTicks() >= ServerConfig.medicalDiseaseTreatmentTicks() * 2L) {
                citizen.clearDisease();
                if (MedicalService.MEDICAL_CARE_MARKER.equals(citizen.workNeedDetail())) {
                    citizen.setWorkNeedDetail("");
                    citizen.setStatusLabel("");
                    citizen.setWorkStatus(citizen.workplaceId() != null ? CitizenWorkStatus.WORKING : CitizenWorkStatus.IDLE);
                }
                CitizenService.save(level, citizen.uuid());
            }
        }
    }

    @Inject(method = "tickDaily", at = @At("HEAD"), cancellable = true, remap = false, require = 1)
    private static void nsuk$skipSicknessWhenNoActiveCity(ServerLevel level, RandomSource random, long currentDay, CallbackInfo ci) {
        if (!SimuKraftCityActivation.hasActiveCity(level)) {
            ci.cancel();
        }
    }
}