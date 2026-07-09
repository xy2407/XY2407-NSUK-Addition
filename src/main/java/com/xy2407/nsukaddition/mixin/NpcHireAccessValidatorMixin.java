package com.xy2407.nsukaddition.mixin;

import com.xy2407.nsukaddition.common.breeding.BreedingConstants;
import com.xy2407.nsukaddition.common.mining.MiningConstants;
import com.xy2407.nsukaddition.common.registry.ModBlocks;
import common.cn.kafei.simukraft.SimuKraft;
import common.cn.kafei.simukraft.building.PlacedBuildingRecord;
import common.cn.kafei.simukraft.building.PlacedBuildingService;
import common.cn.kafei.simukraft.city.CityChunkManager;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Locale;
import java.util.UUID;

/**
 * 修改 NpcHireAccessValidator，让养殖/采矿控制箱成为合法的 NPC 雇佣来源。
 * 同时输出调试信息到玩家聊天框。
 */
@Mixin(targets = "common.cn.kafei.simukraft.network.npc.hire.NpcHireAccessValidator", remap = false)
public class NpcHireAccessValidatorMixin {

    @Inject(method = "validateSource", at = @At("HEAD"), remap = false)
    private static void nsuk$debugValidateHead(ServerPlayer player, ServerLevel level, BlockPos sourcePos,
                                                String sourceType, String role,
                                                CallbackInfoReturnable<Object> cir) {
        player.displayClientMessage(Component.literal(
                "§6[NSUK-DEBUG] validateSource调用 → sourceType=" + sourceType + " role=" + role + " pos=" + sourcePos), false);
    }

    @Inject(method = "validateSource", at = @At("RETURN"), remap = false)
    private static void nsuk$debugValidateReturn(ServerPlayer player, ServerLevel level, BlockPos sourcePos,
                                                  String sourceType, String role,
                                                  CallbackInfoReturnable<Object> cir) {
        Object ret = cir.getReturnValue();
        player.displayClientMessage(Component.literal(
                "§6[NSUK-DEBUG] validateSource返回 → " + (ret != null ? "非null(成功)" : "null(失败)")), false);
    }

    @Inject(method = "resolveCityId", at = @At("HEAD"), cancellable = true, remap = false)
    private static void nsuk$resolveCityIdFallback(ServerLevel level, BlockPos sourcePos,
                                                    String sourceType, String role,
                                                    CallbackInfoReturnable<UUID> cir) {
        String normSource = sourceType == null ? "" : sourceType.trim().toLowerCase(Locale.ROOT);
        String normRole = role == null ? "" : role.trim().toLowerCase(Locale.ROOT);

        if (BreedingConstants.HIRE_SOURCE_TYPE.equals(normSource) && BreedingConstants.HIRE_ROLE.equals(normRole)) {
            if (level.getBlockState(sourcePos).is(ModBlocks.BREEDING_CONTROL_BOX.get())) {
                PlacedBuildingRecord building = PlacedBuildingService.findByContainedPosAndCategory(
                        level, sourcePos, BreedingConstants.BUILDING_CATEGORY, "industry", "industrial");
                UUID cityId = building != null ? building.cityId() : null;
                cir.setReturnValue(cityId);
                return;
            }
        }

        if (MiningConstants.HIRE_SOURCE_TYPE.equals(normSource) && MiningConstants.HIRE_ROLE.equals(normRole)) {
            if (level.getBlockState(sourcePos).is(ModBlocks.MINING_CONTROL_BOX.get())) {
                UUID cityId = CityChunkManager.get(level).getChunkOwner(new ChunkPos(sourcePos).toLong());
                if (cityId == null) {
                    PlacedBuildingRecord building = PlacedBuildingService.findByContainedPosAndCategory(
                            level, sourcePos, "mining", "industry", "industrial");
                    cityId = building != null ? building.cityId() : null;
                }
                cir.setReturnValue(cityId);
                return;
            }
        }

    }
}
