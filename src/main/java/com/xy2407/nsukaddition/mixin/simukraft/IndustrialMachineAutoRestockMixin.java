package com.xy2407.nsukaddition.mixin.simukraft;

import com.xy2407.nsukaddition.NsukAddition;
import com.xy2407.nsukaddition.common.autorestock.AutoRestockConfig;
import common.cn.kafei.simukraft.building.PlacedBuildingRecord;
import common.cn.kafei.simukraft.citizen.CitizenData;
import common.cn.kafei.simukraft.entity.CitizenEntity;
import common.cn.kafei.simukraft.industrial.IndustrialBoxData;
import common.cn.kafei.simukraft.industrial.IndustrialBoxManager;
import common.cn.kafei.simukraft.industrial.IndustrialDefinition;
import common.cn.kafei.simukraft.industrial.IndustrialInventoryService;
import common.cn.kafei.simukraft.industrial.IndustrialMachineOperationService;
import common.cn.kafei.simukraft.logistics.LogisticsManager;
import common.cn.kafei.simukraft.logistics.LogisticsWarehouseData;
import common.cn.kafei.simukraft.logistics.LogisticsWarehouseInventoryService;
import common.cn.kafei.simukraft.material.GenericContainerAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** 在工业机器启动前从仓库自动补货输入材料。 */
@Mixin(IndustrialMachineOperationService.class)
public abstract class IndustrialMachineAutoRestockMixin {

    @Unique
    private static final Logger LOG = LoggerFactory.getLogger("AutoRestockMixin");

    @Inject(method = "start", at = @At("HEAD"), remap = false)
    private static void nsuk$preRestockInputs(ServerLevel level,
                                              IndustrialBoxManager manager,
                                              IndustrialBoxData data,
                                              PlacedBuildingRecord building,
                                              IndustrialDefinition definition,
                                              IndustrialDefinition.RecipeDefinition recipe,
                                              CitizenData worker,
                                              CitizenEntity entity,
                                              IndustrialDefinition.StepDefinition step,
                                              BlockPos machinePos,
                                              List<BlockPos> inputContainers,
                                              List<BlockPos> outputContainers,
                                              @Coerce Object outputPolicy,
                                              String stepKey,
                                              long gameTime,
                                              CallbackInfoReturnable<IndustrialMachineOperationService.Result> cir) {
        BlockPos boxPos = data.boxPos();
        LOG.info("nsuk$preRestockInputs called for box at {}, autoRestock={}, inputContainers={}",
                boxPos, AutoRestockConfig.isEnabled(boxPos), inputContainers);

        if (!AutoRestockConfig.isEnabled(boxPos)) {
            return;
        }

        List<IndustrialDefinition.InputRequirement> machineInputs =
                step.inputsOverride() ? step.inputs() : recipe.inputs();
        LOG.info("machineInputs = {}", machineInputs);
        if (machineInputs == null || machineInputs.isEmpty()) {
            return;
        }
        if (inputContainers == null || inputContainers.isEmpty()) {
            LOG.warn("inputContainers is empty, cannot restock");
            return;
        }

        var existingPlan = IndustrialInventoryService.planInputs(level, inputContainers, machineInputs, 1);
        if (existingPlan.isPresent()) {
            LOG.info("planInputs OK, no restock needed");
            return;
        }
        LOG.info("planInputs empty, need to pull from warehouse");

        BlockPos buildingOrigin = building != null ? building.worldOrigin() : boxPos;
        LogisticsWarehouseData warehouse = findNearestWarehouse(level, buildingOrigin);
        if (warehouse == null) {
            LOG.warn("no warehouse found near {}", buildingOrigin);
            return;
        }
        BlockPos warehousePos = warehouse.boxPos();
        LOG.info("found warehouse at {}", warehousePos);

        List<ItemStack> existingInputs = collectContainerItems(level, inputContainers);
        LOG.info("existingInputs count = {}", existingInputs.size());

        for (IndustrialDefinition.InputRequirement req : machineInputs) {
            for (IndustrialDefinition.ItemRequirement leaf : req.itemLeaves()) {
                ItemStack template = leaf.spec().stack(Math.max(1, leaf.count()));
                if (template.isEmpty()) {
                    continue;
                }
                int existing = countMatching(existingInputs, template);
                int shortage = leaf.count() - existing;
                if (shortage <= 0) {
                    LOG.info("  {}: need={}, existing={}, SKIP", template.getItem(), leaf.count(), existing);
                    continue;
                }
                LOG.info("  {}: need={}, existing={}, shortage={} -> extract from warehouse",
                        template.getItem(), leaf.count(), existing, shortage);

                ItemStack extracted = LogisticsWarehouseInventoryService.extract(
                        level, warehousePos, template, shortage);
                if (!extracted.isEmpty()) {
                    LOG.info("  extracted {} from warehouse", extracted);
                    ItemStack remaining = insertIntoContainers(level, inputContainers, extracted);
                    if (remaining.isEmpty()) {
                        existingInputs.add(extracted);
                        LOG.info("  all inserted into input containers");
                    } else {
                        ItemStack deposited = extracted.copy();
                        deposited.shrink(remaining.getCount());
                        existingInputs.add(deposited);
                        LOG.info("  partial insert: deposited={}, remaining={}", deposited, remaining);
                    }
                } else {
                    LOG.warn("  extract returned empty!");
                }
            }
        }
    }

    @Unique
    private static LogisticsWarehouseData findNearestWarehouse(ServerLevel level, BlockPos pos) {
        return LogisticsManager.get(level).warehouses().stream()
                .min(Comparator.comparingDouble(w -> w.boxPos().distSqr(pos)))
                .orElse(null);
    }

    @Unique
    private static List<ItemStack> collectContainerItems(ServerLevel level, List<BlockPos> containers) {
        List<ItemStack> stacks = new ArrayList<>();
        for (BlockPos container : containers) {
            if (!level.isLoaded(container)) continue;
            for (GenericContainerAccess.SlotSnapshot slot : GenericContainerAccess.snapshotSlots(level, container)) {
                if (!slot.stack().isEmpty()) {
                    stacks.add(slot.stack());
                }
            }
        }
        return stacks;
    }

    @Unique
    private static int countMatching(List<ItemStack> stacks, ItemStack template) {
        int count = 0;
        for (ItemStack stack : stacks) {
            if (ItemStack.isSameItemSameComponents(stack, template)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    @Unique
    private static ItemStack insertIntoContainers(ServerLevel level, List<BlockPos> containers, ItemStack stack) {
        ItemStack remaining = stack.copy();
        for (BlockPos container : containers) {
            if (remaining.isEmpty()) break;
            if (!level.isLoaded(container)) continue;
            remaining = GenericContainerAccess.insert(level, container, remaining);
        }
        return remaining;
    }
}
