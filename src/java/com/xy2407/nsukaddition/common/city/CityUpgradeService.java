package com.xy2407.nsukaddition.common.city;

import common.cn.kafei.simukraft.citizen.CitizenManager;
import common.cn.kafei.simukraft.city.CityData;
import common.cn.kafei.simukraft.logistics.LogisticsManager;
import common.cn.kafei.simukraft.logistics.LogisticsWarehouseData;
import common.cn.kafei.simukraft.logistics.LogisticsWarehouseInventoryService;
import common.cn.kafei.simukraft.material.GenericContainerAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;

/** 城市升级的条件检查与执行服务，负责统计建筑、扣减仓库及玩家资源。 */
public final class CityUpgradeService {

    private CityUpgradeService() {}

    private static final TagKey<Item> STONES_TAG = TagKey.create(
            Registries.ITEM,
            ResourceLocation.fromNamespaceAndPath("c", "stones")
    );

    private static final Predicate<ItemStack> LOGS_MATCHER = stack -> stack.is(ItemTags.LOGS);

    private static final Predicate<ItemStack> STONE_MATCHER = stack -> {
        if (stack.is(STONES_TAG)) return true;
        Item item = stack.getItem();
        return item == Items.COBBLESTONE || item == Items.STONE;
    };

    public record UpgradeCheckResult(
            CityLevel currentLevel,
            CityLevel targetLevel,
            CityUpgradeRequirement requirement,
            int currentPopulation,
            int requiredPopulation,
            boolean populationMet,
            int currentFarms,
            int requiredFarms,
            boolean farmsMet,
            int currentRanches,
            int requiredRanches,
            boolean ranchesMet,
            int currentShops,
            int requiredShops,
            boolean shopsMet,
            int currentFactories,
            int requiredFactories,
            boolean factoriesMet,
            int currentMines,
            int requiredMines,
            boolean minesMet,
            int currentLogs,
            int requiredLogs,
            boolean logsMet,
            int currentStone,
            int requiredStone,
            boolean stoneMet,
            double currentFunds,
            double requiredFunds,
            boolean fundsMet,
            boolean allMet
    ) {}

    public static UpgradeCheckResult checkUpgrade(ServerLevel level, UUID cityId, ServerPlayer operator) {
        if (level == null || cityId == null) {
            return null;
        }
        CityData city = CityDataService.getCity(level, cityId);
        if (city == null) {
            return null;
        }
        CityLevel currentLevel = CityLevel.fromLevel(city.cityLevel());
        if (currentLevel.isMax()) {
            return null;
        }
        CityUpgradeRequirement req = CityUpgradeRequirement.forCurrentLevel(currentLevel);
        if (req == null) {
            return null;
        }
        CityLevel targetLevel = req.targetLevel();

        int population = (int) CitizenManager.get(level).getCityPopulation(cityId);
        boolean populationMet = population >= req.requiredPopulation();

        CityBuildingStats stats = CityBuildingStats.collect(level, cityId);
        boolean farmsMet = stats.farmCount() >= req.requiredFarms();
        boolean ranchesMet = stats.ranchCount() >= req.requiredRanches();
        boolean shopsMet = stats.shopCount() >= req.requiredShops();
        boolean factoriesMet = stats.factoryCount() >= req.requiredFactories();
        boolean minesMet = stats.mineCount() >= req.requiredMines();

        int totalLogs = countMatchingInWarehouses(level, cityId, LOGS_MATCHER)
                + countMatchingInInventory(operator, LOGS_MATCHER);
        int totalStone = countMatchingInWarehouses(level, cityId, STONE_MATCHER)
                + countMatchingInInventory(operator, STONE_MATCHER);
        boolean logsMet = totalLogs >= req.requiredLogs();
        boolean stoneMet = totalStone >= req.requiredStone();

        boolean fundsMet = city.funds() >= req.requiredFunds();

        boolean allMet = populationMet && farmsMet && ranchesMet && shopsMet
                && factoriesMet && minesMet && logsMet && stoneMet && fundsMet;

        return new UpgradeCheckResult(
                currentLevel, targetLevel, req,
                population, req.requiredPopulation(), populationMet,
                stats.farmCount(), req.requiredFarms(), farmsMet,
                stats.ranchCount(), req.requiredRanches(), ranchesMet,
                stats.shopCount(), req.requiredShops(), shopsMet,
                stats.factoryCount(), req.requiredFactories(), factoriesMet,
                stats.mineCount(), req.requiredMines(), minesMet,
                totalLogs, req.requiredLogs(), logsMet,
                totalStone, req.requiredStone(), stoneMet,
                city.funds(), req.requiredFunds(), fundsMet,
                allMet
        );
    }

