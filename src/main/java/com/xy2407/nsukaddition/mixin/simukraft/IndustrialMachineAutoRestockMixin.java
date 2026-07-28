package com.xy2407.nsukaddition.mixin.simukraft;

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

        if (!AutoRestockConfig.isEnabled(boxPos)) {
            return;
        }

        List<IndustrialDefinition.InputRequirement> machineInputs =
                step.inputsOverride() ? step.inputs() : recipe.inputs();
        if (machineInputs == null || machineInputs.isEmpty()) {
            return;
        }
        if (inputContainers == null || inputContainers.isEmpty()) {
            return;
        }

        var existingPlan = IndustrialInventoryService.planInputs(level, inputContainers, machineInputs, 1);
        if (existingPlan.isPresent()) {
            return;
        }

        BlockPos buildingOrigin = building != null ? building.worldOrigin() : boxPos;
        LogisticsWarehouseData warehouse = findNearestWarehouse(level, buildingOrigin);
        if (warehouse == null) {
            return;
        }
        BlockPos warehousePos = warehouse.boxPos();

        List<ItemStack> existingInputs = collectContainerItems(level, inputContainers);

        for (IndustrialDefinition.InputRequirement req : machineInputs) {
            for (IndustrialDefinition.ItemRequirement leaf : req.itemLeaves()) {
                ItemStack template = leaf.spec().stack(Math.max(1, leaf.count()));
                if (template.isEmpty()) {
                    continue;
                }
                int existing = countMatching(existingInputs, template);
                int shortage = leaf.count() - existing;
                if (shortage <= 0) {
                    continue;
                }

                ItemStack extracted = LogisticsWarehouseInventoryService.extract(
                        level, warehousePos, template, shortage);
                if (!extracted.isEmpty()) {
                    ItemStack remaining = insertIntoContainers(level, inputContainers, extracted);
                    if (remaining.isEmpty()) {
                        existingInputs.add(extracted);
                    } else {
                        ItemStack deposited = extracted.copy();
                        deposited.shrink(remaining.getCount());
                        existingInputs.add(deposited);
                    }
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
