package com.xy2407.nsukaddition.mixin;

import com.xy2407.nsukaddition.common.city.CityLevel;
import com.xy2407.nsukaddition.common.colony.ColonySqliteStorage;
import common.cn.kafei.simukraft.city.CityChunkManager;
import common.cn.kafei.simukraft.city.CityClaimService;
import common.cn.kafei.simukraft.city.CityData;
import common.cn.kafei.simukraft.city.CityPermissionLevel;
import common.cn.kafei.simukraft.config.ServerConfig;
import common.cn.kafei.simukraft.economy.EconomyService;
import common.cn.kafei.simukraft.economy.FinanceLedgerService;
import common.cn.kafei.simukraft.city.FinanceTransactionData;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 完全接管 CityClaimService.buyChunk：
 * 1. 领地上限使用 xy's_nsuk 的 5 级制 maxChunks（排除附属地区块，附属地走自己的配额池）；
 * 2. 禁用官方 2.2.0 飞地（enclave）机制——非邻接区块一律拒绝，扩张只能通过附属地系统；
 * 3. 费用与资金流水沿用官方逻辑（主领地统一价，不再区分 50 金币飞地价）。
 */
@Mixin(value = CityClaimService.class, remap = false)
public class CityClaimServiceMixin {

    @Inject(method = "buyChunk", at = @At("HEAD"), cancellable = true, remap = false)
    private static void nsuk$buyChunk(ServerLevel level, ServerPlayer player, CityData city,
                                      int chunkX, int chunkZ,
                                      CallbackInfoReturnable<CityClaimService.ClaimResult> cir) {
        if (level == null || player == null || city == null) {
            return;
        }
        if (!city.hasPermission(player.getUUID(), CityPermissionLevel.OFFICIAL)) {
            cir.setReturnValue(CityClaimService.ClaimResult.failed(
                    Component.translatable("message.simukraft.city_chunk.no_permission")));
            return;
        }
        CityChunkManager chunkManager = CityChunkManager.get(level);
        long chunkLong = ChunkPos.asLong(chunkX, chunkZ);
        if (chunkManager.getChunkOwner(chunkLong) != null) {
            cir.setReturnValue(CityClaimService.ClaimResult.failed(
                    Component.translatable("message.simukraft.city_chunk.already_claimed")));
            return;
        }

        CityLevel cityLevel = CityLevel.fromLevel(city.cityLevel());
        int maxChunks = cityLevel.maxChunks();
        int totalChunks = chunkManager.getCityChunks(city.cityId()).size();
        int colonyChunks = ColonySqliteStorage.countChunksByParentCity(level, city.cityId());
        int cityOwnChunks = totalChunks - colonyChunks;
        if (cityOwnChunks >= maxChunks) {
            cir.setReturnValue(CityClaimService.ClaimResult.failed(
                    Component.translatable("message.xy2407_nsuk_addition.city_chunk.limit_reached",
                            cityLevel.displayName(), maxChunks)));
            return;
        }

        if (!chunkManager.isAdjacentToCity(city.cityId(), chunkLong)) {
            cir.setReturnValue(CityClaimService.ClaimResult.failed(
                    Component.translatable("message.xy2407_nsuk_addition.city_chunk.not_adjacent")));
            return;
        }

        double chunkPrice = ServerConfig.cityChunkPrice();
        if (chunkPrice > 0) {
            if (!EconomyService.canAfford(level, city.cityId(), chunkPrice)) {
                cir.setReturnValue(CityClaimService.ClaimResult.failed(
                        Component.translatable("message.simukraft.city_chunk.not_enough_funds", chunkPrice)));
                return;
            }
            if (!city.withdrawFunds(chunkPrice)) {
                cir.setReturnValue(CityClaimService.ClaimResult.failed(
                        Component.translatable("message.simukraft.city_chunk.not_enough_funds", chunkPrice)));
                return;
            }
        }
        if (!chunkManager.claimChunk(city.cityId(), chunkLong)) {
            if (chunkPrice > 0) {
                city.depositFunds(chunkPrice);
            }
            cir.setReturnValue(CityClaimService.ClaimResult.failed(
                    Component.translatable("message.simukraft.city_chunk.claim_failed")));
            return;
        }
        FinanceLedgerService.record(level, city.cityId(), player, -chunkPrice,
                EconomyService.getCityBalance(level, city.cityId()),
                FinanceTransactionData.Type.EXPENSE, "claim_chunk");
        cir.setReturnValue(CityClaimService.ClaimResult.success(
                Component.translatable("message.simukraft.city_chunk.claimed", chunkX, chunkZ, chunkPrice),
                chunkPrice));
    }
}
