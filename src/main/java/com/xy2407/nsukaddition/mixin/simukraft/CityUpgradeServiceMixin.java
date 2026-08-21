package com.xy2407.nsukaddition.mixin.simukraft;

import com.xy2407.nsukaddition.NsukAddition;
import com.xy2407.nsukaddition.common.city.CityBuildingStats;
import com.xy2407.nsukaddition.common.city.CityLevel;
import com.xy2407.nsukaddition.common.city.CityUpgradeRequirement;
import common.cn.kafei.simukraft.city.CityData;
import common.cn.kafei.simukraft.city.CityLevelDefinition;
import common.cn.kafei.simukraft.city.CityManager;
import common.cn.kafei.simukraft.city.CityPermissionLevel;
import common.cn.kafei.simukraft.city.CityPopulationStats;
import common.cn.kafei.simukraft.city.CityUpgradeService;
import common.cn.kafei.simukraft.city.CityUpgradeState;
import common.cn.kafei.simukraft.city.FinanceTransactionData;
import common.cn.kafei.simukraft.economy.FinanceLedgerService;
import common.cn.kafei.simukraft.logistics.LogisticsManager;
import common.cn.kafei.simukraft.logistics.LogisticsWarehouseData;
import common.cn.kafei.simukraft.logistics.LogisticsWarehouseInventoryService;
import common.cn.kafei.simukraft.storage.SimuSqliteStorage;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * 接管官方 CityUpgradeService.upgrade()：
 * 保留官方异步升级机制（beginUpgrade + tick 到期完成 + persistUpgrade 持久化），
 * 但把条件检测与材料获取替换为 xy's_nsuk 实现：
 * 1. 条件检测：人口 + 建筑(farm/ranch/shop/factory/mine) + 资金 + 材料(原木/石头)（对齐 xy CityUpgradeRequirement）；
 * 2. 材料获取：物流仓库优先，玩家背包补足（xy CityUpgradeService 逻辑）；
 * 3. 其余（权限/等级匹配/存储降级/资金流水/异步进度/持久化）沿用官方语义。
 */
@Mixin(value = CityUpgradeService.class, remap = false)
public abstract class CityUpgradeServiceMixin {

    private static final TagKey<Item> STONES_TAG = TagKey.create(
            Registries.ITEM, ResourceLocation.fromNamespaceAndPath("c", "stones"));

    private static final Predicate<ItemStack> LOGS_MATCHER = stack -> stack.is(ItemTags.LOGS);

    private static final Predicate<ItemStack> STONE_MATCHER = stack -> {
        if (stack.is(STONES_TAG)) {
            return true;
        }
        Item item = stack.getItem();
        return item == Items.COBBLESTONE || item == Items.STONE;
    };