    public static UpgradeResult executeUpgrade(ServerLevel level, UUID cityId, ServerPlayer operator) {
        if (level == null || cityId == null) {
            return UpgradeResult.FAIL_UNKNOWN;
        }
        UpgradeCheckResult check = checkUpgrade(level, cityId, operator);
        if (check == null) {
            return UpgradeResult.FAIL_ALREADY_MAX;
        }
        if (!check.allMet()) {
            return UpgradeResult.FAIL_CONDITION_NOT_MET;
        }

        CityData city = CityDataService.getCity(level, cityId);
        if (city == null) {
            return UpgradeResult.FAIL_UNKNOWN;
        }

        CityUpgradeRequirement req = check.requirement();

        int logsRemaining = req.requiredLogs();
        logsRemaining = deductFromWarehouses(level, cityId, LOGS_MATCHER, logsRemaining);
        if (logsRemaining > 0 && operator != null) {
            logsRemaining = deductFromPlayer(operator, LOGS_MATCHER, logsRemaining);
        }
        if (logsRemaining > 0) {
            return UpgradeResult.FAIL_MATERIAL_SHORTAGE;
        }

        int stoneRemaining = req.requiredStone();
        stoneRemaining = deductFromWarehouses(level, cityId, STONE_MATCHER, stoneRemaining);
        if (stoneRemaining > 0 && operator != null) {
            stoneRemaining = deductFromPlayer(operator, STONE_MATCHER, stoneRemaining);
        }
        if (stoneRemaining > 0) {
            return UpgradeResult.FAIL_MATERIAL_SHORTAGE;
        }

        if (req.requiredFunds() > 0 && !city.withdrawFunds(req.requiredFunds())) {
            return UpgradeResult.FAIL_FUNDS_SHORTAGE;
        }

        CityDataService.setCityLevel(level, cityId, check.targetLevel().level());
        return UpgradeResult.SUCCESS;
    }

    private static int countMatchingInWarehouses(ServerLevel level, UUID cityId, Predicate<ItemStack> matcher) {
        int count = 0;
        List<LogisticsWarehouseData> warehouses = LogisticsManager.get(level).warehouses(cityId);
        for (LogisticsWarehouseData warehouse : warehouses) {
            List<LogisticsWarehouseInventoryService.WarehouseItem> items =
                    LogisticsWarehouseInventoryService.aggregate(level, warehouse.boxPos());
            for (LogisticsWarehouseInventoryService.WarehouseItem item : items) {
                if (matcher.test(item.displayStack())) {
                    count += item.count();
                }
            }
        }
        return count;
    }

    private static int countMatchingInInventory(ServerPlayer player, Predicate<ItemStack> matcher) {
        if (player == null) return 0;
        int count = 0;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (!stack.isEmpty() && matcher.test(stack)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    private static int deductFromWarehouses(ServerLevel level, UUID cityId, Predicate<ItemStack> matcher, int required) {
        if (required <= 0) return 0;
        int remaining = required;
        List<LogisticsWarehouseData> warehouses = LogisticsManager.get(level).warehouses(cityId);
        for (LogisticsWarehouseData warehouse : warehouses) {
            if (remaining <= 0) break;
            List<BlockPos> containers = LogisticsWarehouseInventoryService.containers(level, warehouse.boxPos());
            for (BlockPos containerPos : containers) {
                if (remaining <= 0) break;
                remaining -= deductFromContainer(level, containerPos, matcher, remaining);
            }
        }
        return remaining;
    }

    private static int deductFromContainer(ServerLevel level, BlockPos containerPos,
                                            Predicate<ItemStack> matcher, int maxToDeduct) {
        int deducted = 0;
        List<GenericContainerAccess.SlotSnapshot> snapshots = GenericContainerAccess.snapshotSlots(level, containerPos);
        for (GenericContainerAccess.SlotSnapshot snapshot : snapshots) {
            if (deducted >= maxToDeduct) break;
            ItemStack stack = snapshot.stack();
            if (stack.isEmpty() || !matcher.test(stack)) continue;
            int toTake = Math.min(maxToDeduct - deducted, stack.getCount());

            ItemStack extracted = GenericContainerAccess.extractFromSlot(
                    level, containerPos, snapshot.slot(), snapshot.access(), snapshot.side(),
                    toTake, matcher::test
            );
            if (!extracted.isEmpty()) {
                deducted += extracted.getCount();
            }
        }
        return deducted;
    }

    private static int deductFromPlayer(ServerPlayer player, Predicate<ItemStack> matcher, int remaining) {
        if (player == null || remaining <= 0) return remaining;
        for (int i = 0; i < 36 && remaining > 0; i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.isEmpty() || !matcher.test(stack)) continue;
            int toTake = Math.min(remaining, stack.getCount());
            stack.shrink(toTake);
            remaining -= toTake;
        }
        return remaining;
    }

    public enum UpgradeResult {
        SUCCESS,
        FAIL_ALREADY_MAX,
        FAIL_CONDITION_NOT_MET,
        FAIL_MATERIAL_SHORTAGE,
        FAIL_FUNDS_SHORTAGE,
        FAIL_UNKNOWN
    }
}
