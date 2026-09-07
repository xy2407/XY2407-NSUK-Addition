package com.xy2407.nsukaddition.mixin;

import com.xy2407.nsukaddition.common.advancement.NsukTriggers;
import common.cn.kafei.simukraft.building.BuildingBlockData;
import common.cn.kafei.simukraft.building.PlacedBuildingRecord;
import common.cn.kafei.simukraft.building.PlacedBuildingService;
import common.cn.kafei.simukraft.city.group.CityUserGroup;
import common.cn.kafei.simukraft.city.group.CityUserGroupService;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Set;

/** 建筑落成时按结构内包含的控制盒方块识别建筑类型，向该城市成员触发"建筑完成"成就。 */
@Mixin(value = PlacedBuildingService.class, remap = false)
public class BuildingBuiltTriggerMixin {

    @Unique
    private static final Set<Block> NSUK$CONTROL_BOXES = Set.of(
            common.cn.kafei.simukraft.registry.ModBlocks.COMMERCIAL_CONTROL_BOX.get(),
            common.cn.kafei.simukraft.registry.ModBlocks.INDUSTRIAL_CONTROL_BOX.get(),
            common.cn.kafei.simukraft.registry.ModBlocks.RESIDENTIAL_CONTROL_BOX.get(),
            common.cn.kafei.simukraft.registry.ModBlocks.MEDICAL_CONTROL_BOX.get(),
            common.cn.kafei.simukraft.registry.ModBlocks.MINERAL_DRILLING_CONTROL_BOX.get(),
            com.xy2407.nsukaddition.common.registry.ModBlocks.BREEDING_CONTROL_BOX.get(),
            com.xy2407.nsukaddition.common.registry.ModBlocks.RESTAURANT_CONTROL_BOX.get(),
            com.xy2407.nsukaddition.common.registry.ModBlocks.FOREIGN_TRADE_CONTROL_BOX.get()
    );

    @Inject(method = "register", at = @At("TAIL"), remap = false, require = 0)
    private static void nsuk$onBuildingRegistered(ServerLevel level, PlacedBuildingRecord record, CallbackInfo ci) {
        if (level == null || record == null || record.cityId() == null || record.blocks() == null) {
            return;
        }
        Block controlBox = null;
        for (BuildingBlockData b : record.blocks()) {
            if (b == null || b.state() == null) {
                continue;
            }
            Block block = b.state().getBlock();
            if (NSUK$CONTROL_BOXES.contains(block)) {
                controlBox = block;
                break;
            }
        }
        if (controlBox == null) {
            return;
        }
        Block finalBox = controlBox;
        var trigger = NsukTriggers.BUILDING_BUILT.get();
        CityUserGroupService.forEach(level, CityUserGroup.members(record.cityId()),
                player -> trigger.trigger(player, finalBox));
    }
}