    @Inject(method = "upgrade", at = @At("HEAD"), cancellable = true, remap = false)
    private static void nsuk$upgradeWithXy(ServerLevel level, ServerPlayer player, CityData city,
                                           int expectedCurrentLevel, int targetLevel,
                                           CallbackInfoReturnable<CityUpgradeService.UpgradeResult> cir) {
        if (level == null || player == null || city == null) {
            cir.setReturnValue(new CityUpgradeService.UpgradeResult(
                    CityUpgradeService.Status.INVALID_CITY, null, null));
            return;
        }
        synchronized (city) {
            if (!city.hasPermission(player.getUUID(), CityPermissionLevel.OFFICIAL)) {
                cir.setReturnValue(new CityUpgradeService.UpgradeResult(
                        CityUpgradeService.Status.NO_PERMISSION, null, null));
                return;
            }
            if (city.upgradeState().active()) {
                cir.setReturnValue(new CityUpgradeService.UpgradeResult(
                        CityUpgradeService.Status.UPGRADE_IN_PROGRESS, null, null));
                return;
            }
            if (city.cityLevel() != expectedCurrentLevel) {
                cir.setReturnValue(new CityUpgradeService.UpgradeResult(
                        CityUpgradeService.Status.STALE_REQUEST, null, null));
                return;
            }
            CityLevelDefinition definition = CityUpgradeService.nextDefinition(city);
            if (definition == null) {
                cir.setReturnValue(new CityUpgradeService.UpgradeResult(
                        CityUpgradeService.Status.NO_NEXT_LEVEL, null, null));
                return;
            }
            if (definition.level() != targetLevel) {
                cir.setReturnValue(new CityUpgradeService.UpgradeResult(
                        CityUpgradeService.Status.STALE_REQUEST, definition, null));
                return;
            }
            if (SimuSqliteStorage.isDegraded(level)) {
                cir.setReturnValue(new CityUpgradeService.UpgradeResult(
                        CityUpgradeService.Status.STORAGE_UNAVAILABLE, definition, null));
                return;
            }

            CityLevel currentLevel = CityLevel.fromLevel(city.cityLevel());
            CityUpgradeRequirement req = CityUpgradeRequirement.forCurrentLevel(currentLevel);
            if (req == null) {
                cir.setReturnValue(new CityUpgradeService.UpgradeResult(
                        CityUpgradeService.Status.NO_NEXT_LEVEL, definition, null));
                return;
            }
            if (city.funds() < req.requiredFunds()) {
                cir.setReturnValue(new CityUpgradeService.UpgradeResult(
                        CityUpgradeService.Status.NOT_ENOUGH_FUNDS, definition, null));
                return;
            }
            int population = CityPopulationStats.snapshot(level, city.cityId()).population();
            if (population < req.requiredPopulation()) {
                cir.setReturnValue(new CityUpgradeService.UpgradeResult(
                        CityUpgradeService.Status.NOT_ENOUGH_POPULATION, definition, null));
                return;
            }
            CityBuildingStats stats = CityBuildingStats.collect(level, city.cityId());
            if (stats.farmCount() < req.requiredFarms()
                    || stats.ranchCount() < req.requiredRanches()
                    || stats.shopCount() < req.requiredShops()
                    || stats.factoryCount() < req.requiredFactories()
                    || stats.mineCount() < req.requiredMines()) {
                if (player != null) {
                    player.sendSystemMessage(Component.translatable(
                            "message.xy2407_nsuk_addition.city_upgrade.condition_not_met"));
                }
                cir.setReturnValue(new CityUpgradeService.UpgradeResult(
                        CityUpgradeService.Status.NOT_ENOUGH_POPULATION, definition, null));
                return;
            }
            int totalLogs = com.xy2407.nsukaddition.common.city.CityUpgradeService.countMatchingInWarehouses(
                    level, city.cityId(), LOGS_MATCHER)
                    + com.xy2407.nsukaddition.common.city.CityUpgradeService.countMatchingInInventory(
                    player, LOGS_MATCHER);
            int totalStone = com.xy2407.nsukaddition.common.city.CityUpgradeService.countMatchingInWarehouses(
                    level, city.cityId(), STONE_MATCHER)
                    + com.xy2407.nsukaddition.common.city.CityUpgradeService.countMatchingInInventory(
                    player, STONE_MATCHER);
            if (totalLogs < req.requiredLogs() || totalStone < req.requiredStone()) {
                cir.setReturnValue(new CityUpgradeService.UpgradeResult(
                        CityUpgradeService.Status.NOT_ENOUGH_ITEMS, definition, null));
                return;
            }

            List<ItemStack> removedFromWarehouseLogs = com.xy2407.nsukaddition.common.city.CityUpgradeService
                    .deductFromWarehousesCollect(level, city.cityId(), LOGS_MATCHER, req.requiredLogs());
            List<ItemStack> removedFromWarehouseStone = com.xy2407.nsukaddition.common.city.CityUpgradeService
                    .deductFromWarehousesCollect(level, city.cityId(), STONE_MATCHER, req.requiredStone());
            List<ItemStack> removedFromPlayer = new ArrayList<>();
            int logsShortfall = req.requiredLogs();
            for (ItemStack s : removedFromWarehouseLogs) logsShortfall -= s.getCount();
            if (logsShortfall > 0 && player != null) {
                removedFromPlayer.addAll(takeFromPlayer(player, LOGS_MATCHER, logsShortfall));
            }
            int stoneShortfall = req.requiredStone();
            for (ItemStack s : removedFromWarehouseStone) stoneShortfall -= s.getCount();
            if (stoneShortfall > 0 && player != null) {
                removedFromPlayer.addAll(takeFromPlayer(player, STONE_MATCHER, stoneShortfall));
            }

            double previousFunds = city.funds();
            CityUpgradeState previousUpgradeState = city.upgradeState();
            if (req.requiredFunds() > 0.0D && !city.withdrawFunds(req.requiredFunds())) {
                restoreMaterials(level, city, removedFromWarehouseLogs, removedFromWarehouseStone,
                        removedFromPlayer, player);
                cir.setReturnValue(new CityUpgradeService.UpgradeResult(
                        CityUpgradeService.Status.NOT_ENOUGH_FUNDS, definition, null));
                return;
            }
            if (req.requiredFunds() > 0.0D) {
                FinanceLedgerService.record(level, city.cityId(), player, -req.requiredFunds(),
                        city.funds(), FinanceTransactionData.Type.EXPENSE, "city_upgrade");
            }

            ((CityDataUpgradeInvoker) (Object) city).nsuk$beginUpgrade(
                    targetLevel, level.getGameTime(), definition.durationTicks());
            boolean persisted;
            try {
                persisted = ((CityManagerUpgradeInvoker) (Object) CityManager.get(level)).nsuk$persistUpgrade(city);
            } catch (RuntimeException exception) {
                NsukAddition.LOGGER.error("Failed to persist started city upgrade for {}", city.cityId(), exception);
                persisted = false;
            }
            if (!persisted) {
                city.setFunds(previousFunds);
                ((CityDataUpgradeInvoker) (Object) city).nsuk$restoreUpgradeState(previousUpgradeState);
                restoreMaterials(level, city, removedFromWarehouseLogs, removedFromWarehouseStone,
                        removedFromPlayer, player);
                player.getInventory().setChanged();
                player.containerMenu.broadcastChanges();
                cir.setReturnValue(new CityUpgradeService.UpgradeResult(
                        CityUpgradeService.Status.STORAGE_UNAVAILABLE, definition, null));
                return;
            }
            player.getInventory().setChanged();
            player.containerMenu.broadcastChanges();
            cir.setReturnValue(new CityUpgradeService.UpgradeResult(
                    CityUpgradeService.Status.STARTED, definition, null));
        }
    }

