package com.xy2407.nsukaddition.mixin;

import com.xy2407.nsukaddition.common.city.CityLevel;
import com.xy2407.nsukaddition.common.colony.ColonySqliteStorage;
import common.cn.kafei.simukraft.city.CityChunkManager;
import common.cn.kafei.simukraft.city.CityClaimService;
import common.cn.kafei.simukraft.city.CityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 拦截 CityClaimService.buyChunk，在购买前检查城市等级对应的区块领地上限（排除附属地区块）。 */
@Mixin(value = CityClaimService.class, remap = false)
public class CityClaimServiceMixin {

    @Inject(method = "buyChunk", at = @At("HEAD"), cancellable = true, remap = false)
    private static void nsuk$checkChunkLimit(ServerLevel level, ServerPlayer player, CityData city,
                                              int chunkX, int chunkZ,
                                              CallbackInfoReturnable<CityClaimService.ClaimResult> cir) {
        if (level == null || city == null) {
            return;
        }
        CityLevel cityLevel = CityLevel.fromLevel(city.cityLevel());
        int maxChunks = cityLevel.maxChunks();
        CityChunkManager chunkManager = CityChunkManager.get(level);
        int totalChunks = chunkManager.getCityChunks(city.cityId()).size();

        // 减去附属地区块数，城市核心方块购买只消耗城市自身的区块配额
        int colonyChunks = ColonySqliteStorage.countChunksByParentCity(level, city.cityId());
        int cityOwnChunks = totalChunks - colonyChunks;

        if (cityOwnChunks >= maxChunks) {
            cir.setReturnValue(CityClaimService.ClaimResult.failed(
                    net.minecraft.network.chat.Component.translatable(
                            "message.xy2407_nsuk_addition.city_chunk.limit_reached",
                            cityLevel.displayName(), maxChunks)
            ));
        }
    }
}