    private static List<ItemStack> takeFromPlayer(ServerPlayer player, Predicate<ItemStack> matcher, int remaining) {
        List<ItemStack> removed = new ArrayList<>();
        for (int i = 0; i < player.getInventory().getContainerSize() && remaining > 0; i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.isEmpty() || !matcher.test(stack)) {
                continue;
            }
            int toTake = Math.min(remaining, stack.getCount());
            removed.add(stack.copyWithCount(toTake));
            stack.shrink(toTake);
            remaining -= toTake;
        }
        return removed;
    }

    private static void restorePlayerItems(ServerPlayer player, List<ItemStack> removed) {
        for (int index = removed.size() - 1; index >= 0; index--) {
            ItemStack restored = removed.get(index);
            if (!player.getInventory().add(restored)) {
                player.drop(restored, false);
            }
        }
    }

    private static void restoreMaterials(ServerLevel level, CityData city,
                                         List<ItemStack> warehouseLogs, List<ItemStack> warehouseStone,
                                         List<ItemStack> playerItems, ServerPlayer player) {
        List<ItemStack> remaining = new ArrayList<>();
        remaining.addAll(warehouseLogs);
        remaining.addAll(warehouseStone);
        List<LogisticsWarehouseData> warehouses = LogisticsManager.get(level).warehouses(city.cityId());
        for (LogisticsWarehouseData warehouse : warehouses) {
            for (int i = remaining.size() - 1; i >= 0; i--) {
                ItemStack stack = remaining.get(i);
                if (stack.isEmpty()) {
                    remaining.remove(i);
                    continue;
                }
                ItemStack leftover = LogisticsWarehouseInventoryService.insert(level, warehouse.boxPos(), stack);
                if (leftover.isEmpty()) {
                    remaining.remove(i);
                } else {
                    remaining.set(i, leftover);
                }
            }
        }
        for (ItemStack stack : remaining) {
            if (stack.isEmpty()) continue;
            ItemStack leftover = LogisticsWarehouseInventoryService.insertIntoPlayerInventory(
                    player.getInventory(), stack);
            if (!leftover.isEmpty()) {
                player.drop(leftover, false);
            }
        }
        restorePlayerItems(player, playerItems);
    }
}